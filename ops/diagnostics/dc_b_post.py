"""B안 종료 후 서버 점검(읽기 전용). DB는 SELECT만. 합성 작업 폴더의 PDF 메타만 읽는다."""
import glob, hashlib, json, os, re, subprocess, time

SD = '/var/lib/feel2real/designcraft-maintenance'
out = {}


def sh(cmd, timeout=40):
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    return (p.stdout or '').strip()


def meminfo():
    d = {}
    for line in open('/proc/meminfo'):
        k, _, v = line.partition(':')
        d[k] = int(v.split()[0]) // 1024
    return d


def parse_env(path):
    d = {}
    for line in open(path, encoding='utf-8', errors='replace'):
        line = re.sub(r'^\s*export\s+', '', line.strip())
        k, s, v = line.partition('=')
        if s and not line.startswith('#'):
            d[k.strip()] = v.strip().strip('"').strip("'")
    return d


pid = sh('systemctl show f2r-app.service -p MainPID --value')
inode = sh(f"stat -c %i {SD}/instance.lock")
pids = sh(f"awk -v i={inode} '{{split($6,a,\":\"); if (a[3]==i) print $5}}' /proc/locks").split()
out['gate'] = {'state_file': sh(f'cat {SD}/state'), 'lock_held_by_spring_main_pid': pid in pids}
out['health'] = {'spring': sh("curl -s -m 8 http://127.0.0.1:8080/actuator/health | head -c 100"), 'wiki_http': sh("curl -s -o /dev/null -m 10 -w '%{http_code}' http://127.0.0.1:8000/api/health")}
out['external_cli_processes'] = int(sh("ps -eo args | grep -iE 'designcraft-cli|verapdf' | grep -v grep | grep -v dc_b_post | grep -v dc_gate_watchdog | wc -l") or 0)
m = meminfo()
out['memory_now'] = {'avail_MiB': m['MemAvailable'], 'swap_used_MiB': m['SwapTotal'] - m['SwapFree']}
out['oom_60min'] = sh("journalctl -k --since '-60 min' --no-pager 2>/dev/null | grep -ci 'out of memory\\|oom-kill' || true")
out['f2r'] = {'restarts': sh('systemctl show f2r-app.service -p NRestarts --value'), 'since': sh('systemctl show f2r-app.service -p ActiveEnterTimestamp --value'),
              'ERROR_lines_20min': sh("journalctl -u f2r-app.service --since '-20 min' --no-pager -o cat 2>/dev/null | grep -c ' ERROR ' || true", 60),
              'designcraft_related_lines_20min(count only)': sh("journalctl -u f2r-app.service --since '-20 min' --no-pager -o cat 2>/dev/null | grep -ciE 'Publishing|템플릿 배치|Document Fonts|PDF 출력' || true", 60),
              'document_fonts_not_found_warns_20min': sh("journalctl -u f2r-app.service --since '-20 min' --no-pager -o cat 2>/dev/null | grep -c 'Document Fonts를 찾을 수 없습니다' || true", 60)}
env = parse_env('/etc/f2r/env')
host, port, name, user, pw = env.get('DB_HOST'), env.get('DB_PORT') or '3306', env.get('DB_NAME') or 'f2r_db', env.get('DB_USER'), env.get('DB_PASSWORD')


def q(sql):
    p = subprocess.run(['mysql', '--connect-timeout=10', '-h', host, '-P', port, '-u', user, name, '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; ' + sql], env=dict(os.environ, MYSQL_PWD=pw), capture_output=True, text=True, timeout=40)
    return p.stdout.strip().splitlines()


out['db_readonly'] = {'publishing_jobs': {r.split('\t')[0]: int(r.split('\t')[1]) for r in q("SELECT status, COUNT(*) FROM publishing_jobs GROUP BY status;") if '\t' in r},
                      'hwpx_jobs': {r.split('\t')[0]: int(r.split('\t')[1]) for r in q("SELECT status, COUNT(*) FROM hwpx_jobs GROUP BY status;") if '\t' in r},
                      'running_rows(status,created,updated)': [r.split('\t') for r in q("SELECT status, created_at, updated_at FROM publishing_jobs WHERE status='RUNNING' ORDER BY id LIMIT 5;")],
                      'synthetic_rows_left': (q("SELECT COUNT(*) FROM publishing_jobs WHERE title LIKE 'SYNTH-DC-TEST-%';") or ['?'])[0],
                      'artifacts_rows_total': (q("SELECT COUNT(*) FROM publishing_artifacts;") or ['?'])[0]}
# 합성 작업 폴더 탐색: 원고에 합성 문구가 있고 최근 90분 이내
found = []
for d in glob.glob('/tmp/publishing/jobs/*'):
    try:
        if time.time() - os.path.getmtime(d) > 5400:
            continue
        for ms in glob.glob(d + '/manuscript.*'):
            if '합성 테스트' in open(ms, encoding='utf-8', errors='replace').read():
                found.append(d)
                break
    except OSError:
        pass
res = []
for d in found:
    item = {'dir': d, 'files': sorted(f'{f} {os.path.getsize(os.path.join(d, f))}B' for f in os.listdir(d) if os.path.isfile(os.path.join(d, f)))}
    pdfs = sorted(glob.glob(d + '/export-*.pdf'))
    if pdfs:
        b = open(pdfs[-1], 'rb').read()
        item['pdf'] = {'file': os.path.basename(pdfs[-1]), 'bytes': len(b), 'header_ok': b[:5] == b'%PDF-', 'eof': b[-1024:].find(b'%%EOF') >= 0, 'sha256_16': hashlib.sha256(b).hexdigest()[:16], 'NotoSansKR_refs': b.count(b'NotoSansKR')}
        open('/var/tmp/dc_b_verify_pdf_path.txt', 'w').write(pdfs[-1])
    pngs = sorted(glob.glob(d + '/*.png'))
    if pngs:
        item['png_files'] = [f'{os.path.basename(p)} {os.path.getsize(p)}B' for p in pngs]
        open('/var/tmp/dc_b_verify_png_path.txt', 'w').write(pngs[-1])
    res.append(item)
out['synthetic_job_dirs_on_disk'] = res
out['tmp_publishing_listing'] = sh("ls -la /tmp/publishing/jobs 2>&1 | tail -n +2 | awk '{print $6,$7,$8,$9}'")
print(json.dumps(out, ensure_ascii=False, indent=1))
