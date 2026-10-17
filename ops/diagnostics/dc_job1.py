"""DesignCraft Job 1 생존·상태 조사. 읽기 전용(SELECT만, SET SESSION TRANSACTION READ ONLY). 변경·생성·삭제 없음. 비밀값·사용자 식별 정보 미출력."""
import json, os, re, subprocess, time

out = {}


def sh(cmd, timeout=60):
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    return (p.stdout or '').strip()


env = {}
for line in open('/etc/f2r/env', errors='replace'):
    k, s, v = re.sub(r'^\s*export\s+', '', line.strip()).partition('=')
    if s and not k.startswith('#'):
        env[k.strip()] = v.strip().strip('"\'')


def q(sql):
    r = subprocess.run(['mysql', '--connect-timeout=10', '-h', env['DB_HOST'], '-P', env.get('DB_PORT') or '3306', '-u', env['DB_USER'], env.get('DB_NAME') or 'f2r_db', '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; ' + sql],
                       env=dict(os.environ, MYSQL_PWD=env['DB_PASSWORD']), capture_output=True, text=True, timeout=30)
    return r.stdout.strip().splitlines() if r.returncode == 0 else ['ERR ' + r.stderr.strip()[:120]]


out['now_utc'] = sh('date -u +%FT%TZ')
out['jobs_all'] = q("SELECT id,status,progress,current_step,attempt_count,template,created_at,updated_at,IFNULL(LEFT(error,60),'') FROM publishing_jobs ORDER BY id")
out['job1_versions'] = q("SELECT COUNT(*),MIN(created_at),MAX(created_at) FROM publishing_versions WHERE job_id=1")
out['job1_columns_present'] = q("SELECT work_dir IS NOT NULL, designcraft_file IS NOT NULL FROM publishing_jobs WHERE id=1")
j1 = '/tmp/publishing/jobs/1'
files = []
for root, d, fs in os.walk(j1):
    for f in fs:
        p = os.path.join(root, f); st = os.stat(p)
        files.append((p.replace(j1 + '/', ''), st.st_size, time.strftime('%FT%TZ', time.gmtime(st.st_mtime))))
out['job1_files'] = sorted(files)[:60]
out['job1_file_count'] = len(files)
out['job1_newest_mtime'] = max([f[2] for f in files]) if files else None
out['job1_pdf_outputs'] = [f for f in files if f[0].lower().endswith('.pdf')]
out['processes'] = {'designcraft_cli': sh("ps -eo pid,etimes,args | grep '[d]esigncraft-cli' | cut -c1-120"), 'node_or_chromium_pdf': sh("ps -eo pid,etimes,comm | grep -E 'chrom|node|vivliostyle|weasy' | grep -v grep | head -5"),
                'open_files_in_job1_dir': sh(f"lsof +D {j1} 2>/dev/null | tail -n +2 | wc -l") or 'lsof-unavailable'}
out['locks_dir'] = sh('ls -la /var/lib/feel2real/designcraft-maintenance/ | tail -n +2 | awk \'{print $1,$5,$6,$7,$8,$9}\'')
out['gate_state'] = sh('cat /var/lib/feel2real/designcraft-maintenance/state')
out['app_log_job1_mentions_7d'] = sh("journalctl -u f2r-app.service --since '-7 days' --no-pager 2>/dev/null | grep -E 'jobId=1[^0-9]|작업 1 |jobs/1[^0-9]' | cut -c1-200 | sed -E 's/(password|secret|token|key)=[^ ]+/\\1=***/Ig' | tail -12")
out['app_log_publishing_errors_7d'] = sh("journalctl -u f2r-app.service --since '-7 days' --no-pager 2>/dev/null | grep -i 'publishing' | grep ' ERROR ' | cut -c1-200 | tail -5")
out['workdir_disk'] = sh(f"du -sk {j1} | cut -f1")
out['tmp_publishing_is_tmpfs_or_disk'] = sh("df -T /tmp/publishing | tail -1 | awk '{print $2,$6}'")
out['tmp_cleanup_policy'] = sh("systemctl cat systemd-tmpfiles-clean.timer 2>/dev/null | grep -i 'OnUnitActiveSec\\|OnBootSec'; grep -rh '/tmp' /usr/lib/tmpfiles.d/tmp.conf /etc/tmpfiles.d/*.conf 2>/dev/null | head -4")
print(json.dumps(out, indent=1, ensure_ascii=False))
