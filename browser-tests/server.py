"""Production static fixture with the same callback rewrite as resources/_redirects."""
import http.server
import os
os.chdir('composeApp/build/dist/wasmJs/productionExecutable')
class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path.split('?')[0] == '/oauth/spotify/callback':
            self.path = '/index.html'
        super().do_GET()
http.server.HTTPServer(('127.0.0.1', 8092), Handler).serve_forever()
