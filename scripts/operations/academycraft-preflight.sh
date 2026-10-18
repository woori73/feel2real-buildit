#!/usr/bin/env bash
# 신규 호스트에서만 실행하는 읽기 전용 사전점검. 운영 데이터 변경 없음.
set -euo pipefail
printf '=== host resources ===\n'
nproc
free -m
df -m /
uptime
ps -eo comm,rss,pcpu --sort=-rss | head -12
systemctl show f2r-app buildit-wiki -p Id -p ActiveState -p MemoryCurrent -p CPUUsageNSec
ss -ltn | grep -E ':(8080|8082|8000)\b' || true
for f in /etc/academycraft/env /opt/academycraft/academycraft.jar; do
  if [ -e "$f" ]; then stat -c '%n mode=%a owner=%U' "$f"; else echo "$f absent"; fi
done
printf '=== DB connection capacity and permissions (read only) ===\n'
python3 - <<'PY'
import os,shlex,subprocess,tempfile
values={}
for line in open('/etc/f2r/env'):
 line=line.strip()
 if not line or line.startswith('#') or '=' not in line:continue
 k,v=line.split('=',1)
 if k in ('DB_HOST','DB_USER','DB_USERNAME','DB_PASSWORD','DB_PORT'):
  parts=shlex.split(v);values[k]=parts[0] if parts else ''
host=values.get('DB_HOST');user=values.get('DB_USER') or values.get('DB_USERNAME');password=values.get('DB_PASSWORD')
if not all((host,user,password)):raise SystemExit('DB credential fields unavailable; no values printed')
client=next((p for p in ('/usr/bin/mysql','/usr/bin/mariadb') if os.path.isfile(p)),None)
if not client:raise SystemExit('MySQL client unavailable')
def quote(v):return '"'+v.replace('\\','\\\\').replace('"','\\"').replace('\n','\\n')+'"'
f=tempfile.NamedTemporaryFile(mode='w',prefix='academy-preflight-',delete=False)
try:
 os.chmod(f.name,0o600)
 f.write('[client]\nhost='+quote(host)+'\nuser='+quote(user)+'\npassword='+quote(password)+'\nconnect-timeout=10\n');f.close()
 sql="SELECT VERSION(); SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Max_used_connections'); SHOW GLOBAL VARIABLES WHERE Variable_name IN ('max_connections'); SHOW GRANTS FOR CURRENT_USER(); SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('academycraft','academycraft_restore_test');"
 subprocess.run([client,'--defaults-extra-file='+f.name,'--batch','--skip-column-names','-e',sql],check=True)
finally:
 f.close();os.unlink(f.name)
PY
