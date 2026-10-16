"""DesignCraft/HWPX 진행 작업과 재시작 안전성 읽기 전용 점검. 비밀값·문서 내용·사용자 식별 정보는 출력하지 않는다.
출력: 프로세스 이름·경과 초 수, 서비스 상태·health 코드, 작업 상태별 건수(집계), 백업 파일 존재/크기, 디스크 여유.
DB는 SELECT 집계(읽기 전용 세션)만 하고 접속 정보는 읽어도 출력하지 않는다."""
import json, os, re, shutil, subprocess, time


def sh(cmd, timeout=25):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or '').strip()
    except Exception as e:  # noqa: BLE001
        return 'ERR:' + type(e).__name__


out = {'checked_at_utc': time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime())}

procs = []
for line in sh("ps -eo pid=,etimes=,comm=,args= | grep -Ei 'designcraft-cli|hwpx|soffice|libreoffice|publishing' | grep -v grep").splitlines():
    parts = line.split(None, 3)
    if len(parts) >= 3:
        procs.append({'comm': parts[2], 'elapsed_s': parts[1], 'args_basename': os.path.basename(parts[3].split()[0]) if len(parts) > 3 and parts[3].split() else ''})
out['processes_designcraft_hwpx'] = {'count': len(procs), 'items': procs[:12]}
out['designcraft_cli_running_count'] = len([p for p in procs if 'designcraft-cli' in (p['comm'] + p['args_basename'])])

out['services'] = {'f2r-app': sh('systemctl is-active f2r-app.service'), 'buildit-wiki': sh('systemctl is-active buildit-wiki.service')}
out['health'] = {'spring_actuator': sh("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 120"),
                 'wiki_http': sh("curl -s -o /dev/null -m 5 -w '%{http_code}' http://127.0.0.1:8000/api/health")}

jar = '/home/ec2-user/app.jar'
prev = '/home/ec2-user/app.jar.previous'
out['rollback_ready'] = {
    'app_jar_exists': os.path.exists(jar), 'app_jar_sha256': sh(f"sha256sum {jar} | cut -d' ' -f1", 60),
    'previous_jar_exists': os.path.exists(prev),
    'previous_jar_size': os.path.getsize(prev) if os.path.exists(prev) else None,
    'previous_jar_mtime': sh(f"stat -c %y {prev} 2>/dev/null"),
    'env_backups_count': sh("ls /etc/f2r/env.bak-* 2>/dev/null | wc -l"),
    'wiki_app_dir_exists': os.path.isdir('/opt/buildit-wiki/app'),
}
out['disk_free_gib'] = {d: round(shutil.disk_usage(d).free / 2**30, 2) for d in ('/', '/home', '/opt', '/tmp') if os.path.exists(d)}
out['memory_avail_mib'] = sh("awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo")

out['log_30min_designcraft_hwpx_lines'] = sh("journalctl -u f2r-app.service --since '-30 min' --no-pager -o cat 2>/dev/null | grep -ciE 'publishing|designcraft|hwpx' || true", 60)
out['log_30min_cli_fail_lines'] = sh("journalctl -u f2r-app.service --since '-30 min' --no-pager -o cat 2>/dev/null | grep -ciE 'GLIBC|조판 엔진' || true", 60)
pub = '/tmp/publishing'
if os.path.isdir(pub):
    newest = sh(f"find {pub} -type f -printf '%T@\\n' 2>/dev/null | sort -n | tail -1")
    out['publishing_tmp'] = {'top_entries': len(os.listdir(pub)),
                             'newest_file_age_s': int(time.time() - float(newest)) if newest.replace('.', '', 1).isdigit() else None}


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
url, user, pw = env.get('SPRING_DATASOURCE_URL'), env.get('SPRING_DATASOURCE_USERNAME'), env.get('SPRING_DATASOURCE_PASSWORD')
db = {'creds_found': bool(url and user and pw), 'mysql_client': bool(shutil.which('mysql'))}
m = re.match(r'jdbc:mysql://([^:/]+)(?::(\d+))?/([^?]+)', url or '')
if db['creds_found'] and db['mysql_client'] and m:
    host, port, name = m.group(1), m.group(2) or '3306', m.group(3)
    q = ("SET SESSION TRANSACTION READ ONLY; "
         "SELECT 'publishing_jobs' t, status, COUNT(*) n FROM publishing_jobs GROUP BY status; "
         "SELECT 'hwpx_jobs' t, status, COUNT(*) n FROM hwpx_jobs GROUP BY status;")
    try:
        p = subprocess.run(['mysql', '-h', host, '-P', port, '-u', user, name, '-N', '-B', '-e', q], env=dict(os.environ, MYSQL_PWD=pw), capture_output=True, text=True, timeout=30)
        db['job_status_counts'] = [l.split('\t') for l in p.stdout.strip().splitlines()]
        db['rc'] = p.returncode
        if p.returncode != 0:
            db['error_class'] = 'mysql_client_failed'
    except Exception as e:  # noqa: BLE001
        db['error_class'] = type(e).__name__
elif db['creds_found'] and not db['mysql_client']:
    db['note'] = 'mysql client not installed; job counts not collected'
out['db_readonly'] = db
print(json.dumps(out, ensure_ascii=False, indent=1))
