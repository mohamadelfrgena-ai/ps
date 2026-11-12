import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const port=Number(process.env.PORT||4174);
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json; charset=utf-8','.png':'image/png','.md':'text/markdown; charset=utf-8','.mcpack':'application/octet-stream','.mcaddon':'application/octet-stream','.txt':'text/plain; charset=utf-8'};
http.createServer((req,res)=>{ try { const u=new URL(req.url,'http://preview.local'); let rel=decodeURIComponent(u.pathname); if (rel==='/') rel='/index.html'; const allowed=/^\/(index\.html|styles\.css|src\/[a-z-]+\.js|assets\/[a-z_/-]+\.(png|js)|dist\/[A-Za-z0-9_.-]+\.(mcpack|mcaddon|txt)|README\.md|INSTALL\.md|TEST-RESULTS\.md|RESEARCH\.md|PS4-FTP\.md)$/; if (!allowed.test(rel)) {res.writeHead(404);res.end('Not found');return;} const file=path.join(root,rel); if (!file.startsWith(root+path.sep)) {res.writeHead(403);res.end('Forbidden');return;} fs.stat(file,(err,st)=>{if(err||!st.isFile()){res.writeHead(404);res.end('Not found');return;} res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream','Content-Length':st.size,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}); fs.createReadStream(file).pipe(res);}); } catch {res.writeHead(400);res.end('Bad request');} }).listen(port,'0.0.0.0',()=>console.log(`Tier B preview on http://0.0.0.0:${port}`));
