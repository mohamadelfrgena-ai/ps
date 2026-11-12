// Structural validation of the Skin Menu add-on. No game client is launched here.
// Every check is a static assertion about the built packs, not a claim about in-game behaviour.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repo = path.resolve(root, '..');
let pass = 0; const fails = [];
const ok = (name, cond, detail = '') => { if (cond) pass++; else fails.push(name + (detail ? ` — ${detail}` : '')); };

// ---- minimal ZIP reader (accepts UTF-8 flag 0x0800) ----
function unzip(buf) {
  const files = new Map();
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0; i--) if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  if (eocd < 0) throw new Error('no EOCD');
  const count = buf.readUInt16LE(eocd + 10);
  let off = buf.readUInt32LE(eocd + 16);
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(off) !== 0x02014b50) throw new Error('bad central header');
    const flags = buf.readUInt16LE(off + 8), method = buf.readUInt16LE(off + 10);
    const crc = buf.readUInt32LE(off + 16), csize = buf.readUInt32LE(off + 20), usize = buf.readUInt32LE(off + 24);
    const nlen = buf.readUInt16LE(off + 28), elen = buf.readUInt16LE(off + 30), clen = buf.readUInt16LE(off + 32);
    const lho = buf.readUInt32LE(off + 42);
    const name = buf.subarray(off + 46, off + 46 + nlen).toString('utf8');
    const lnlen = buf.readUInt16LE(lho + 26), lelen = buf.readUInt16LE(lho + 28);
    const dstart = lho + 30 + lnlen + lelen;
    const comp = buf.subarray(dstart, dstart + csize);
    const data = method === 8 ? zlib.inflateRawSync(comp) : comp;
    files.set(name, { data, flags, crc, usize, method });
    off += 46 + nlen + elen + clen;
  }
  return files;
}
const T = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = (c & 1) ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (const x of b) c = T[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const J = (files, name) => JSON.parse(files.get(name).data.toString('utf8'));

const LOOKS = ['azure', 'ember', 'moss', 'violet'];
const PROP = 'skinmenu:look';
const dist = path.join(root, 'dist');

// ---- 1. artifacts present ----
const names = ['skin-menu.mcaddon', 'skinmenu-behavior.mcpack', 'skinmenu-resource.mcpack', 'SHA256SUMS.txt'];
for (const n of names) ok(`artifact exists: ${n}`, fs.existsSync(path.join(dist, n)));
if (fails.length) { console.log('FAIL: missing artifacts'); process.exit(1); }

const addonBuf = fs.readFileSync(path.join(dist, 'skin-menu.mcaddon'));
const bpBuf = fs.readFileSync(path.join(dist, 'skinmenu-behavior.mcpack'));
const rpBuf = fs.readFileSync(path.join(dist, 'skinmenu-resource.mcpack'));

// ---- 2. checksums ----
const sums = fs.readFileSync(path.join(dist, 'SHA256SUMS.txt'), 'utf8').trim().split('\n');
for (const line of sums) {
  const [digest, name] = line.split(/\s+/);
  const buf = fs.readFileSync(path.join(dist, name));
  ok(`sha256 recorded correctly: ${name}`, sha(buf) === digest);
}

// ---- 3. container integrity ----
const addon = unzip(addonBuf), bp = unzip(bpBuf), rp = unzip(rpBuf);
ok('mcaddon holds exactly 2 sub-packs', addon.size === 2, `got ${addon.size}`);
ok('mcaddon contains behaviour sub-pack', addon.has('skinmenu_behavior.mcpack'));
ok('mcaddon contains resource sub-pack', addon.has('skinmenu_resource.mcpack'));
ok('embedded BP identical to standalone BP', sha(addon.get('skinmenu_behavior.mcpack').data) === sha(bpBuf));
ok('embedded RP identical to standalone RP', sha(addon.get('skinmenu_resource.mcpack').data) === sha(rpBuf));
for (const [label, files] of [['mcaddon', addon], ['BP', bp], ['RP', rp]]) {
  for (const [name, f] of files) {
    ok(`${label} CRC valid: ${name}`, crc32(f.data) === f.crc);
    ok(`${label} size valid: ${name}`, f.data.length === f.usize);
    ok(`${label} UTF-8 flag: ${name}`, (f.flags & 0x0800) !== 0);
    ok(`${label} no path traversal: ${name}`, !name.includes('..') && !name.startsWith('/'));
  }
}
ok('BP manifest at zip root', bp.has('manifest.json'));
ok('RP manifest at zip root', rp.has('manifest.json'));

// ---- 4. manifests ----
const bm = J(bp, 'manifest.json'), rm = J(rp, 'manifest.json');
ok('BP manifest format_version 2', bm.format_version === 2);
ok('RP manifest format_version 2', rm.format_version === 2);
const uuidRe = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const allUuids = [bm.header.uuid, ...bm.modules.map(m => m.uuid), rm.header.uuid, ...rm.modules.map(m => m.uuid)];
for (const u of allUuids) ok(`valid v4 UUID: ${u}`, uuidRe.test(u));
ok('all UUIDs unique', new Set(allUuids).size === allUuids.length);
ok('BP depends on RP header', bm.dependencies.some(d => d.uuid === rm.header.uuid));
ok('BP declares @minecraft/server', bm.dependencies.some(d => d.module_name === '@minecraft/server'));
ok('BP declares @minecraft/server-ui', bm.dependencies.some(d => d.module_name === '@minecraft/server-ui'));
const scriptMod = bm.modules.find(m => m.type === 'script');
ok('BP has script module', !!scriptMod);
ok('script entry is scripts/main.js', scriptMod?.entry === 'scripts/main.js');
ok('script entry exists in BP', bp.has('scripts/main.js'));
ok('BP has data module', bm.modules.some(m => m.type === 'data'));
ok('RP has resources module', rm.modules.some(m => m.type === 'resources'));
ok('BP min_engine_version present', Array.isArray(bm.header.min_engine_version));
ok('no skin_pack module (this is an add-on, not a skin pack)',
  !bm.modules.some(m => m.type === 'skin_pack') && !rm.modules.some(m => m.type === 'skin_pack'));

// ---- 5. every JSON parses ----
for (const [label, files] of [['BP', bp], ['RP', rp]]) {
  for (const [name, f] of files) {
    if (!name.endsWith('.json')) continue;
    let parsed = true;
    try { JSON.parse(f.data.toString('utf8')); } catch { parsed = false; }
    ok(`${label} JSON parses: ${name}`, parsed);
  }
}

// ---- 6. behaviour: player override ----
const bpe = J(bp, 'entities/player.json')['minecraft:entity'];
ok('BP targets minecraft:player', bpe.description.identifier === 'minecraft:player');
ok('player not spawnable', bpe.description.is_spawnable === false);
ok('player not summonable', bpe.description.is_summonable === false);
const prop = bpe.description.properties?.[PROP];
ok('look property defined', !!prop);
ok('property is int', prop?.type === 'int');
ok('property client_sync true', prop?.client_sync === true);
ok('property range covers 0..N', prop?.range?.[0] === 0 && prop?.range?.[1] === LOOKS.length);
ok('property default is own skin (0)', prop?.default === 0);
// vanilla behaviour preserved
const vBP = JSON.parse(fs.readFileSync(path.join(repo, '.cache/tier-d-vanilla/latest-player.behavior.json'), 'utf8'))['minecraft:entity'];
for (const k of Object.keys(vBP.components || {})) ok(`vanilla component preserved: ${k}`, !!bpe.components?.[k]);
for (const k of Object.keys(vBP.component_groups || {})) ok(`vanilla group preserved: ${k}`, !!bpe.component_groups?.[k]);
for (const k of Object.keys(vBP.events || {})) ok(`vanilla event preserved: ${k}`, !!bpe.events?.[k]);
// fallback channel
for (let i = 0; i <= LOOKS.length; i++) {
  ok(`mark_variant group ${i}`, bpe.component_groups?.[`skinmenu:look_${i}`]?.['minecraft:mark_variant']?.value === i);
  const ev = bpe.events?.[`skinmenu:set_look_${i}`];
  ok(`event ${i} exists`, !!ev);
  ok(`event ${i} adds its group`, ev?.add?.component_groups?.includes(`skinmenu:look_${i}`));
  ok(`event ${i} removes siblings`, (ev?.remove?.component_groups?.length ?? 0) === LOOKS.length);
  ok(`event ${i} sets property`, ev?.set_property?.[PROP] === i);
}

// ---- 7. script ----
const js = bp.get('scripts/main.js').data.toString('utf8');
ok('script imports server', /from "@minecraft\/server"/.test(js));
ok('script imports server-ui', /from "@minecraft\/server-ui"/.test(js));
ok('script uses ActionFormData', js.includes('new ActionFormData()'));
ok('script has a button per look plus reset', (js.match(/form\.button\(/g) || []).length === 2, 'one literal + one loop');
ok('script loops all looks', js.includes('for (const look of LOOKS)'));
ok('script writes property channel', js.includes('player.setProperty(PROP, i)'));
ok('script writes event channel', js.includes('player.triggerEvent('));
ok('script persists choice', js.includes('setDynamicProperty(SAVE_KEY'));
ok('script restores on spawn', js.includes('playerSpawn.subscribe') && js.includes('applyLook(player, saved, false)'));
ok('script opens via chat', js.includes('chatSend.subscribe'));
ok('script opens via item use', js.includes('itemUse.subscribe'));
ok('script registers custom command', js.includes('customCommandRegistry.registerCommand'));
ok('custom command needs no cheats', /cheatsRequired:\s*false/.test(js));
ok('custom command usable by anyone', /permissionLevel:\s*0/.test(js));
ok('no Dressing Room dependency in script', !/dressing|persona|SkinData|setSkin/i.test(js));
ok('no network/account calls in script', !/http|fetch|socket|xbox|microsoft|login|signin/i.test(js));
ok('no simulated player / gametest', !/gametest|SimulatedPlayer/i.test(js));
ok('property writes are guarded', /try\s*\{\s*player\.setProperty/.test(js));
ok('event writes are guarded', /try\s*\{\s*player\.triggerEvent/.test(js));
for (const l of LOOKS) ok(`script lists look: ${l}`, js.includes(`"${l}"`));

// ---- 8. item ----
const item = J(bp, 'items/skin_remote.json')['minecraft:item'];
ok('remote identifier', item.description.identifier === 'skinmenu:remote');
ok('remote stack size 1', item.components['minecraft:max_stack_size'] === 1);
ok('remote has icon', !!item.components['minecraft:icon']);
ok('remote appears in creative menu', item.description.menu_category?.category === 'items');
ok('remote icon registered in RP atlas', !!J(rp, 'textures/item_texture.json').texture_data.skinmenu_remote);
ok('remote icon texture present', rp.has('textures/items/skin_remote.png'));

// ---- 9. resource: client entity ----
const cpe = J(rp, 'entity/player.entity.json');
const desc = cpe['minecraft:client_entity'].description;
ok('RP targets minecraft:player', desc.identifier === 'minecraft:player');
ok('min_engine_version pinned for Persona support', desc.min_engine_version === '1.13.0');
ok('attachables still enabled', desc.enable_attachables === true);
const vRP = JSON.parse(fs.readFileSync(path.join(repo, '.cache/tier-d-vanilla/latest-player.entity.json'), 'utf8'))['minecraft:client_entity'].description;
for (const k of Object.keys(vRP.animations || {})) ok(`vanilla animation preserved: ${k}`, desc.animations?.[k] === vRP.animations[k]);
for (const k of Object.keys(vRP.materials || {})) ok(`vanilla material preserved: ${k}`, desc.materials?.[k] === vRP.materials[k]);
for (const k of Object.keys(vRP.geometry || {})) ok(`vanilla geometry preserved: ${k}`, desc.geometry?.[k] === vRP.geometry[k]);
ok('vanilla default texture untouched', desc.textures.default === vRP.textures.default);
ok('vanilla scripts.scale untouched', desc.scripts?.scale === vRP.scripts?.scale);
ok('vanilla pre_animation untouched', JSON.stringify(desc.scripts?.pre_animation) === JSON.stringify(vRP.scripts?.pre_animation));
ok('vanilla initialise untouched', JSON.stringify(desc.scripts?.initialize) === JSON.stringify(vRP.scripts?.initialize));
for (const l of LOOKS) ok(`look texture short-name: ${l}`, desc.textures[`look_${l}`] === `textures/skinmenu/skins/${l}`);

// controllers: vanilla retained but gated, custom added
const flat = Object.assign({}, ...desc.render_controllers.map(e => typeof e === 'string' ? { [e]: '' } : e));
for (const name of Object.keys(Object.assign({}, ...vRP.render_controllers.map(e => typeof e === 'string' ? { [e]: '' } : e)))) {
  ok(`vanilla controller still referenced: ${name}`, name in flat);
}
ok('custom third-person controller referenced', 'controller.render.skinmenu.third_person' in flat);
ok('custom first-person controller referenced', 'controller.render.skinmenu.first_person' in flat);
ok('vanilla third-person gated to index 0', String(flat['controller.render.player.third_person']).includes('== 0)'));
ok('vanilla first-person gated to index 0', String(flat['controller.render.player.first_person']).includes('== 0)'));
ok('custom first-person gated to index > 0', flat['controller.render.skinmenu.first_person'].includes('> 0)'));
ok('custom third-person gated to index > 0', flat['controller.render.skinmenu.third_person'].includes('> 0)'));
ok('spectator controllers not re-gated', !String(flat['controller.render.player.third_person_spectator']).includes('skinmenu'));
ok('map controller untouched', flat['controller.render.player.map'] === vRP.render_controllers.find(e => typeof e === 'object' && e['controller.render.player.map'])['controller.render.player.map']);

// ---- 10. resource: render controllers ----
ok('vanilla player RC file NOT overridden', !rp.has('render_controllers/player.render_controllers.json'));
const rc = J(rp, 'render_controllers/skinmenu.render_controllers.json').render_controllers;
const vRC = JSON.parse(fs.readFileSync(path.join(repo, '.cache/tier-d-vanilla/latest-player.render_controllers.json'), 'utf8')).render_controllers;
for (const view of ['first_person', 'third_person']) {
  const c = rc[`controller.render.skinmenu.${view}`], v = vRC[`controller.render.player.${view}`];
  ok(`${view}: controller defined`, !!c);
  ok(`${view}: geometry preserved`, c?.geometry === v.geometry);
  ok(`${view}: materials preserved`, JSON.stringify(c?.materials) === JSON.stringify(v.materials));
  ok(`${view}: part_visibility preserved`, JSON.stringify(c?.part_visibility) === JSON.stringify(v.part_visibility));
  ok(`${view}: array has one entry per look`, c?.arrays?.textures?.['Array.looks']?.length === LOOKS.length);
  ok(`${view}: array order matches looks`, JSON.stringify(c?.arrays?.textures?.['Array.looks']) === JSON.stringify(LOOKS.map(l => `Texture.look_${l}`)));
  ok(`${view}: texture indexed by property`, c?.textures?.[0]?.includes(`query.property('${PROP}')`));
  ok(`${view}: texture indexed by mark_variant fallback`, c?.textures?.[0]?.includes('query.mark_variant'));
  ok(`${view}: index clamped`, c?.textures?.[0]?.includes(`math.clamp(`) && c?.textures?.[0]?.includes(`0, ${LOOKS.length - 1})`));
}

// ---- 11. textures are the frozen Tier B originals ----
function pngInfo(buf) {
  if (buf.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a') return null;
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20), depth: buf[24], colour: buf[25] };
}
for (const l of LOOKS) {
  const rel = `textures/skinmenu/skins/${l}.png`;
  ok(`skin texture present: ${l}`, rp.has(rel));
  if (!rp.has(rel)) continue;
  const data = rp.get(rel).data;
  const src = fs.readFileSync(path.join(repo, 'tier-b', 'packs', 'tier_b_skins', `${l}.png`));
  ok(`skin byte-identical to frozen Tier B: ${l}`, sha(data) === sha(src));
  const info = pngInfo(data);
  ok(`skin is a PNG: ${l}`, !!info);
  ok(`skin is 64x64: ${l}`, info?.width === 64 && info?.height === 64, `${info?.width}x${info?.height}`);
  ok(`skin has alpha channel: ${l}`, info?.colour === 6);
  const icon = `textures/skinmenu/icons/${l}.png`;
  ok(`menu icon present: ${l}`, rp.has(icon));
  const ii = rp.has(icon) ? pngInfo(rp.get(icon).data) : null;
  ok(`menu icon is 16x16 PNG: ${l}`, ii?.width === 16 && ii?.height === 16);
}
ok('exactly four original skins shipped', [...rp.keys()].filter(k => k.startsWith('textures/skinmenu/skins/')).length === 4);
for (const [label, files] of [['BP', bp], ['RP', rp]]) {
  ok(`${label} has pack_icon.png`, files.has('pack_icon.png'));
}

// ---- 12. language files ----
ok('RP languages.json lists en_US', J(rp, 'texts/languages.json').includes('en_US'));
ok('RP en_US.lang names the remote', rp.get('texts/en_US.lang').data.toString('utf8').includes('item.skinmenu:remote='));

// ---- 13. build report agrees with disk ----
const report = JSON.parse(fs.readFileSync(path.join(root, 'reports/build.json'), 'utf8'));
for (const a of report.artifacts) {
  const buf = fs.readFileSync(path.join(dist, a.name));
  ok(`report sha256 matches disk: ${a.name}`, sha(buf) === a.sha256);
  ok(`report byte count matches disk: ${a.name}`, buf.length === a.bytes);
}
ok('report lists BP files accurately', JSON.stringify(report.bpFiles) === JSON.stringify([...bp.keys()]));
ok('report lists RP files accurately', JSON.stringify(report.rpFiles) === JSON.stringify([...rp.keys()]));

// ---- result ----
const total = pass + fails.length;
if (fails.length) {
  console.log(`STRUCTURAL_FAIL ${pass}/${total}`);
  for (const f of fails) console.log('  FAIL ' + f);
  process.exit(1);
}
console.log(`STRUCTURAL_PASS ${pass}/${total}`);
console.log('NOTE: static checks only. In-game behaviour is NOT RUN until observed on a real client.');
