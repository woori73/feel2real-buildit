"""Read-only preview. No DB, upstream proxy, login simulation or production writes."""
import argparse
import json
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=8765)
parser.add_argument('--media-dir', type=Path, help='Optional READ-ONLY directory containing already verified seven/ and suni/ assets')
args = parser.parse_args()
media = args.media_dir.resolve() if args.media_dir else None
allowed = {'poster.jpg', 'front.webp', 'idle.mp4', 'think.mp4', 'speak.mp4'}

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=str(ROOT), **kw)

    def do_GET(self):
        path = unquote(urlsplit(self.path).path)
        if path == '/api/agent-media/public':
            agents = {}
            for agent in ('seven', 'suni'):
                files = []
                if media:
                    for name in sorted(allowed):
                        f = (media / agent / name).resolve()
                        if f.is_relative_to(media) and f.is_file():
                            files.append(name)
                agents[agent] = {'files': files}
            body = json.dumps({'agents': agents}).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            self.wfile.write(body)
            return
        if path.startswith('/agent-media/public/'):
            parts = path.split('/')
            if media and len(parts) == 5 and parts[3] in ('seven', 'suni') and parts[4] in allowed:
                f = (media / parts[3] / parts[4]).resolve()
                if f.is_relative_to(media) and f.is_file():
                    self.send_response(200)
                    self.send_header('Content-Type', self.guess_type(str(f)))
                    self.send_header('Content-Length', str(f.stat().st_size))
                    self.end_headers()
                    with f.open('rb') as stream:
                        self.copyfile(stream, self.wfile)
                    return
            self.send_error(404)
            return
        if path.startswith('/api/') or path.startswith('/dashboard/'):
            self.send_error(404, 'This preview has no authenticated service or DB')
            return
        super().do_GET()

ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
