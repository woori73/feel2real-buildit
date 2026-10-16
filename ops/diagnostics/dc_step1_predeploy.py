"""EC2 증설 사전점검 2차(인스턴스 내부, 읽기 전용): Nitro(t3) 전환 호환성. 비밀·user-data·자격증명 미출력."""
import json, re, subprocess


def sh(cmd, timeout=40):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or p.stderr or '').strip()
    except Exception as e:  # noqa: BLE001
        return 'ERR:' + type(e).__name__


tok = sh("curl -s -m 5 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60'")
def imds(path):
    return sh(f"curl -s -m 5 -H 'X-aws-ec2-metadata-token: {tok}' http://169.254.169.254/latest/meta-data/{path}")


mac = imds('network/interfaces/macs/').strip('/').splitlines()[0] if imds('network/interfaces/macs/') else ''
out = {'kernel': sh('uname -r'), 'virtualization': sh('systemd-detect-virt'), 'hypervisor_uuid_prefix': sh("head -c 3 /sys/hypervisor/uuid 2>/dev/null"),
       'dmi_product': sh("cat /sys/class/dmi/id/product_name 2>/dev/null")}
out['network_imds'] = {k: imds(f'network/interfaces/macs/{mac}/{k}') for k in ('vpc-id', 'subnet-id', 'security-group-ids', 'public-ipv4s', 'device-number', 'ipv6s')} if mac else {}
out['public_hostname'] = imds('public-hostname')
out['nic_driver'] = sh("ethtool -i $(ls /sys/class/net | grep -v lo | head -1) 2>&1 | head -3")
out['modules_available'] = {m: sh(f"modinfo -F filename {m} 2>&1 | head -1") for m in ('ena', 'nvme', 'nvme_core', 'xen_netfront', 'xen_blkfront')}
out['modules_loaded'] = sh("lsmod | awk '{print $1}' | grep -E '^(ena|nvme|nvme_core|xen_netfront|xen_blkfront)$' | tr '\\n' ' '")
out['initramfs_has_nvme_ena'] = sh("lsinitrd /boot/initramfs-$(uname -r).img 2>/dev/null | grep -oE '(nvme[a-z_]*|ena)\\.ko[.a-z]*' | sort -u | tr '\\n' ' '")
fs = []
for line in open('/etc/fstab'):
    if line.strip() and not line.startswith('#'):
        src = line.split()[0]
        fs.append({'source_kind': 'UUID' if src.startswith('UUID=') else ('LABEL' if src.startswith('LABEL=') else ('PARTUUID' if src.startswith('PARTUUID=') else src)), 'mount': line.split()[1]})
out['fstab_sources'] = fs
out['grub_root_ref'] = sh("grep -oE 'root=[^ ]+' /proc/cmdline")
out['swapfile'] = sh("ls -l /swapfile | awk '{print $1,$3,$5}'; grep -c swapfile /etc/fstab")
out['net_naming'] = sh("ls /sys/class/net | tr '\\n' ' '")
out['ssm_agent_version'] = sh("amazon-ssm-agent --version 2>&1 | head -1")
out['ami_boot_mode'] = sh("[ -d /sys/firmware/efi ] && echo uefi || echo bios")
out['root_volume_xfs_label'] = sh("lsblk -no SERIAL /dev/xvda 2>/dev/null | head -1")
print(json.dumps(out, ensure_ascii=False, indent=1))
