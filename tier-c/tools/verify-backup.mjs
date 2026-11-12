import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const dir=process.argv[2],manifestPath=process.argv[3];
if(!dir||!manifestPath){console.error('Usage: node tools/verify-backup.mjs <backup-directory> <manifest.json>');process.exit(2);}
const root=path.resolve(dir);const manifest=JSON.parse(fs.readFileSync(manifestPath,'utf8'));const errors=[];const actual=new Map();
if(!Array.isArray(manifest.files)){console.error('BACKUP_HASH_FAIL: manifest.files missing');process.exit(1);}

function hashFile(fp){const h=crypto.createHash('sha256');const fd=fs.openSync(fp,'r');const buf=Buffer.alloc(1024*1024);let bytes=0;try{for(;;){const n=fs.readSync(fd,buf,0,buf.length,null);if(n===0)break;h.update(buf.subarray(0,n));bytes+=n;}}finally{fs.closeSync(fd);}return{bytes,sha256:h.digest('hex')};}
function walk(d,p=''){for(const e of fs.readdirSync(d,{withFileTypes:true})){const rel=p?p+'/'+e.name:e.name;const fp=path.join(d,e.name);if(e.isSymbolicLink())errors.push('symlink '+rel);else if(e.isDirectory())walk(fp,rel);else if(e.isFile())actual.set(rel,fp);else errors.push('unsupported '+rel);}}
try{walk(root);}catch(e){errors.push(String(e));}
for(const f of manifest.files){const fp=actual.get(f.path);if(!fp){errors.push('missing '+f.path);continue;}try{const h=hashFile(fp);if(h.bytes!==f.bytes)errors.push('size '+f.path);if(h.sha256!==f.sha256)errors.push('sha256 '+f.path);}catch(e){errors.push('read '+f.path+': '+String(e));}actual.delete(f.path);}for(const extra of actual.keys())errors.push('unexpected '+extra);
const ok=errors.length===0&&manifest.files.length>0;console.log(ok?`BACKUP_HASH_PASS: ${manifest.files.length} files`:`BACKUP_HASH_FAIL: ${errors.length} problems`);if(!ok){console.error(errors.slice(0,50).join('\n'));process.exit(1);}console.log('This verifies local backup bytes only. Operator must still attest complete FTP transfer, native savedata backup, zero skipped/failed files, and exact target identity before any write.');
