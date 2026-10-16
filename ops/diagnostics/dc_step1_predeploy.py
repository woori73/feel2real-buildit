"""DesignCraft 4단계 사전점검 + 템플릿/실행 경로 확인(읽기 전용). 변경 없음. 비밀·본문 미출력."""
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
    for line in open(path, encoding='utf-8', errors='replace'):
        line = re.sub(r'^\s*export\s+', '', line.strip())
        k, s, v = line.partition('=')
        if s and not line.startswith('#'):
            d[k.strip()] = v.strip().strip('"').strip("'")
    return d


out = {'checked_at_utc': time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime())}
pid = sh('systemctl show f2r-app.service -p MainPID --value')
out['spring'] = {'health': sh("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 100"), 'since': sh('systemctl show f2r-app.service -p ActiveEnterTimestamp --value'), 'main_pid': pid}
out['wiki_http'] = sh("curl -s -o /dev/null -m 10 -w '%{http_code}' http://127.0.0.1:8000/api/health")
sd = '/var/lib/feel2real/designcraft-maintenance'
inode = sh(f"stat -c %i {sd}/instance.lock")
pids = sh(f"awk -v i={inode} '{{split($6,a,\":\"); if (a[3]==i) print $5}}' /proc/locks").split()
out['gate'] = {'state_file': sh(f"cat {sd}/state"), 'lock_held_by_spring_main_pid': pid in pids}
out['cli'] = {'stable_is_symlink': os.path.islink('/opt/designcraft/designcraft-cli'), 'realpath': sh('readlink -f /opt/designcraft/designcraft-cli'),
              'sha256': sh("sha256sum /opt/designcraft/designcraft-cli | cut -d' ' -f1", 60), 'ec2user_version': sh("cd /tmp && runuser -u ec2-user -- /opt/designcraft/designcraft-cli --version 2>&1 | head -1"),
              'external_processes': sh("ps -eo pid,args | grep -iE 'designcraft-cli|verapdf' | grep -v grep | wc -l")}
m0, a, b = mem(), vm('pswpin'), vm('pswpout'); time.sleep(15); m1 = mem()
out['memory'] = {'t0': m0, 't15s': m1, 'swap_in_pages_15s': vm('pswpin') - a, 'swap_out_pages_15s': vm('pswpout') - b,
                 'oom_60min': sh("journalctl -k --since '-60 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true")}
env = parse_env('/etc/f2r/env'); host, port, name, user, pw = env.get('DB_HOST'), env.get('DB_PORT') or '3306', env.get('DB_NAME') or 'f2r_db', env.get('DB_USER'), env.get('DB_PASSWORD')


def q(sql):
    p = subprocess.run(['mysql', '--connect-timeout=10', '-h', host, '-P', port, '-u', user, name, '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; ' + sql], env=dict(os.environ, MYSQL_PWD=pw), capture_output=True, text=True, timeout=40)
    return p.stdout.strip().splitlines()


db = {}
for t in ('publishing_jobs', 'hwpx_jobs'):
    db[t] = {r.split('\t')[0]: int(r.split('\t')[1]) for r in q(f"SELECT status, COUNT(*) FROM {t} GROUP BY status;") if '\t' in r}
db['running_rows(status,created,updated)'] = [r.split('\t') for r in q("SELECT status, created_at, updated_at FROM publishing_jobs WHERE status='RUNNING' ORDER BY id LIMIT 5;")]
out['db_readonly'] = db
# Spring이 쓰는 publishing 설정 출처(경로·이름만, 비밀 제외)
cmd = sh(f"tr '\\0' ' ' < /proc/{pid}/cmdline")
out['spring_effective_config'] = {'java_args(filtered)': ' '.join(re.findall(r'(?:-Xm[sx]\S+|-Dpublishing\S*|-Dspring\.profiles\S*|-Djava\.io\.tmpdir\S*|-jar \S+)', cmd)),
    'environ_PUBLISHING_names_and_values': sh(f"tr '\\0' '\\n' < /proc/{pid}/environ | grep -E '^(PUBLISHING|DESIGNCRAFT)[A-Z_]*=' | head"),
    'environ_TMPDIR_JAVA_TOOL_names': sh(f"tr '\\0' '\\n' < /proc/{pid}/environ | grep -E '^(TMPDIR|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS)=' | cut -d= -f1"),
    'env_file_keys_PUBLISHING': [k for k in env if k.upper().startswith(('PUBLISHING', 'DESIGNCRAFT'))],
    'jar_yaml_publishing_block': sh("for f in application.yaml application-prd.yaml; do echo \"## $f\"; unzip -p /home/ec2-user/app.jar BOOT-INF/classes/$f 2>/dev/null | awk '/^publishing:/{p=1;print;next} p&&/^[^ #]/{p=0} p{print}' | head -15; done", 60)}
# 템플릿 경로 사용·권한
T = '/opt/designcraft/templates'
out['templates'] = {'dir_stat': sh(f"stat -c '%U:%G %a' {T}"), 'opt_designcraft_stat': sh("stat -c '%U:%G %a' /opt/designcraft"),
    'tree_names_perms': sh(f"find {T} -maxdepth 3 -printf '%M %u:%g %y %P\\n' | sort | head -40"),
    'ec2user_can_list': sh(f"cd /tmp && runuser -u ec2-user -- ls {T} 2>&1 | head -5"),
    'ec2user_can_read_any_font': sh(f"cd /tmp && F=$(find {T} -name '*.ttf' | head -1); [ -n \"$F\" ] && (runuser -u ec2-user -- head -c 4 \"$F\" >/dev/null 2>&1 && echo yes || echo no) || echo no-font-found"),
    'template_codes': sh(f"ls {T}"), 'font_file_count': sh(f"find {T} -name '*.ttf' | wc -l")}
out['service_logs_since_start'] = {'document_fonts_not_found_warns': sh("journalctl -u f2r-app.service --since \"$(systemctl show f2r-app.service -p ActiveEnterTimestamp --value)\" --no-pager -o cat 2>/dev/null | grep -c 'Document Fonts를 찾을 수 없습니다' || true", 60),
    'worker_related_lines': sh("journalctl -u f2r-app.service --since \"$(systemctl show f2r-app.service -p ActiveEnterTimestamp --value)\" --no-pager -o cat 2>/dev/null | grep -ciE 'PublishingWorker|designcraft' || true", 60)}
out['storage'] = {'tmp_publishing': sh("ls -ld /tmp/publishing 2>&1 | cut -c1-80"), 'tmp_avail': sh("df -BM --output=avail,pcent /tmp | tail -1"), 'var_lib_feel2real': sh("ls -la /var/lib/feel2real | tail -n +2 | awk '{print $1,$3,$4,$9}'")}
print(json.dumps(out, ensure_ascii=False, indent=1))
