"""DesignCraft CLI 0.8.0 교체(승인 범위). 변경 전 모든 중단 조건을 검사하고, 교체 후 검증 실패 시 자동 복원한다.
건드리지 않는 것: gate 상태 디렉터리, DB 데이터, Spring/Wiki 서비스, 환경 파일, unit, 템플릿. 비밀·본문 미출력."""
import hashlib, json, os, re, shutil, subprocess, tempfile, time

CHANGE_ID = 'dc-cli-0.8.0-20261016'
BUCKET, KEY = 'feel2real-buildit-deploy', 'designcraft-cli-0.8.0-al2023-x86_64.tar.gz'
WANT_TAR = '5d4a3e7b9a0569ed904ae41d777c1a02854e579fd8aaa2769a34a4c3228aee6d'
WANT_BIN = '2edd60ef2f575263a31ad6cb1ec661181bd24207d4aefbec8516b04990700335'
OLD_SHA = '64a467a06dceac46a7cca5990e46946faa1027d3b33228f1c42caaff9e33da87'
STABLE = '/opt/designcraft/designcraft-cli'
REL = '/opt/designcraft/releases/0.8.0-al2023-2edd60ef2f57'
NEWBIN = REL + '/bin/designcraft-cli'
BK = f'/var/backups/designcraft/{CHANGE_ID}'
RUNNING_UPDATED = '2026-10-16 02:14:44'   # 이전 점검에서 확인한 마지막 변경시각(미갱신이어야 함)
SD = '/var/lib/feel2real/designcraft-maintenance'

out = {'change_id': CHANGE_ID, 'stages': []}


def sh(cmd, timeout=60):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return p.returncode, (p.stdout or '').strip(), (p.stderr or '').strip()
    except Exception as e:  # noqa: BLE001
        return 99, '', type(e).__name__


def so(cmd, timeout=60):
    return sh(cmd, timeout)[1]


def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def finish(status, **kw):
    out['status'] = status
    out.update(kw)
    print(json.dumps(out, ensure_ascii=False, indent=1))
    raise SystemExit(0 if status in ('SWAPPED_OK', 'ABORT_PRECHECK_NO_CHANGE') else 1)


def meminfo():
    d = {}
    for line in open('/proc/meminfo'):
        k, _, v = line.partition(':')
        d[k] = int(v.split()[0]) // 1024
    return d


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


def gate_state():
    inode = so(f"stat -c %i {SD}/instance.lock 2>/dev/null")
    pids = so(f"awk -v i={inode} '{{split($6,a,\":\"); if (a[3]==i) print $5}}' /proc/locks").split() if inode else []
    main = so('systemctl show f2r-app.service -p MainPID --value')
    return {'state': so(f"cat {SD}/state 2>/dev/null"), 'lock_held_by_spring_main_pid': main in pids}


def queue_state():
    env = parse_env('/etc/f2r/env')
    host, port, name, user, pw = env.get('DB_HOST'), env.get('DB_PORT') or '3306', env.get('DB_NAME') or 'f2r_db', env.get('DB_USER'), env.get('DB_PASSWORD')

    def q(sql):
        p = subprocess.run(['mysql', '--connect-timeout=10', '-h', host, '-P', port, '-u', user, name, '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; ' + sql],
                           env=dict(os.environ, MYSQL_PWD=pw), capture_output=True, text=True, timeout=40)
        return p.stdout.strip().splitlines()
    res = {}
    for t in ('publishing_jobs', 'hwpx_jobs'):
        res[t] = {r.split('\t')[0]: int(r.split('\t')[1]) for r in q(f"SELECT status, COUNT(*) FROM {t} GROUP BY status;") if '\t' in r}
    res['running_rows'] = [r.split('\t') for r in q("SELECT status, created_at, updated_at FROM publishing_jobs WHERE status='RUNNING' ORDER BY id LIMIT 5;")]
    return res


def services_ok():
    spring = so("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 100")
    wiki = so("curl -s -o /dev/null -m 10 -w '%{http_code}' http://127.0.0.1:8000/api/health")
    return {'spring_health': spring, 'wiki_http': wiki, 'ok': '"status":"UP"' in spring and wiki == '200'}


def cli_procs():
    return int(so("ps -eo pid,args | grep -iE 'designcraft-cli|verapdf' | grep -v grep | grep -v dc_cli_swap | wc -l") or 0)


def precheck(tag):
    """반환: (ok, detail). 하나라도 어긋나면 ok=False."""
    d, why = {}, []
    s = services_ok(); d['services'] = s
    if not s['ok']: why.append('service_health')
    g = gate_state(); d['gate'] = g
    if g['state'] != 'CLOSED' or not g['lock_held_by_spring_main_pid']: why.append('gate_not_closed_or_lock')
    d['external_cli_processes'] = cli_procs()
    if d['external_cli_processes'] != 0: why.append('cli_processes_present')
    qs = queue_state(); d['queue'] = {'publishing_jobs': qs['publishing_jobs'], 'hwpx_jobs': qs['hwpx_jobs'], 'running_rows': qs['running_rows']}
    if qs['publishing_jobs'] != {'RUNNING': 1} or qs['hwpx_jobs'] != {}: why.append('queue_changed')
    rr = qs['running_rows']
    if len(rr) != 1 or rr[0][2] != RUNNING_UPDATED: why.append('running_row_updated_or_new')
    m1, p1, a1 = meminfo(), vm('pswpout'), vm('pswpin'); time.sleep(10); m2, p2, a2 = meminfo(), vm('pswpout'), vm('pswpin')
    su = lambda m: m['SwapTotal'] - m['SwapFree']
    d['memory'] = {'avail_MiB': [m1['MemAvailable'], m2['MemAvailable']], 'swap_used_MiB': [su(m1), su(m2)], 'swap_out_pages_10s': p2 - p1, 'swap_in_pages_10s': a2 - a1}
    if min(m1['MemAvailable'], m2['MemAvailable']) < 300: why.append('memavailable_below_300')
    if su(m2) - su(m1) > 16 or (p2 - p1) > 2560: why.append('swap_growth')
    d['oom_60min'] = so("journalctl -k --since '-60 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true")
    if d['oom_60min'] not in ('0', ''): why.append('oom')
    out['stages'].append({tag: d, 'failed_conditions': why})
    return (not why), d


# ── 0. 교체 전 중단 조건 검사 ─────────────────────────────────────────────
ok, _ = precheck('precheck_1')
if not ok:
    finish('ABORT_PRECHECK_NO_CHANGE', note='변경 없음')
cur = {'is_symlink': os.path.islink(STABLE), 'sha256': sha(STABLE) if os.path.isfile(STABLE) else None, 'stat': so(f"stat -L -c '%U:%G %a %s' {STABLE}")}
out['before'] = cur
if cur['is_symlink'] or cur['sha256'] != OLD_SHA or os.path.exists('/opt/designcraft/releases/0.8.0-al2023-2edd60ef2f57') or os.path.exists(BK):
    finish('ABORT_PRECHECK_NO_CHANGE', note='현재 CLI 구조·SHA가 예상과 다르거나 대상 경로가 이미 존재')
if int(so("df -BM --output=avail /opt | tail -1").rstrip('M') or 0) < 500 or int(so("df -BM --output=avail /var | tail -1").rstrip('M') or 0) < 500:
    finish('ABORT_PRECHECK_NO_CHANGE', note='디스크 여유 부족')

# ── 1. 백업 ───────────────────────────────────────────────────────────────
os.makedirs('/var/backups/designcraft', mode=0o700, exist_ok=True)
os.mkdir(BK, 0o700)
shutil.copy2(STABLE, BK + '/designcraft-cli.bin')
open(BK + '/original-metadata.txt', 'w').write(so(f"stat {STABLE}; ls -la --time-style=long-iso /opt/designcraft") + '\n')
if sha(BK + '/designcraft-cli.bin') != OLD_SHA:
    finish('ABORT_AFTER_BACKUP_NO_RUNTIME_CHANGE', note='백업 SHA 불일치(실행 경로는 변경하지 않음)')
out['stages'].append({'backup': {'dir': BK, 'sha256_ok': True, 'size': os.path.getsize(BK + '/designcraft-cli.bin')}})

# ── 2. 아티팩트 수령·SHA 검증 ─────────────────────────────────────────────
work = tempfile.mkdtemp(prefix='dccli-', dir='/var/tmp')
tarball = work + '/engine.tar.gz'
rc, o, e = sh(f"aws s3 cp --no-progress --region ap-northeast-2 s3://{BUCKET}/{KEY} {tarball}", 180)
if rc != 0 or not os.path.exists(tarball):
    finish('ABORT_NO_RUNTIME_CHANGE', note='S3 다운로드 실패', s3_error=(e or o)[:300])
got_tar = sha(tarball)
out['stages'].append({'artifact': {'tar_sha256_match': got_tar == WANT_TAR}})
if got_tar != WANT_TAR:
    finish('ABORT_NO_RUNTIME_CHANGE', note='tar.gz SHA 불일치')
ex = work + '/x'; os.mkdir(ex)
subprocess.run(['tar', '--no-same-owner', '-xzf', tarball, '-C', ex], check=True)
if sha(ex + '/bin/designcraft-cli') != WANT_BIN:
    finish('ABORT_NO_RUNTIME_CHANGE', note='바이너리 SHA 불일치')

# ── 3. 새 릴리스 경로에 설치(기존 실행 경로는 아직 그대로) ────────────────
os.makedirs('/opt/designcraft/releases', mode=0o755, exist_ok=True)
shutil.copytree(ex, REL, symlinks=True)
sh(f"chown -R root:root {REL} && find {REL} -type d -exec chmod 755 {{}} + && find {REL} -type f -exec chmod 644 {{}} + && chmod 755 {NEWBIN}")
inst = {'sha256_match': sha(NEWBIN) == WANT_BIN, 'stat': so(f"stat -c '%U:%G %a %s' {NEWBIN}")}
v = sh(f"cd /tmp && runuser -u ec2-user -- {NEWBIN} --version", 30)
inst['ec2user_version'] = v[1][:60]; inst['version_rc'] = v[0]
ld = so(f"cd /tmp && runuser -u ec2-user -- ldd {NEWBIN} 2>&1", 30)
inst['ldd_missing_count'] = ld.count('not found'); inst['ldd_has_libc'] = 'libc.so.6' in ld
out['stages'].append({'installed_new_release': inst})
if not (inst['sha256_match'] and v[0] == 0 and v[1].startswith('designcraft-cli 0.8.0') and inst['ldd_missing_count'] == 0 and inst['ldd_has_libc']):
    finish('ABORT_BEFORE_SWAP_STABLE_PATH_UNCHANGED', note='새 릴리스 검증 실패: 실행 경로는 기존 CLI 그대로. 새 릴리스 디렉터리는 미사용 상태로 남음')

# ── 4. 교체 직전 재확인 후 원자 교체 ──────────────────────────────────────
ok, _ = precheck('precheck_2_before_swap')
if not ok:
    finish('ABORT_BEFORE_SWAP_STABLE_PATH_UNCHANGED', note='교체 직전 재확인 실패: 실행 경로는 기존 CLI 그대로')
tmp_link = f'/opt/designcraft/.designcraft-cli.{CHANGE_ID}'
os.symlink(NEWBIN, tmp_link)
rc, _, e = sh(f"mv -Tf {tmp_link} {STABLE}")
out['swap'] = {'mv_rc': rc}


def verify_new():
    r = {'is_symlink': os.path.islink(STABLE), 'readlink': so(f'readlink {STABLE}'), 'realpath': so(f'readlink -f {STABLE}')}
    r['target_sha256_match'] = os.path.exists(STABLE) and sha(STABLE) == WANT_BIN
    vv = sh(f"cd /tmp && runuser -u ec2-user -- {STABLE} --version", 30)
    r['ec2user_version'] = vv[1][:60]
    l2 = so(f"cd /tmp && runuser -u ec2-user -- ldd {STABLE} 2>&1", 30)
    r['ldd_missing_count'] = l2.count('not found')
    s = services_ok(); g = gate_state()
    r['services'] = s; r['gate'] = g
    m = meminfo(); r['memory'] = {'avail_MiB': m['MemAvailable'], 'swap_used_MiB': m['SwapTotal'] - m['SwapFree']}
    r['oom_5min'] = so("journalctl -k --since '-5 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true")
    r['f2r_ERROR_lines_5min'] = so("journalctl -u f2r-app.service --since '-5 min' --no-pager -o cat 2>/dev/null | grep -c ' ERROR ' || true")
    r['external_cli_processes'] = cli_procs()
    good = (r['is_symlink'] and r['realpath'] == NEWBIN and r['target_sha256_match'] and vv[0] == 0 and vv[1].startswith('designcraft-cli 0.8.0')
            and r['ldd_missing_count'] == 0 and s['ok'] and g['state'] == 'CLOSED' and g['lock_held_by_spring_main_pid']
            and m['MemAvailable'] >= 300 and r['oom_5min'] in ('0', ''))
    return good, r


try:
    good, vr = verify_new() if rc == 0 else (False, {'note': 'mv 실패'})
except Exception as ex_:  # noqa: BLE001
    good, vr = False, {'verify_exception': type(ex_).__name__}
out['stages'].append({'post_swap_verification': vr})
if good:
    finish('SWAPPED_OK', after={'path': STABLE, 'symlink_target': NEWBIN, 'sha256': WANT_BIN},
           note='gate는 CLOSED 유지, resume 미실행. 실사용(PDF/HWPX) 검증은 미수행')

# ── 5. 실패 시 자동 복원(기존 CLI 파일 원자 복원) ─────────────────────────
rb = f'/opt/designcraft/.rollback.{CHANGE_ID}'
sh(f"install -o root -g root -m 0751 {BK}/designcraft-cli.bin {rb} && mv -Tf {rb} {STABLE}")
restored = os.path.isfile(STABLE) and not os.path.islink(STABLE) and sha(STABLE) == OLD_SHA
finish('ROLLED_BACK' if restored else 'ROLLBACK_FAILED_MANUAL_ACTION_NEEDED',
       restored_old_cli_sha_ok=restored, restored_stat=so(f"stat -L -c '%U:%G %a %s' {STABLE}"),
       note='기존 CLI는 GLIBC_2.35 요구로 호스트(2.34)에서 실행 불가: 복원은 변경 철회일 뿐 DesignCraft 정상화가 아님. gate는 건드리지 않음(CLOSED 유지)')
