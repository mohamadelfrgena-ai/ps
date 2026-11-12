'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const core = require('../src/pack-core.js');

const root = path.join(__dirname, '..');
const demoPng = fs.readFileSync(path.join(root, 'examples', 'signal_scout.png'));
const slimPng = fs.readFileSync(path.join(root, 'examples', 'lunar_courier.png'));

function entriesToMap(entries) { return new Map(entries.map(e => [e.path, Buffer.from(e.data)])); }
function validEntries() {
  return core.buildPack({ name: 'Test Pack', skins: [{ name: 'A', model: 'classic', bytes: demoPng }, { name: 'B', model: 'slim', bytes: slimPng }] });
}
function rawZip(entries) {
  // Minimal stored ZIP writer used only to craft malformed/security fixtures.
  const locals = [], centrals = []; let offset = 0;
  for (const e of entries) {
    const name = Buffer.from(e.name, 'utf8'); const data = Buffer.from(e.data || '');
    const crc = core.crc32(data); const flags = e.flags || 0; const method = e.method || 0;
    const payload = method === 8 ? zlib.deflateRawSync(data) : data;
    const lh = Buffer.alloc(30);
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt16LE(flags, 6); lh.writeUInt16LE(method, 8);
    lh.writeUInt32LE(crc, 14); lh.writeUInt32LE(payload.length, 18); lh.writeUInt32LE(data.length, 22);
    lh.writeUInt16LE(name.length, 26);
    locals.push(lh, name, payload);
    const ch = Buffer.alloc(46);
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6); ch.writeUInt16LE(flags, 8); ch.writeUInt16LE(method, 10);
    ch.writeUInt32LE(crc, 16); ch.writeUInt32LE(payload.length, 20); ch.writeUInt32LE(data.length, 24);
    ch.writeUInt16LE(name.length, 28); ch.writeUInt32LE(e.extAttr || 0, 38); ch.writeUInt32LE(offset, 42);
    centrals.push(ch, name);
    offset += lh.length + name.length + payload.length;
  }
  const central = Buffer.concat(centrals); const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0); eocd.writeUInt16LE(entries.length, 8); eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(central.length, 12); eocd.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, central, eocd]);
}

test('safePath rejects traversal, absolute, backslash, drive, control, and empty segments', () => {
  for (const p of ['../x', 'a/../../x', '/abs', 'C:/x', 'a\\b', 'a//b', 'a/./b', '', 'a/\u0000b']) {
    assert.equal(core.safePath(p), false, p);
  }
  assert.equal(core.safePath('texts/en_US.lang'), true);
  assert.equal(core.safePath('skin_001.png'), true);
});

test('makeZip/readZip round-trips stored entries and validatePack accepts generated pack', async () => {
  const entries = validEntries();
  const zip = core.makeZip(entries);
  const files = await core.readZip(zip);
  const pack = core.validatePack(files);
  assert.equal(pack.skins.length, 2);
  assert.deepEqual(pack.warnings, []);
  assert.equal(pack.skins[0].model, 'classic');
  assert.equal(pack.skins[1].model, 'slim');
});

test('readZip accepts deflate entries', async () => {
  const zip = rawZip([{ name: 'manifest.json', data: Buffer.from('{"ok":true}\n'), method: 8 }]);
  const files = await core.readZip(zip);
  assert.equal(Buffer.from(files.get('manifest.json')).toString(), '{"ok":true}\n');
});

test('makeZip rejects duplicate and unsafe paths', () => {
  assert.throws(() => core.makeZip([{ path: 'a.txt', data: 'a' }, { path: 'A.txt', data: 'b' }]), /Duplicate/);
  assert.throws(() => core.makeZip([{ path: '../evil', data: 'x' }]), /Unsafe/);
});

test('readZip rejects traversal, duplicate, encrypted, symlink, and CRC mismatch', async () => {
  await assert.rejects(core.readZip(rawZip([{ name: '../evil.txt', data: 'x' }])), /Unsafe/);
  await assert.rejects(core.readZip(rawZip([{ name: 'a.txt', data: 'x' }, { name: 'A.txt', data: 'y' }])), /Duplicate/);
  await assert.rejects(core.readZip(rawZip([{ name: 'a.txt', data: 'x', flags: 1 }])), /Encrypted/);
  await assert.rejects(core.readZip(rawZip([{ name: 'link', data: 'x', extAttr: (0o120777 << 16) >>> 0 }])), /Symlink/);
  const zip = rawZip([{ name: 'a.txt', data: 'hello' }]);
  // Corrupt stored payload after CRC was written.
  const idx = zip.indexOf(Buffer.from('hello')); zip[idx] = 0x78;
  await assert.rejects(core.readZip(zip), /CRC/);
});

test('inspectPng accepts demo textures and rejects wrong dimensions/truncation', () => {
  assert.deepEqual(core.inspectPng(demoPng).width, 64);
  assert.deepEqual(core.inspectPng(slimPng).height, 64);
  assert.throws(() => core.inspectPng(demoPng.subarray(0, 20)), /too short|Truncated/);
  const bad = Buffer.from(demoPng); bad.writeUInt32BE(32, 16); // width field in IHDR
  // CRC now mismatches; either rejection is acceptable.
  assert.throws(() => core.inspectPng(bad), /CRC|64x64/);
});

test('validatePack rejects missing files, unsupported geometry, and script modules', () => {
  const base = entriesToMap(validEntries());
  const missing = new Map(base); missing.delete('skins.json');
  assert.throws(() => core.validatePack(missing), /skins.json/);
  const badGeo = new Map(base);
  const sj = JSON.parse(Buffer.from(badGeo.get('skins.json')).toString());
  sj.skins[0].geometry = 'geometry.custom_dragon';
  badGeo.set('skins.json', Buffer.from(JSON.stringify(sj)));
  assert.throws(() => core.validatePack(badGeo), /Unsupported skin geometry/);
  const script = new Map(base);
  const mf = JSON.parse(Buffer.from(script.get('manifest.json')).toString());
  mf.modules.push({ type: 'script', uuid: core.uuid4(), version: [1, 0, 0] });
  script.set('manifest.json', Buffer.from(JSON.stringify(mf)));
  assert.throws(() => core.validatePack(script), /exactly one module/);
});

test('buildPack creates fresh UUIDs and rejects empty/oversized/invalid input', () => {
  const a = entriesToMap(validEntries()); const b = entriesToMap(validEntries());
  const ma = JSON.parse(Buffer.from(a.get('manifest.json')).toString());
  const mb = JSON.parse(Buffer.from(b.get('manifest.json')).toString());
  assert.notEqual(ma.header.uuid, mb.header.uuid);
  assert.notEqual(ma.modules[0].uuid, mb.modules[0].uuid);
  assert.throws(() => core.buildPack({ name: 'Empty', skins: [] }), /Select/);
  assert.throws(() => core.buildPack({ name: 'Bad', skins: [{ name: 'X', model: 'slim', bytes: Buffer.from('not png') }] }), /PNG/);
  const tooMany = Array.from({ length: core.LIMITS.MAX_SKINS + 1 }, (_, i) => ({ name: 'S' + i, model: 'classic', bytes: demoPng }));
  assert.throws(() => core.buildPack({ name: 'Too many', skins: tooMany }), /Select/);
});

test('demo .mcpack passes structural validation', async () => {
  const bytes = fs.readFileSync(path.join(root, 'examples', 'skin-lab-demo.mcpack'));
  const pack = core.validatePack(await core.readZip(bytes));
  assert.equal(pack.name, 'Skin Lab Original Demo');
  assert.equal(pack.skins.length, 2);
  assert.deepEqual(pack.warnings, []);
});
