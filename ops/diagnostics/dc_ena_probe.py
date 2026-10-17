"""ENA/initramfs/EIP 단서 읽기 전용 조사 (파일 읽기·조회 명령만, 어떤 것도 변경하지 않음)."""
import json, subprocess

def sh(c, t=40):
    try:
        return subprocess.run(c, shell=True, capture_output=True, text=True, timeout=t).stdout.strip()[:1500]
    except Exception as e:
        return 'ERR ' + str(e)[:100]

kv = sh('uname -r')
tok = sh("curl -s -m 3 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60'")
def imds(p):
    return sh(f"curl -s -m 3 -H 'X-aws-ec2-metadata-token: {tok}' http://169.254.169.254/latest/meta-data/{p}")
mac = imds('mac')
out = {
  'kernel': kv,
  'imds': {'instance-type': imds('instance-type'), 'ami-id': imds('ami-id'), 'placement/az': imds('placement/availability-zone'),
           'public-ipv4': imds('public-ipv4'), 'public-hostname': imds('public-hostname'),
           'ipv4-associations': imds(f'network/interfaces/macs/{mac}/ipv4-associations/') if mac else ''},
  'ena_module_in_kernel_tree': sh(f"find /lib/modules/{kv} -name 'ena*' 2>/dev/null | head -5"),
  'modinfo_ena': sh('modinfo ena 2>&1 | head -4'),
  'kernel_config_ena': sh(f"grep -E 'CONFIG_ENA_ETHERNET|CONFIG_BLK_DEV_NVME|CONFIG_NVME_CORE' /boot/config-{kv} 2>/dev/null || zcat /proc/config.gz 2>/dev/null | grep -E 'CONFIG_ENA_ETHERNET|CONFIG_BLK_DEV_NVME'"),
  'initramfs_listing_ena_nvme_xen': sh(f"lsinitrd /boot/initramfs-{kv}.img 2>&1 | grep -iE 'ena|nvme|xen' | head -20"),
  'initramfs_lsinitrd_head': sh(f"lsinitrd /boot/initramfs-{kv}.img 2>&1 | head -5"),
  'boot_files': sh('ls -la /boot | head -15'),
  'nic_driver': sh("for i in /sys/class/net/*; do n=$(basename $i); [ $n = lo ] && continue; echo $n $(basename $(readlink -f $i/device/driver) 2>/dev/null); done"),
  'root_dev': sh('findmnt -no SOURCE,FSTYPE / ; lsblk -no NAME,SIZE,TYPE | head -5'),
}
print(json.dumps(out, indent=1, ensure_ascii=False))
