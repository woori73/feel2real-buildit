#!/usr/bin/env python3
"""Fixed EC2 payload. Read metadata/SELECT only; never print env, SQL errors or journals."""
import hashlib
import json
import os
import pathlib
import re
import shlex
import subprocess
import sys
import zipfile


def command(argv, **kwargs):
    return subprocess.run(argv, capture_output=True, text=True, timeout=20, **kwargs)


def env_values(path):
    values = {}
    for line in path.read_text().splitlines():
        match = re.match(r"^(?:export\s+)?(DB_HOST|DB_PORT|DB_USER|DB_PASSWORD|DB_NAME)=(.*)$", line)
        if match:
            parts = shlex.split(match[2], comments=True)
            if len(parts) == 1:
                values[match[1]] = parts[0]
    return values


def jar_info(path):
    result = {"exists": path.is_file(), "gitSha": None}
    if not path.is_file():
        return result
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(part)
    result.update(sha256=digest.hexdigest(), bytes=path.stat().st_size)
    with zipfile.ZipFile(path) as jar:
        for name in ('META-INF/MANIFEST.MF', 'BOOT-INF/classes/git.properties'):
            if name not in jar.namelist():
                continue
            text = jar.read(name).decode('utf-8', errors='replace')
            match = re.search(r'(?mi)^(?:Build-Commit: |git.commit.id(?:.full)?=)([a-f0-9]{40})\s*$', text)
            if match and not re.search(r'(?mi)^Build-Working-Tree: dirty\s*$', text):
                result['gitSha'] = match[1]
        result['poiLibraries'] = sorted(n.rsplit('/', 1)[-1] for n in jar.namelist()
                                       if re.match(r'BOOT-INF/lib/poi(?:-ooxml(?:-lite)?)?-.*\.jar$', n))
    return result


def collect(expected_db_host):
    report = {'memory': {}, 'service': {}, 'jars': {}, 'flyway': {'status': 'UNVERIFIED'}}
    for line in pathlib.Path('/proc/meminfo').read_text().splitlines():
        key, value = line.split(':', 1)
        if key in ('MemTotal', 'MemAvailable', 'SwapTotal', 'SwapFree'):
            report['memory'][key + 'KiB'] = int(value.strip().split()[0])
    result = command(['systemctl', 'show', 'f2r-app.service', '--property=ActiveState,SubState,MainPID,NRestarts'])
    report['service'] = dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)
    for label, path in [('current', '/home/ec2-user/app.jar'), ('previous', '/home/ec2-user/app.jar.previous')]:
        try:
            report['jars'][label] = jar_info(pathlib.Path(path))
        except Exception:
            report['jars'][label] = {'status': 'UNVERIFIED', 'reason': 'jar_read_failed'}
    # The on-disk artifact is evidence, not proof of which generation a running JVM loaded.
    pid = report['service'].get('MainPID', '0')
    report['runningSha'] = {'status': 'UNVERIFIED', 'reason': 'no_runtime_generation_attestation'}
    if pid.isdigit() and int(pid) > 0:
        try:
            uptime = float(pathlib.Path('/proc/uptime').read_text().split()[0])
            stat = pathlib.Path('/proc/' + pid + '/stat').read_text().rsplit(')', 1)[1].split()
            age = uptime - int(stat[19]) / os.sysconf('SC_CLK_TCK')
            process_started = __import__('time').time() - age
            jar = pathlib.Path('/home/ec2-user/app.jar')
            sha = report['jars'].get('current', {}).get('gitSha')
            cmdline = pathlib.Path('/proc/' + pid + '/cmdline').read_bytes().split(b'\0')
            if sha and b'/home/ec2-user/app.jar' in cmdline and jar.stat().st_mtime <= process_started:
                report['runningSha'] = {'status': 'OBSERVED', 'sha': sha,
                                        'evidence': 'embedded_manifest_and_process_start_after_jar_mtime'}
        except Exception:
            pass
    try:
        values = env_values(pathlib.Path('/etc/f2r/env'))
        if not expected_db_host or values.get('DB_HOST') != expected_db_host:
            report['flyway']['reason'] = 'new_account_rds_endpoint_not_verified'
            return report
        if not re.fullmatch(r'[A-Za-z0-9_]+', values.get('DB_NAME', '')):
            raise ValueError('database name')
        sql = ("SET SESSION TRANSACTION READ ONLY; START TRANSACTION READ ONLY; "
               "SELECT JSON_OBJECT('version',version,'checksum',checksum,'success',success) "
               "FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 12; COMMIT;")
        child_env = os.environ.copy()
        child_env['MYSQL_PWD'] = values['DB_PASSWORD']
        result = command(['mysql', '--connect-timeout=8', '--ssl-mode=REQUIRED', '--batch', '--raw',
                          '--skip-column-names', '-h', expected_db_host, '-P', values.get('DB_PORT', '3306'),
                          '-u', values['DB_USER'], values['DB_NAME'], '-e', sql], env=child_env)
        if result.returncode:
            raise ValueError('mysql read failed')
        report['flyway'] = {'status': 'OBSERVED', 'recent': [json.loads(s) for s in result.stdout.splitlines()]}
    except Exception:
        report['flyway'] = {'status': 'UNVERIFIED', 'reason': 'mysql_client_or_read_access_unavailable'}
    return report


if __name__ == '__main__':
    print(json.dumps(collect(sys.argv[1] if len(sys.argv) > 1 else ''), ensure_ascii=False))
