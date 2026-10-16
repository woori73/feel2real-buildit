"""DesignCraft 운영 복구 1단계(승인 범위): 읽기 전용 배포 직전 점검 + 상태 디렉터리 1개 생성.
- 읽기 전용: health, 메모리/스왑, 외부 CLI 프로세스, 작업 큐 상태(개인정보·본문·경로 미조회), 롤백 JAR 메타, 디스크.
- 변경: /var/lib/feel2real/designcraft-maintenance 디렉터리 1개만(ec2-user:ec2-user 0700). 존재하면 건드리지 않는다.
- 비밀값(DB 비밀번호 등)은 출력하지 않는다."""
import json, os, re, shutil, subprocess, time


def sh(cmd, timeout=40):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or '').strip()
    except Exception as e:  # noqa: BLE001
        return 'ERR:' + type(e).__name__


def vm(key):
    for line in open('/proc/vmstat'):
        if line.startswith(key + ' '):
            return int(line.split()[1])
    return None


def mem():
    d = {}
    for line in open('/proc/meminfo'):
        k, _, v = line.partition(':')
        d[k] = int(v.split()[0]) // 1024
    return {'MemAvailable_MiB': d['MemAvailable'], 'MemTotal_MiB': d['MemTotal'], 'SwapTotal_MiB': d['SwapTotal'],
            'SwapUsed_MiB': d['SwapTotal'] - d['SwapFree']}


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


out = {'checked_at_utc': time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime())}

# 2. health
out['spring'] = {'active': sh('systemctl is-active f2r-app.service'), 'health': sh("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 100"),
                 'since': sh('systemctl show f2r-app.service -p ActiveEnterTimestamp --value'), 'n_restarts': sh('systemctl show f2r-app.service -p NRestarts --value')}
out['wiki'] = {'active': sh('systemctl is-active buildit-wiki.service'), 'health_http': sh("curl -s -o /dev/null -m 10 -w '%{http_code}' http://127.0.0.1:8000/api/health"),
               'since': sh('systemctl show buildit-wiki.service -p ActiveEnterTimestamp --value')}

# 3. memory / swap (두 시점 샘플로 스왑 증가 지속 여부 판단)
m1, in1, out1 = mem(), vm('pswpin'), vm('pswpout')
time.sleep(20)
m2, in2, out2 = mem(), vm('pswpin'), vm('pswpout')
out['memory'] = {'t0': m1, 't20s': m2, 'swap_pages_in_during_20s': in2 - in1, 'swap_pages_out_during_20s': out2 - out1,
                 'swap_used_delta_MiB': m2['SwapUsed_MiB'] - m1['SwapUsed_MiB']}

# 4. 외부 DesignCraft CLI 프로세스 (자기 자신/grep 제외)
out['external_cli_processes'] = sh("ps -eo pid,etimes,args | grep -i 'designcraft-cli' | grep -v grep | grep -v dc_step1 | wc -l")

# 5/6. 작업 큐 (읽기 전용)
env = parse_env('/etc/f2r/env')
host, port, name, user, pw = env.get('DB_HOST'), env.get('DB_PORT') or '3306', env.get('DB_NAME') or 'f2r_db', env.get('DB_USER'), env.get('DB_PASSWORD')
db = {'mysql_client': bool(shutil.which('mysql')), 'creds_present': bool(host and user and pw)}


def q(sql):
    p = subprocess.run(['mysql', '--connect-timeout=10', '-h', host, '-P', port, '-u', user, name, '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; ' + sql],
                       env=dict(os.environ, MYSQL_PWD=pw), capture_output=True, text=True, timeout=40)
    m = re.search(r'ERROR (\d+)', p.stderr or '')
    return p.stdout.strip().splitlines(), (m.group(1) if m else None)


if db['mysql_client'] and db['creds_present']:
    try:
        for table in ('publishing_jobs', 'hwpx_jobs'):
            rows, err = q(f"SELECT status, COUNT(*) FROM {table} GROUP BY status;")
            db[table + '_counts'] = {r.split('\t')[0]: int(r.split('\t')[1]) for r in rows if '\t' in r}
            db[table + '_err'] = err
        rows, err = q("SELECT status, created_at, updated_at, TIMESTAMPDIFF(MINUTE, updated_at, NOW()), NOW() FROM publishing_jobs WHERE status='RUNNING' ORDER BY id LIMIT 5;")
        db['publishing_running_rows(status,created_at,updated_at,minutes_since_update,db_now)'] = [r.split('\t') for r in rows]
        db['running_err'] = err
    except Exception as e:  # noqa: BLE001
        db['error_class'] = type(e).__name__
out['db_readonly'] = db

# 7. 롤백 JAR / 디스크
out['rollback_jar'] = {'current_jar': sh("stat -c '%s bytes mtime=%y' /home/ec2-user/app.jar"),
                       'previous_jar': sh("stat -c '%s bytes mtime=%y' /home/ec2-user/app.jar.previous 2>/dev/null || echo missing")}
out['disk'] = {p: sh(f"df -BM --output=avail,pcent {p} | tail -1") for p in ('/', '/tmp', '/home/ec2-user', '/opt', '/var/lib')}

# 상태 디렉터리 점검 (읽기 전용: 생성·수정 없음)
target = '/var/lib/feel2real/designcraft-maintenance'
out['state_dir'] = {'exists': os.path.isdir(target), 'stat': sh(f"stat -c '%U:%G %a' {target}"),
                    'ec2user_can_write': sh(f"runuser -u ec2-user -- test -w {target} && echo yes || echo no"),
                    'entries_count': len(os.listdir(target)) if os.path.isdir(target) else None}
# 복구 수단 (메타데이터만)
out['recovery'] = {'app_jar_previous': sh("stat -c '%s bytes mtime=%y' /home/ec2-user/app.jar.previous 2>/dev/null || echo missing"),
                   'app_jar_sha': sh("sha256sum /home/ec2-user/app.jar | cut -c1-16", 90), 'previous_jar_sha': sh("sha256sum /home/ec2-user/app.jar.previous | cut -c1-16", 90),
                   'wiki_unit_exists': os.path.exists('/etc/systemd/system/buildit-wiki.service'),
                   'f2r_unit_exists': os.path.exists('/etc/systemd/system/f2r-app.service'),
                   'wiki_unit_backups': sh("ls /etc/systemd/system | grep -c 'buildit-wiki.service.' || true"),
                   'wiki_dir': sh("systemctl show buildit-wiki.service -p WorkingDirectory --value")}
print(json.dumps(out, ensure_ascii=False, indent=1))
