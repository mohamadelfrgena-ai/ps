import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const packDir=path.join(root,'pack','tier_c_local_probe');
const dist=path.join(root,'dist');
fs.mkdirSync(packDir,{recursive:true}); fs.mkdirSync(dist,{recursive:true}); fs.mkdirSync(path.join(root,'reports'),{recursive:true});
const skins=[
 {id:'c1',name:'C1 Signal Red',base:[176,42,48],accent:[255,224,128]},
 {id:'c2',name:'C2 Beacon Green',base:[38,142,72],accent:[220,255,160]},
 {id:'c3',name:'C3 Relay Blue',base:[42,92,176],accent:[160,224,255]},
 {id:'c4',name:'C4 Marker Yellow',base:[204,164,38],accent:[72,48,120]}
];
const uuids={header:'8f3c2a10-6c2e-4f3b-9b7a-2d1e5c9a4c01',module:'8f3c2a10-6c2e-4f3b-9b7a-2d1e5c9a4c02'};
const table=new Uint32Array(256);for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=(c&1)?0xedb88320^(c>>>1):c>>>1;table[n]=c>>>0;}
function crc32(b){let c=0xffffffff;for(const x of b)c=table[(c^x)&255]^(c>>>8);return(c^0xffffffff)>>>0;}
function chunk(t,d){const type=Buffer.from(t);const len=Buffer.alloc(4);len.writeUInt32BE(d.length);const crc=Buffer.alloc(4);crc.writeUInt32BE(crc32(Buffer.concat([type,d])));return Buffer.concat([len,type,d,crc]);}
function png(w,h,rgba){const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(w,0);ihdr.writeUInt32BE(h,4);ihdr[8]=8;ihdr[9]=6;const raw=Buffer.alloc((w*4+1)*h);for(let y=0;y<h;y++){raw[y*(w*4+1)]=0;Buffer.from(rgba.buffer,rgba.byteOffset+y*w*4,w*4).copy(raw,y*(w*4+1)+1);}return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ihdr),chunk('IDAT',zlib.deflateSync(raw,{level:9})),chunk('IEND',Buffer.alloc(0))]);}
function px(b,x,y,c){if(x<0||y<0||x>=64||y>=64)return;const i=(y*64+x)*4;b[i]=c[0];b[i+1]=c[1];b[i+2]=c[2];b[i+3]=c[3]??255;}
function rect(b,x,y,w,h,c){for(let j=0;j<h;j++)for(let i=0;i<w;i++)px(b,x+i,y+j,c);}
function shade(c,d){return[Math.max(0,Math.min(255,c[0]+d)),Math.max(0,Math.min(255,c[1]+d)),Math.max(0,Math.min(255,c[2]+d)),c[3]??255];}
function box(b,u,v,w,h,d,base,top=base){rect(b,u+d,v,w,d,top);rect(b,u+d+w,v,w,d,shade(top,-18));rect(b,u,v+d,d,h,shade(base,-10));rect(b,u+d,v+d,w,h,base);rect(b,u+d+w,v+d,d,h,shade(base,-22));rect(b,u+d+w+d,v+d,w,h,shade(base,-30));}
function skin(s,idx){const b=new Uint8Array(64*64*4);const skinTone=[214,170,128];const hair=[38,34,40];box(b,0,0,8,8,8,skinTone,hair);rect(b,8,8,8,2,hair);rect(b,8,11,2,1,[20,22,28]);rect(b,13,11,2,1,[20,22,28]);rect(b,10,14,4,1,shade(skinTone,-45));box(b,16,16,8,12,4,s.base);box(b,40,16,4,12,4,s.base);box(b,32,48,4,12,4,s.base);box(b,0,16,4,12,4,shade(s.base,-35));box(b,16,48,4,12,4,shade(s.base,-35));rect(b,4,28,4,4,[28,30,36]);rect(b,20,60,4,4,[28,30,36]);rect(b,44,28,4,4,skinTone);rect(b,36,60,4,4,skinTone);
 // High-contrast fiducial: a 4x4 chest square plus idx+1 horizontal scan lines.
 rect(b,22,22,4,4,s.accent);for(let i=0;i<=idx;i++)rect(b,20,27+i,8,1,[245,248,255]);
 let seed=(0xc100+idx*131)>>>0;for(let y=0;y<64;y++)for(let x=0;x<64;x++){const i=(y*64+x)*4;if(!b[i+3])continue;seed=(Math.imul(seed,1664525)+1013904223)>>>0;const d=(seed>>>28)-8;b[i]=Math.max(0,Math.min(255,b[i]+d));b[i+1]=Math.max(0,Math.min(255,b[i+1]+d));b[i+2]=Math.max(0,Math.min(255,b[i+2]+d));}return png(64,64,b);}
function write(rel,data){const p=path.join(packDir,rel);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,data);}
function json(rel,v){write(rel,Buffer.from(JSON.stringify(v,null,2)+'\n'));}
json('manifest.json',{format_version:2,header:{name:'Tier C Local Skin Probe',description:'Four synthetic skins for a backup-gated PS4 local loading experiment. Not a compatibility claim.',uuid:uuids.header,version:[0,1,0]},modules:[{type:'skin_pack',description:'Minimal local skin_pack probe',uuid:uuids.module,version:[0,1,0]}],metadata:{authors:['Skin Lab contributors'],license:'MIT'}});
json('skins.json',{serialize_name:'tier_c_local_probe',localization_name:'tier_c_local_probe',skins:skins.map(s=>({localization_name:s.id,geometry:'geometry.humanoid.custom',texture:`${s.id}.png`,type:'free'}))});
json('texts/languages.json',['en_US']);
write('texts/en_US.lang',Buffer.from(['skinpack.tier_c_local_probe=Tier C Local Skin Probe','skinpack.tier_c_local_probe.by=Skin Lab contributors',...skins.map(s=>`skin.tier_c_local_probe.${s.id}=${s.name}`),''].join('\n')));
for(const [i,s] of skins.entries())write(`${s.id}.png`,skin(s,i));
const icon=new Uint8Array(64*64*4);for(let q=0;q<4;q++)for(let y=0;y<32;y++)for(let x=0;x<32;x++)px(icon,(q%2)*32+x,Math.floor(q/2)*32+y,shade(skins[q].base,(x+y)%5-2));rect(icon,24,24,16,16,[12,16,24]);rect(icon,27,28,3,3,[240,248,255]);rect(icon,34,28,3,3,[240,248,255]);rect(icon,28,35,8,2,[180,200,220]);write('pack_icon.png',png(64,64,icon));
write('README.txt',Buffer.from('Tier C local skin probe. Unpacked folder is the FTP test artifact. The .mcpack is a ZIP container for reference/PC-mobile import only; do not assume PS4 recognizes .mcpack over FTP. No scripts, behavior pack, resource pack, save edits, or executable edits.\n'));
// Deterministic stored ZIP for reference; not the PS4 FTP install form.
const entries=[];function walk(dir,prefix=''){for(const e of fs.readdirSync(dir,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name))){const rel=prefix?prefix+'/'+e.name:e.name;const p=path.join(dir,e.name);if(e.isDirectory())walk(p,rel);else entries.push({path:rel,data:fs.readFileSync(p)});}}walk(packDir);
const locals=[],central=[];let offset=0;const date=((2026-1980)<<9)|(11<<5)|12,time=12<<11;for(const e of entries){const name=Buffer.from(e.path),data=e.data,crc=crc32(data);const lh=Buffer.alloc(30+name.length);lh.writeUInt32LE(0x04034b50,0);lh.writeUInt16LE(20,4);lh.writeUInt16LE(0x0800,6);lh.writeUInt16LE(0,8);lh.writeUInt16LE(time,10);lh.writeUInt16LE(date,12);lh.writeUInt32LE(crc,14);lh.writeUInt32LE(data.length,18);lh.writeUInt32LE(data.length,22);lh.writeUInt16LE(name.length,26);name.copy(lh,30);locals.push(lh,data);const ch=Buffer.alloc(46+name.length);ch.writeUInt32LE(0x02014b50,0);ch.writeUInt16LE(20,4);ch.writeUInt16LE(20,6);ch.writeUInt16LE(0x0800,8);ch.writeUInt16LE(0,10);ch.writeUInt16LE(time,12);ch.writeUInt16LE(date,14);ch.writeUInt32LE(crc,16);ch.writeUInt32LE(data.length,20);ch.writeUInt32LE(data.length,24);ch.writeUInt16LE(name.length,28);ch.writeUInt32LE(offset,42);name.copy(ch,46);central.push(ch);offset+=lh.length+data.length;}const cd=Buffer.concat(central),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50,0);end.writeUInt16LE(entries.length,8);end.writeUInt16LE(entries.length,10);end.writeUInt32LE(cd.length,12);end.writeUInt32LE(offset,16);const zip=Buffer.concat([...locals,cd,end]);fs.writeFileSync(path.join(dist,'tier-c-local-probe.mcpack'),zip);
const manifest={pack:'tier_c_local_probe',uuids,skins:skins.map(s=>({id:s.id,name:s.name,texture:s.id+'.png'})),files:entries.map(e=>({path:e.path,bytes:e.data.length,sha256:crypto.createHash('sha256').update(e.data).digest('hex')})),mcpack:{bytes:zip.length,sha256:crypto.createHash('sha256').update(zip).digest('hex')}};fs.writeFileSync(path.join(root,'reports','pack-manifest.json'),JSON.stringify(manifest,null,2)+'\n');fs.writeFileSync(path.join(dist,'SHA256SUMS.txt'),`${manifest.mcpack.sha256}  tier-c-local-probe.mcpack\n`);console.log(`Built tier_c_local_probe: ${entries.length} files; mcpack ${zip.length} bytes`);
