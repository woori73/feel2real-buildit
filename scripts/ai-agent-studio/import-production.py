#!/usr/bin/env python3
"""Verify pinned Git originals, then register via existing authenticated Studio APIs.

Default is a read-only dry run. Two apply targets exist:
  * local:      loopback isolated instance only (http://127.0.0.1:18090), unchanged behaviour.
  * production: --production. HTTPS only, the deployed SHA must carry this exact catalog and importer
                and be the latest successful "Deploy to EC2" run, a pre-deploy snapshot name must be
                confirmed, and every write needs --apply.
No paid AI calls. Cookies are read from a 0600 file and never printed or written to the receipt.
"""
import argparse, datetime, hashlib, json, os, pathlib, re, ssl, stat, subprocess, sys, time
import urllib.error, urllib.parse, urllib.request, uuid

PIN = '09d62dee982e2e89e3653c90b1ee42f05bff4f9d'
REPO = pathlib.Path(__file__).resolve().parents[2]
CATALOG = 'src/main/resources/studio/production-09d62dee.json'
IMPORTER = 'src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/studio/StudioProductionImporter.java'
PRODUCTION_HOSTS = {'feel2real.co.kr', 'www.feel2real.co.kr'}
LOOPBACK = {'127.0.0.1', 'localhost'}
GITHUB_API = os.environ.get('F2R_GITHUB_API', 'https://api.github.com')
GITHUB_REPO = 'woori73/feel2real-buildit'
PROJECT = 'ai-agent-part1'


class Abort(Exception):
    """A precondition failed. Nothing further is sent."""


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise Abort('HTTP redirect refused; credentials were not forwarded')


def verify_source(source, catalog):
    actual = {p.relative_to(source).as_posix() for p in source.rglob('*') if p.is_file()}
    if actual != {r['path'] for r in catalog['files']}:
        raise Abort('536개 원본 경로 불일치')
    unique = {}
    for row in catalog['files']:
        file = source / row['path']
        if file.is_symlink():
            raise Abort('symlink not allowed: ' + row['path'])
        data = file.read_bytes()
        if len(data) != row['size'] or hashlib.sha256(data).hexdigest() != row['sha256']:
            raise Abort('원본 SHA·크기 불일치: ' + row['path'])
        unique.setdefault(row['sha256'], row)
    return unique


def git_show(rev, path):
    return subprocess.run(['git', 'show', f'{rev}:{path}'], cwd=REPO, capture_output=True, check=True).stdout


def check_deployed_sha(sha, catalog_bytes, github=True):
    """The deployed commit must contain this exact catalog and the source-import API."""
    if not re.fullmatch(r'[0-9a-f]{40}', sha or ''):
        raise Abort('--deployed-sha 는 40자리 커밋 SHA여야 합니다.')
    try:
        if git_show(sha, CATALOG) != catalog_bytes:
            raise Abort('배포 커밋의 카탈로그가 이 스크립트의 카탈로그와 다릅니다.')
        git_show(sha, IMPORTER)
    except subprocess.CalledProcessError:
        raise Abort('배포 커밋에 카탈로그 또는 적재 API가 없습니다(먼저 git fetch, 또는 아직 미배포).')
    if github:
        url = f'{GITHUB_API}/repos/{GITHUB_REPO}/actions/workflows/deploy-ec2.yml/runs?status=success&per_page=1'
        try:
            req = urllib.request.Request(url, headers={'Accept': 'application/vnd.github+json'})
            with urllib.request.urlopen(req, timeout=30) as r:
                runs = json.loads(r.read()).get('workflow_runs', [])
        except (urllib.error.URLError, ValueError) as e:
            raise Abort(f'GitHub 배포 기록을 확인할 수 없습니다: {e}')
        if not runs or runs[0].get('head_sha') != sha:
            raise Abort('가장 최근 성공 배포(Deploy to EC2)의 SHA와 --deployed-sha 가 다릅니다.')


def check_snapshot(name, sha, now=None):
    """Consistency check only. Existence must be confirmed by the approved snapshot step."""
    m = re.fullmatch(r'f2r-predeploy-(\d{12})-([0-9a-f]{8})', name or '')
    if not m:
        raise Abort('--confirm-snapshot 형식: f2r-predeploy-YYYYMMDDHHMM-<배포 SHA 앞 8자리>')
    if m.group(2) != sha[:8]:
        raise Abort('스냅샷 이름의 SHA가 배포 SHA와 다릅니다.')
    taken = datetime.datetime.strptime(m.group(1), '%Y%m%d%H%M').replace(tzinfo=datetime.timezone.utc)
    age = (now or datetime.datetime.now(datetime.timezone.utc)) - taken
    if not datetime.timedelta(0) <= age <= datetime.timedelta(hours=24):
        raise Abort('스냅샷 시각이 최근 24시간 안이 아닙니다.')


def read_cookie(auth_file, key):
    if not auth_file:
        raise Abort('--auth-file required; never pass a token on the command line')
    if stat.S_IMODE(auth_file.stat().st_mode) & 0o077:
        raise Abort('인증 파일 권한은 0600 이어야 합니다.')
    cookie = json.loads(auth_file.read_text()).get(key)
    if not cookie:
        raise Abort(f'인증 파일에 {key} 가 없습니다.')
    if '\r' in cookie or '\n' in cookie:
        raise Abort('Invalid session cookie')
    return cookie


class Client:
    def __init__(self, base, cookie, proxies=None, context=None, retries=3):
        self.base, self.cookie, self.retries = base.rstrip('/'), cookie, retries
        handlers = [NoRedirect()]
        if proxies is not None:
            handlers.append(urllib.request.ProxyHandler(proxies))
        if context is not None:
            handlers.append(urllib.request.HTTPSHandler(context=context))
        self.opener = urllib.request.build_opener(*handlers)

    def call(self, path, data=None, ctype='application/json', retry=True):
        headers = {'Cookie': self.cookie, 'Origin': self.base}
        if data is not None:
            headers['Content-Type'] = ctype
        attempts = self.retries if retry else 1
        for attempt in range(1, attempts + 1):
            req = urllib.request.Request(self.base + '/api/studio/' + path, data=data, headers=headers,
                                         method='POST' if data is not None else 'GET')
            try:
                with self.opener.open(req, timeout=180) as r:
                    return r.read()
            except urllib.error.HTTPError as e:
                if e.code < 500 or attempt == attempts:
                    raise Abort(f'{req.method} {path} -> HTTP {e.code}')
            except urllib.error.URLError as e:
                if attempt == attempts:
                    raise Abort(f'{req.method} {path} -> {e.reason}')
            time.sleep(2 ** attempt)

    def json(self, path, data=None, ctype='application/json', retry=True):
        return json.loads(self.call(path, data, ctype, retry))


def multipart(name, data):
    boundary = 'f2r-' + uuid.uuid4().hex
    name = name.replace('"', '_')
    head = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\n'
            'Content-Type: application/octet-stream\r\n\r\n').encode()
    return head + data + f'\r\n--{boundary}--\r\n'.encode(), 'multipart/form-data; boundary=' + boundary


def existing_files(client):
    found, offset = {}, 0
    while True:
        page = client.json(f'files?limit=200&offset={offset}')
        for f in page:
            found.setdefault(f['sha256'], f['id'])
        if len(page) < 200:
            return found
        offset += 200


def revision_approvals(client):
    found = {}
    for offset in range(0, 1000001, 200):
        rows = client.json(f'revisions?limit=200&offset={offset}')
        if not isinstance(rows, list):
            raise Abort('Invalid revision page')
        for row in rows:
            if row['item_key'].startswith('character-approved-') and not row.get('approved_id'):
                raise Abort('Existing character approval was withdrawn; automatic reapproval refused')
            if row.get('approved_id') == row['id']:
                found[row['item_key']] = (row['id'], row.get('file_id'), row.get('sha256'))
        if len(rows) < 200:
            return found
    raise Abort('Revision pagination limit reached; incomplete verification refused')


def verify_registration(client, before):
    if client.json('source-import').get('registeredSourcePaths') != 536:
        raise Abort('등록 후 경로 수가 536이 아닙니다.')
    after = revision_approvals(client)
    if any(after.get(key) != value for key, value in before.items()):
        raise Abort('Existing canonical approval pointer/file/SHA changed; manual investigation required')
    expected = set(json.loads((REPO / CATALOG).read_bytes())['approvedSha256'])
    observed = {value[2] for key, value in after.items() if key.startswith('character-approved-')}
    if observed != expected:
        raise Abort('Five canonical character SHA values were not observed')


def load_state(path):
    if path and path.exists():
        state = json.loads(path.read_text())
        if state.get('commit') != PIN:
            raise Abort('재시작 상태 파일의 커밋이 다릅니다.')
        return state
    return {'commit': PIN, 'verified': {}}


def save_state(path, state):
    if path:
        tmp = path.with_suffix(path.suffix + '.tmp')
        tmp.write_text(json.dumps(state, indent=2) + '\n')
        tmp.replace(path)


def stage_and_register(client, source, unique, state_path, apply):
    """Idempotent: verified uploads are skipped on restart; register runs once and is atomic server-side."""
    project = client.json('project')
    if not project.get('initialized'):
        raise Abort('제작 프로젝트가 초기화되지 않았습니다(ADMIN이 /ai-agent-studio 에서 먼저 개설).')
    if apply and (project.get('projectId') != PROJECT or not project.get('canManage') or project.get('archived')):
        raise Abort('Active original project and ADMIN permission required')
    registered = client.json('source-import').get('registeredSourcePaths')
    if registered == 536:
        if apply:
            verify_registration(client, revision_approvals(client))
        return {'action': 'already-registered', 'registeredSourcePaths': 536}
    if registered != 0:
        raise Abort(f'부분 적재 상태({registered}/536) — 자동 진행하지 않습니다.')
    before = revision_approvals(client) if apply else {}
    state = load_state(state_path)
    present = existing_files(client)
    plan = {'uniqueFiles': len(unique), 'alreadyStored': sum(1 for s in unique if s in present),
            'toUpload': sum(1 for s in unique if s not in present)}
    if not apply:
        return {'action': 'dry-run', **plan}
    mapping = {}
    for i, (sha, row) in enumerate(unique.items(), 1):
        file_id = state['verified'].get(sha)
        if file_id is None:
            file_id = present.get(sha)
            if file_id is None:
                body, ctype = multipart(pathlib.PurePosixPath(row['path']).name, (source / row['path']).read_bytes())
                stored = client.json('files', body, ctype)['file']  # the server de-duplicates by SHA
                if stored['sha256'] != sha:
                    raise Abort('업로드 응답 SHA 불일치: ' + row['path'])
                file_id = stored['id']
            # A successful response is not enough: re-read the persisted bytes once per file.
            if hashlib.sha256(client.call('files/' + file_id + '/content')).hexdigest() != sha:
                raise Abort('저장 원본 SHA 불일치: ' + row['path'])
            state['verified'][sha] = file_id
            save_state(state_path, state)
        mapping[sha] = file_id
        if i % 50 == 0:
            print(f'verified stored originals {i}/{len(unique)}', flush=True)
    result = client.json('source-import', json.dumps({'commit': PIN, 'files': mapping}).encode(), retry=False)
    verify_registration(client, before)
    return {'action': 'registered', **plan, 'storedOriginalsVerified': len(mapping), 'registration': result,
            'mapping': mapping}


def production(args, catalog_bytes, unique, source):
    parsed = urllib.parse.urlsplit(args.base_url)
    test_hosts = set(args.allow_test_host)
    if not test_hosts <= LOOPBACK:
        raise Abort('테스트 호스트는 루프백만 허용합니다.')
    if (parsed.scheme != 'https' or parsed.username or parsed.password or parsed.path not in ('', '/')
            or parsed.query or parsed.fragment or (parsed.hostname not in LOOPBACK and parsed.port not in (None, 443))):
        raise Abort('production 은 https://<운영 도메인> 만 허용합니다(경로·계정 정보 없음).')
    if parsed.hostname not in PRODUCTION_HOSTS | test_hosts:
        raise Abort('허용되지 않은 운영 호스트: ' + str(parsed.hostname))
    if args.confirm_host != parsed.hostname:
        raise Abort('--confirm-host 가 대상 호스트와 다릅니다.')
    check_deployed_sha(args.deployed_sha, catalog_bytes, github=parsed.hostname not in test_hosts)
    if args.apply:
        check_snapshot(args.confirm_snapshot, args.deployed_sha)
    cookie = read_cookie(args.auth_file, 'adminCookie')
    context = ssl.create_default_context(cafile=args.ca_file) if args.ca_file else None
    out = stage_and_register(Client(args.base_url, cookie, context=context), source, unique, args.state, args.apply)
    out.update(target=parsed.hostname, deployedSha=args.deployed_sha,
               snapshot=args.confirm_snapshot if args.apply else None)
    return out


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=pathlib.Path)
    parser.add_argument('--extract-to', type=pathlib.Path)
    parser.add_argument('--base-url', default='http://127.0.0.1:18090')
    parser.add_argument('--auth-file', type=pathlib.Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--receipt', type=pathlib.Path, required=True)
    parser.add_argument('--production', action='store_true', help='HTTPS 운영 대상(기본 dry-run)')
    parser.add_argument('--deployed-sha', help='현재 운영에 배포된 main 커밋(40자리)')
    parser.add_argument('--confirm-snapshot', help='배포 직전 RDS 스냅샷 이름(f2r-predeploy-YYYYMMDDHHMM-<sha8>)')
    parser.add_argument('--confirm-host', help='대상 호스트를 한 번 더 입력')
    parser.add_argument('--state', type=pathlib.Path, help='재시작용 진행 상태 파일(검증된 업로드 기록)')
    parser.add_argument('--ca-file', help=argparse.SUPPRESS)  # tests only: trust a local CA
    parser.add_argument('--allow-test-host', action='append', default=[], help=argparse.SUPPRESS)  # loopback only
    args = parser.parse_args(argv)
    catalog_bytes = (REPO / CATALOG).read_bytes()
    catalog = json.loads(catalog_bytes)
    if args.extract_to:
        args.extract_to.mkdir(parents=True, exist_ok=True)
        for row in catalog['files']:
            dest = args.extract_to / row['path']
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(git_show(PIN, 'docs/ai-agent-production/' + row['path']))
        args.source = args.extract_to
    if not args.source:
        parser.error('--source or --extract-to is required')
    source = args.source.resolve()
    try:
        unique = verify_source(source, catalog)
        receipt = {'commit': PIN, 'mode': 'dry-run', 'sourcePaths': 536, 'uniqueFiles': len(unique),
                   'duplicatePaths': 536 - len(unique), 'bytes': sum(r['size'] for r in catalog['files']),
                   'verifiedSha': 536, 'approvedOriginals': catalog['approvedSha256'],
                   'originalComparedCuts': 0, 'confirmedPassCuts': 0, 'representativeImagesCreated': 0,
                   'externalAiCalls': 0}
        if args.production:
            out = production(args, catalog_bytes, unique, source)
            mapping = out.pop('mapping', None)
            receipt.update(mode='production-' + out['action'], production=out)
            if mapping:
                receipt['sources'] = [{**row, 'fileId': mapping[row['sha256']]} for row in catalog['files']]
        elif args.apply:
            parsed = urllib.parse.urlsplit(args.base_url)
            if not (parsed.scheme == 'http' and parsed.hostname in LOOPBACK and parsed.port == 18090
                    and not parsed.username and parsed.path in ('', '/')):
                raise Abort('Only isolated loopback port 18090 is allowed')
            # Do not use a system proxy for the explicitly isolated loopback endpoint.
            client = Client(args.base_url, read_cookie(args.auth_file, 'adminCookie'), proxies={}, retries=1)
            mapping = {}
            for i, (sha, row) in enumerate(unique.items(), 1):
                body, ctype = multipart(pathlib.PurePosixPath(row['path']).name, (source / row['path']).read_bytes())
                stored = client.json('files', body, ctype)['file']
                if stored['sha256'] != sha:
                    raise Abort('업로드 응답 SHA 불일치: ' + row['path'])
                mapping[sha] = stored['id']
                # Re-read persisted original bytes; a successful upload response alone is insufficient.
                if hashlib.sha256(client.call('files/' + stored['id'] + '/content')).hexdigest() != sha:
                    raise Abort('저장 원본 SHA 불일치: ' + row['path'])
                if i % 50 == 0:
                    print(f'verified stored originals {i}/{len(unique)}', flush=True)
            result = client.json('source-import', json.dumps({'commit': PIN, 'files': mapping}).encode())
            receipt.update(mode='applied-local', storedOriginalsVerified=len(mapping), registration=result,
                           sources=[{**row, 'fileId': mapping[row['sha256']]} for row in catalog['files']])
    except Abort as e:
        print('ABORT: ' + str(e), file=sys.stderr)
        return 2
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: v for k, v in receipt.items() if k != 'sources'}, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    sys.exit(main())
