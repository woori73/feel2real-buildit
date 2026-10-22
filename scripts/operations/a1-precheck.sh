#!/usr/bin/env bash
# A1(블루·그린) 사전 점검 — 운영 호스트(f2r-app)에서 읽기 전용으로만 실행한다.
# 쓰기·재시작·포트 점유·DB 쓰기 없음. 비밀값은 출력하지 않는다(DB_HOST는 첫 라벨=인스턴스 식별자만).
set -uo pipefail
LIB_B64="${A1_LIB_B64:-}"
if [ -n "$LIB_B64" ]; then echo "$LIB_B64" | base64 -d >/tmp/a1-gates-lib.sh && source /tmp/a1-gates-lib.sh; fi

printf '=== 1. memory (현재) ===\n'
free -m
AVAIL=$(awk '/MemAvailable/ {printf "%d", $2/1024}' /proc/meminfo)
printf 'MemAvailableMB=%s\n' "$AVAIL"
declare -F a1_mem_gate >/dev/null && a1_mem_gate "$AVAIL" || true
printf -- '--- swap\n'; swapon --show || true
printf -- '--- 서비스별 cgroup 메모리(현재/피크/상한)\n'
for u in f2r-app academycraft-app f2r-academycraft admissions-embedding wiki wiki-app; do
  systemctl cat "$u" >/dev/null 2>&1 || continue
  printf '%s ' "$u"; systemctl show "$u" -p ActiveState -p MemoryCurrent -p MemoryPeak -p MemoryMax -p NRestarts -p ActiveEnterTimestamp | tr '\n' ' '; echo
done
printf -- '--- 프로세스 RSS 상위\n'
ps -eo pid,user,rss,vsz,nlwp,comm,args --sort=-rss | head -8 | cut -c1-170

printf '=== 2. f2r-app JVM ===\n'
PID=$(systemctl show f2r-app -p MainPID --value 2>/dev/null)
if [ -n "${PID:-}" ] && [ "$PID" != 0 ]; then
  grep -E 'VmHWM|VmRSS|Threads' "/proc/$PID/status"
  tr '\0' ' ' <"/proc/$PID/cmdline" | grep -oE -- '-Xm[sx][0-9]+[mMgG]|-XX:[A-Za-z]+=?[0-9A-Za-z]*' | tr '\n' ' '; echo
  JCMD=$(command -v jcmd || ls /usr/lib/jvm/*/bin/jcmd 2>/dev/null | head -1)
  if [ -n "${JCMD:-}" ]; then
    sudo -u "$(ps -o user= -p "$PID")" "$JCMD" "$PID" GC.heap_info 2>&1 | head -12
    sudo -u "$(ps -o user= -p "$PID")" "$JCMD" "$PID" VM.flags 2>&1 | grep -oE 'MaxHeapSize=[0-9]+|MaxMetaspaceSize=[0-9]+|UseG1GC|UseSerialGC|MaxRAM[A-Za-z]*=[0-9]+' | tr '\n' ' '; echo
  else echo 'jcmd 없음(JRE만 설치) — 힙 사용량은 actuator 로 대체'; fi
else echo 'f2r-app MainPID 없음'; fi
printf -- '--- actuator 지표(노출된 것만)\n'
for m in jvm.memory.used jvm.memory.max hikaricp.connections.max hikaricp.connections.active hikaricp.connections.pending process.uptime; do
  printf '%s: ' "$m"; curl -fsS --max-time 4 "http://127.0.0.1:8080/actuator/metrics/$m" 2>/dev/null | head -c 220 || printf 'not exposed'; echo
done
printf -- '--- 커널 OOM 7일\n'
journalctl -k --since '7 days ago' --no-pager 2>/dev/null | grep -Ei 'out of memory|oom-kill|killed process' | tail -3 || true
echo '(끝)'

printf '=== 3. systemd 유닛 핵심 설정(비밀 제외) ===\n'
systemctl cat f2r-app 2>/dev/null | grep -E '^(ExecStart|Restart|RestartSec|TimeoutStopSec|TimeoutStartSec|MemoryMax|User|WorkingDirectory|EnvironmentFile)=' || true

printf '=== 4. 포트·바인딩(8083 등) ===\n'
ss -ltnH | awk '{print $4}' | grep -E ':(8000|8080|8081|8082|8083|3306)$' | sort || true
printf -- '--- 8083 사용 여부: '; (ss -ltnH | grep -q ':8083$' && echo IN-USE) || echo free
printf -- '--- 로컬 방화벽\n'; (iptables -S 2>/dev/null | head -5) || echo 'iptables 조회 불가'

printf '=== 5. 환경변수 이름만(스케줄러·워커 스위치) ===\n'
grep -oE '^(export )?(PUBLISHING_WORKER_ENABLED|HWPX_WORKER_ENABLED|APP_NEWSTART_DISCOVERY_SCHEDULER_ENABLED|APP_STATISTICS_ALB_ENABLED|APP_VIDEO_OPTIMIZATION_ENABLED|EASY_COLLECTION_CRON|CHART_RE_RESEARCH_CRON|KIS_[A-Z_]*|WEMADE_[A-Z_]*|SPRING_DATASOURCE_HIKARI[A-Z_]*|SERVER_ADDRESS|SERVER_PORT)=' /etc/f2r/env 2>/dev/null | sed 's/^export //' | sort -u | tr '\n' ' '; echo

printf '=== 6. DB 연결·Flyway (읽기 전용) ===\n'
python3 - <<'PY'
import os,shlex,subprocess
vals={}
for line in open('/etc/f2r/env'):
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); k=k.replace('export ','').strip()
    if k in ('DB_HOST','DB_USER','DB_USERNAME','DB_PASSWORD','DB_NAME','DB_PORT'):
        p=shlex.split(v); vals[k]=p[0] if p else ''
host=vals.get('DB_HOST'); user=vals.get('DB_USER') or vals.get('DB_USERNAME'); pw=vals.get('DB_PASSWORD')
if not all((host,user,pw)): raise SystemExit('DB credential fields unavailable')
print('app DB instance identifier (host first label):', host.split('.')[0])
client=next((p for p in ('/usr/bin/mysql','/usr/bin/mariadb') if os.path.isfile(p)),None)
if not client: raise SystemExit('MySQL client unavailable')
import tempfile
SQL="""SET SESSION TRANSACTION READ ONLY;
SELECT 'max_connections', @@max_connections;
SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Max_used_connections','Uptime');
SELECT 'conn by user/db', user, db, COUNT(*) FROM information_schema.processlist GROUP BY user, db;
SELECT 'flyway latest', installed_rank, version, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
SELECT 'flyway pending-style check: failed rows', COUNT(*) FROM flyway_schema_history WHERE success=0;"""
cnf=tempfile.NamedTemporaryFile('w',delete=False,suffix='.cnf'); os.chmod(cnf.name,0o600)
q=lambda v:'"'+v.replace('\\','\\\\').replace('"','\\"')+'"'
cnf.write('[client]\nhost=%s\nuser=%s\npassword=%s\nport=%s\n'%(q(host),q(user),q(pw),vals.get('DB_PORT') or '3306')); cnf.close()
try:
    r=subprocess.run([client,'--defaults-extra-file='+cnf.name,vals.get('DB_NAME') or 'f2r_db','-t','-e',SQL],capture_output=True,text=True,timeout=40)
    print(r.stdout[-3500:]); print(r.stderr[-300:].replace(pw,'<masked>'))
finally: os.unlink(cnf.name)
PY
