#!/usr/bin/env bash
# 배포 후 읽기 전용 점검(운영 호스트 f2r-app에서만 실행). 쓰기·재시작 없음, 비밀값·개인정보 출력 없음.
# 1) 지정 시각 이후 f2r-app 로그의 WARN/ERROR를 수준·로거·메시지 앞부분으로 묶어 건수만 출력(숫자·이메일 가림)
# 2) 제작실(studio_*) 프로젝트·자료·리비전·승인 포인터·멤버 건수(SELECT, READ ONLY 트랜잭션)
set -uo pipefail

# 로그 한 줄을 "수준 로거 : 메시지"로 줄이고 이메일·비밀값·숫자를 가린다(표준입력→표준출력).
normalize_log() {
  grep -E '^[0-9TZ:.-]+ +(WARN|ERROR) ' \
    | sed -E 's/^[0-9TZ:.-]+ +(WARN|ERROR) +[0-9]+ --- \[[^]]*\] \[[^]]*\] +([^ ]+) +: (.*)$/\1 \2 : \3/' \
    | sed -E 's/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+/<email>/g; s/((KEY|SECRET|TOKEN|PASSWORD|PASS|COOKIE)[A-Za-z_]*[=:])[^ ]+/\1<masked>/Ig; s/[0-9]+/#/g' \
    | cut -c1-170
}
# 테스트에서 source하면 함수만 정의하고 끝낸다. SSM처럼 표준입력으로 실행되면 BASH_SOURCE가 비어 있다.
[[ -z "${BASH_SOURCE[0]:-}" || "${BASH_SOURCE[0]}" == "$0" ]] || return 0

SINCE="${DIAG_SINCE:-2026-10-23 02:17:00 UTC}"
printf '=== f2r-app WARN/ERROR since %s ===\n' "$SINCE"
journalctl -u f2r-app --since "$SINCE" --no-pager -o cat 2>/dev/null > /tmp/f2r-postdeploy.log || true
printf 'total WARN=%s ERROR=%s\n' "$(grep -c -E '^[0-9TZ:.-]+ +WARN ' /tmp/f2r-postdeploy.log)" "$(grep -c -E '^[0-9TZ:.-]+ +ERROR ' /tmp/f2r-postdeploy.log)"
normalize_log < /tmp/f2r-postdeploy.log | sort | uniq -c | sort -rn | head -40
printf -- '--- exception class names (counts)\n'
grep -oE '(Caused by: )?[a-z][a-zA-Z0-9_.]+\.[A-Z][A-Za-z0-9]*(Exception|Error)' /tmp/f2r-postdeploy.log | sort | uniq -c | sort -rn | head -15
rm -f /tmp/f2r-postdeploy.log
printf '=== studio DB counts (read only) ===\n'
python3 - <<'PY'
import os,shlex,subprocess,tempfile
vals={}
for line in open('/etc/f2r/env'):
    line=line.strip()
    if line.startswith('export '): line=line[7:]
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1)
    if k in ('DB_HOST','DB_USER','DB_USERNAME','DB_PASSWORD','DB_NAME','DB_PORT'):
        p=shlex.split(v); vals[k]=p[0] if p else ''
host=vals.get('DB_HOST'); user=vals.get('DB_USER') or vals.get('DB_USERNAME'); pw=vals.get('DB_PASSWORD')
if not all((host,user,pw)): raise SystemExit('DB credential fields unavailable; no values printed')
client=next((p for p in ('/usr/bin/mysql','/usr/bin/mariadb') if os.path.isfile(p)),None)
if not client: raise SystemExit('MySQL client unavailable')
q=lambda v:'"'+v.replace('\\','\\\\').replace('"','\\"')+'"'
SQL="""SET SESSION TRANSACTION READ ONLY;
START TRANSACTION READ ONLY;
SELECT 'projects', COUNT(*) FROM studio_projects;
SELECT 'project', id, CHAR_LENGTH(title) AS title_chars, created_at FROM studio_projects ORDER BY created_at;
SELECT 'files_by_project', project_id, COUNT(*), COALESCE(SUM(size_bytes),0) FROM studio_files GROUP BY project_id;
SELECT 'items_by_project', project_id, COUNT(*), SUM(approved_id IS NOT NULL) AS approved FROM studio_items GROUP BY project_id;
SELECT 'source_import_registered', COUNT(*) FROM studio_items WHERE project_id='ai-agent-part1' AND item_key LIKE 'source-09d62dee-%';
SELECT 'character_approved_items', COUNT(*), SUM(approved_id IS NOT NULL) FROM studio_items WHERE project_id='ai-agent-part1' AND item_key LIKE 'character-approved-%';
SELECT 'revisions_by_project', project_id, COUNT(*) FROM studio_revisions GROUP BY project_id;
SELECT 'members_by_permission', project_id, permission, COUNT(*) FROM studio_members GROUP BY project_id, permission;
SELECT 'events_by_action', action, COUNT(*) FROM studio_events GROUP BY action;
SELECT 'mcp_clients', COUNT(*) FROM mcp_clients;
SELECT 'mcp_tokens', COUNT(*) FROM mcp_tokens;
ROLLBACK;"""
cnf=tempfile.NamedTemporaryFile('w',delete=False,suffix='.cnf',dir='/dev/shm' if os.path.isdir('/dev/shm') else None)
os.chmod(cnf.name,0o600)
cnf.write('[client]\nhost=%s\nuser=%s\npassword=%s\nport=%s\n'%(q(host),q(user),q(pw),vals.get('DB_PORT') or '3306')); cnf.close()
try:
    r=subprocess.run([client,'--defaults-extra-file='+cnf.name,'--batch','--skip-column-names',vals.get('DB_NAME') or 'f2r_db'],input=SQL,capture_output=True,text=True,timeout=40)
    print(r.stdout[-4000:])
    if r.returncode: print('db query failed (details suppressed)')
finally: os.unlink(cnf.name)
PY
