"""B안 통합 테스트 직전 읽기 전용 사전점검. 하나라도 어긋나면 종료 코드 3(무장하지 않음). DB는 SELECT만, 변경 없음."""
import json, os, re, shutil, subprocess, sys, time

RUNNING_UPDATED = '2026-10-16 02:14:44'   # 기존 RUNNING 1건의 마지막 변경시각(미갱신이어야 함)
SD = '/var/lib/feel2real/designcraft-maintenance'


def sh(cmd, timeout=40):
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    return (p.stdout or '').strip()


def meminfo():
    d = {}
    for line in open('/proc/meminfo'):
        k, _, v = line.partition(':')
        d[k] = int(v.split()[0]) // 1024
    return d


def vm(key):
    for line in open('/proc/vmstat'):
        if line.startswith(key + ' '):
            return int(line.split()[1])


def parse_env(path):
    d = {}
    for line in open(path, encoding='utf-8', errors='replace'):
        line = re.sub(r'^\s*export\s+', '', line.strip())
        k, s, v = line.partition('=')
        if s and not line.startswith('#'):
            d[k.strip()] = v.strip().strip('"').strip("'")
    return d


out, why = {}, []
pid = sh('systemctl show f2r-app.service -p MainPID --value')
inode = sh(f"stat -c %i {SD}/instance.lock 2>/dev/null")
pids = sh(f"awk -v i={inode} '{{split($6,a,\":\"); if (a[3]==i) print $5}}' /proc/locks").split() if inode else []
out['gate'] = {'state_file': sh(f'cat {SD}/state'), 'lock_held_by_spring_main_pid': pid in pids}
if out['gate']['state_file'] != 'CLOSED' or not out['gate']['lock_held_by_spring_main_pid']: why.append('gate')
spring = sh("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 100"); wiki = sh("curl -s -o /dev/null -m 10 -w '%{http_code}' http://127.0.0.1:8000/api/health")
out['health'] = {'spring': spring, 'wiki_http': wiki}
if '"status":"UP"' not in spring or wiki != '200': why.append('health')
out['external_cli_processes'] = int(sh("ps -eo args | grep -iE 'designcraft-cli|verapdf' | grep -v grep | grep -v dc_b_precheck | grep -v dc_gate_watchdog | wc -l") or 0)
if out['external_cli_processes'] != 0: why.append('cli_processes')
out['watchdog_unit_already_active'] = sh('systemctl is-active dc-gate-watchdog 2>&1')
if out['watchdog_unit_already_active'] == 'active': why.append('watchdog_already_running')
env = parse_env('/etc/f2r/env')
host, port, name, user, pw = env.get('DB_HOST'), env.get('DB_PORT') or '3306', env.get('DB_NAME') or 'f2r_db', env.get('DB_USER'), env.get('DB_PASSWORD')


def q(sql):
    p = subprocess.run(['mysql', '--connect-timeout=10', '-h', host, '-P', port, '-u', user, name, '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; ' + sql],
                       env=dict(os.environ, MYSQL_PWD=pw), capture_output=True, text=True, timeout=40)
    return p.stdout.strip().splitlines()


counts = {r.split('\t')[0]: int(r.split('\t')[1]) for r in q("SELECT status, COUNT(*) FROM publishing_jobs GROUP BY status;") if '\t' in r}
hw = {r.split('\t')[0]: int(r.split('\t')[1]) for r in q("SELECT status, COUNT(*) FROM hwpx_jobs GROUP BY status;") if '\t' in r}
running = [r.split('\t') for r in q("SELECT status, created_at, updated_at FROM publishing_jobs WHERE status='RUNNING' ORDER BY id LIMIT 5;")]
synth = q("SELECT COUNT(*) FROM publishing_jobs WHERE title LIKE 'SYNTH-DC-TEST-%';")
out['queue'] = {'publishing_jobs': counts, 'hwpx_jobs': hw, 'running_rows': running, 'existing_synthetic_rows': synth[0] if synth else None}
if counts != {'RUNNING': 1} or hw != {}: why.append('queue_or_active_jobs_changed')
if len(running) != 1 or running[0][2] != RUNNING_UPDATED: why.append('existing_running_row_changed')
if out['queue']['existing_synthetic_rows'] != '0': why.append('stale_synthetic_rows')
m0, a0, p0 = meminfo(), vm('pswpin'), vm('pswpout'); time.sleep(10); m1 = meminfo()
su = lambda m: m['SwapTotal'] - m['SwapFree']
out['memory'] = {'avail_MiB': [m0['MemAvailable'], m1['MemAvailable']], 'swap_used_MiB': [su(m0), su(m1)], 'swap_out_pages_10s': vm('pswpout') - p0, 'swap_in_pages_10s': vm('pswpin') - a0}
if min(out['memory']['avail_MiB']) < 300 or su(m1) - su(m0) > 16 or out['memory']['swap_out_pages_10s'] > 2560: why.append('memory_or_swap')
out['oom_60min'] = sh("journalctl -k --since '-60 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true")
if out['oom_60min'] not in ('0', ''): why.append('oom')
out['failed_conditions'] = why
print(json.dumps(out, ensure_ascii=False, indent=1))
sys.exit(3 if why else 0)
