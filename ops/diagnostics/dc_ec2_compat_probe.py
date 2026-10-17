"""EC2 2GB→4GB 증설 호환성 점검(읽기 전용, 인스턴스 내부). 값(비밀)은 출력하지 않는다."""
import json, subprocess

out = {}


def sh(cmd, timeout=40):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout).stdout.strip()


tok = sh("curl -s -m 3 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60'")
H = f"-H 'X-aws-ec2-metadata-token: {tok}'"
md = lambda p: sh(f"curl -s -m 3 {H} http://169.254.169.254/latest/meta-data/{p}")
mac = md('mac')
out['imds'] = {'instance-type': md('instance-type'), 'instance-id': md('instance-id'), 'az': md('placement/availability-zone'), 'ami-id': md('ami-id'), 'public-ipv4': md('public-ipv4'),
               'local-ipv4': md('local-ipv4'), 'security-groups': md('security-groups').split(), 'root-device': md('block-device-mapping/root'), 'ebs-mapping': md('block-device-mapping/').split(),
               'instance-life-cycle': md('instance-life-cycle'), 'iam-role': md('iam/info')[:200] and json.loads(md('iam/info') or '{}').get('InstanceProfileArn'),
               'vpc-id': md(f'network/interfaces/macs/{mac}/vpc-id'), 'subnet-id': md(f'network/interfaces/macs/{mac}/subnet-id'),
               'public-ip-associations': md(f'network/interfaces/macs/{mac}/ipv4-associations/'),
               'hibernation/stop-protection-not-readable-from-imds': True}
out['os'] = {'kernel': sh('uname -r'), 'arch': sh('uname -m'), 'os': sh("grep PRETTY_NAME /etc/os-release | cut -d= -f2"), 'cpus': sh('nproc'), 'loadavg': sh('cat /proc/loadavg'), 'uptime': sh('uptime -p')}
out['net_driver'] = {'eth0_driver': sh("basename $(readlink -f /sys/class/net/eth0/device/driver 2>/dev/null) 2>/dev/null"), 'ethtool': sh('ethtool -i eth0 2>/dev/null | head -3'),
                     'ena_module_available': sh("modinfo ena 2>/dev/null | head -1; ls /lib/modules/$(uname -r)/kernel/drivers/amazon/net/ena 2>/dev/null | head -2"), 'ifaces': sh("ip -o link | awk '{print $2}'")}
out['storage'] = {'lsblk': sh("lsblk -o NAME,SIZE,TYPE,FSTYPE,MOUNTPOINT,UUID | head -12").splitlines(), 'fstab_names_not_uuid': sh("grep -vE '^#|^$' /etc/fstab").splitlines(),
                  'nvme_module': sh("modinfo nvme 2>/dev/null | head -1; ls /lib/modules/$(uname -r)/kernel/drivers/nvme 2>/dev/null | head -3"),
                  'initramfs_has_nvme_ena': sh("(lsinitrd /boot/initramfs-$(uname -r).img 2>/dev/null || true) | grep -ciE 'nvme|ena\\.ko' "),
                  'swap': sh("swapon --show"), 'tmp_fs': sh('findmnt -n -o SOURCE,FSTYPE,SIZE /tmp'), 'fs_free': sh("df -h / | tail -1")}
out['boot_config'] = {'grub_cmdline': sh("cat /proc/cmdline | sed 's/root=UUID=[^ ]*/root=UUID=<masked>/'"), 'net_persistent_names': sh('ls /etc/udev/rules.d 2>/dev/null | head -10')}
out['services'] = {u: sh(f'systemctl is-enabled {u} 2>&1; systemctl is-active {u}').replace('\n', ' / ') for u in ('f2r-app.service', 'buildit-wiki.service', 'amazon-ssm-agent.service', 'chronyd.service')}
out['data_on_tmpfs_lost_on_stop'] = {'publishing_jobs_dir': sh("du -sh /tmp/publishing 2>/dev/null | cut -f1"), 'job1': sh("ls /tmp/publishing/jobs/1 2>/dev/null | tr '\\n' ' '")}
out['hardcoded_public_ip_refs'] = sh(f"grep -rIl '{md('public-ipv4')}' /etc/f2r /etc/systemd/system /etc/nginx 2>/dev/null | head -5")
out['memory_now'] = sh('free -m | sed -n 1,3p').splitlines()
out['cpu_5s_sample_top'] = sh("top -bn2 -d 5 | grep -E '^%Cpu' | tail -1")
print(json.dumps(out, ensure_ascii=False, indent=1))
