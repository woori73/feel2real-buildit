"""DesignCraft CLI 교체 사전 점검(읽기 전용). 환경파일·DB·문서 내용은 읽지 않는다.
실행하는 것: 구 CLI --version(오류 확인용, 서비스 사용자 권한), getconf, ldd(구 CLI), pgrep, df, free, systemctl show, ls/stat/sha256."""
import hashlib, json, os, shutil, subprocess
from pathlib import Path

def run(args, timeout=15, user=None):
    if user:
        args = ['runuser', '-u', user, '--'] + args
    try:
        p = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
        return {'rc': p.returncode, 'out': p.stdout.strip()[:600], 'err': p.stderr.strip()[:600]}
    except Exception as e:  # noqa: BLE001
        return {'rc': None, 'err': str(e)[:200]}

def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()

cli = '/opt/designcraft/designcraft-cli'
res = {'glibc': run(['getconf', 'GNU_LIBC_VERSION'])['out'], 'arch': os.uname().machine}
res['os'] = {k: v.strip('"') for k, _, v in (l.partition('=') for l in Path('/etc/os-release').read_text().splitlines()) if k in ('PRETTY_NAME',)}
p = Path(cli)
res['cli'] = {'exists': p.exists() or p.is_symlink(), 'is_symlink': p.is_symlink(), 'realpath': os.path.realpath(cli)}
if p.exists():
    st = p.stat()
    res['cli'].update({'size': st.st_size, 'mode': oct(st.st_mode), 'owner_uid': st.st_uid, 'sha256': sha(cli)})
    res['cli_version_run_as_ec2_user'] = run([cli, '--version'], user='ec2-user')
    res['ldd_old_cli'] = run(['ldd', cli])
res['opt_designcraft_ls'] = run(['ls', '-la', '/opt/designcraft'])['out']
res['releases_dir'] = run(['ls', '-la', '/opt/designcraft/releases'])
res['running_cli_procs'] = run(['pgrep', '-af', 'designcraft-cli'])
svc = {}
for key in ['ActiveState', 'SubState', 'MainPID', 'User', 'MemoryCurrent']:
    svc[key] = run(['systemctl', 'show', 'f2r-app.service', '-p', key, '--value'])['out']
res['service'] = svc
mem = {}
for line in Path('/proc/meminfo').read_text().splitlines():
    k, _, v = line.partition(':')
    if k in ('MemTotal', 'MemAvailable', 'SwapTotal', 'SwapFree'):
        mem[k] = v.strip()
res['memory'] = mem
res['disk'] = {d: dict(zip(('total', 'used', 'free'), shutil.disk_usage(d))) for d in ('/opt', '/var', '/tmp') if os.path.exists(d)}
pub = Path('/tmp/publishing')
res['publishing_tmp'] = {'exists': pub.exists(), 'entries': len(list(pub.iterdir())) if pub.exists() else 0,
                         'du': run(['du', '-sh', '/tmp/publishing'])['out'] if pub.exists() else None}
res['backup_dir_parent'] = run(['ls', '-ld', '/var/backups'])['out']
# 오류 로그: GLIBC 오류 줄만(문서 내용 제외), 건수와 첫 2줄
full = subprocess.run(['journalctl', '-u', 'f2r-app.service', '--since', '-6 hours', '--no-pager', '-o', 'cat'], capture_output=True, text=True, timeout=60).stdout.splitlines()
glibc_lines = [l[:220] for l in full if 'GLIBC' in l]
res['log_6h'] = {'glibc_error_lines': len(glibc_lines), 'sample': glibc_lines[:2],
                 'oom_lines': sum(1 for l in full if 'Out of memory' in l or 'OutOfMemory' in l),
                 'designcraft_cli_fail_lines': sum(1 for l in full if '조판' in l and ('실패' in l or 'FAILED' in l))}
res['kernel_oom_6h'] = run(['bash', '-c', "journalctl -k --since '-6 hours' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true"])['out']
print(json.dumps(res, ensure_ascii=False, indent=2))
