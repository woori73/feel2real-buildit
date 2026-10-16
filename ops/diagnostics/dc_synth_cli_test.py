"""승인 범위: (A) /opt/designcraft/templates 그룹을 ec2-user로 변경(소유자 root 유지, chmod 없음, 예외 구조 발견 시 무변경 중단)
(B) CLI 0.8 단독 합성 문서 1건 테스트(앱·gate·DB 미사용). 운영 DB 접근 없음, gate 상태는 읽기만, 서비스 재시작 없음."""
import hashlib, json, os, re, shutil, struct, subprocess, threading, time, zipfile

T = '/opt/designcraft/templates'
CLI = '/opt/designcraft/designcraft-cli'
JAR = '/home/ec2-user/app.jar'
TEMPLATE = 'ko-textbook-a4'
STAMP = time.strftime('%Y%m%d-%H%M%S', time.gmtime())
W = f'/var/tmp/dc-synth-test-{STAMP}'
BK = '/var/backups/designcraft'
SD = '/var/lib/feel2real/designcraft-maintenance'
out = {'stamp': STAMP, 'stages': []}


def sh(cmd, timeout=60):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or '').strip()
    except Exception as e:  # noqa: BLE001
        return 'ERR:' + type(e).__name__


def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for c in iter(lambda: f.read(1 << 20), b''):
            h.update(c)
    return h.hexdigest()


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


def health():
    s = sh("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 100")
    w = sh("curl -s -o /dev/null -m 10 -w '%{http_code}' http://127.0.0.1:8000/api/health")
    return {'spring': s, 'wiki_http': w, 'ok': '"status":"UP"' in s and w == '200'}


def gate():
    return {'state_file': sh(f"cat {SD}/state 2>/dev/null")}


def finish(status, **kw):
    out['status'] = status
    out.update(kw)
    print(json.dumps(out, ensure_ascii=False, indent=1))
    raise SystemExit(0)


# ── 사전 점검 ──────────────────────────────────────────────────────────────
m0 = meminfo(); p0, a0 = vm('pswpout'), vm('pswpin'); time.sleep(10); m1 = meminfo()
pre = {'health': health(), 'gate': gate(), 'external_cli_processes': int(sh("ps -eo args | grep -iE 'designcraft-cli|verapdf' | grep -v grep | grep -v dc_synth | wc -l") or 0),
       'memory': {'avail_MiB': [m0['MemAvailable'], m1['MemAvailable']], 'swap_used_MiB': [m0['SwapTotal'] - m0['SwapFree'], m1['SwapTotal'] - m1['SwapFree']],
                  'swap_out_pages_10s': vm('pswpout') - p0, 'swap_in_pages_10s': vm('pswpin') - a0},
       'oom_60min': sh("journalctl -k --since '-60 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true")}
out['stages'].append({'precheck': pre})
if (not pre['health']['ok'] or pre['gate']['state_file'] != 'CLOSED' or pre['external_cli_processes'] != 0 or min(pre['memory']['avail_MiB']) < 300
        or pre['memory']['swap_out_pages_10s'] > 2560 or pre['oom_60min'] not in ('0', '')):
    finish('ABORT_PRECHECK_NO_CHANGE')
baseline_swap = m1['SwapTotal'] - m1['SwapFree']
baseline_swapout = vm('pswpout')

# ── A. 템플릿 권한(그룹만 ec2-user, 예외 구조면 무변경 중단) ───────────────
tmpl = {'is_symlink': os.path.islink(T), 'realpath_equals': os.path.realpath(T) == T}
rows = []
for line in sh(f"find {T} -printf '%m|%u|%g|%y|%n|%p\\n'").splitlines():
    mode, u, g, ty, nl, p = line.split('|', 5)
    rows.append((mode, u, g, ty, int(nl), p))
exceptions = []
for mode, u, g, ty, nl, p in rows:
    if ty not in ('f', 'd'): exceptions.append(f'type={ty}:{p}')
    elif (u, g) != ('root', 'root'): exceptions.append(f'owner={u}:{g}:{p}')
    elif ty == 'f' and (mode != '640' or nl != 1): exceptions.append(f'file mode/links={mode}/{nl}:{p}')
    elif ty == 'd' and mode != '750': exceptions.append(f'dir mode={mode}:{p}')
acl = str(sh(f"getfacl -R -s -p {T} 2>/dev/null").count('# file:')) if shutil.which('getfacl') else 'n/a'
attr = str(sum(1 for l in sh(f"lsattr -R {T} 2>/dev/null").splitlines() if l.split() and re.fullmatch(r'[-a-zA-Z]{15,}', l.split()[0]) and 'i' in l.split()[0])) if shutil.which('lsattr') else 'n/a'
tmpl.update({'entries': len(rows), 'exceptions': exceptions[:20], 'extra_acl_entries': acl, 'immutable_flag_entries': attr,
             'mount_ro': sh(f"findmnt -n -o OPTIONS --target {T} | tr ',' '\\n' | grep -c '^ro$' || true")})
out['stages'].append({'templates_scan': tmpl})
if tmpl['is_symlink'] or not tmpl['realpath_equals'] or exceptions or acl not in ('0', 'n/a') or attr not in ('0', 'n/a') or tmpl['mount_ro'] not in ('0', ''):
    finish('ABORT_TEMPLATES_EXCEPTION_NO_CHANGE', note='예외적 구조 발견: 일괄 변경하지 않음')
os.makedirs(BK, mode=0o700, exist_ok=True)
before_sha = {p: sha(p) for _, _, _, ty, _, p in rows if ty == 'f'}
before_stat = {p: (mode, u, g) for mode, u, g, ty, nl, p in rows}
open(f'{BK}/templates-perms-before-{STAMP}.txt', 'w').write('\n'.join(f'{m} {u}:{g} {t} {p}' for m, u, g, t, _, p in rows) + '\n\nsha256:\n' + '\n'.join(f'{h}  {p}' for p, h in sorted(before_sha.items())) + '\n')
os.chmod(f'{BK}/templates-perms-before-{STAMP}.txt', 0o600)
rc = subprocess.run(['chgrp', '-R', '--no-dereference', 'ec2-user', T]).returncode
after = [tuple(l.split('|', 5)) for l in sh(f"find {T} -printf '%m|%u|%g|%y|%n|%p\\n'").splitlines()]
bad = [p for mode, u, g, ty, nl, p in after if (u, g) != ('root', 'ec2-user') or mode != before_stat[p][0] or (int(mode, 8) & 0o007) != 0 or (int(mode, 8) & 0o020) != 0]
sha_same = all(sha(p) == h for p, h in before_sha.items())
font = f'{T}/{TEMPLATE}/Document Fonts/NotoSansKR-Regular.ttf'
rd = subprocess.run(['sh', '-c', f'head -c 4 "{font}" | od -An -tx1; ls "{T}" | wc -l'], user='ec2-user', group='ec2-user', extra_groups=[], capture_output=True, text=True)
tm = {'chgrp_rc': rc, 'entries_not_as_expected': bad[:10], 'content_sha256_unchanged': sha_same, 'ec2user_read_font_head_hex': rd.stdout.split('\n')[0].strip(),
      'ec2user_ls_template_count': rd.stdout.split('\n')[1].strip() if '\n' in rd.stdout else rd.stderr[:100],
      'after_sample': [f'{m} {u}:{g} {t} {p.replace(T, "")}' for m, u, g, t, _, p in after[:4]]}
out['stages'].append({'templates_fix': tm})
if rc != 0 or bad or not sha_same or rd.returncode != 0:
    subprocess.run(['chgrp', '-R', '--no-dereference', 'root', T])
    finish('ABORT_TEMPLATES_FIX_VERIFY_FAILED_ROLLED_BACK_GROUP', note='그룹을 root로 되돌림')

# ── B. CLI 단독 합성 테스트 ────────────────────────────────────────────────
os.makedirs(W, mode=0o700); shutil.chown(W, 'ec2-user', 'ec2-user')
doc = f'{W}/job.designcraft'
open(doc, 'wb').write(zipfile.ZipFile(JAR).read(f'BOOT-INF/classes/publishing/templates/{TEMPLATE}/template.designcraft'))
open(f'{W}/synthetic.md', 'w', encoding='utf-8').write(
    '# 합성 테스트 문서\n\n한글과 English가 섞인 짧은 합성 문장입니다. (개인정보 없음) 2026학년도 "인용 부호"와 (괄호), 마침표.\n\n'
    '## 1절 합성 어휘\n\ncomprehension (명) 이해력\n\ncontext (명) 문맥, 상황\n\n두 번째 문단입니다. 이 문서는 CLI 조판 기능만 확인하기 위한 합성 데이터입니다.\n')
for f in (doc, f'{W}/synthetic.md'):
    shutil.chown(f, 'ec2-user', 'ec2-user')
cp = subprocess.run(['cp', '-r', f'{T}/{TEMPLATE}/Document Fonts', f'{W}/Document Fonts'], user='ec2-user', group='ec2-user', extra_groups=[], capture_output=True, text=True)
out['stages'].append({'job_dir_prepared': {'dir': W, 'template_resource_from_jar': TEMPLATE, 'fonts_copied_as_ec2user_rc': cp.returncode, 'fonts_files': sorted(os.listdir(f'{W}/Document Fonts')) if cp.returncode == 0 else cp.stderr[:200]}})
if cp.returncode != 0:
    finish('ABORT_FONT_COPY_FAILED', test_dir=W)

ENV = {'PATH': '/usr/bin:/bin', 'HOME': W, 'LANG': 'C.UTF-8', 'LC_ALL': 'C.UTF-8'}
aborted = {'why': None}


def run_cli(label, args, stdin=None, timeout=120):
    """ec2-user로 CLI 1개 실행. 메모리 300MiB 미만·스왑 급증 시 즉시 kill."""
    h = health()
    if not h['ok']:
        aborted['why'] = 'service_unhealthy_before_' + label
        return {'label': label, 'skipped': True}
    t0 = time.time()
    p = subprocess.Popen(args, cwd=W, env=ENV, user='ec2-user', group='ec2-user', extra_groups=[], stdin=subprocess.PIPE if stdin else subprocess.DEVNULL,
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    res = {}
    th = threading.Thread(target=lambda: res.update(zip(('o', 'e'), p.communicate(stdin, timeout=None))))
    th.start()
    min_avail, peak_rss, max_swap, timed_out = 10 ** 6, 0, 0, False
    while th.is_alive():
        m = meminfo(); min_avail = min(min_avail, m['MemAvailable']); max_swap = max(max_swap, m['SwapTotal'] - m['SwapFree'])
        try:
            for line in open(f'/proc/{p.pid}/status'):
                if line.startswith('VmHWM:'): peak_rss = max(peak_rss, int(line.split()[1]) // 1024)
        except OSError:
            pass
        if min_avail < 300 or (max_swap - baseline_swap) > 64 or (vm('pswpout') - baseline_swapout) > 16384:
            p.kill(); aborted['why'] = f'memory_or_swap_guard_in_{label}'; break
        if time.time() - t0 > timeout:
            p.kill(); timed_out = True; aborted['why'] = f'timeout_in_{label}'; break
        time.sleep(0.2)
    th.join(30)
    return {'label': label, 'rc': p.returncode, 'seconds': round(time.time() - t0, 1), 'min_MemAvailable_MiB': min_avail, 'cli_peak_rss_MiB': peak_rss,
            'swap_used_peak_MiB': max_swap, 'timed_out': timed_out, 'stdout_head': (res.get('o') or '')[:700], 'stderr_head': (res.get('e') or '')[:400]}


steps = []
# 1) 반입(앱의 importManuscript와 같은 한 script): file.place → selectAll → ko-body 적용 후 저장
script_in = ('file.place={"path":"synthetic.md","autoflow":true}\nedit.selectAll={}\nstyle.paragraph.apply={"name":"ko-body"}\n')
r = run_cli('1_import_manuscript', [CLI, 'script', '-', '--in', doc, '--save', doc + '.saving'], stdin=script_in); steps.append(r)
if r.get('rc') == 0 and os.path.exists(doc + '.saving'):
    os.replace(doc + '.saving', doc)
# 2) 문서 정보
page_count = None
if not aborted['why'] and r.get('rc') == 0:
    r = run_cli('2_inspect', [CLI, 'run', '--in', doc, '--cmd', 'document.inspect']); steps.append(r)
    try: page_count = json.loads(r['stdout_head'] if len(r.get('stdout_head', '')) < 700 else '{}').get('pageCount')
    except ValueError: page_count = 'see_stdout_head'
# 3) PNG 미리보기(앱과 같은 인자)
png = f'{W}/page-001.png'
if not aborted['why'] and steps[-1].get('rc') == 0:
    r = run_cli('3_render_png', [CLI, 'run', '--in', doc, '--page', '0', '--scale', '2.0', '--export', png]); steps.append(r)
# 4) PDF 출력(앱과 같은 파라미터: x4, marks, tagged) — 실패 시 standard=none으로 1회만 보조 시도
pdf = f'{W}/synthetic-x4.pdf'
pdf_params = json.dumps({'path': pdf, 'standard': 'x4', 'marks': {'crop': True, 'bleed': True, 'pageInfo': True}, 'tagged': True}, ensure_ascii=False)
if not aborted['why'] and steps[-1].get('rc') == 0:
    r = run_cli('4_export_pdf_x4', [CLI, 'run', '--in', doc, '--cmd', 'file.exportPdf=' + pdf_params]); steps.append(r)
    if r.get('rc') != 0 and not aborted['why']:
        pdf = f'{W}/synthetic-none.pdf'
        r = run_cli('4b_export_pdf_none_fallback_once', [CLI, 'run', '--in', doc, '--cmd', 'file.exportPdf=' + json.dumps({'path': pdf, 'standard': 'none'})]); steps.append(r)
out['stages'].append({'cli_steps': steps})

# 산출물 검증
art = {}
if os.path.exists(png):
    b = open(png, 'rb').read(); art['png'] = {'bytes': len(b), 'magic_ok': b[:8] == b'\x89PNG\r\n\x1a\n', 'width_height': struct.unpack('>II', b[16:24]) if len(b) > 24 else None, 'sha256': sha(png)[:16]}
else:
    art['png'] = 'not_created'
if os.path.exists(pdf):
    b = open(pdf, 'rb').read()
    art['pdf'] = {'file': os.path.basename(pdf), 'bytes': len(b), 'header_ok': b[:5] == b'%PDF-', 'eof_marker': b[-1024:].find(b'%%EOF') >= 0, 'sha256': sha(pdf)[:16],
                  'mentions_NotoSansKR': b.count(b'NotoSansKR'), 'page_objects_hint': len(re.findall(rb'/Type\s*/Page[^s]', b)),
                  'pdftotext_available': bool(shutil.which('pdftotext'))}
    if shutil.which('pdftotext'):
        txt = subprocess.run(['pdftotext', pdf, '-'], capture_output=True, text=True).stdout
        art['pdf']['hangul_chars_extracted'] = len(re.findall('[가-힣]', txt)); art['pdf']['english_words_extracted'] = len(re.findall('[A-Za-z]{3,}', txt))
else:
    art['pdf'] = 'not_created'
art['page_count_from_inspect'] = page_count
art['kept_in'] = W
art['files'] = sorted(f'{f} {os.path.getsize(os.path.join(W, f))}B' for f in os.listdir(W) if os.path.isfile(os.path.join(W, f)))
out['stages'].append({'artifacts': art})

# 사후 점검
time.sleep(3)
mpost = meminfo()
out['post'] = {'health': health(), 'gate': gate(), 'external_cli_processes': int(sh("ps -eo args | grep -iE 'designcraft-cli|verapdf' | grep -v grep | grep -v dc_synth | wc -l") or 0),
               'memory': {'avail_MiB': mpost['MemAvailable'], 'swap_used_MiB': mpost['SwapTotal'] - mpost['SwapFree']}, 'swap_used_baseline_MiB': baseline_swap,
               'oom_since_start': sh("journalctl -k --since '-10 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true"),
               'f2r_ERROR_lines_10min': sh("journalctl -u f2r-app.service --since '-10 min' --no-pager -o cat 2>/dev/null | grep -c ' ERROR ' || true")}
finish('ABORTED_BY_GUARD' if aborted['why'] else 'CLI_TEST_DONE', guard=aborted['why'])
