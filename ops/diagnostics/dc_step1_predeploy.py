"""EC2 증설 사전점검(인스턴스 내부, 읽기 전용). user-data·자격증명·환경값·비밀 미출력."""
import json, os, re, subprocess


def sh(cmd, timeout=30):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or '').strip()
    except Exception as e:  # noqa: BLE001
        return 'ERR:' + type(e).__name__


tok = sh("curl -s -m 5 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60'")
def imds(path):
    return sh(f"curl -s -m 5 -H 'X-aws-ec2-metadata-token: {tok}' http://169.254.169.254/latest/meta-data/{path}")


out = {}
out['imds'] = {k: imds(k) for k in ('instance-type', 'instance-id', 'placement/availability-zone', 'ami-id', 'local-ipv4', 'public-ipv4', 'instance-life-cycle', 'ebs-optimized' if False else 'instance-action')}
out['imds']['block_device_mapping'] = imds('block-device-mapping/').splitlines()
out['imds']['security_groups'] = imds('security-groups').splitlines()
out['imds']['iam_profile_arn_only'] = (re.search(r'"InstanceProfileArn"\s*:\s*"([^"]+)"', imds('iam/info')) or [None, None])[1]
out['imds']['imds_token_required'] = bool(tok)
out['cpu'] = {'nproc': sh('nproc'), 'model': sh("lscpu | awk -F: '/Model name/ {gsub(/^ +/,\"\",$2); print $2}'"), 'arch': sh('uname -m')}
out['memory'] = sh("free -m | awk 'NR<=3'")
out['swap'] = {'swapon': sh('swapon --show --noheadings'), 'swappiness': sh('sysctl -n vm.swappiness'), 'fstab_swap_lines': sh("grep -c swap /etc/fstab || true")}
out['disks'] = {'lsblk': sh('lsblk -o NAME,SIZE,TYPE,MOUNTPOINT,FSTYPE -n'), 'df': sh("df -hT -x tmpfs -x devtmpfs | awk '{print $1,$2,$3,$4,$5,$6,$7}'"),
                'tmp_mount': sh("findmnt -n -o SOURCE,FSTYPE,SIZE /tmp"), 'fstab_nonswap': sh("grep -v '^#' /etc/fstab | grep -v swap | awk '{print $2,$3,$4}'")}
out['os'] = sh("grep PRETTY_NAME /etc/os-release | cut -d= -f2")
out['uptime'] = sh('uptime -p; uptime -s')
svcs = ['f2r-app.service', 'buildit-wiki.service', 'amazon-ssm-agent.service', 'mysqld.service', 'mariadb.service', 'nginx.service', 'cloud-init.service']
out['services'] = {s: {'enabled': sh(f'systemctl is-enabled {s} 2>&1'), 'active': sh(f'systemctl is-active {s} 2>&1')} for s in svcs}
env = {}
try:
    for line in open('/etc/f2r/env', encoding='utf-8', errors='replace'):
        k, s, v = re.sub(r'^\s*export\s+', '', line.strip()).partition('=')
        if s and not line.strip().startswith('#'): env[k.strip()] = v.strip().strip('"').strip("'")
except Exception:  # noqa: BLE001
    pass
h = env.get('DB_HOST', '')
out['db_location'] = 'local-same-instance' if h in ('localhost', '127.0.0.1', '::1') else ('remote(rds-like)' if 'rds.amazonaws.com' in h else ('remote-other' if h else 'unknown'))
out['jvm_flags_in_unit(Xmx/Xms/MemoryMax only)'] = sh("systemctl cat f2r-app.service | grep -oE '(-Xm[sx][0-9]+[mMgG]|MemoryMax=[^ ]+|MemoryHigh=[^ ]+)' | sort -u")
out['wiki_mem_settings'] = sh("systemctl cat buildit-wiki.service | grep -oE '(--workers[ =][0-9]+|MemoryMax=[^ ]+)' | sort -u")
out['mysql_local_present'] = sh("which mysqld mariadbd 2>/dev/null | wc -l")
out['cron_or_timers'] = {'user_crontabs': sh("ls /var/spool/cron 2>/dev/null | tr '\\n' ' '"), 'enabled_timers': sh("systemctl list-timers --no-pager --no-legend | wc -l")}
out['tmp_publishing_usage'] = sh("du -sm /tmp/publishing 2>/dev/null | cut -f1")
print(json.dumps(out, ensure_ascii=False, indent=1))
