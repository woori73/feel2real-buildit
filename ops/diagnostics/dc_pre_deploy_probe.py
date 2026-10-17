"""#721/#722 운영 배포 전 안전 점검(읽기 전용). 값(비밀)은 출력하지 않고 키 이름·해시·코드만 낸다."""
import glob, hashlib, json, os, re, subprocess

out = {}


def sh(cmd, timeout=60):
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    return (p.stdout or '').strip()


def sha(path):
    try:
        h = hashlib.sha256()
        with open(path, 'rb') as f:
            for chunk in iter(lambda: f.read(1 << 20), b''):
                h.update(chunk)
        return h.hexdigest()[:16]
    except OSError as e:
        return f'ERR {e.__class__.__name__}'


def stat(path):
    try:
        s = os.stat(path)
        return {'bytes': s.st_size, 'mtime_utc': sh(f"date -u -d @{int(s.st_mtime)} +%FT%TZ"), 'sha256_16': sha(path)}
    except OSError:
        return None


out['now_utc'] = sh('date -u +%FT%TZ')
pid = sh('systemctl show f2r-app.service -p MainPID --value')
out['f2r_app'] = {'active_since': sh('systemctl show f2r-app.service -p ActiveEnterTimestamp --value'), 'restarts': sh('systemctl show f2r-app.service -p NRestarts --value'),
                  'restart_policy': sh('systemctl show f2r-app.service -p Restart --value'), 'java_cmd': sh(f"tr '\\0' ' ' < /proc/{pid}/cmdline | sed 's/--[a-z.]*password[^ ]*/<masked>/g' | cut -c1-260"),
                  'startup_seconds_last3': sh("journalctl -u f2r-app.service --no-pager -o cat 2>/dev/null | grep -o 'Started PrivateTutorSolutionBackApplication in [0-9.]* seconds' | tail -3", 90).splitlines()}
out['jars'] = {'current /home/ec2-user/app.jar': stat('/home/ec2-user/app.jar'), 'rollback /home/ec2-user/app.jar.previous': stat('/home/ec2-user/app.jar.previous'),
               'other jar backups': sh("ls -la /home/ec2-user/*.jar* 2>/dev/null | awk '{print $5,$6,$7,$8,$9}'").splitlines()}
out['disk'] = sh("df -h / /tmp /var /home 2>/dev/null | awk '{print $1,$2,$4,$5,$6}'").splitlines()
out['memory_now'] = sh("free -m | sed -n 1,3p").splitlines()
names = sorted({l.split('=', 1)[0].replace('export ', '').strip() for l in open('/etc/f2r/env', errors='replace') if '=' in l and not l.lstrip().startswith('#')})
out['f2r_env'] = {'perm': sh("stat -c '%a %U:%G' /etc/f2r/env"), 'key_names': names, 'backups': sh("ls -la /etc/f2r/ | awk '{print $1,$5,$6,$7,$8,$9}'").splitlines()}
out['designcraft'] = {'cli_symlink': sh("readlink -f /opt/designcraft/designcraft-cli"), 'cli_version': sh("/opt/designcraft/designcraft-cli --version 2>&1 | head -1", 20),
                      'backup_dirs': sh("ls -1 /var/backups/designcraft 2>/dev/null").splitlines(), 'state_dir': sh("ls -la /var/lib/feel2real/designcraft-maintenance | tail -n +2 | awk '{print $1,$3,$5,$9}'").splitlines(),
                      'gate_state_file': sh('cat /var/lib/feel2real/designcraft-maintenance/state'), 'watchdog_units': sh("systemctl list-units 'dc-gate-watchdog*' --all --no-legend | head -3"),
                      'job1_workdir': sh("ls -la /tmp/publishing/jobs/1 2>&1 | tail -n +2 | awk '{print $5,$9}'").splitlines(), 'tmp_fs': sh("findmnt -n -o FSTYPE,SIZE,AVAIL /tmp")}
codes = {}
for path in ['/actuator/health', '/', '/ledger', '/builder', '/testprep', '/login', '/designcraft', '/problem-generator', '/api/auth/me', '/api/publishing/jobs', '/api/admin/publishing/maintenance']:
    codes[path] = sh(f"curl -s -o /dev/null -m 10 -w '%{{http_code}}' http://127.0.0.1:8080{path}")
codes['wiki:8000/api/health'] = sh("curl -s -o /dev/null -m 10 -w '%{http_code}' http://127.0.0.1:8000/api/health")
out['http_baseline'] = codes
out['wiki_unit'] = {'active': sh('systemctl is-active buildit-wiki'), 'since': sh('systemctl show buildit-wiki -p ActiveEnterTimestamp --value')}
out['errors_last_hour'] = sh("journalctl -u f2r-app.service --since '-60 min' --no-pager -o cat 2>/dev/null | grep -c ' ERROR ' || true", 90)

env = {}
for line in open('/etc/f2r/env', encoding='utf-8', errors='replace'):
    line = re.sub(r'^\s*export\s+', '', line.strip())
    k, s, v = line.partition('=')
    if s and not line.startswith('#'):
        env[k.strip()] = v.strip().strip('"').strip("'")


def q(sql):
    p = subprocess.run(['mysql', '--connect-timeout=10', '-h', env['DB_HOST'], '-P', env.get('DB_PORT') or '3306', '-u', env['DB_USER'], env.get('DB_NAME') or 'f2r_db', '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; ' + sql],
                       env=dict(os.environ, MYSQL_PWD=env['DB_PASSWORD']), capture_output=True, text=True, timeout=40)
    return (p.stdout + p.stderr).strip().splitlines()


out['db_readonly'] = {'publishing_jobs_by_status': q("SELECT status, COUNT(*), MIN(id), MAX(id) FROM publishing_jobs GROUP BY status;"),
                      'job1': q("SELECT id, status, progress, attempt_count, created_at, updated_at FROM publishing_jobs WHERE id=1;"),
                      'flyway_latest': q("SELECT version, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"),
                      'flyway_failed': q("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;"),
                      'db_host_is_rds': 'rds.amazonaws.com' in (env.get('DB_HOST') or '')}
print(json.dumps(out, ensure_ascii=False, indent=1))
