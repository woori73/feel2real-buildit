"""DesignCraft CLI 0.8 교체 직전 읽기 전용 점검. 변경 없음. 비밀·사용자 데이터·작업 본문 미출력."""
import json, os, re, shutil, subprocess, time


def sh(cmd, timeout=40):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or '').strip()
    except Exception as e:  # noqa: BLE001
        return 'ERR:' + type(e).__name__


def mem():
    d = {}
    for line in open('/proc/meminfo'):
        k, _, v = line.partition(':')
        d[k] = int(v.split()[0]) // 1024
    return {'MemAvailable_MiB': d['MemAvailable'], 'SwapUsed_MiB': d['SwapTotal'] - d['SwapFree']}


def vm(key):
    for line in open('/proc/vmstat'):
        if line.startswith(key + ' '):
            return int(line.split()[1])


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
out['spring'] = {'active': sh('systemctl is-active f2r-app.service'), 'health': sh("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 100"),
                 'since': sh('systemctl show f2r-app.service -p ActiveEnterTimestamp --value'), 'n_restarts': sh('systemctl show f2r-app.service -p NRestarts --value'),
                 'main_pid': sh('systemctl show f2r-app.service -p MainPID --value')}
out['wiki'] = {'active': sh('systemctl is-active buildit-wiki.service'), 'health_http': sh("curl -s -o /dev/null -m 10 -w '%{http_code}' http://127.0.0.1:8000/api/health"),
               'n_restarts': sh('systemctl show buildit-wiki.service -p NRestarts --value')}
sd = '/var/lib/feel2real/designcraft-maintenance'
inode = sh(f"stat -c %i {sd}/instance.lock 2>/dev/null")
pids = sh(f"awk -v i={inode} '{{split($6,a,\":\"); if (a[3]==i) print $5}}' /proc/locks") if inode else ''
out['gate'] = {'state_file_content': sh(f"cat {sd}/state 2>/dev/null"), 'lock_holder_is_f2r_main_pid': out['spring']['main_pid'] in pids.split() if pids else False,
               'state_file_mtime': sh(f"stat -c %y {sd}/state")}
a, b, m0 = vm('pswpin'), vm('pswpout'), mem()
out['external_cli_processes'] = sh("ps -eo pid,etimes,args | grep -i 'designcraft-cli' | grep -v grep | wc -l")
env = parse_env('/etc/f2r/env')
host, port, name, user, pw = env.get('DB_HOST'), env.get('DB_PORT') or '3306', env.get('DB_NAME') or 'f2r_db', env.get('DB_USER'), env.get('DB_PASSWORD')
db = {}


def q(sql):
    p = subprocess.run(['mysql', '--connect-timeout=10', '-h', host, '-P', port, '-u', user, name, '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; ' + sql],
                       env=dict(os.environ, MYSQL_PWD=pw), capture_output=True, text=True, timeout=40)
    m = re.search(r'ERROR (\d+)', p.stderr or '')
    return p.stdout.strip().splitlines(), (m.group(1) if m else None)


if shutil.which('mysql') and host and user and pw:
    try:
        for t in ('publishing_jobs', 'hwpx_jobs'):
            rows, err = q(f"SELECT status, COUNT(*) FROM {t} GROUP BY status;")
            db[t + '_counts'] = {r.split('\t')[0]: int(r.split('\t')[1]) for r in rows if '\t' in r}
            db[t + '_err'] = err
        rows, err = q("SELECT status, created_at, updated_at, TIMESTAMPDIFF(MINUTE, updated_at, NOW()), NOW() FROM publishing_jobs WHERE status='RUNNING' ORDER BY id LIMIT 5;")
        db['running_rows(status,created_at,updated_at,min_since_update,db_now)'] = [r.split('\t') for r in rows]
        db['running_err'] = err
    except Exception as e:  # noqa: BLE001
        db['error_class'] = type(e).__name__
out['db_readonly'] = db
# CLI 경로/링크 구조 (읽기 전용)
cli = '/opt/designcraft/designcraft-cli'
out['cli_layout'] = {'ls_opt_designcraft': sh("ls -la --time-style=long-iso /opt/designcraft | tail -n +2 | awk '{print $1,$3,$4,$5,$6,$7,$8,$9,$10,$11}'"),
                     'is_symlink': os.path.islink(cli), 'readlink': sh(f"readlink {cli}"), 'realpath': sh(f"readlink -f {cli}"),
                     'stat_target': sh(f"stat -L -c '%U:%G %a %s bytes' {cli}"), 'sha256_target': sh(f"sha256sum {cli} | cut -d' ' -f1", 60),
                     'releases_dir_exists': os.path.isdir('/opt/designcraft/releases'), 'releases_ls': sh("ls /opt/designcraft/releases 2>/dev/null"),
                     'new_release_path_exists': os.path.exists('/opt/designcraft/releases/0.8.0-al2023-2edd60ef2f57'),
                     'backups_dir_exists': os.path.isdir('/var/backups/designcraft'), 'var_backups_avail': sh("df -BM --output=avail,pcent /var/backups | tail -1"),
                     'opt_avail': sh("df -BM --output=avail,pcent /opt | tail -1"), 'tmp_avail': sh("df -BM --output=avail,pcent /tmp | tail -1"),
                     'host_glibc': sh("ldd --version | head -1"), 'publishing_cli_path_env_set': 'PUBLISHING_CLI_PATH' in env or 'PUBLISHING_CLI_PATH' in open('/etc/systemd/system/f2r-app.service').read()}
time.sleep(20)
out['memory'] = {'t0': m0, 't20s': mem(), 'swap_in_pages_20s': vm('pswpin') - a, 'swap_out_pages_20s': vm('pswpout') - b}
out['logs'] = {'f2r_ERROR_lines_30min': sh("journalctl -u f2r-app.service --since '-30 min' --no-pager -o cat 2>/dev/null | grep -c ' ERROR ' || true", 60),
               'oom_kernel_60min': sh("journalctl -k --since '-60 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true", 30)}
print(json.dumps(out, ensure_ascii=False, indent=1))
