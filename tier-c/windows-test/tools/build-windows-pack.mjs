// Builds clean Windows Bedrock skin packs from FROZEN Tier C / Tier B source textures.
// Reads sources only. Never writes outside tier-c/windows-test/.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const outRoot = path.resolve(here, '..');
const repo = path.resolve(outRoot, '..', '..');

const table = new Uint32Array(256);
for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = (c & 1) ? 0xedb88320 ^ (c >>> 1) : c >>> 1; table[n] = c >>> 0; }
function crc32(b) { let c = 0xffffffff; for (const x of b) c = table[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function sha(b) { return crypto.createHash('sha256').update(b).digest('hex'); }

// Fixed, documented UUIDs. Distinct from every Tier A/B/C UUID so Minecraft treats
// these as new packs and never silently reuses a previously imported copy.
const TRACKS = [
  {
    id: 'tier_c_windows',
    file: 'windows-skin-test.mcpack',
    source: path.join(repo, 'tier-c', 'pack', 'tier_c_local_probe'),
    packName: 'Tier C Windows Skin Test',
    description: 'Four original Tier C skins for a real Windows Bedrock Dressing Room equip test.',
    headerUuid: '3f7b1d20-9a4c-4e6b-8d15-7c2a9e0b5f01',
    moduleUuid: '3f7b1d20-9a4c-4e6b-8d15-7c2a9e0b5f02',
    skins: [
      { key: 'c1', texture: 'c1.png', name: 'C1 Signal Red' },
      { key: 'c2', texture: 'c2.png', name: 'C2 Beacon Green' },
      { key: 'c3', texture: 'c3.png', name: 'C3 Relay Blue' },
      { key: 'c4', texture: 'c4.png', name: 'C4 Marker Yellow' }
    ],
    equipTarget: 'C1 Signal Red'
  },
  {
    id: 'tier_b_azure_windows',
    file: 'windows-skin-test-azure.mcpack',
    source: path.join(repo, 'tier-b', 'packs', 'tier_b_skins'),
    packName: 'Azure Mechanic Skin Test',
    description: 'Four original Tier B skins including Azure Mechanic, for a real Windows Bedrock equip test.',
    headerUuid: '3f7b1d20-9a4c-4e6b-8d15-7c2a9e0b5f03',
    moduleUuid: '3f7b1d20-9a4c-4e6b-8d15-7c2a9e0b5f04',
    skins: [
      { key: 'azure', texture: 'azure.png', name: 'Azure Mechanic' },
      { key: 'ember', texture: 'ember.png', name: 'Ember Explorer' },
      { key: 'moss', texture: 'moss.png', name: 'Moss Gardener' },
      { key: 'violet', texture: 'violet.png', name: 'Violet Navigator' }
    ],
    equipTarget: 'Azure Mechanic'
  }
];

function collect(dir, prefix = '') {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    const rel = prefix ? `${prefix}/${e.name}` : e.name;
    const p = path.join(dir, e.name);
    if (e.isSymbolicLink()) throw new Error(`Refusing symlink in pack output: ${rel}`);
    if (e.isDirectory()) out.push(...collect(p, rel));
    else out.push({ path: rel, data: fs.readFileSync(p) });
  }
  return out;
}

// Conventional deflate ZIP (method 8), manifest.json at archive root, forward slashes,
// UTF-8 flag set, fixed DOS timestamp for reproducible hashes.
function zip(entries) {
  const locals = [], central = [];
  let offset = 0;
  const date = ((2026 - 1980) << 9) | (11 << 5) | 12, time = 12 << 11;
  for (const e of entries) {
    const name = Buffer.from(e.path, 'utf8');
    const data = e.data;
    const comp = zlib.deflateRawSync(data, { level: 9 });
    const crc = crc32(data);
    const lh = Buffer.alloc(30 + name.length);
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt16LE(0x0800, 6);
    lh.writeUInt16LE(8, 8); lh.writeUInt16LE(time, 10); lh.writeUInt16LE(date, 12);
    lh.writeUInt32LE(crc, 14); lh.writeUInt32LE(comp.length, 18); lh.writeUInt32LE(data.length, 22);
    lh.writeUInt16LE(name.length, 26); name.copy(lh, 30);
    locals.push(lh, comp);
    const ch = Buffer.alloc(46 + name.length);
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6);
    ch.writeUInt16LE(0x0800, 8); ch.writeUInt16LE(8, 10); ch.writeUInt16LE(time, 12); ch.writeUInt16LE(date, 14);
    ch.writeUInt32LE(crc, 16); ch.writeUInt32LE(comp.length, 20); ch.writeUInt32LE(data.length, 24);
    ch.writeUInt16LE(name.length, 28); ch.writeUInt32LE(offset, 42); name.copy(ch, 46);
    central.push(ch);
    offset += lh.length + comp.length;
  }
  const cd = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(cd.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, cd, end]);
}

const audit = { scope: 'Read-only audit of frozen source packs. No source file is modified.', sources: [] };
const built = [];

for (const t of TRACKS) {
  if (!fs.existsSync(t.source)) throw new Error(`Missing source pack: ${t.source}`);
  const srcManifest = JSON.parse(fs.readFileSync(path.join(t.source, 'manifest.json'), 'utf8'));
  const srcSkins = JSON.parse(fs.readFileSync(path.join(t.source, 'skins.json'), 'utf8'));

  const srcRecord = {
    track: t.id,
    source: path.relative(repo, t.source).split(path.sep).join('/'),
    source_module_type: srcManifest.modules?.[0]?.type,
    source_format_version: srcManifest.format_version,
    source_header_uuid: srcManifest.header?.uuid,
    source_skin_count: srcSkins.skins?.length,
    textures: []
  };

  const packDir = path.join(outRoot, 'pack', t.id);
  fs.rmSync(packDir, { recursive: true, force: true });
  fs.mkdirSync(path.join(packDir, 'texts'), { recursive: true });

  // Copy textures byte-for-byte. No re-encode, no resize, no palette change.
  for (const s of t.skins) {
    const src = path.join(t.source, s.texture);
    const data = fs.readFileSync(src);
    fs.writeFileSync(path.join(packDir, s.texture), data);
    srcRecord.textures.push({
      texture: s.texture, display_name: s.name, bytes: data.length, sha256: sha(data),
      copied_byte_identical: true
    });
  }
  const icon = fs.readFileSync(path.join(t.source, 'pack_icon.png'));
  fs.writeFileSync(path.join(packDir, 'pack_icon.png'), icon);

  const manifest = {
    format_version: 2,
    header: { name: t.packName, description: t.description, uuid: t.headerUuid, version: [1, 0, 0] },
    modules: [{ type: 'skin_pack', description: 'Classic skins for Dressing Room selection', uuid: t.moduleUuid, version: [1, 0, 0] }],
    metadata: { authors: ['Skin Lab contributors'], license: 'MIT' }
  };
  const skins = {
    serialize_name: t.id,
    localization_name: t.id,
    skins: t.skins.map(s => ({ localization_name: s.key, geometry: 'geometry.humanoid.custom', texture: s.texture, type: 'free' }))
  };
  const lang = [
    `skinpack.${t.id}=${t.packName}`,
    `skinpack.${t.id}.by=Skin Lab contributors`,
    ...t.skins.map(s => `skin.${t.id}.${s.key}=${s.name}`),
    ''
  ].join('\n');

  fs.writeFileSync(path.join(packDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  fs.writeFileSync(path.join(packDir, 'skins.json'), JSON.stringify(skins, null, 2) + '\n');
  fs.writeFileSync(path.join(packDir, 'texts', 'en_US.lang'), lang);
  fs.writeFileSync(path.join(packDir, 'texts', 'languages.json'), JSON.stringify(['en_US']) + '\n');
  fs.writeFileSync(path.join(packDir, 'README.txt'),
    `${t.packName}\n\n` +
    `A conventional Minecraft Bedrock skin pack (manifest module type "skin_pack").\n` +
    `It is NOT a resource pack and contains no mannequin, entity, script, or behavior pack.\n` +
    `Equip target for the test: ${t.equipTarget}.\n` +
    `Install: open the .mcpack on Windows, or copy this folder into com.mojang/skin_packs.\n` +
    `Success means the real local player model changes in a world, not a preview.\n`);

  const entries = collect(packDir);
  const archive = zip(entries);
  fs.writeFileSync(path.join(outRoot, t.file), archive);

  audit.sources.push(srcRecord);
  built.push({
    track: t.id, file: t.file, pack_folder: `pack/${t.id}`, pack_name: t.packName,
    equip_target: t.equipTarget, header_uuid: t.headerUuid, module_uuid: t.moduleUuid,
    skin_count: t.skins.length, skins: t.skins,
    bytes: archive.length, sha256: sha(archive),
    files: entries.map(e => ({ path: e.path, bytes: e.data.length, sha256: sha(e.data) }))
  });
  console.log(`Built ${t.file}: ${entries.length} files, ${archive.length} bytes, equip target "${t.equipTarget}"`);
}

fs.mkdirSync(path.join(outRoot, 'reports'), { recursive: true });
fs.writeFileSync(path.join(outRoot, 'reports', 'source-audit.json'), JSON.stringify(audit, null, 2) + '\n');
fs.writeFileSync(path.join(outRoot, 'reports', 'build-manifest.json'), JSON.stringify({ packs: built }, null, 2) + '\n');
fs.writeFileSync(path.join(outRoot, 'SHA256SUMS.txt'), built.map(b => `${b.sha256}  ${b.file}`).join('\n') + '\n');
