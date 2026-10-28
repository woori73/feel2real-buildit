#!/usr/bin/env python3
"""READ ONLY. Run through an existing authorized server session; no restart or writes.
Print only fixed public assets, file digests and anonymous media manifest metadata.
Never read environment files, cookies, database credentials or JAR configuration.
"""
import hashlib
import json
from pathlib import Path
import re
import urllib.request
import zipfile

PREFIX = 'BOOT-INF/classes/'
ASSETS = [
    'templates/public/landing.html',
    'static/js/f2r_home_agent.js',
    'static/js/f2r_agent_clip_switch.js',
    'static/js/f2r_home_presentation.js',
    'static/css/f2r_home_agent.css',
    'static/css/f2r_home_compact.css',
]
BASELINE = '18ca78cb7743fbd9e94e055fa5c6a4cd6fb5ca1011885b63d866f96eaa1dcdc0'


def digest(stream):
    h = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        h.update(block)
    return h.hexdigest()


def inspect_jar(path):
    if not path.is_file():
        return {'path': str(path), 'exists': False}
    with path.open('rb') as stream:
        result = {'path': str(path), 'sha256': digest(stream), 'assets': {}}
    result['matches_normal_df425588_jar'] = result['sha256'] == BASELINE
    with zipfile.ZipFile(path) as jar:
        for name in ASSETS:
            key = PREFIX + name
            try:
                with jar.open(key) as stream:
                    result['assets'][key] = digest(stream)
            except KeyError:
                result['assets'][key] = None
    return result


def main():
    report = {'read_only': True, 'jars': [inspect_jar(p) for p in [
        Path('/home/ec2-user/app.jar'),
        Path('/var/backups/f2r-pr914-df425588/app.jar')
    ]]}
    try:
        req = urllib.request.Request('http://127.0.0.1:8080/api/agent-media/public', method='GET')
        with urllib.request.urlopen(req, timeout=10) as response:
            report['manifest'] = {'status': response.status, 'content_type': response.headers.get('Content-Type'),
                                  'cache_control': response.headers.get('Cache-Control'), 'body': json.load(response)}
    except Exception as error:
        report['manifest'] = {'error_type': type(error).__name__}
    # Fixed public endpoint entries only; never print raw log lines or client identifiers.
    report['public_media_access'] = []
    pattern = re.compile(r'\[([^\]]+)\] "(GET|HEAD) ((?:/api/agent-media/public|/agent-media/public/(?:seven|suni)/(?:poster\.jpg|idle\.mp4|speak\.mp4)))(?:\?[^ ]*)? HTTP/[^" ]+" (\d{3}) (\d+|-)')
    for path in [Path('/var/log/nginx/access.log.1'), Path('/var/log/nginx/access.log')]:
        if not path.is_file():
            continue
        try:
            with path.open(errors='replace') as stream:
                for line in stream:
                    match = pattern.search(line)
                    if match and match[1].startswith('28/Oct/2026:03:'):
                        report['public_media_access'].append(dict(zip(['time','method','path','status','bytes'], match.groups())))
        except PermissionError:
            report.setdefault('unreadable_logs', []).append(str(path))
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
