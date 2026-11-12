'use strict';
/* Generates two original synthetic 64x64 skin textures and a tiny demo .mcpack.
 * No upstream/Marketplace artwork is used. Run: node tools/make-examples.js */
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const core = require('../src/pack-core.js');

const root = path.join(__dirname, '..');
const out = path.join(root, 'examples');
fs.mkdirSync(out, { recursive: true });

function chunk(type, data) {
  const t = Buffer.from(type, 'ascii');
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(core.crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crc]);
}
function encodePng(w, h, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    Buffer.from(rgba.buffer, rgba.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1);
  }
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))
  ]);
}
function canvas64() { return new Uint8Array(64 * 64 * 4); }
function px(buf, x, y, r, g, b, a = 255) {
  if (x < 0 || y < 0 || x >= 64 || y >= 64) return;
  const i = (y * 64 + x) * 4; buf[i] = r; buf[i + 1] = g; buf[i + 2] = b; buf[i + 3] = a;
}
function rect(buf, x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) px(buf, x + i, y + j, c[0], c[1], c[2], c[3] ?? 255); }
function shade(c, d) { return [Math.max(0, Math.min(255, c[0] + d)), Math.max(0, Math.min(255, c[1] + d)), Math.max(0, Math.min(255, c[2] + d)), c[3] ?? 255]; }
function box(buf, u, v, w, h, d, base, top = base) {
  rect(buf, u + d, v, w, d, top);
  rect(buf, u + d + w, v, w, d, shade(top, -18));
  rect(buf, u, v + d, d, h, shade(base, -10));
  rect(buf, u + d, v + d, w, h, base);
  rect(buf, u + d + w, v + d, d, h, shade(base, -22));
  rect(buf, u + d + w + d, v + d, w, h, shade(base, -30));
}
function noise(buf, seed) {
  let s = seed >>> 0;
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
    const i = (y * 64 + x) * 4;
    if (buf[i + 3] === 0) continue;
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    const d = ((s >>> 28) - 8);
    buf[i] = Math.max(0, Math.min(255, buf[i] + d));
    buf[i + 1] = Math.max(0, Math.min(255, buf[i + 1] + d));
    buf[i + 2] = Math.max(0, Math.min(255, buf[i + 2] + d));
  }
}
function makeSkin(kind) {
  const b = canvas64();
  const palette = kind === 'scout'
    ? { skin: [224, 172, 120], hair: [64, 44, 32], shirt: [32, 148, 136], accent: [240, 138, 36], pants: [34, 52, 88], boots: [28, 30, 36] }
    : { skin: [210, 180, 160], hair: [40, 36, 60], shirt: [226, 230, 240], accent: [124, 78, 210], pants: [70, 74, 100], boots: [40, 42, 58] };
  // Head + hair cap.
  box(b, 0, 0, 8, 8, 8, palette.skin, palette.hair);
  rect(b, 8, 8, 8, 2, palette.hair);
  rect(b, 8, 11, 2, 1, [30, 32, 40]); rect(b, 13, 11, 2, 1, [30, 32, 40]);
  rect(b, 10, 14, 4, 1, shade(palette.skin, -45));
  // Body, arms, legs. Slim texture still uses the standard 64x64 atlas layout.
  box(b, 16, 16, 8, 12, 4, palette.shirt);
  rect(b, 20, 20, 8, 2, palette.accent);
  rect(b, 20, 29, 8, 2, shade(palette.pants, 10));
  box(b, 40, 16, 4, 12, 4, palette.accent);
  box(b, 32, 48, 4, 12, 4, palette.accent);
  rect(b, 44, 28, 4, 4, palette.skin); rect(b, 36, 60, 4, 4, palette.skin);
  box(b, 0, 16, 4, 12, 4, palette.pants);
  box(b, 16, 48, 4, 12, 4, palette.pants);
  rect(b, 4, 28, 4, 4, palette.boots); rect(b, 20, 60, 4, 4, palette.boots);
  // Small original chest mark.
  rect(b, 22, 23, 4, 4, palette.accent); px(b, 23, 24, 255, 255, 255); px(b, 24, 25, 255, 255, 255);
  noise(b, kind === 'scout' ? 0x51A7 : 0x10A7);
  return encodePng(64, 64, b);
}

const skins = [
  { name: 'Signal Scout', model: 'classic', bytes: makeSkin('scout') },
  { name: 'Lunar Courier', model: 'slim', bytes: makeSkin('courier') }
];
for (const s of skins) {
  core.assertSkinPng(s.bytes);
  fs.writeFileSync(path.join(out, core.slug(s.name) + '.png'), s.bytes);
}
let entries = core.buildPack({ name: 'Skin Lab Original Demo', skins });
const manifest = JSON.parse(Buffer.from(entries.find(e => e.path === 'manifest.json').data).toString('utf8'));
manifest.header.uuid = 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d';
manifest.modules[0].uuid = 'b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e';
entries = entries.map(e => e.path === 'manifest.json' ? { path: e.path, data: Buffer.from(JSON.stringify(manifest, null, 2) + '\n') } : e);
const map = new Map(entries.map(e => [e.path, Buffer.from(e.data)]));
const checked = core.validatePack(map);
if (checked.warnings.length) throw new Error(checked.warnings.join('\n'));
const zip = core.makeZip(entries);
fs.writeFileSync(path.join(out, 'skin-lab-demo.mcpack'), zip);
fs.writeFileSync(path.join(out, 'demo-data.js'), '/* Generated by tools/make-examples.js. Original synthetic demo only. */\nwindow.SKIN_LAB_DEMO_BASE64 = ' + JSON.stringify(Buffer.from(zip).toString('base64')) + ';\n');
fs.writeFileSync(path.join(out, 'README.md'), '# Original demo assets\n\n`signal_scout.png` and `lunar_courier.png` are tiny synthetic textures generated by `tools/make-examples.js` for this prototype. They are not copied from Bedrock-Skins, Minecraft, or Marketplace packs.\n\n`skin-lab-demo.mcpack` is a structural example for Windows/mobile import testing. It is not a PS4 installer and does not bypass console skin restrictions.\n');
console.log(JSON.stringify({ out, skins: skins.map(s => ({ name: s.name, model: s.model, bytes: s.bytes.length })), mcpackBytes: zip.length, skinsInPack: checked.skins.length }, null, 2));
