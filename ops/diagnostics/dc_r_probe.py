"""잔여 문제 조사(읽기 전용). DB는 SELECT만, 로그는 마스킹, PDF/PNG는 S3 임시 업로드용 경로만 기록한다."""
import glob, json, os, re, subprocess, time

out = {}


def sh(cmd, timeout=60):
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    return (p.stdout or '').strip()


def mask(s):
    s = re.sub(r'[\w.+-]+@[\w-]+\.[\w.-]+', '<email>', s)
    s = re.sub(r'eyJ[\w-]{10,}\.[\w-]{10,}\.[\w-]*', '<jwt>', s)
    s = re.sub(r'\b\d{1,3}(\.\d{1,3}){3}\b', '<ip>', s)
    s = re.sub(r'(?i)(password|passwd|secret|token|api[-_]?key|authorization|cookie)(["\'=:\s]+)\S+', r'\1\2<masked>', s)
    s = re.sub(r'[A-Za-z0-9+/_=-]{32,}', '<longtoken>', s)
    return s[:300]


# 1) f2r ERROR 로그 (최근 6시간, 마스킹, 후속 줄은 예외/Caused by/f2r 스택 상위만)
lines = sh("journalctl -u f2r-app.service --since '-6 hours' --no-pager -o short-iso 2>/dev/null", 90).splitlines()
errs = []
for i, l in enumerate(lines):
    if ' ERROR ' in l:
        ctx = [mask(x) for x in lines[i + 1:i + 40] if re.search(r'Exception|Error:|Caused by|\bat f2r\.', x)][:6]
        errs.append({'line': mask(l), 'context': ctx})
out['error_lines_6h'] = {'count': len(errs), 'items': errs[:20]}
out['service'] = {'active_since': sh('systemctl show f2r-app.service -p ActiveEnterTimestamp --value'),
                  'now_utc': sh('date -u +%FT%TZ'), 'restarts': sh('systemctl show f2r-app.service -p NRestarts --value')}

# 2) DB (SELECT only)
env = {}
for line in open('/etc/f2r/env', encoding='utf-8', errors='replace'):
    line = re.sub(r'^\s*export\s+', '', line.strip())
    k, s, v = line.partition('=')
    if s and not line.startswith('#'):
        env[k.strip()] = v.strip().strip('"').strip("'")


def q(sql):
    p = subprocess.run(['mysql', '--connect-timeout=10', '-h', env['DB_HOST'], '-P', env.get('DB_PORT') or '3306', '-u', env['DB_USER'], env.get('DB_NAME') or 'f2r_db', '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; ' + sql],
                       env=dict(os.environ, MYSQL_PWD=env['DB_PASSWORD']), capture_output=True, text=True, timeout=40)
    return p.stdout.strip().splitlines()


cols = [r.split('\t') for r in q("SELECT column_name, data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='publishing_jobs' ORDER BY ordinal_position;")]
safe_text = {'status', 'template', 'current_step', 'work_dir', 'designcraft_file'}
exprs = []
for c, t in cols:
    if t in ('varchar', 'text', 'longtext', 'json') and c not in safe_text:
        exprs.append(f"CONCAT('len(',IFNULL(LENGTH(`{c}`),'NULL'),')') AS `{c}`")
    else:
        exprs.append(f"`{c}`")
db = {'columns': [c for c, _ in cols], 'now_and_tz': q("SELECT NOW(), @@system_time_zone, @@session.time_zone;"),
      'running_rows': [r.split('\t') for r in q("SELECT " + ','.join(exprs) + " FROM publishing_jobs WHERE status='RUNNING' ORDER BY id LIMIT 5;")],
      'status_counts': q("SELECT status, COUNT(*), MIN(created_at), MAX(updated_at) FROM publishing_jobs GROUP BY status;")}
ids = [r[0] for r in db['running_rows']]
if ids:
    jid = int(ids[0])
    rel = {}
    for t in q("SELECT table_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name LIKE 'publishing%' AND column_name='job_id';"):
        rel[t] = (q(f"SELECT COUNT(*) FROM `{t}` WHERE job_id={jid};") or ['?'])[0]
    db['related_row_counts_for_running_job'] = rel
    ver = [r for r in q("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='publishing_versions';")]
    out['running_job_id'] = jid
    wd = f'/tmp/publishing/jobs/{jid}'
    out['running_job_workdir'] = {'exists': os.path.isdir(wd),
                                  'files': sh(f"ls -la --time-style=full-iso {wd} 2>&1 | tail -n +2 | awk '{{print $5,$6,$7,$9}}'"),
                                  'open_handles': sh(f"lsof +D {wd} 2>/dev/null | wc -l || true"),
                                  'jvm_log_lines_for_job(masked)': [mask(x) for x in sh(f"journalctl -u f2r-app.service --since '2026-10-14' --no-pager -o short-iso 2>/dev/null | grep -E 'jobId={jid}\\b|작업 {jid} ' | tail -12", 90).splitlines()]}
out['db'] = db
out['spring_uptime_vs_row'] = sh("ps -o etime= -p $(systemctl show f2r-app.service -p MainPID --value)")

# 3) PDF/PNG 비교용 파일 (읽기만)
cli_dir = '/var/tmp/dc-synth-test-20261016-154353'
out['cli_test_dir'] = sh(f"ls -la {cli_dir} | tail -n +2 | awk '{{print $5,$9}}'")
srv_pdfs = sorted(glob.glob('/tmp/publishing/jobs/2/export-*.pdf'))
out['server_export_pdfs'] = srv_pdfs
open('/var/tmp/dc_r_files.txt', 'w').write('\n'.join(
    [f'srv.pdf {srv_pdfs[-1]}' if srv_pdfs else ''] + [f'cli.pdf {p}' for p in glob.glob(cli_dir + '/*.pdf')[:1]] + [f'cli{i}.png {p}' for i, p in enumerate(sorted(glob.glob(cli_dir + '/*.png'))[:3])]).strip() + '\n')
print(json.dumps(out, ensure_ascii=False, indent=1))
