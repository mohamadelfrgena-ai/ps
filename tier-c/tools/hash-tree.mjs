import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const target=process.argv[2];const out=process.argv[3]||'backup-manifest.json';
if(!target){console.error('Usage: node tools/hash-tree.mjs <backup-directory> [manifest.json]');process.exit(2);}
const root=path.resolve(target);if(!fs.existsSync(root)||!fs.statSync(root).isDirectory()){console.error('Backup directory missing');process.exit(2);}

// Stream large save/world files instead of loading whole files into memory.
function hashFile(fp){const h=crypto.createHash('sha256');const fd=fs.openSync(fp,'r');const buf=Buffer.alloc(1024*1024);let bytes=0;try{for(;;){const n=fs.readSync(fd,buf,0,buf.length,null);if(n===0)break;h.update(buf.subarray(0,n));bytes+=n;}}finally{fs.closeSync(fd);}return{bytes,sha256:h.digest('hex')};}

const files=[];let bytes=0;function walk(d,p=''){for(const e of fs.readdirSync(d,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name))){const rel=p?p+'/'+e.name:e.name;const fp=path.join(d,e.name);if(e.isSymbolicLink())throw new Error('Symlink in backup: '+rel);if(e.isDirectory())walk(fp,rel);else if(e.isFile()){const h=hashFile(fp);bytes+=h.bytes;files.push({path:rel,bytes:h.bytes,sha256:h.sha256});}else throw new Error('Unsupported backup entry: '+rel);}}walk(root);
const manifest={created:new Date().toISOString(),rootName:path.basename(root),fileCount:files.length,totalBytes:bytes,files};fs.mkdirSync(path.dirname(path.resolve(out)),{recursive:true});fs.writeFileSync(out,JSON.stringify(manifest,null,2)+'\n');console.log(`Hashed ${files.length} files, ${bytes} bytes -> ${out}`);
