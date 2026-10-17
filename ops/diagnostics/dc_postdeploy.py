"""배포 후 읽기 전용 검증. 변경·생성 없음. 비밀값 미출력(env는 키 이름 차이만)."""
import hashlib, json, os, re, subprocess

out = {}


def sh(cmd, timeout=60):
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    return (p.stdout or '').strip()


def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for c in iter(lambda: f.read(1 << 20), b''):
            h.update(c)
    return h.hexdigest()


spring = sh('systemctl show f2r-app.service -p MainPID --value'); wiki = sh('systemctl show buildit-wiki.service -p MainPID --value')
inode = sh("stat -c %i /var/lib/feel2real/designcraft-maintenance/instance.lock")
holders = sh(f"awk -v i={inode} '{{split($6,a,\":\"); if (a[3]==i) print $5}}' /proc/locks").split()
out['services'] = {'f2r_app': sh('systemctl is-active f2r-app.service'), 'spring_main_pid': spring, 'spring_uptime_s': sh(f'ps -o etimes= -p {spring}'),
                   'wiki': sh('systemctl is-active buildit-wiki.service'), 'wiki_main_pid': wiki, 'wiki_uptime_s': sh(f'ps -o etimes= -p {wiki}'),
                   'spring_health': sh("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 60"), 'wiki_http': sh("curl -s -o /dev/null -m 8 -w '%{http_code}' http://127.0.0.1:8000/api/health"),
                   'n_restarts_f2r_app': sh('systemctl show f2r-app.service -p NRestarts --value')}
out['gate'] = {'state_file': sh('cat /var/lib/feel2real/designcraft-maintenance/state'), 'lock_held_by_new_spring': spring in holders, 'lock_holders': holders, 'external_cli_procs': sh("ps -eo args | grep -c '[d]esigncraft-cli'")}
pages = {}
for p in ['/', '/ledger', '/builder', '/testprep', '/login', '/designcraft', '/problem-generator', '/wemade', '/actuator/health']:
    pages[p] = sh(f"curl -s -o /dev/null -m 20 -w '%{{http_code}} %{{redirect_url}}' http://127.0.0.1:8080{p}")
out['pages(no-follow, anonymous)'] = pages
out['widgets_api_unauthenticated'] = {
    'GET': sh("curl -s -o /dev/null -m 20 -w '%{http_code} %{redirect_url}' 'http://127.0.0.1:8080/api/admin/easy/dashboard/widgets?businessId=1'"),
    'GET_json_accept': sh("curl -s -m 20 -H 'Accept: application/json' -o /dev/null -w '%{http_code}' 'http://127.0.0.1:8080/api/admin/easy/dashboard/widgets?businessId=1'"),
    'body_head': sh("curl -s -m 20 -H 'Accept: application/json' 'http://127.0.0.1:8080/api/admin/easy/dashboard/widgets?businessId=1' | head -c 120")}
j1 = '/tmp/publishing/jobs/1'
out['job1_workdir'] = {f: {'bytes': os.path.getsize(os.path.join(j1, f)), 'sha16': sha(os.path.join(j1, f))[:16]} for f in sorted(os.listdir(j1)) if os.path.isfile(os.path.join(j1, f))}
out['job1_snapshots_count'] = len(os.listdir(j1 + '/snapshots')) if os.path.isdir(j1 + '/snapshots') else None
env = {}
for line in open('/etc/f2r/env', errors='replace'):
    k, s, v = re.sub(r'^\s*export\s+', '', line.strip()).partition('=')
    if s and not k.startswith('#'):
        env[k.strip()] = v.strip().strip('"\'')
q = subprocess.run(['mysql', '--connect-timeout=10', '-h', env['DB_HOST'], '-P', env.get('DB_PORT') or '3306', '-u', env['DB_USER'], env.get('DB_NAME') or 'f2r_db', '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; SELECT id,status,progress,attempt_count,created_at,updated_at FROM publishing_jobs ORDER BY id;'],
                   env=dict(os.environ, MYSQL_PWD=env['DB_PASSWORD']), capture_output=True, text=True, timeout=30)
out['db_publishing_jobs_rows'] = q.stdout.strip().splitlines()
out['memory'] = sh('free -m | sed -n 2,3p').splitlines()
out['oom_since_boot_recent_60min'] = sh("journalctl -k --since '-60 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill'")
start = sh("systemctl show f2r-app.service -p ActiveEnterTimestamp --value")
out['spring_active_since'] = start
out['spring_log_since_start'] = {'ERROR_lines': sh(f"journalctl -u f2r-app.service --since '{start}' --no-pager 2>/dev/null | grep -c ' ERROR '"),
                                 'ERROR_samples_masked': sh(f"journalctl -u f2r-app.service --since '{start}' --no-pager 2>/dev/null | grep ' ERROR ' | cut -c1-230 | sed -E 's/(password|secret|token|key)=[^ ]+/\\1=***/Ig' | head -5"),
                                 'WARN_lines': sh(f"journalctl -u f2r-app.service --since '{start}' --no-pager 2>/dev/null | grep -c ' WARN '")}
out['jar'] = {'stat': sh("stat -c '%Y %s %U' /home/ec2-user/app.jar"), 'sha16': sha('/home/ec2-user/app.jar')[:16], 'previous_sha16': sha('/home/ec2-user/app.jar.previous')[:16],
              'contains_EasyDashboardWidgetController': sh("unzip -l /home/ec2-user/app.jar | grep -c 'EasyDashboardWidgetController'"),
              'contains_PdfMarkTitle': sh("unzip -l /home/ec2-user/app.jar | grep -c 'PdfMarkTitle'"),
              'contains_gate_ttl(PublishingMaintenanceGate)': sh("unzip -l /home/ec2-user/app.jar | grep -c 'PublishingMaintenanceGate'")}
bd = sh('ls -1d /var/backups/f2r-deploy-env/*/ 2>/dev/null | tail -3').splitlines()
out['env_backup'] = {'dirs': bd}
if bd:
    d = bd[-1].rstrip('/')
    out['env_backup'].update({'dir_mode': sh(f"stat -c '%a %U' {d}"), 'files': sh(f"ls -l {d} | tail -n +2 | awk '{{print $1,$3,$4,$5,$9}}'").splitlines()})
    def keys(path):
        ks = []
        for line in open(path, errors='replace'):
            k = re.sub(r'^\s*export\s+', '', line.strip()).partition('=')[0].strip()
            if '=' in line and not k.startswith('#'):
                ks.append(k)
        return ks
    b = keys(f'{d}/env'); l = keys('/etc/f2r/env')
    out['env_backup']['live_vs_backup_keys'] = {'only_in_backup': sorted(set(b) - set(l)), 'only_in_live': sorted(set(l) - set(b)), 'dup_keys_live': sorted({k for k in l if l.count(k) > 1}), 'content_identical': sha(f'{d}/env') == sha('/etc/f2r/env')}
    out['env_backup']['live_env_mode_owner'] = sh("stat -c '%a %U:%G' /etc/f2r/env /etc/buildit-wiki-identity.env")
    out['env_backup']['backup_env_mode_owner'] = sh(f"stat -c '%a %U:%G' {d}/env {d}/buildit-wiki-identity.env")
out['preexisting_backups_intact'] = {'dir': sh("ls /var/backups/dc-deploy-pre-bbbd9222"), 'jar_backup_sha16': sha('/var/backups/dc-deploy-pre-bbbd9222/20261017-012537/app.jar')[:16]}
out['tmp_publishing'] = sh("find /tmp/publishing -type f | wc -l")
print(json.dumps(out, indent=1, ensure_ascii=False))
