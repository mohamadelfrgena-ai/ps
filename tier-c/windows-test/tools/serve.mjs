// Static download server for the Windows test kit. Binds 0.0.0.0 for the live preview.
// Serves only files inside tier-c/windows-test. Read-only; no traversal outside the root.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const port = Number(process.env.PORT || 4175);

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.md': 'text/plain; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.mjs': 'text/plain; charset=utf-8',
  '.ps1': 'text/plain; charset=utf-8',
  '.zip': 'application/zip',
  '.mcpack': 'application/octet-stream'
};

const server = http.createServer((req, res) => {
  let url;
  try { url = new URL(req.url, 'http://localhost'); }
  catch { res.writeHead(400).end('Bad request'); return; }

  let rel;
  try { rel = decodeURIComponent(url.pathname); }
  catch { res.writeHead(400).end('Bad request'); return; }
  if (rel === '/' || rel === '') rel = '/index.html';

  const target = path.resolve(root, '.' + rel);
  if (target !== root && !target.startsWith(root + path.sep)) {
    res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' }).end('Forbidden');
    return;
  }

  let st;
  try { st = fs.statSync(target); }
  catch { res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }).end('Not found'); return; }
  if (st.isDirectory()) {
    res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' }).end('Directory listing disabled');
    return;
  }

  const ext = path.extname(target).toLowerCase();
  const headers = {
    'Content-Type': TYPES[ext] || 'application/octet-stream',
    'Content-Length': st.size,
    'Cache-Control': 'no-store',
    'Accept-Ranges': 'bytes',
    'Access-Control-Allow-Origin': '*'
  };

  // Attachment only when explicitly requested. Some embedded preview frames block
  // forced downloads, so ?inline=1 lets the browser handle the response normally.
  const wantAttachment = ext === '.mcpack' && url.searchParams.get('inline') !== '1';
  if (wantAttachment) headers['Content-Disposition'] = `attachment; filename="${path.basename(target)}"`;

  // Range support so download managers / resumed downloads work.
  const range = req.headers.range;
  if (range) {
    const m = /^bytes=(\d*)-(\d*)$/.exec(range);
    if (m) {
      let start = m[1] === '' ? 0 : Number(m[1]);
      let end = m[2] === '' ? st.size - 1 : Number(m[2]);
      if (m[1] === '' && m[2] !== '') { start = Math.max(0, st.size - Number(m[2])); end = st.size - 1; }
      if (start <= end && start < st.size) {
        end = Math.min(end, st.size - 1);
        headers['Content-Range'] = `bytes ${start}-${end}/${st.size}`;
        headers['Content-Length'] = end - start + 1;
        res.writeHead(206, headers);
        if (req.method === 'HEAD') { res.end(); return; }
        fs.createReadStream(target, { start, end }).pipe(res);
        return;
      }
    }
  }

  res.writeHead(200, headers);
  if (req.method === 'HEAD') { res.end(); return; }
  fs.createReadStream(target).pipe(res);
});

server.listen(port, '0.0.0.0', () => console.log(`Windows skin test kit on http://0.0.0.0:${port} (root ${root})`));
