#!/usr/bin/env python3
"""Tests for import-production.py --production against a local fake HTTPS Studio API.

No production host, credential or paid API is contacted. Run:
  python3 -m unittest scripts/ai-agent-studio/test_import_production.py
"""
import datetime, hashlib, http.server, importlib.util, io, json, os, pathlib, re, ssl, subprocess
import tempfile, threading, unittest, urllib.parse, uuid
from contextlib import redirect_stderr, redirect_stdout

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('import_production', HERE / 'import-production.py')
ip = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ip)
HEAD = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ip.REPO, capture_output=True, text=True, check=True).stdout.strip()
COOKIE = 'f2r_token=test-cookie-' + uuid.uuid4().hex


class FakeStudio:
    """Minimal in-memory Studio API: SHA de-duplicated files and an atomic source-import."""

    def __init__(self):
        self.files, self.registered, self.initialized = {}, 0, True
        self.posts, self.uploads, self.registers, self.content_reads = 0, 0, 0, 0
        self.fail_upload_after = None

    def handler(self):
        studio = self

        class H(http.server.BaseHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def reply(self, code, body, ctype='application/json'):
                data = body if isinstance(body, bytes) else json.dumps(body).encode()
                self.send_response(code)
                self.send_header('Content-Type', ctype)
                self.send_header('Content-Length', str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def authorized(self):
                if self.headers.get('Cookie') != COOKIE:
                    self.reply(401, {'message': 'login'})
                    return False
                return True

            def do_GET(self):
                if not self.authorized():
                    return
                url = urllib.parse.urlsplit(self.path)
                q = urllib.parse.parse_qs(url.query)
                if url.path == '/api/studio/project':
                    return self.reply(200, {'initialized': studio.initialized})
                if url.path == '/api/studio/source-import':
                    return self.reply(200, {'registeredSourcePaths': studio.registered})
                if url.path == '/api/studio/files':
                    rows = sorted(studio.files.values(), key=lambda f: f['id'])
                    off, lim = int(q.get('offset', ['0'])[0]), int(q.get('limit', ['200'])[0])
                    return self.reply(200, [{k: f[k] for k in ('id', 'sha256')} for f in rows[off:off + lim]])
                m = re.fullmatch(r'/api/studio/files/([0-9a-f-]+)/content', url.path)
                if m:
                    studio.content_reads += 1
                    f = next((f for f in studio.files.values() if f['id'] == m.group(1)), None)
                    return self.reply(200, f['data'], 'application/octet-stream') if f else self.reply(404, {})
                self.reply(404, {})

            def do_POST(self):
                if not self.authorized():
                    return
                studio.posts += 1
                body = self.rfile.read(int(self.headers['Content-Length']))
                if self.headers.get('Origin') != 'https://localhost:%d' % self.server.server_port:
                    return self.reply(403, {'message': 'origin'})
                if self.path == '/api/studio/files':
                    if studio.fail_upload_after is not None and studio.uploads >= studio.fail_upload_after:
                        return self.reply(400, {'message': 'injected failure'})
                    studio.uploads += 1
                    boundary = self.headers['Content-Type'].split('boundary=')[1].encode()
                    part = body.split(b'--' + boundary)[1]
                    data = part.split(b'\r\n\r\n', 1)[1][:-2]
                    sha = hashlib.sha256(data).hexdigest()
                    f = studio.files.setdefault(sha, {'id': str(uuid.uuid4()), 'sha256': sha, 'data': data})
                    return self.reply(200, {'file': {'id': f['id'], 'sha256': sha}})
                if self.path == '/api/studio/source-import':
                    studio.registers += 1
                    mapping = json.loads(body)['files']
                    if len(mapping) != 316 or any(studio.files.get(s, {}).get('id') != i for s, i in mapping.items()):
                        return self.reply(400, {'message': 'mapping'})
                    studio.registered = 536
                    return self.reply(200, {'registered': 536})
                self.reply(404, {})

        return H


class ProductionModeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = pathlib.Path(tempfile.mkdtemp(prefix='f2r-import-test-'))
        cls.source = cls.tmp / 'source'
        receipt = cls.tmp / 'extract.json'
        with redirect_stdout(io.StringIO()):
            assert ip.main(['--extract-to', str(cls.source), '--receipt', str(receipt)]) == 0
        key, cert = cls.tmp / 'key.pem', cls.tmp / 'cert.pem'
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1', '-subj', '/CN=localhost',
                        '-addext', 'subjectAltName=DNS:localhost', '-keyout', str(key), '-out', str(cert)],
                       check=True, capture_output=True)
        cls.cert = str(cert)
        cls.ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        cls.ctx.load_cert_chain(cert, key)
        cls.auth = cls.tmp / 'auth.json'
        cls.auth.write_text(json.dumps({'adminCookie': COOKIE}))
        os.chmod(cls.auth, 0o600)

    def setUp(self):
        self.studio = FakeStudio()
        self.server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), self.studio.handler())
        self.server.socket = self.ctx.wrap_socket(self.server.socket, server_side=True)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.base = 'https://localhost:%d' % self.server.server_port
        self.state = self.tmp / ('state-%s.json' % uuid.uuid4().hex)
        self.receipt = self.tmp / ('receipt-%s.json' % uuid.uuid4().hex)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def snapshot(self, sha=HEAD, minutes_ago=10):
        t = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(minutes=minutes_ago)
        return 'f2r-predeploy-%s-%s' % (t.strftime('%Y%m%d%H%M'), sha[:8])

    def run_tool(self, *extra, base=None, host='localhost', sha=HEAD, apply=False, snapshot=None, auth=None):
        argv = ['--source', str(self.source), '--receipt', str(self.receipt), '--production',
                '--base-url', base or self.base, '--confirm-host', host, '--deployed-sha', sha,
                '--auth-file', str(auth or self.auth), '--state', str(self.state),
                '--ca-file', self.cert, '--allow-test-host', 'localhost', *extra]
        if apply:
            argv += ['--apply', '--confirm-snapshot', snapshot or self.snapshot(sha)]
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = ip.main(argv)
        return code, out.getvalue(), err.getvalue()

    def test_givenNoApply_whenRun_thenDryRunSendsNoWrites(self):
        # given / when
        code, out, _ = self.run_tool()
        # then
        self.assertEqual(0, code)
        self.assertEqual(0, self.studio.posts)
        receipt = json.loads(self.receipt.read_text())
        self.assertEqual('production-dry-run', receipt['mode'])
        self.assertEqual(316, receipt['production']['toUpload'])
        self.assertNotIn(COOKIE, self.receipt.read_text() + out)

    def test_givenUnsafeTarget_whenRun_thenAbortBeforeAnyRequest(self):
        # given: http, wrong host confirmation, non-loopback test host, unknown host
        cases = [dict(base=self.base.replace('https', 'http')), dict(host='feel2real.co.kr'),
                 dict(base='https://example.com', host='example.com')]
        for case in cases:
            # when
            code, _, err = self.run_tool(**case)
            # then
            self.assertEqual(2, code, case)
            self.assertIn('ABORT', err)
        code, _, err = self.run_tool('--allow-test-host', 'evil.example')
        self.assertEqual(2, code)
        self.assertEqual(0, self.studio.posts)

    def test_givenBadSnapshot_whenApply_thenAbortWithoutWrites(self):
        # given: wrong format, SHA of another commit, older than 24h
        for snap in ['manual-snapshot', self.snapshot(sha='0' * 40), self.snapshot(minutes_ago=60 * 25)]:
            # when
            code, _, err = self.run_tool(apply=True, snapshot=snap)
            # then
            self.assertEqual(2, code, snap)
        self.assertEqual(0, self.studio.posts)

    def test_givenDeployedShaWithoutCatalog_whenRun_thenAbort(self):
        # given: main before #865 had no catalog or importer
        code, _, err = self.run_tool(sha='ae5d1760b9ba76ca79ef5c6f906acc59e6c293b1')
        # then
        self.assertEqual(2, code)
        self.assertIn('카탈로그 또는 적재 API', err)

    def test_givenWorldReadableAuthFile_whenRun_thenAbort(self):
        # given
        loose = self.tmp / 'loose.json'
        loose.write_text(json.dumps({'adminCookie': COOKIE}))
        os.chmod(loose, 0o644)
        # when
        code, _, err = self.run_tool(auth=loose)
        # then
        self.assertEqual(2, code)
        self.assertIn('0600', err)

    def test_givenUninitializedProject_whenRun_thenAbort(self):
        # given
        self.studio.initialized = False
        # when / then
        self.assertEqual(2, self.run_tool()[0])

    def test_givenInterruptedUpload_whenRerun_thenResumesWithoutDuplicatesAndRegistersOnce(self):
        # given: the 100th upload fails
        self.studio.fail_upload_after = 99
        code, _, _ = self.run_tool(apply=True)
        self.assertEqual(2, code)
        self.assertEqual(99, len(json.loads(self.state.read_text())['verified']))
        self.assertEqual(0, self.studio.registers)
        # when: the operator reruns the same command
        self.studio.fail_upload_after = None
        reads_before = self.studio.content_reads
        code, out, _ = self.run_tool(apply=True)
        # then
        self.assertEqual(0, code)
        self.assertEqual(316, len(self.studio.files))
        self.assertEqual(316, self.studio.uploads)
        self.assertEqual(316 - 99, self.studio.content_reads - reads_before)
        self.assertEqual(1, self.studio.registers)
        receipt = json.loads(self.receipt.read_text())
        self.assertEqual('production-registered', receipt['mode'])
        self.assertEqual(536, len(receipt['sources']))
        self.assertNotIn(COOKIE, self.receipt.read_text() + out + self.state.read_text())
        # and: running again is a no-op
        posts = self.studio.posts
        code, _, _ = self.run_tool(apply=True)
        self.assertEqual(0, code)
        self.assertEqual('production-already-registered', json.loads(self.receipt.read_text())['mode'])
        self.assertEqual(posts, self.studio.posts)

    def test_givenPartialRegistration_whenRun_thenAbortWithoutWrites(self):
        # given
        self.studio.registered = 100
        # when
        code, _, err = self.run_tool(apply=True)
        # then
        self.assertEqual(2, code)
        self.assertIn('부분 적재', err)
        self.assertEqual(0, self.studio.posts)

    def test_givenGithubDeployRecord_whenShaDiffers_thenAbort(self):
        # given: a fake GitHub API returning the latest successful deploy
        latest = {'sha': HEAD}

        class G(http.server.BaseHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def do_GET(self):
                data = json.dumps({'workflow_runs': [{'head_sha': latest['sha']}]}).encode()
                self.send_response(200)
                self.send_header('Content-Length', str(len(data)))
                self.end_headers()
                self.wfile.write(data)

        gh = http.server.ThreadingHTTPServer(('127.0.0.1', 0), G)
        threading.Thread(target=gh.serve_forever, daemon=True).start()
        old = ip.GITHUB_API
        ip.GITHUB_API = 'http://127.0.0.1:%d' % gh.server_port
        catalog = (ip.REPO / ip.CATALOG).read_bytes()
        try:
            # when / then
            ip.check_deployed_sha(HEAD, catalog, github=True)
            latest['sha'] = 'f' * 40
            with self.assertRaises(ip.Abort):
                ip.check_deployed_sha(HEAD, catalog, github=True)
        finally:
            ip.GITHUB_API = old
            gh.shutdown()
            gh.server_close()


if __name__ == '__main__':
    unittest.main()
