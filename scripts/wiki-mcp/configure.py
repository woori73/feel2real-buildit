"""Configure the existing Wiki API-key endpoint; never print plaintext credentials.

Runs as root on f2r-app via the manual GitHub workflow. Only the Wiki environment
file and service are changed. The caller supplies an RSA public key for a sealed
credential handoff; the private key must remain on the operator's computer.
"""
import base64
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ENV_FILE = Path('/etc/buildit-wiki.env')
KEY_NAME = 'BUILDIT_MCP_API_KEY'
BASE = 'http://127.0.0.1:8000'
REQUIRED_TOOLS = {'get_final_context', 'get_jikbo_context', 'save_generated_test'}


def environment_key(text):
    values = []
    for line in text.splitlines():
        if line.strip().startswith(KEY_NAME + '='):
            value = line.strip().split('=', 1)[1].strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            if value:
                # A conservative subset accepted unambiguously by systemd EnvironmentFile.
                if not re.fullmatch(r'[A-Za-z0-9_.+/=-]{16,180}', value):
                    raise ValueError('Existing MCP key format needs manual review; not rotated')
                values.append(value)
    if len(set(values)) > 1:
        raise ValueError('Conflicting MCP keys; not rotated')
    return values[-1] if values else None


def configured_text(text, key):
    lines = [line for line in text.splitlines()
             if not line.strip().startswith(KEY_NAME + '=')]
    return '\n'.join(lines) + ('\n' if lines else '') + KEY_NAME + '=' + key + '\n'


def atomic_write(path, contents):
    fd, temporary = tempfile.mkstemp(prefix='.wiki-mcp-', dir=path.parent)
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, 'wb') as stream:
            stream.write(contents)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def seal(key, public_pem):
    with tempfile.TemporaryDirectory(prefix='wiki-mcp-public-') as directory:
        public_path = Path(directory) / 'public.pem'
        public_path.write_bytes(public_pem)
        result = subprocess.run([
            'openssl', 'pkeyutl', '-encrypt', '-pubin', '-inkey', str(public_path),
            '-pkeyopt', 'rsa_padding_mode:oaep', '-pkeyopt', 'rsa_oaep_md:sha256',
            '-pkeyopt', 'rsa_mgf1_md:sha256',
        ], input=key.encode(), capture_output=True)
        if result.returncode or len(result.stdout) < 256:
            raise ValueError('A valid RSA public key of at least 2048 bits is required')
        return base64.b64encode(result.stdout).decode()


def request(path, payload=None, key=None):
    headers = {'Accept': 'application/json, text/event-stream'}
    if key:
        headers['X-Api-Key'] = key
    data = None if payload is None else json.dumps(payload).encode()
    if data:
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, data=data, headers=headers)
    # Localhost only: do not send the credential through inherited HTTP proxies.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=10) as response:
            return response.status, response.read(2 * 1024 * 1024).decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read(2048).decode()


def rpc_result(body):
    if body.lstrip().startswith('{'):
        value = json.loads(body)
    else:
        events = [line[5:].strip() for line in body.splitlines() if line.startswith('data:')]
        if not events:
            raise ValueError('MCP response has no JSON event')
        value = json.loads(events[-1])
    if 'error' in value or 'result' not in value:
        raise ValueError('MCP protocol verification failed')
    return value['result']


def verify(key):
    status, _ = request('/api/health')
    if status != 200:
        raise ValueError('Wiki health verification failed')
    for path in ('/api/mcp', '/api/mcp/'):
        initialize = {'jsonrpc': '2.0', 'id': 1, 'method': 'initialize', 'params': {
            'protocolVersion': '2025-03-26', 'capabilities': {},
            'clientInfo': {'name': 'wiki-connection-check', 'version': '1'}}}
        for supplied in (None, 'invalid-connection-check-key'):
            status, _ = request(path, initialize, supplied)
            if status != 401:
                raise ValueError('Unauthenticated MCP access is not closed')
        status, body = request(path, initialize, key)
        if status != 200 or not rpc_result(body).get('serverInfo'):
            raise ValueError('Authenticated MCP initialize failed')
    status, body = request('/api/mcp', {
        'jsonrpc': '2.0', 'id': 2, 'method': 'tools/list', 'params': {}}, key)
    if status != 200:
        raise ValueError('MCP tool discovery failed')
    names = {tool['name'] for tool in rpc_result(body).get('tools', [])}
    if not REQUIRED_TOOLS.issubset(names):
        raise ValueError('Required Wiki tools are missing')
    return sorted(names)


def restart():
    subprocess.run(['systemctl', 'restart', 'buildit-wiki.service'], check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(45):
        try:
            if request('/api/health')[0] == 200:
                return
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(2)
    raise ValueError('Wiki did not become healthy after restart')


def configure(path, public_pem, restart_service=restart, verify_service=verify):
    original = path.read_bytes() if path.exists() else None
    text = original.decode() if original is not None else ''
    existing = environment_key(text)
    key = existing or secrets.token_urlsafe(48)
    # Validate the recipient before touching production state.
    encrypted = seal(key, public_pem)
    changed = existing is None
    backup = None
    if changed:
        if original is not None:
            backup = path.with_name(path.name + '.mcp-backup-' + str(time.time_ns()))
            atomic_write(backup, original)
        atomic_write(path, configured_text(text, key).encode())
    try:
        if changed:
            restart_service()
        else:
            try:
                verify_service(key)
            except Exception:
                # The file can contain a key that the current process hasn't loaded.
                restart_service()
        tools = verify_service(key)
    except Exception:
        if changed:
            if original is None:
                path.unlink(missing_ok=True)
            else:
                atomic_write(path, original)
            restart_service()
        raise ValueError('Wiki MCP verification failed; environment restored') from None
    return {'configured': True, 'new_key': changed, 'tools': tools,
            'credential_rsa_oaep_sha256': encrypted}


def main():
    if os.geteuid() != 0:
        raise ValueError('Root is required')
    public_pem = base64.b64decode(os.environ['WIKI_MCP_PUBLIC_KEY_B64'], validate=True)
    if not public_pem.startswith(b'-----BEGIN PUBLIC KEY-----') or len(public_pem) > 4096:
        raise ValueError('Invalid public key')
    unit = subprocess.run(['systemctl', 'cat', 'buildit-wiki.service'],
                          capture_output=True, check=True).stdout
    if b'EnvironmentFile=-/etc/buildit-wiki.env' not in unit:
        raise ValueError('Wiki unit does not load the expected environment file')
    result = configure(ENV_FILE, public_pem)
    print(json.dumps(result))


if __name__ == '__main__':
    try:
        main()
    except Exception:
        # Do not emit subprocess details, request headers, environment or credential values.
        print('Wiki MCP configuration failed; inspect service health and protected environment files')
        raise SystemExit(1)
