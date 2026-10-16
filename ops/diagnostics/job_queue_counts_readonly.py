"""publishing_jobs / hwpx_jobs 상태별 건수만 읽기 전용으로 조회한다(승인 범위).
접속 정보는 기존 앱 설정(/etc/f2r/env의 DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD)을 읽어 쓰되 값은 출력하지 않는다.
개인정보·작업 본문 조회 금지: 상태 컬럼과 COUNT(*)만 선택한다."""
import json, os, re, shutil, subprocess


def parse_env(path):
    d = {}
    try:
        for line in open(path, encoding='utf-8', errors='replace'):
            line = re.sub(r'^\s*export\s+', '', line.strip())
            k, s, v = line.partition('=')
            if s and not line.startswith('#'):
                d[k.strip()] = v.strip().strip('"').strip("'")
    except Exception:  # noqa: BLE001
        pass
    return d


env = parse_env('/etc/f2r/env')
host, port, name, user, pw = (env.get('DB_HOST'), env.get('DB_PORT') or '3306', env.get('DB_NAME') or 'f2r_db', env.get('DB_USER'), env.get('DB_PASSWORD'))
out = {'creds_present': {'DB_HOST': bool(host), 'DB_USER': bool(user), 'DB_PASSWORD': bool(pw)}, 'mysql_client': bool(shutil.which('mysql')), 'tables': {}}
if host and user and pw and out['mysql_client']:
    for table in ('publishing_jobs', 'hwpx_jobs'):
        sql = f"SET SESSION TRANSACTION READ ONLY; SELECT status, COUNT(*) FROM {table} GROUP BY status;"
        try:
            p = subprocess.run(['mysql', '--connect-timeout=10', '-h', host, '-P', port, '-u', user, name, '-N', '-B', '-e', sql],
                               env=dict(os.environ, MYSQL_PWD=pw), capture_output=True, text=True, timeout=40)
            rows = {}
            for line in p.stdout.strip().splitlines():
                a, _, b = line.partition('\t')
                if a:
                    rows[a] = int(b) if b.isdigit() else b
            m = re.search(r'ERROR (\d+)', p.stderr or '')
            out['tables'][table] = {'rc': p.returncode, 'counts_by_status': rows, 'mysql_error_code': m.group(1) if m else None}
        except Exception as e:  # noqa: BLE001
            out['tables'][table] = {'error_class': type(e).__name__}
print(json.dumps(out, ensure_ascii=False, indent=1))
