"""admission gate 상태 디렉터리 사전조건 읽기 전용 점검(메타데이터만, 파일 내용·비밀 미출력)."""
import json, os, subprocess


def sh(cmd, timeout=25):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or '').strip()
    except Exception as e:  # noqa: BLE001
        return 'ERR:' + type(e).__name__


out = {}
for d in ('/var/lib/feel2real', '/var/lib/feel2real/designcraft-maintenance', '/var/lib/feel2real/publishing', '/opt/designcraft', '/var/backups'):
    out[d] = {'exists': os.path.isdir(d), 'stat': sh(f"stat -c '%U:%G %a' {d} 2>/dev/null"),
              'ec2user_can_write': sh(f"runuser -u ec2-user -- test -w {d} && echo yes || echo no") if os.path.isdir(d) else None}
out['parent_var_lib_ec2user_can_create'] = sh("runuser -u ec2-user -- test -w /var/lib && echo yes || echo no")
unit = {}
for k in ('User', 'ProtectSystem', 'ProtectHome', 'ReadWritePaths', 'ReadOnlyPaths', 'PrivateTmp', 'NoNewPrivileges', 'StateDirectory', 'MemoryMax', 'MemoryHigh', 'LimitNOFILE'):
    unit[k] = sh(f"systemctl show f2r-app.service -p {k} --value")
out['f2r_app_unit'] = unit
out['selinux'] = sh("getenforce 2>/dev/null || echo not-installed")
out['memory_mib'] = {'avail': sh("awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo"), 'total': sh("awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo"),
                     'swap_free': sh("awk '/SwapFree/ {print int($2/1024)}' /proc/meminfo")}
out['f2r_app_rss_mib'] = sh("ps -o rss= -p $(systemctl show f2r-app.service -p MainPID --value) | awk '{print int($1/1024)}'")
out['wiki_rss_mib'] = sh("ps -o rss= -p $(systemctl show buildit-wiki.service -p MainPID --value) | awk '{print int($1/1024)}'")
out['publishing_storage_root_present'] = os.path.isdir('/var/lib/feel2real/publishing')
print(json.dumps(out, ensure_ascii=False, indent=1))
