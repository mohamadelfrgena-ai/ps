#!/usr/bin/env node
'use strict';
/* Tiny static server for local preview. The app also works from file:// in most browsers. */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const port = Number(process.env.PORT || 4173);
const mime = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8', '.md': 'text/markdown; charset=utf-8', '.png': 'image/png',
  '.mcpack': 'application/octet-stream', '.zip': 'application/zip', '.txt': 'text/plain; charset=utf-8'
};
const server = http.createServer((req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    let rel = decodeURIComponent(url.pathname);
    if (rel === '/') rel = '/index.html';
    const file = path.normalize(path.join(root, rel));
    if (!file.startsWith(root + path.sep) && file !== root) { res.writeHead(403); res.end('Forbidden'); return; }
    fs.stat(file, (err, st) => {
      if (err || !st.isFile()) { res.writeHead(404); res.end('Not found'); return; }
      res.writeHead(200, {
        'Content-Type': mime[path.extname(file).toLowerCase()] || 'application/octet-stream',
        'Content-Length': st.size,
        'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff'
      });
      fs.createReadStream(file).pipe(res);
    });
  } catch (_e) { res.writeHead(400); res.end('Bad request'); }
});
server.listen(port, '0.0.0.0', () => console.log('Skin Lab static preview on http://0.0.0.0:' + port));
