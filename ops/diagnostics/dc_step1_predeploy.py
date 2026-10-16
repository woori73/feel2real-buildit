"""DesignCraft 2단계 배포 후 읽기 전용 점검. 변경 없음. 비밀·사용자 데이터 미출력."""
import json, os, subprocess, time


def sh(cmd, timeout=40):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or '').strip()
    except Exception as e:  # noqa: BLE001
        return 'ERR:' + type(e).__name__


def code(path, port=8080):
    return sh(f"curl -s -o /dev/null -m 10 -w '%{{http_code}}' http://127.0.0.1:{port}{path}")


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


out = {'checked_at_utc': time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime())}
jar = '/home/ec2-user/app.jar'
out['spring'] = {'active': sh('systemctl is-active f2r-app.service'), 'health': sh("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 100"),
                 'since': sh('systemctl show f2r-app.service -p ActiveEnterTimestamp --value'), 'n_restarts': sh('systemctl show f2r-app.service -p NRestarts --value'),
                 'main_pid': sh('systemctl show f2r-app.service -p MainPID --value'), 'user': sh("ps -o user= -p $(systemctl show f2r-app.service -p MainPID --value)")}
out['wiki'] = {'active': sh('systemctl is-active buildit-wiki.service'), 'health_http': code('/api/health', 8000),
               'since': sh('systemctl show buildit-wiki.service -p ActiveEnterTimestamp --value'), 'n_restarts': sh('systemctl show buildit-wiki.service -p NRestarts --value')}
out['jar'] = {'mtime': sh(f"stat -c %y {jar}"), 'size': sh(f"stat -c %s {jar}"), 'sha256': sh(f"sha256sum {jar} | cut -d' ' -f1", 90),
              'previous_sha256': sh("sha256sum /home/ec2-user/app.jar.previous | cut -d' ' -f1", 90),
              'has_PublishingMaintenanceGate_class(expect>=1)': sh(f"unzip -l {jar} | grep -c 'publishing/maintenance/PublishingMaintenanceGate' || true", 90),
              'has_ExamInsight_classes': sh(f"unzip -l {jar} | grep -c 'domain/examinsight/' || true", 90),
              'build_info': sh(f"unzip -p {jar} BOOT-INF/classes/META-INF/build-info.properties 2>/dev/null | grep -i 'time\\|version\\|revision' | head -5")}
sd = '/var/lib/feel2real/designcraft-maintenance'
inode = sh(f"stat -c %i {sd}/instance.lock 2>/dev/null")
lock_pids = sh(f"awk -v i={inode} '{{split($6,a,\":\"); if (a[3]==i) print $5}}' /proc/locks") if inode else ''
out['gate'] = {'state_file_content': sh(f"cat {sd}/state 2>/dev/null"), 'dir_stat': sh(f"stat -c '%U:%G %a' {sd}"),
               'files': sh(f"ls -l --time-style=+%H:%M:%S {sd} | tail -n +2 | awk '{{print $3\":\"$4, $1, $5, $6, $7}}'"),
               'instance_lock_inode': inode, 'lock_holder_pids': lock_pids,
               'lock_holder_is_f2r_main_pid': (out['spring']['main_pid'] in lock_pids.split()) if lock_pids else False,
               'lock_holder_user': sh(f"ps -o user= -p {lock_pids.split()[0]}") if lock_pids else None,
               'state_file_owner': sh(f"stat -c '%U:%G %a' {sd}/state 2>/dev/null"), 'lock_file_owner': sh(f"stat -c '%U:%G %a' {sd}/instance.lock 2>/dev/null")}
out['designcraft_cli'] = {'external_processes': sh("ps -eo pid,args | grep -i 'designcraft-cli' | grep -v grep | wc -l"),
                          'cli_sha256_unchanged_expect_64a467a0': sh("sha256sum /opt/designcraft/designcraft-cli | cut -c1-8")}
home = sh("curl -s -m 10 http://127.0.0.1:8080/")
routes = ['/', '/wiki', '/ledger', '/testprep', '/testprep/practice', '/testprep/vocab', '/exam-analysis', '/builder/entry', '/designcraft', '/designcraft/projects', '/api/admin/publishing/maintenance']
out['routes_anonymous_http'] = {r: code(r) for r in routes}
out['wiki_routes_http'] = {'/api/health': code('/api/health', 8000)}
out['home_markers'] = {'agent_hero': home.count('agent-hero'), 'wiki_label_new': home.count('모의고사위키')}
a, b, c, d = vm('pswpin'), vm('pswpout'), mem(), None
time.sleep(15)
out['memory'] = {'t0': c, 't15s': mem(), 'swap_in_pages_15s': vm('pswpin') - a, 'swap_out_pages_15s': vm('pswpout') - b,
                 'f2r_rss_mib': sh("ps -o rss= -p $(systemctl show f2r-app.service -p MainPID --value) | awk '{print int($1/1024)}'"),
                 'wiki_rss_mib': sh("ps -o rss= -p $(systemctl show buildit-wiki.service -p MainPID --value) | awk '{print int($1/1024)}'")}
out['logs'] = {'f2r_ERROR_lines_since_30min': sh("journalctl -u f2r-app.service --since '-30 min' --no-pager -o cat 2>/dev/null | grep -c ' ERROR ' || true", 60),
               'f2r_gate_related_lines(count)': sh("journalctl -u f2r-app.service --since '-30 min' --no-pager -o cat 2>/dev/null | grep -ci 'maintenance\\|PublishingMaintenanceGate' || true", 60),
               'wiki_ERROR_lines_since_30min': sh("journalctl -u buildit-wiki.service --since '-30 min' --no-pager -o cat 2>/dev/null | grep -ci 'error\\|traceback' || true", 60),
               'oom_kernel_since_60min': sh("journalctl -k --since '-60 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true", 30)}
out['db_running_row'] = 'not queried here'
print(json.dumps(out, ensure_ascii=False, indent=1))
