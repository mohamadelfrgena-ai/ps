import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const catalog = JSON.parse(fs.readFileSync(path.join(root, 'src/catalog.json'), 'utf8'));
const crcTable = new Uint32Array(256);
for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1); crcTable[n] = c >>> 0; }
function crc32(b) { let c = 0xffffffff; for (const x of b) c = crcTable[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const t = Buffer.from(type); const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([t, data]))); return Buffer.concat([len, t, data, crc]); }
function png(w, h, rgba) { const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6; const raw = Buffer.alloc((w * 4 + 1) * h); for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; Buffer.from(rgba.buffer, rgba.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1); } return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, {level:9})), chunk('IEND', Buffer.alloc(0))]); }
function px(b, w, x, y, c) { if (x < 0 || y < 0 || x >= w || y >= 64) return; const i = (y * w + x) * 4; b[i]=c[0]; b[i+1]=c[1]; b[i+2]=c[2]; b[i+3]=c[3] ?? 255; }
function rect(b, x, y, w, h, c) { for (let j=0;j<h;j++) for (let i=0;i<w;i++) px(b,64,x+i,y+j,c); }
function shade(c,d) { return [Math.max(0,Math.min(255,c[0]+d)),Math.max(0,Math.min(255,c[1]+d)),Math.max(0,Math.min(255,c[2]+d)),c[3]??255]; }
function box(b,u,v,w,h,d,base,top=base) { rect(b,u+d,v,w,d,top); rect(b,u+d+w,v,w,d,shade(top,-18)); rect(b,u,v+d,d,h,shade(base,-10)); rect(b,u+d,v+d,w,h,base); rect(b,u+d+w,v+d,d,h,shade(base,-22)); rect(b,u+d+w+d,v+d,w,h,shade(base,-30)); }
function skin(s, index) { const b = new Uint8Array(64*64*4); const p=s.palette; box(b,0,0,8,8,8,p.skin,p.hair); rect(b,8,8,8,2,p.hair); rect(b,8,11,2,1,[25,28,35]); rect(b,13,11,2,1,[25,28,35]); rect(b,10,14,4,1,shade(p.skin,-45)); box(b,16,16,8,12,4,p.shirt); box(b,40,16,4,12,4,p.shirt); box(b,32,48,4,12,4,p.shirt); box(b,0,16,4,12,4,p.pants); box(b,16,48,4,12,4,p.pants); rect(b,4,28,4,4,p.boots); rect(b,20,60,4,4,p.boots); rect(b,44,28,4,4,p.skin); rect(b,36,60,4,4,p.skin); rect(b,20,20,8,2,p.accent); rect(b,20,29,8,2,shade(p.pants,10));
  // Four original chest emblems, not Marketplace artwork.
  if (index===0) { rect(b,22,23,4,4,p.accent); rect(b,23,24,2,2,[235,250,255]); }
  if (index===1) { rect(b,23,22,2,6,p.accent); rect(b,21,24,6,2,p.accent); }
  if (index===2) { rect(b,22,23,2,2,p.accent); rect(b,24,25,2,2,p.accent); rect(b,23,25,2,3,shade(p.accent,-35)); }
  if (index===3) { rect(b,23,22,2,2,p.accent); rect(b,22,24,4,2,p.accent); rect(b,23,26,2,2,p.accent); }
  let seed=(0x7100+index*97)>>>0; for (let y=0;y<64;y++) for (let x=0;x<64;x++) { const i=(y*64+x)*4; if (!b[i+3]) continue; seed=(Math.imul(seed,1664525)+1013904223)>>>0; const d=(seed>>>28)-8; b[i]=Math.max(0,Math.min(255,b[i]+d)); b[i+1]=Math.max(0,Math.min(255,b[i+1]+d)); b[i+2]=Math.max(0,Math.min(255,b[i+2]+d)); }
  return b; }
function icon(rgba) { const out=new Uint8Array(16*16*4); for (let y=0;y<8;y++) for (let x=0;x<8;x++) { const base=( (8+y)*64+(8+x) )*4; const over=((8+y)*64+(40+x))*4; const a=rgba[over+3]/255; for (let j=0;j<2;j++) for (let i=0;i<2;i++) { const o=(((y*2+j)*16)+(x*2+i))*4; for (let c=0;c<3;c++) out[o+c]=Math.round(rgba[base+c]*(1-a)+rgba[over+c]*a); out[o+3]=255; } } return png(16,16,out); }
function write(rel, data) { const p=path.join(root,rel); fs.mkdirSync(path.dirname(p),{recursive:true}); fs.writeFileSync(p,data); }
const iconCanvas=new Uint8Array(64*64*4); for (let q=0;q<4;q++) { const c=catalog.skins[q].palette.accent; for (let y=0;y<32;y++) for (let x=0;x<32;x++) px(iconCanvas,64,(q%2)*32+x,Math.floor(q/2)*32+y,shade(c,(x+y)%7-3)); } rect(iconCanvas,24,24,16,16,[13,17,23]); rect(iconCanvas,27,28,3,3,[235,245,255]); rect(iconCanvas,34,28,3,3,[235,245,255]); rect(iconCanvas,28,35,8,2,[148,163,184]);
const packIcon=png(64,64,iconCanvas);
for (const [i,s] of catalog.skins.entries()) { if (s.model!=='classic') throw new Error('Tier B is limited to four classic skins'); const rgba=skin(s,i); const bytes=png(64,64,rgba); const face=icon(rgba); write(`assets/skins/${s.id}.png`,bytes); write(`assets/icons/${s.id}.png`,face); write(`packs/tier_b_resource/textures/tierb/skins/${s.id}.png`,bytes); write(`packs/tier_b_resource/textures/tierb/icons/${s.id}.png`,face); write(`packs/tier_b_skins/${s.id}.png`,bytes); }
for (const rel of ['assets/pack_icon.png','packs/tier_b_resource/pack_icon.png','packs/tier_b_behavior/pack_icon.png','packs/tier_b_skins/pack_icon.png']) write(rel,packIcon);
write('assets/catalog.js',`// Generated from src/catalog.json by tools/build-assets.mjs. Do not edit.\nwindow.TIER_B_CATALOG = ${JSON.stringify(catalog,null,2)};\n`);
const ts=`// Generated from src/catalog.json by tools/build-assets.mjs. Do not edit.\nexport const NAMESPACE = ${JSON.stringify(catalog.namespace)};\nexport const MENU_EVENT = ${JSON.stringify(catalog.namespace+':preview')};\nexport const PREVIEW_ENTITY = ${JSON.stringify(catalog.namespace+':skin_preview')};\nexport const PREVIEW_TAG = 'tierb_preview';\nexport const SKINS = ${JSON.stringify(catalog.skins.map((s,variant)=>({id:s.id,name:s.name,variant,event:`${catalog.namespace}:select_${variant}`,icon:`textures/tierb/icons/${s.id}`})),null,2)} as const;\n`;
write('src/behavior/catalog.ts',ts);
console.log(`Built ${catalog.skins.length} original 64x64 skins, icons, and catalog constants.`);
