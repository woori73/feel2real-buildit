"""배포 직전 재점검 (읽기 전용): 서비스·gate·job1·메모리 + 백업 상태 검증. 어떤 것도 변경·생성하지 않는다."""
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


def alive(pid):
    return os.path.exists(f'/proc/{pid}')


# 1) 서비스·gate·job 1 상태
spring = sh('systemctl show f2r-app.service -p MainPID --value'); wiki = sh('systemctl show buildit-wiki.service -p MainPID --value')
inode = sh("stat -c %i /var/lib/feel2real/designcraft-maintenance/instance.lock")
holders = sh(f"awk -v i={inode} '{{split($6,a,\":\"); if (a[3]==i) print $5}}' /proc/locks").split()
out['services'] = {'f2r_app': sh('systemctl is-active f2r-app.service'), 'spring_main_pid': spring, 'wiki': sh('systemctl is-active buildit-wiki.service'), 'wiki_main_pid': wiki,
                   'spring_health': sh("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 60"), 'wiki_http': sh("curl -s -o /dev/null -m 8 -w '%{http_code}' http://127.0.0.1:8000/api/health"),
                   'gate_state_file': sh('cat /var/lib/feel2real/designcraft-maintenance/state'), 'gate_lock_held_by_spring': spring in holders,
                   'external_cli_procs': sh("ps -eo args | grep -c '[d]esigncraft-cli'"), 'app_jar': sh("stat -c '%Y %s' /home/ec2-user/app.jar"), 'app_jar_sha16': sha('/home/ec2-user/app.jar')[:16]}
j1 = '/tmp/publishing/jobs/1'
out['job1_workdir'] = {f: {'bytes': os.path.getsize(os.path.join(j1, f)), 'sha16': sha(os.path.join(j1, f))[:16]} for f in sorted(os.listdir(j1)) if os.path.isfile(os.path.join(j1, f))}
out['job1_snapshots'] = sorted(os.listdir(j1 + '/snapshots')) if os.path.isdir(j1 + '/snapshots') else None
env = {}
for line in open('/etc/f2r/env', errors='replace'):
    k, s, v = re.sub(r'^\s*export\s+', '', line.strip()).partition('=')
    if s and not k.startswith('#'):
        env[k.strip()] = v.strip().strip('"\'')
q = subprocess.run(['mysql', '--connect-timeout=10', '-h', env['DB_HOST'], '-P', env.get('DB_PORT') or '3306', '-u', env['DB_USER'], env.get('DB_NAME') or 'f2r_db', '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; SELECT id,status,progress,attempt_count,created_at,updated_at FROM publishing_jobs ORDER BY id;'],
                   env=dict(os.environ, MYSQL_PWD=env['DB_PASSWORD']), capture_output=True, text=True, timeout=30)
out['db_publishing_jobs_rows'] = q.stdout.strip().splitlines()
out['memory'] = sh('free -m | sed -n 2,3p').splitlines(); out['oom_60min'] = sh("journalctl -k --since '-60 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill'")


# 백업 상태 (읽기 전용 해시 대조, 비밀값 미출력)
base = '/var/backups/dc-deploy-pre-bbbd9222'
bk = {'dir_mode': sh(f"stat -c '%a %U' {base}"), 'subdirs': sorted(os.listdir(base)) if os.path.isdir(base) else None}
for sub in bk['subdirs'] or []:
    p = f'{base}/{sub}'
    if os.path.isdir(p):
        bk[sub] = {'files': sum(len(fs) for _, _, fs in os.walk(p)), 'bytes': int(sh(f"du -sb {p} | cut -f1") or 0)}
envdir = next((f'{base}/{s}' for s in (bk['subdirs'] or []) if s.endswith('-env-wiki')), None)
chk = {}
if envdir:
    mf = json.load(open(f'{envdir}/MANIFEST.sha256.json')) if os.path.exists(f'{envdir}/MANIFEST.sha256.json') else {}
    bad = []
    n = 0
    for rel, h in (mf.get('files', mf) or {}).items() if isinstance(mf, dict) else []:
        fp = os.path.join(envdir, rel)
        n += 1
        if not os.path.exists(fp) or sha(fp) != (h if isinstance(h, str) else h.get('sha256', '')):
            bad.append(rel)
    chk = {'manifest_entries': n, 'mismatched_or_missing': bad}
    for name, live in (('etc/env', '/etc/f2r/env'), ('etc/buildit-wiki-identity.env', '/etc/buildit-wiki-identity.env')):
        cand = [os.path.join(dp, f) for dp, _, fs in os.walk(envdir) for f in fs if os.path.join(dp, f).endswith(name) or f == os.path.basename(live)]
        if cand and os.path.exists(live):
            chk[f'live_equals_backup:{os.path.basename(live)}'] = sha(cand[0]) == sha(live)
bk['env_wiki_verify'] = chk
jars = {}
for sub in bk['subdirs'] or []:
    for f in ('app.jar', 'app.jar.previous'):
        fp = f'{base}/{sub}/{f}'
        if os.path.exists(fp):
            jars[f] = {'bytes': os.path.getsize(fp), 'sha16': sha(fp)[:16]}
bk['jar_backups'] = jars
bk['live_jar_equals_backup'] = (jars.get('app.jar', {}).get('sha16') == sha('/home/ec2-user/app.jar')[:16]) if jars.get('app.jar') else None
bk['old_designcraft_backup_dir_untouched'] = sh('ls /var/backups/designcraft | wc -l')
out['backups'] = bk
out['uptime_spring_s'] = sh("ps -o etimes= -p $(systemctl show f2r-app.service -p MainPID --value)")
out['kill_script'] = {'staged': os.path.exists('/var/tmp/kill_deploy_tree.sh'), 'sha16': sha('/var/tmp/kill_deploy_tree.sh')[:16] if os.path.exists('/var/tmp/kill_deploy_tree.sh') else None}
print(json.dumps(out, indent=1, ensure_ascii=False))
# recheck 2026-10-17T02:52:23Z
