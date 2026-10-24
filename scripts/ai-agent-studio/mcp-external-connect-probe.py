#!/usr/bin/env python3
"""ChatGPT 커넥터가 MCP 서버에 붙을 때의 공개 단계를 외부에서 그대로 따라가 본다.

401(WWW-Authenticate) -> protected resource metadata -> authorization server metadata(RFC 9207 iss) →
동적 등록(고정 callback) -> 인가 요청이 로그인/동의 화면으로 이어지는지까지 확인한다.
토큰·비밀값을 쓰지 않으며, 사용자 로그인·동의(실제 연결)는 하지 않는다. 등록 단계는 mcp_clients에 시험용 client
1건을 만든다(이름: "Claude connection probe").

사용: python scripts/ai-agent-studio/mcp-external-connect-probe.py [https://feel2real.co.kr]
"""
import base64
import hashlib
import json
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "https://feel2real.co.kr").rstrip("/")
STABLE_CALLBACK = "https://chatgpt.com/connector_platform_oauth_redirect"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


opener = urllib.request.build_opener(NoRedirect)


def call(method, url, body=None, headers=None):
    req = urllib.request.Request(url, data=body, method=method, headers=headers or {})
    try:
        with opener.open(req, timeout=20) as r:
            return r.status, dict(r.headers), r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode("utf-8", "replace")


results = []


def check(name, ok, detail=""):
    results.append({"step": name, "pass": bool(ok), "detail": detail})
    print(("PASS " if ok else "FAIL ") + name + (f" - {detail}" if detail else ""))
    return ok


# 1. 토큰 없이 initialize -> 401 + resource_metadata
init = json.dumps(
    {
        "jsonrpc": "2.0",
        "id": 0,
        "method": "initialize",
        "params": {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "probe", "version": "1"}},
    }
).encode()
s, h, _ = call("POST", BASE + "/mcp", init, {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"})
www = {k.lower(): v for k, v in h.items()}.get("www-authenticate", "")
check("unauthenticated initialize -> 401 with resource_metadata", s == 401 and "resource_metadata=" in www, f"{s} {www}")
prm_url = www.split('resource_metadata="')[1].split('"')[0] if 'resource_metadata="' in www else BASE + "/.well-known/oauth-protected-resource"

# 2. protected resource metadata
s, _, b = call("GET", prm_url)
prm = json.loads(b) if s == 200 else {}
check("protected resource metadata", s == 200 and prm.get("resource") == BASE + "/mcp", f"{s} resource={prm.get('resource')}")
issuer = (prm.get("authorization_servers") or [BASE])[0]

# 3. authorization server metadata (RFC 8414) + RFC 9207
s, _, b = call("GET", issuer.rstrip("/") + "/.well-known/oauth-authorization-server")
asm = json.loads(b) if s == 200 else {}
check("authorization server metadata", s == 200 and asm.get("issuer") == issuer, f"{s} issuer={asm.get('issuer')}")
check("PKCE S256 advertised", "S256" in asm.get("code_challenge_methods_supported", []))
check(
    "RFC 9207 iss advertised (ChatGPT uses stable callback)",
    asm.get("authorization_response_iss_parameter_supported") is True,
)
scopes = asm.get("scopes_supported", [])
check("scopes limited to studio collaboration", set(scopes) <= {"studio:read", "studio:propose", "originals:read", "offline_access"}, " ".join(scopes))

# 4. 동적 등록: 고정 callback
reg = json.dumps(
    {
        "client_name": "Claude connection probe",
        "redirect_uris": [STABLE_CALLBACK],
        "grant_types": ["authorization_code", "refresh_token"],
        "response_types": ["code"],
        "token_endpoint_auth_method": "none",
    }
).encode()
s, _, b = call("POST", asm.get("registration_endpoint", BASE + "/mcp/oauth/register"), reg, {"Content-Type": "application/json"})
client = json.loads(b).get("client_id") if s == 200 else None
check("dynamic client registration with stable ChatGPT callback", bool(client), f"{s} {b[:120] if not client else ''}")

# 5. 인가 요청 -> 로그인 안내(비로그인) 또는 동의 화면. 400이면 ChatGPT 화면에서 연결 실패로 보인다.
if client:
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    q = urllib.parse.urlencode(
        {
            "response_type": "code",
            "client_id": client,
            "redirect_uri": STABLE_CALLBACK,
            "state": "probe-state",
            "code_challenge": challenge,
            "code_challenge_method": "S256",
            "resource": BASE + "/mcp",
            "scope": " ".join(scopes),
        }
    )
    s, h, b = call("GET", asm["authorization_endpoint"] + "?" + q)
    location = {k.lower(): v for k, v in h.items()}.get("location", "")
    check(
        "authorization request reaches login/consent screen",
        (s in (302, 303) and "login=required" in location and "returnUrl=" in location) or (s == 200 and "nonce" in b),
        f"{s} {location[:100]}",
    )

print(json.dumps({"base": BASE, "passed": all(r["pass"] for r in results), "results": results}, ensure_ascii=False, indent=1))
sys.exit(0 if all(r["pass"] for r in results) else 1)
