"""Local production preview. Reads the shipped Pages/Netlify callback rewrite.

Run from repository root; never use this development server as a public host.
OAuth query strings are deliberately excluded from request logs.
"""
import http.server
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1] / 'composeApp/build/dist/wasmJs/productionExecutable'
REWRITES = {}
for line in (ROOT / '_redirects').read_text().splitlines():
    if line.strip() and not line.lstrip().startswith('#'):
        source, target, status = line.split()
        assert status == '200', 'Only static rewrites supported'
        REWRITES[source] = target
assert REWRITES == {'/oauth/spotify/callback': '/index.html'}

class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map,
                      '.wasm': 'application/wasm', '.js': 'text/javascript'}

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def do_GET(self):
        self.path = REWRITES.get(urlsplit(self.path).path, self.path)
        super().do_GET()

    def do_HEAD(self):
        self.path = REWRITES.get(urlsplit(self.path).path, self.path)
        super().do_HEAD()

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def log_message(self, format, *args):
        pass  # Never log OAuth callback codes/state.

if __name__ == '__main__':
    print('Production preview: http://127.0.0.1:8092', flush=True)
    http.server.HTTPServer(('127.0.0.1', 8092), Handler).serve_forever()
