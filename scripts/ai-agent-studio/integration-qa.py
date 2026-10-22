#!/usr/bin/env python3
"""Actual local #853/#854/#855 API boundary test; synthetic fixtures, no bank calls.
Requires the two apps and task-only DB described in e2e/unified-assistant/README.md.
OAuth redirects are parsed, never visited. No cookies/tokens or financial rows are saved.
"""
import base64, hashlib, json, re, subprocess
from pathlib import Path
from urllib.parse import urlencode, urlparse, parse_qs
import httpx

BASE = 'http://localhost:8080'
ROOT = Path(__file__).resolve().parents[2]
CALLBACK = 'https://claude.ai/api/mcp/auth_callback'
results = []

def passed(name):
    results.append({'name': name, 'pass': True})

def login(login_id):
    c = httpx.Client(base_url=BASE, trust_env=False, headers={'Origin': BASE}, timeout=30)
    c.post('/api/auth/login', json={'loginId': login_id, 'password': 'admin1234'}).raise_for_status()
    return c

def authorize(c):
    r = c.post('/mcp/oauth/register', json={'client_name': 'Synthetic integration QA', 'redirect_uris': [CALLBACK], 'token_endpoint_auth_method': 'none'})
    r.raise_for_status(); cid = r.json()['client_id']; verifier = 'qa-' + 'x' * 60
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
    page = c.get('/mcp/oauth/authorize', params={'client_id': cid, 'redirect_uri': CALLBACK, 'response_type': 'code', 'state': 'synthetic-only', 'code_challenge_method': 'S256', 'code_challenge': challenge, 'resource': BASE + '/mcp'})
    page.raise_for_status(); nonce = re.search(r'name="nonce" value="([^"]+)"', page.text).group(1)
    consent = c.post('/mcp/oauth/authorize', content=urlencode([('nonce', nonce), ('scopes', 'catalog:read'), ('scopes', 'academy:read'), ('scopes', 'easy:read'), ('sensitiveAcknowledged', 'true')]), headers={'Content-Type': 'application/x-www-form-urlencoded'})
    assert consent.status_code == 303, consent.status_code
    code = parse_qs(urlparse(consent.headers['location']).query)['code'][0]
    r = c.post('/mcp/oauth/token', data={'grant_type': 'authorization_code', 'client_id': cid, 'redirect_uri': CALLBACK, 'code': code, 'code_verifier': verifier, 'resource': BASE + '/mcp'})
    r.raise_for_status(); return r.json()['access_token']

def read(token, service, endpoint, paths=None, query=None):
    # MCP must succeed without the browser login cookie.
    with httpx.Client(base_url=BASE, trust_env=False, timeout=30) as c:
        r = c.post('/mcp', headers={'Authorization': 'Bearer ' + token}, json={'jsonrpc': '2.0', 'id': 1, 'method': 'tools/call', 'params': {'name': 'read_' + service, 'arguments': {'endpointId': endpoint, 'pathParams': paths or {}, 'query': query or {}}}})
        r.raise_for_status(); return r.json()['result']

def ledger_count():
    # Fixed local container and test schema only. Never accepts a remote DB URL.
    out = subprocess.check_output(['docker', 'exec', 'unified-ai-mysql', 'mysql', '-N', '-uroot', '-punified-local-only', '-e', 'SELECT COUNT(*) FROM f2r_unified_test.easy_transactions'], stderr=subprocess.DEVNULL)
    return int(out.strip())

admin, other = login('admin'), login('test-tutor')
try:
    a, b = authorize(admin), authorize(other)
    r = read(a, 'academy', 'GET /academycraft/api/rea/{academyId}/students', {'academyId': '7'})
    assert not r['isError'], r
    assert 'authKey' not in json.dumps(r), 'upstream credential leaked'
    passed('AcademyCraft actual signed GET succeeds; authKey redacted')
    r = read(b, 'academy', 'GET /academycraft/api/rea/{academyId}/students', {'academyId': '7'})
    assert r['isError'], r
    passed('Different academy user cannot read AcademyCraft academy 7')
    for token, business in [(a, 1), (b, 2)]:
        r = read(token, 'easy', 'GET /api/mcp/finance/summary', query={'businessId': str(business), 'month': '2026-10'})
        assert not r['isError'], r
        d = json.loads(r['content'][0]['text'])['data']
        assert set(d) == {'businessId','month','income','expense','profit','receivable','ledgerSavedCount','openReviewCount'}, d.keys()
    passed('Each permitted business exposes only eight summary fields')
    assert read(b, 'easy', 'GET /api/mcp/finance/summary', query={'businessId': '1', 'month': '2026-10'})['isError']
    passed('Foreign business summary denied')
    for service, endpoint in [('easy','GET /api/admin/easy/transactions'), ('easy','GET /api/admin/easy/financial-collector/previews/{id}'), ('easy','POST /api/admin/easy/financial-collector/previews/{id}/approve-and-commit'), ('easy','POST /api/dashboard/assistant/v1/drafts/{id}/approve')]:
        assert read(a, service, endpoint, {'id': 'synthetic'})['isError'], endpoint
    passed('Even ADMIN MCP cannot read raw finance/collector previews or invoke either approval POST')
    prefix = '/api/admin/easy/financial-collector/previews'
    r = admin.post(prefix, params={'businessId': 1, 'dataset': 'BANK_ACCOUNT', 'accountRef': 'SYNTHETIC_REVIEW_855', 'from': '2026-10-01', 'to': '2026-10-31', 'userApprovedUpload': 'true'}, files={'file': ('synthetic-bank.csv', (ROOT/'docs/easy/financial-collector/sample-bank.csv').read_bytes(), 'text/csv')})
    r.raise_for_status(); cid = r.json()['collectionId']; before = ledger_count()
    target = prefix + '/' + cid + '/approve-and-commit'
    r = admin.post(target, params={'businessId': 1}, json={'userApprovedLedgerCommit': False, 'reviewHash': ''})
    assert r.status_code == 400 and 'USER_LEDGER_APPROVAL_REQUIRED' in r.text, (r.status_code, r.text)
    with httpx.Client(base_url=BASE, trust_env=False) as external:
        r = external.post(target, params={'businessId': 1}, headers={'Authorization': 'Bearer ' + a, 'Origin': BASE}, json={'userApprovedLedgerCommit': True, 'reviewHash': ''})
        assert r.status_code in (401,403), r.status_code
    assert ledger_count() == before
    passed('Actual #853 endpoint rejects absent approval and external MCP bearer; ledger count unchanged')
finally:
    admin.close(); other.close()

report = {'environment': 'loopback BuildIt + actual AcademyCraft + isolated MySQL; synthetic financial CSV; no financial institution access', 'checks': results, 'passed': len(results), 'actualChatGPTClaudeAccounts': 'NOT VERIFIED'}
(ROOT/'docs/ai-agent-studio/integration-results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
print(json.dumps(report, ensure_ascii=False, indent=2))
