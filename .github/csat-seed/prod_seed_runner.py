"""PR #430 DAY-1 fixtures production seed runner — 기존 merged 스크립트를 그대로 실행한다.

재분석·재생성 없음. 서버(127.0.0.1:8000)에서 서명 identity 헤더를 요청마다 붙여
register_csat_day1.py → seed_csat_dna_day1.py → seed_csat_expert_lee.py 순으로 실행하고
단계별 운영 DB count(read-only)를 출력한다.

WIKI_IDENTITY_SECRET은 /etc/buildit-wiki-identity.env를 source한 셸에서 주입된다.
"""
import argparse
import hashlib
import hmac
import os
import runpy
import sqlite3
import sys
import time
from pathlib import Path

import httpx

USER_ID = os.getenv('CSAT_SEED_USER_ID', '4')
LOGIN_ID = os.getenv('CSAT_SEED_LOGIN_ID', 'admin-f2r')
ROLE = 'ADMIN'
SECRET = os.getenv('WIKI_IDENTITY_SECRET') or os.getenv('JWT_SECRET') or ''
if not SECRET:
    sys.exit('WIKI_IDENTITY_SECRET/JWT_SECRET 미설정 — 서명 identity 불가, 중단.')

TABLES = ('csat_exams', 'csat_exam_resources', 'csat_questions', 'csat_question_dna',
          'csat_dna_claims', 'csat_dna_comparisons', 'csat_expert_sources')


def _signed_send(self, request, **kwargs):
    ts = str(int(time.time()))
    payload = f'{USER_ID}\n{LOGIN_ID}\n{ROLE}\n{ts}'.encode()
    signature = hmac.new(SECRET.encode(), payload, hashlib.sha256).hexdigest()
    request.headers['x-buildit-user-id'] = USER_ID
    request.headers['x-buildit-login-id'] = LOGIN_ID
    request.headers['x-buildit-role'] = ROLE
    request.headers['x-buildit-timestamp'] = ts
    request.headers['x-buildit-signature'] = signature
    return _ORIG_SEND(self, request, **kwargs)


_ORIG_SEND = httpx.Client.send
httpx.Client.send = _signed_send


def counts(db_path: str, label: str) -> None:
    conn = sqlite3.connect(f'file:{db_path}?mode=ro', uri=True)
    print(f'--- counts[{label}] ---')
    for table in TABLES:
        try:
            print(f'  {table}={conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]}')
        except sqlite3.Error:
            print(f'  {table}=MISSING')
    for source, sql in (
        ('dna_provenance', "SELECT provenance, COUNT(*) FROM csat_question_dna GROUP BY provenance"),
        ('claims_provenance', "SELECT provenance, COUNT(*) FROM csat_dna_claims GROUP BY provenance"),
        ('comparisons_provenance', "SELECT provenance, COUNT(*) FROM csat_dna_comparisons GROUP BY provenance"),
        ('expert_analysis_total', "SELECT 'EXPERT_ANALYSIS', (SELECT COUNT(*) FROM csat_question_dna WHERE provenance='EXPERT_ANALYSIS')+(SELECT COUNT(*) FROM csat_dna_claims WHERE provenance='EXPERT_ANALYSIS')+(SELECT COUNT(*) FROM csat_dna_comparisons WHERE provenance='EXPERT_ANALYSIS')"),
    ):
        for tag, value in conn.execute(sql).fetchall():
            print(f'  {source}:{tag}={value}')
    conn.close()


def run_script(path: Path, argv: list[str]) -> None:
    print(f'===== RUN {path.name} {argv[1:]} =====')
    sys.argv = [str(path)] + argv[1:]
    runpy.run_path(str(path), init_globals={'__file__': str(path)}, run_name='__main__')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://127.0.0.1:8000')
    parser.add_argument('--sources', default='/tmp/csat-seed/sources', help='원본 PDF 디렉터리(없으면 register가 resource만 skip)')
    parser.add_argument('--root', required=True, help='wiki-backend-src 루트')
    parser.add_argument('--db', default='/opt/buildit-wiki/data/app.db')
    args = parser.parse_args()
    scripts = Path(args.root) / 'scripts'

    counts(args.db, 'before')
    run_script(scripts / 'register_csat_day1.py', ['x', '--base', args.base, '--sources', args.sources])
    counts(args.db, 'after-register')
    run_script(scripts / 'seed_csat_dna_day1.py', ['x', '--base', args.base])
    counts(args.db, 'after-dna')
    run_script(scripts / 'seed_csat_expert_lee.py', ['x', '--base', args.base])
    counts(args.db, 'final')
    print('CSAT DAY-1 시드 러너 완료')


if __name__ == '__main__':
    main()
