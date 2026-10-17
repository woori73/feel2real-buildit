"""배포 직전 사전 점검(읽기 위주) + 중단 명령 대상 검증(미끼 프로세스) + EIP/ENA/initramfs 호환성 + /tmp/publishing 인벤토리.
미끼 시험은 /tmp 아래 임시 파일과 sleep 프로세스만 만들고 시험 후 직접 만든 것만 정리한다."""
import hashlib, json, os, re, subprocess, time

KILL = open('/var/tmp/kill_deploy_tree.sh').read() if os.path.exists('/var/tmp/kill_deploy_tree.sh') else None
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

# 2) 중단 명령 대상 검증 (미끼)
kt = {}
if KILL is None:
    kt['error'] = 'kill script not staged'
else:
    d = f'/tmp/dc-killtest-{os.getpid()}'; os.mkdir(d, 0o700)
    decoy = f'{d}/deploy-buildit.sh'
    open(decoy, 'w').write('#!/usr/bin/env bash\nsleep 300 &\nsleep 301 &\nwait\n'); os.chmod(decoy, 0o700)
    p_decoy = subprocess.Popen([decoy], start_new_session=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    ctrl = subprocess.Popen(['sleep', '302'], start_new_session=True)
    time.sleep(1.5)
    kids = [int(x) for x in sh(f'pgrep -P {p_decoy.pid}').split()]
    kt['decoy_cmdline'] = sh(f"tr '\\0' ' ' < /proc/{p_decoy.pid}/cmdline")
    kt['decoy_children'] = len(kids)
    kt['real_regex_matches_nothing_now(no deploy running)'] = sh("pgrep -fa '^(/usr/bin/env )?(/usr/bin/|/bin/)?bash /tmp/deploy-buildit\\.sh$' | head -3") == ''
    kt['jar_replaced_guard'] = sh('bash /var/tmp/kill_deploy_tree.sh /tmp/deploy-buildit.sh "0 0"')
    kt['decoy_kill_output'] = sh(f'bash /var/tmp/kill_deploy_tree.sh {decoy} SKIP').splitlines()
    time.sleep(1)
    kt['decoy_parent_dead'] = not alive(p_decoy.pid) or sh(f'ps -o stat= -p {p_decoy.pid}').startswith('Z')
    kt['decoy_children_dead'] = all((not alive(k)) or sh(f'ps -o stat= -p {k}').startswith('Z') for k in kids)
    kt['control_sleep_untouched'] = alive(ctrl.pid) and ctrl.poll() is None
    kt['spring_untouched'] = alive(int(spring)) and sh('systemctl show f2r-app.service -p MainPID --value') == spring
    kt['wiki_untouched'] = alive(int(wiki)) and sh('systemctl show buildit-wiki.service -p MainPID --value') == wiki
    ctrl.terminate(); ctrl.wait(); p_decoy.poll()
    sh(f'rm -rf {d}')
    kt['cleaned_up_own_files'] = not os.path.exists(d)
out['kill_target_verification'] = kt

# 3) EIP / ENA / 볼륨 (인스턴스 프로필 자격으로 읽기 전용 조회; 거부되면 미확인)
tok = sh("curl -s -m 3 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60'")
md = lambda p: sh(f"curl -s -m 3 -H 'X-aws-ec2-metadata-token: {tok}' http://169.254.169.254/latest/meta-data/{p}")
iid, ip = md('instance-id'), md('public-ipv4')
aws = lambda c: sh(f'aws --region ap-northeast-2 {c} 2>&1 | head -c 700')
out['aws_readonly_with_instance_profile'] = {
    'describe-addresses(this ip)': aws(f"ec2 describe-addresses --filters Name=public-ip,Values={ip} --query 'Addresses[].{{ip:PublicIp,alloc:AllocationId,assoc:AssociationId,instance:InstanceId}}' --output json"),
    'describe-instances(ena/type/stop-protection-not-here)': aws(f"ec2 describe-instances --instance-ids {iid} --query 'Reservations[0].Instances[0].{{type:InstanceType,ena:EnaSupport,sriov:SriovNetSupport,state:State.Name,vol:BlockDeviceMappings[0].Ebs.VolumeId,monitoring:Monitoring.State}}' --output json"),
    'disableApiStop': aws(f"ec2 describe-instance-attribute --instance-id {iid} --attribute disableApiStop --output json"),
    'describe-images(ena flag)': aws(f"ec2 describe-images --image-ids {md('ami-id')} --query 'Images[0].{{ena:EnaSupport,arch:Architecture,virt:VirtualizationType}}' --output json"),
}
# 4) initramfs / dracut / kernel
kv = sh('uname -r')
out['initramfs_compat'] = {'dracut_rpm': sh('rpm -q dracut'), 'lsinitrd': sh('which lsinitrd'), 'lsinitrd_nvme_ena_xen': sh(f"lsinitrd /boot/initramfs-{kv}.img 2>/dev/null | grep -iE 'nvme|/ena|xen' | head -12"),
                          'dracut_conf': sh("grep -rhE '^(hostonly|add_drivers|force_drivers|add_dracutmodules|omit_drivers)' /etc/dracut.conf /etc/dracut.conf.d /usr/lib/dracut/dracut.conf.d 2>/dev/null | head -10"),
                          'initramfs_file': sh(f"ls -la /boot/initramfs-{kv}.img | awk '{{print $5,$6,$7,$8}}'"),
                          'loaded_modules': sh("lsmod | grep -E '^(nvme|ena|xen)' | awk '{print $1}' | tr '\\n' ' '"), 'builtin_or_module': sh("for m in nvme nvme_core ena xen_netfront xen_blkfront; do printf '%s=' $m; (ls -d /sys/module/$m >/dev/null 2>&1 && (test -e /sys/module/$m/initstate && cat /sys/module/$m/initstate || echo builtin)) || echo absent; done | tr '\\n' ' '"),
                          'kernel_config': sh("zcat /proc/config.gz 2>/dev/null | grep -E '^CONFIG_(BLK_DEV_NVME|NVME_CORE|ENA_ETHERNET|XEN_NETDEV_FRONTEND|XEN_BLKDEV_FRONTEND)=' | tr '\\n' ' '"),
                          'netplan_or_networkd': sh("ls /etc/systemd/network /usr/lib/systemd/network 2>/dev/null | head -8; cat /usr/lib/systemd/network/80-ec2.network 2>/dev/null | head -8")}
# 5) /tmp/publishing 인벤토리(백업·복원 절차 준비용, 읽기 전용)
files = []
for dp, dn, fn in os.walk('/tmp/publishing'):
    for f in fn:
        p = os.path.join(dp, f)
        try:
            st = os.lstat(p); files.append((p, st.st_size, st.st_uid, st.st_gid, oct(st.st_mode & 0o777)))
        except OSError:
            pass
out['tmp_publishing_inventory'] = {'dir_owner_mode': sh("stat -c '%U:%G %a' /tmp/publishing /tmp/publishing/jobs /tmp/publishing/jobs/1"), 'files': len(files), 'bytes': sum(f[1] for f in files),
                                   'owners': sorted({f'{f[2]}:{f[3]} {f[4]}' for f in files}), 'storage_root_in_env': env.get('PUBLISHING_STORAGE_ROOT', '(default /tmp/publishing)'),
                                   'listing_hash16': hashlib.sha256(json.dumps(sorted(files)).encode()).hexdigest()[:16], 'tmp_mount': sh('findmnt -n -o SOURCE,FSTYPE,SIZE,OPTIONS /tmp')}
print(json.dumps(out, ensure_ascii=False, indent=1))
