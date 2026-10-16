"""publishing_versions 정합성 확인(읽기 전용 SELECT). 본문/경로 값은 출력하지 않는다."""
import json, os, re, subprocess
env = {}
for line in open('/etc/f2r/env', encoding='utf-8', errors='replace'):
    line = re.sub(r'^\s*export\s+', '', line.strip())
    k, s, v = line.partition('=')
    if s and not line.startswith('#'):
        env[k.strip()] = v.strip().strip('"').strip("'")


def q(sql):
    p = subprocess.run(['mysql', '--connect-timeout=10', '-h', env['DB_HOST'], '-P', env.get('DB_PORT') or '3306', '-u', env['DB_USER'], env.get('DB_NAME') or 'f2r_db', '-N', '-B', '-e', 'SET SESSION TRANSACTION READ ONLY; ' + sql],
                       env=dict(os.environ, MYSQL_PWD=env['DB_PASSWORD']), capture_output=True, text=True, timeout=40)
    return (p.stdout + p.stderr).strip().splitlines()


out = {'versions_total_max_id': q("SELECT COUNT(*), MAX(id) FROM publishing_versions;"),
       'jobs_total_max_id': q("SELECT COUNT(*), MAX(id) FROM publishing_jobs;"),
       'auto_increment': q("SELECT table_name, auto_increment FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'publishing%';"),
       'fks': q("SELECT table_name, constraint_name, referenced_table_name, delete_rule FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name LIKE 'publishing%';"),
       'snapshot_files_job1': subprocess.run("ls -1 /tmp/publishing/jobs/1/snapshots | wc -l; du -sh /tmp/publishing/jobs/1 | cut -f1; df -h /tmp | tail -1 | awk '{print $1,$2,$4}'", shell=True, capture_output=True, text=True).stdout.split(),
       'tmp_is_tmpfs': subprocess.run("findmnt -n -o FSTYPE /tmp", shell=True, capture_output=True, text=True).stdout.strip(),
       'tmp_publishing_total': subprocess.run("du -sh /tmp/publishing | cut -f1", shell=True, capture_output=True, text=True).stdout.strip()}
print(json.dumps(out, ensure_ascii=False, indent=1))
