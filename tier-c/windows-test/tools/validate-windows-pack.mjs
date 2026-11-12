// Deep structural validation of the Windows test packs.
// Verifies ZIP layout, manifest/skins schema, UUID uniqueness across all tiers,
// full PNG decode (not just header), localization, and absence of RP/BP/mannequin content.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const repo = path.resolve(root, '..', '..');
const build = JSON.parse(fs.readFileSync(path.join(root, 'reports', 'build-manifest.json'), 'utf8'));
const checks = [];
function check(pack, name, ok, detail = '') { checks.push({ pack, name, result: ok ? 'PASS' : 'FAIL', detail: String(detail) }); }
function sha(b) { return crypto.createHash('sha256').update(b).digest('hex'); }

// ---- minimal ZIP reader (central directory) ----
function readZip(buf) {
  const eocd = buf.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  if (eocd < 0) throw new Error('no EOCD');
  const count = buf.readUInt16LE(eocd + 10);
  let off = buf.readUInt32LE(eocd + 16);
  const entries = [];
  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(off) !== 0x02014b50) throw new Error('bad central header');
    const flags = buf.readUInt16LE(off + 8), method = buf.readUInt16LE(off + 10);
    const crc = buf.readUInt32LE(off + 16), csize = buf.readUInt32LE(off + 20), usize = buf.readUInt32LE(off + 24);
    const nlen = buf.readUInt16LE(off + 28), elen = buf.readUInt16LE(off + 30), clen = buf.readUInt16LE(off + 32);
    const lho = buf.readUInt32LE(off + 42);
    const name = buf.subarray(off + 46, off + 46 + nlen).toString('utf8');
    const lnlen = buf.readUInt16LE(lho + 26), lelen = buf.readUInt16LE(lho + 28);
    const dstart = lho + 30 + lnlen + lelen;
    const comp = buf.subarray(dstart, dstart + csize);
    const data = method === 0 ? comp : zlib.inflateRawSync(comp);
    entries.push({ name, method, flags, crc, usize, data });
    off += 46 + nlen + elen + clen;
  }
  return entries;
}
const CRC_T = new Uint32Array(256);
for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = (c & 1) ? 0xedb88320 ^ (c >>> 1) : c >>> 1; CRC_T[n] = c >>> 0; }
function crc32(b) { let c = 0xffffffff; for (const x of b) c = CRC_T[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }

// ---- full PNG decode with unfiltering ----
function decodePng(b) {
  if (!b.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) throw new Error('bad signature');
  let off = 8, ihdr = null, idat = [], iend = false;
  while (off + 12 <= b.length) {
    const n = b.readUInt32BE(off), type = b.subarray(off + 4, off + 8).toString('ascii');
    const data = b.subarray(off + 8, off + 8 + n);
    const crc = b.readUInt32BE(off + 8 + n);
    if (crc32(Buffer.concat([Buffer.from(type), data])) !== crc) throw new Error(`chunk CRC ${type}`);
    if (type === 'IHDR') ihdr = { w: data.readUInt32BE(0), h: data.readUInt32BE(4), depth: data[8], color: data[9], interlace: data[12] };
    if (type === 'IDAT') idat.push(data);
    if (type === 'IEND') iend = true;
    off += 12 + n;
  }
  if (!ihdr || !iend || off !== b.length) throw new Error('chunk layout');
  if (ihdr.interlace !== 0) throw new Error('interlaced not supported');
  const ch = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ihdr.color];
  if (!ch) throw new Error('bad color type');
  const bpp = ch * (ihdr.depth / 8), stride = ihdr.w * bpp;
  const raw = zlib.inflateSync(Buffer.concat(idat));
  if (raw.length !== (stride + 1) * ihdr.h) throw new Error('raw length');
  const out = Buffer.alloc(stride * ihdr.h);
  let pos = 0;
  for (let y = 0; y < ihdr.h; y++) {
    const f = raw[pos++];
    const line = Buffer.from(raw.subarray(pos, pos + stride)); pos += stride;
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? line[i - bpp] : 0;
      const bb = y > 0 ? out[(y - 1) * stride + i] : 0;
      const c = (i >= bpp && y > 0) ? out[(y - 1) * stride + i - bpp] : 0;
      if (f === 1) line[i] = (line[i] + a) & 255;
      else if (f === 2) line[i] = (line[i] + bb) & 255;
      else if (f === 3) line[i] = (line[i] + ((a + bb) >> 1)) & 255;
      else if (f === 4) { const p = a + bb - c, pa = Math.abs(p - a), pb = Math.abs(p - bb), pc = Math.abs(p - c); line[i] = (line[i] + (pa <= pb && pa <= pc ? a : pb <= pc ? bb : c)) & 255; }
      else if (f !== 0) throw new Error('bad filter ' + f);
    }
    line.copy(out, y * stride);
  }
  return { ...ihdr, channels: ch, pixels: out };
}

// Collect every UUID already used in frozen tiers, to prove no collision.
const existingUuids = new Map();
function scanUuids(dir, label) {
  if (!fs.existsSync(dir)) return;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name === 'node_modules' || e.name === 'windows-test') continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) scanUuids(p, label);
    else if (e.name === 'manifest.json') {
      try {
        const m = JSON.parse(fs.readFileSync(p, 'utf8'));
        if (m.header?.uuid) existingUuids.set(m.header.uuid.toLowerCase(), path.relative(repo, p));
        for (const mod of m.modules || []) if (mod.uuid) existingUuids.set(mod.uuid.toLowerCase(), path.relative(repo, p));
      } catch { /* ignore non-manifest */ }
    }
  }
}
for (const t of ['skin-lab', 'tier-b', 'tier-c']) scanUuids(path.join(repo, t), t);

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const seenNew = new Set();
const packReports = [];

for (const p of build.packs) {
  const buf = fs.readFileSync(path.join(root, p.file));
  const entries = readZip(buf);
  const names = entries.map(e => e.name);
  const dir = path.join(root, 'pack', p.track);

  check(p.track, 'archive sha256 matches build manifest', sha(buf) === p.sha256);
  check(p.track, 'all ZIP entry CRCs valid', entries.every(e => crc32(e.data) === e.crc));
  check(p.track, 'all ZIP declared sizes match', entries.every(e => e.data.length === e.usize));
  check(p.track, 'manifest.json at archive root (no wrapper folder)', names.includes('manifest.json'));
  check(p.track, 'no absolute/traversal/backslash entry paths', names.every(n => !n.startsWith('/') && !n.includes('..') && !n.includes('\\')));
  check(p.track, 'UTF-8 filename flag set', entries.every(e => (e.flags & 0x800) !== 0));
  check(p.track, 'archive matches unpacked folder byte-for-byte',
    entries.every(e => fs.existsSync(path.join(dir, e.name)) && sha(fs.readFileSync(path.join(dir, e.name))) === sha(e.data)));

  const m = JSON.parse(entries.find(e => e.name === 'manifest.json').data.toString('utf8'));
  const s = JSON.parse(entries.find(e => e.name === 'skins.json').data.toString('utf8'));
  const lang = entries.find(e => e.name === 'texts/en_US.lang').data.toString('utf8');

  check(p.track, 'manifest format_version 2', m.format_version === 2);
  check(p.track, 'exactly one module, type skin_pack', m.modules?.length === 1 && m.modules[0].type === 'skin_pack');
  check(p.track, 'not a resource/behavior/data pack', !m.modules.some(x => ['resources', 'data', 'script', 'world_template'].includes(x.type)));
  check(p.track, 'header+module UUIDs valid v4', UUID_RE.test(m.header.uuid) && UUID_RE.test(m.modules[0].uuid));
  check(p.track, 'header and module UUIDs differ', m.header.uuid !== m.modules[0].uuid);
  check(p.track, 'UUIDs unused by any frozen tier pack', !existingUuids.has(m.header.uuid.toLowerCase()) && !existingUuids.has(m.modules[0].uuid.toLowerCase()));
  check(p.track, 'UUIDs unique across the two new packs', !seenNew.has(m.header.uuid) && !seenNew.has(m.modules[0].uuid));
  seenNew.add(m.header.uuid); seenNew.add(m.modules[0].uuid);
  check(p.track, 'version triples present', Array.isArray(m.header.version) && m.header.version.length === 3 && Array.isArray(m.modules[0].version) && m.modules[0].version.length === 3);
  check(p.track, 'pack has a display name', typeof m.header.name === 'string' && m.header.name.length > 0);

  check(p.track, 'exactly four skins', s.skins?.length === 4);
  check(p.track, 'serialize_name matches localization_name', s.serialize_name === s.localization_name && s.serialize_name === p.track);
  check(p.track, 'localization keys are punctuation-free', /^[a-z0-9_]+$/.test(s.serialize_name) && s.skins.every(x => /^[a-z0-9_]+$/.test(x.localization_name)));
  check(p.track, 'pack localization key present', lang.includes(`skinpack.${s.serialize_name}=`));
  check(p.track, 'languages.json lists en_US', JSON.parse(entries.find(e => e.name === 'texts/languages.json').data.toString('utf8')).includes('en_US'));

  const decoded = [];
  for (const skin of s.skins) {
    const id = skin.localization_name;
    check(p.track, `skin ${id}: classic geometry`, skin.geometry === 'geometry.humanoid.custom');
    check(p.track, `skin ${id}: type free`, skin.type === 'free');
    check(p.track, `skin ${id}: texture at pack root`, names.includes(skin.texture) && !skin.texture.includes('/'));
    check(p.track, `skin ${id}: localization present`, lang.includes(`skin.${s.serialize_name}.${id}=`));
    try {
      const d = decodePng(entries.find(e => e.name === skin.texture).data);
      const ok = d.w === 64 && d.h === 64 && d.depth === 8 && d.color === 6;
      check(p.track, `skin ${id}: decodes to 64x64 8-bit RGBA`, ok, `${d.w}x${d.h} depth=${d.depth} color=${d.color}`);
      let opaque = 0; for (let i = 3; i < d.pixels.length; i += 4) if (d.pixels[i] > 0) opaque++;
      check(p.track, `skin ${id}: has visible opaque pixels`, opaque > 500, `${opaque} opaque`);
      // Head front (8,8)-(15,15) and torso front (20,20)-(27,31) must be painted.
      const at = (x, y) => d.pixels[(y * 64 + x) * 4 + 3];
      check(p.track, `skin ${id}: head+torso regions painted`, at(10, 10) === 255 && at(22, 24) === 255);
      decoded.push({ id, opaque, chest: Array.from(d.pixels.subarray((22 * 64 + 22) * 4, (22 * 64 + 22) * 4 + 4)) });
    } catch (err) { check(p.track, `skin ${id}: decodes to 64x64 8-bit RGBA`, false, err.message); }
  }
  const distinct = new Set(decoded.map(d => d.chest.join(','))).size;
  check(p.track, 'four skins are visually distinguishable', distinct === 4, `${distinct} distinct chest colors`);

  try { const d = decodePng(entries.find(e => e.name === 'pack_icon.png').data); check(p.track, 'pack_icon decodes', d.w > 0 && d.h > 0); }
  catch (e) { check(p.track, 'pack_icon decodes', false, e.message); }

  check(p.track, 'no mannequin/entity/render-controller/script content',
    !names.some(n => /(entity\/|entities\/|render_controllers\/|scripts?\/|animation)/i.test(n)));
  check(p.track, 'no executable/save/world payload',
    !names.some(n => /\.(exe|dll|bin|self|sprx|mcworld|mctemplate|dat)$/i.test(n)));
  check(p.track, 'equip target exists in pack', p.skins.some(x => x.name === p.equip_target));

  packReports.push({ track: p.track, file: p.file, bytes: buf.length, sha256: sha(buf), entries: names, equip_target: p.equip_target, decoded_chest_colors: decoded });
}

const fail = checks.filter(c => c.result === 'FAIL');
const report = {
  scope: 'Structural/archive/image validation only. Minecraft client was NOT launched. Import, Dressing Room, equip, in-world appearance and persistence are NOT RUN.',
  result: fail.length ? 'FAIL' : 'STRUCTURAL_PASS',
  checkCount: checks.length, passCount: checks.length - fail.length,
  failures: fail, packs: packReports, checks
};
fs.writeFileSync(path.join(root, 'reports', 'structural-validation.json'), JSON.stringify(report, null, 2) + '\n');
console.log(`${report.result}: ${report.passCount}/${report.checkCount}`);
if (fail.length) { console.error(fail.map(f => `${f.pack}: ${f.name} ${f.detail}`).join('\n')); process.exitCode = 1; }
