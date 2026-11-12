import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { listFiles, makeZip } from './zip.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const dist=path.join(root,'dist'); fs.mkdirSync(dist,{recursive:true});
const artifacts=[];
function pack(folder,name) { const entries=listFiles(path.join(root,'packs',folder)); const bytes=makeZip(entries); fs.writeFileSync(path.join(dist,name),bytes); artifacts.push({name,bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex'),entries:entries.length}); return {path:name,data:bytes}; }
const rp=pack('tier_b_resource','tier-b-preview-rp.mcpack');
const bp=pack('tier_b_behavior','tier-b-preview-bp.mcpack');
pack('tier_b_skins','tier-b-manual-skins-CONTROL.mcpack');
const addon=makeZip([rp,bp]); fs.writeFileSync(path.join(dist,'tier-b-preview.mcaddon'),addon); artifacts.push({name:'tier-b-preview.mcaddon',bytes:addon.length,sha256:crypto.createHash('sha256').update(addon).digest('hex'),entries:2});
fs.writeFileSync(path.join(dist,'SHA256SUMS.txt'),artifacts.map(a=>`${a.sha256}  ${a.name}`).join('\n')+'\n');
fs.writeFileSync(path.join(root,'reports','artifacts.json'),JSON.stringify({generated_by:'tools/package.mjs',note:'Structural artifacts only. No Minecraft client or PS4 hardware test is implied.',artifacts},null,2)+'\n');
console.log(artifacts.map(a=>`${a.name}: ${a.bytes} bytes`).join('\n'));
