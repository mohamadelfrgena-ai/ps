// Builds "Skin Menu" — an in-world menu add-on that changes how the local player renders.
// Inputs: frozen Tier B textures + cached vanilla player files. Writes only inside tier-d/.
//
// Two independent switch channels are wired, because whether entity properties sync on
// minecraft:player is not documented:
//   A) BP entity property  skinmenu:look  (client_sync)  -> query.property('skinmenu:look')
//   B) BP component group  minecraft:mark_variant        -> query.mark_variant
// The render controller reads math.max(A, B), so either channel alone is enough.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const repo = path.resolve(root, '..');
const vanillaDir = path.join(repo, '.cache', 'tier-d-vanilla');
const skinSrc = path.join(repo, 'tier-b', 'packs', 'tier_b_skins');

const VANILLA = process.argv.includes('--vanilla=stable') ? 'stable' : 'latest';
const vfile = n => path.join(vanillaDir, VANILLA === 'latest' ? `latest-${n}` : n);

const NS = 'skinmenu';
const PROP = `${NS}:look`;
const UUID = n => `5c1f8a30-2b64-4d19-9e77-1a3f6b8d0e0${n}`;
const ENGINE = [1, 21, 90];

const LOOKS = [
  { key: 'azure',  name: 'Azure Mechanic',   label: '\u00a7bAzure Mechanic' },
  { key: 'ember',  name: 'Ember Explorer',   label: '\u00a76Ember Explorer' },
  { key: 'moss',   name: 'Moss Gardener',    label: '\u00a7aMoss Gardener' },
  { key: 'violet', name: 'Violet Navigator', label: '\u00a7dViolet Navigator' }
];
const N = LOOKS.length;
// One Molang expression used everywhere: whichever channel is live wins.
const IDX = `math.max(query.property('${PROP}'), query.mark_variant)`;

const bpDir = path.join(root, 'packs', 'skinmenu_behavior');
const rpDir = path.join(root, 'packs', 'skinmenu_resource');
for (const d of [bpDir, rpDir]) fs.rmSync(d, { recursive: true, force: true });
const w = (base, rel, data) => {
  const p = path.join(base, rel);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, typeof data === 'string' ? data : JSON.stringify(data, null, 2) + '\n');
};

// ================= Behaviour pack =================
w(bpDir, 'manifest.json', {
  format_version: 2,
  header: {
    name: 'Skin Menu — In-World Selector',
    description: 'Open a menu inside the world and change your look. No Dressing Room, no account.',
    uuid: UUID(1), version: [1, 0, 0], min_engine_version: ENGINE
  },
  modules: [
    { type: 'data', uuid: UUID(2), version: [1, 0, 0] },
    { type: 'script', language: 'javascript', uuid: UUID(5), version: [1, 0, 0], entry: 'scripts/main.js' }
  ],
  dependencies: [
    { uuid: UUID(3), version: [1, 0, 0] },
    { module_name: '@minecraft/server', version: '2.0.0' },
    { module_name: '@minecraft/server-ui', version: '2.0.0' }
  ]
});

// Vanilla player behaviour preserved; add one property + look component groups/events.
const bpPlayer = JSON.parse(fs.readFileSync(vfile('player.behavior.json'), 'utf8'));
const bEnt = bpPlayer['minecraft:entity'];
bEnt.description.properties = { [PROP]: { type: 'int', range: [0, N], default: 0, client_sync: true } };
bEnt.component_groups = bEnt.component_groups || {};
bEnt.events = bEnt.events || {};
const groupNames = [];
for (let i = 0; i <= N; i++) {
  const g = `${NS}:look_${i}`;
  groupNames.push(g);
  bEnt.component_groups[g] = { 'minecraft:mark_variant': { value: i } };
}
for (let i = 0; i <= N; i++) {
  bEnt.events[`${NS}:set_look_${i}`] = {
    remove: { component_groups: groupNames.filter(g => g !== `${NS}:look_${i}`) },
    add: { component_groups: [`${NS}:look_${i}`] },
    set_property: { [PROP]: i }
  };
}
w(bpDir, 'entities/player.json', bpPlayer);

w(bpDir, 'items/skin_remote.json', {
  format_version: '1.21.60',
  'minecraft:item': {
    description: { identifier: `${NS}:remote`, menu_category: { category: 'items' } },
    components: {
      'minecraft:display_name': { value: 'Skin Menu Remote' },
      'minecraft:max_stack_size': 1,
      'minecraft:icon': 'skinmenu_remote',
      'minecraft:hand_equipped': false,
      'minecraft:glint': true
    }
  }
});

w(bpDir, 'scripts/main.js', `// Skin Menu — in-world look selector.
// Only long-stable exports are imported, so a newer enum cannot break the whole module.
import { world, system, ItemStack } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";

const PROP = "${PROP}";
const REMOTE = "${NS}:remote";
const LOOKS = ${JSON.stringify(LOOKS.map(l => ({ key: l.key, label: l.label, name: l.name })))};
const TRIGGERS = ["!skin", "!skins", "!look"];
const SAVE_KEY = "${NS}:saved_look";

function labelOf(i) {
  return i > 0 && i <= LOOKS.length ? LOOKS[i - 1].name : "your own skin";
}

function readLook(player) {
  try {
    const v = player.getDynamicProperty(SAVE_KEY);
    return typeof v === "number" ? v : 0;
  } catch {
    return 0;
  }
}

// Write every channel we have. Each is independently guarded: if one is unsupported
// on this client the others still run.
function applyLook(player, index, announce) {
  const i = Math.max(0, Math.min(LOOKS.length, index | 0));
  let ok = false;

  try { player.setDynamicProperty(SAVE_KEY, i); } catch { /* storage unavailable */ }

  try {
    player.setProperty(PROP, i);
    ok = true;
  } catch { /* property channel unavailable on this client */ }

  try {
    player.triggerEvent("${NS}:set_look_" + i);
    ok = true;
  } catch { /* event channel unavailable */ }

  if (!announce) return;
  if (ok) {
    player.sendMessage("\\u00a7aLook: \\u00a7f" + labelOf(i) + "\\u00a7a. Press F5 to see yourself.");
  } else {
    player.sendMessage("\\u00a7cThis client refused the change. Both packs must be active on the world.");
  }
}

function openMenu(player) {
  const active = readLook(player);
  const form = new ActionFormData()
    .title("Skin Menu")
    .body("Choose a look for your character in this world.\\nCurrent: " + labelOf(active));

  form.button("\\u00a7fUse my own skin", "textures/ui/icon_steve");
  for (const look of LOOKS) form.button(look.label, "textures/skinmenu/icons/" + look.key);

  form.show(player).then((res) => {
    if (res.canceled || res.selection === undefined) return;
    applyLook(player, res.selection, true);
  }).catch(() => {
    player.sendMessage("\\u00a7eMenu busy. Type !skin again in a moment.");
  });
}

// Trigger 1 — chat keyword. Most reliable path; needs no item and no cheats.
world.beforeEvents.chatSend.subscribe((ev) => {
  if (!TRIGGERS.includes(ev.message.trim().toLowerCase())) return;
  ev.cancel = true;
  const player = ev.sender;
  system.run(() => openMenu(player));
});

// Trigger 2 — use the remote item.
world.afterEvents.itemUse.subscribe((ev) => {
  if (ev.itemStack?.typeId !== REMOTE) return;
  const player = ev.source;
  system.run(() => openMenu(player));
});

// Trigger 3 — start sneaking while holding the remote.
const wasSneaking = new Map();
system.runInterval(() => {
  for (const player of world.getAllPlayers()) {
    try {
      const inv = player.getComponent("minecraft:inventory");
      const held = inv?.container?.getItem(player.selectedSlotIndex);
      const key = player.id;
      const now = player.isSneaking && held?.typeId === REMOTE;
      const before = wasSneaking.get(key) === true;
      wasSneaking.set(key, now);
      if (now && !before) openMenu(player);
    } catch { /* player not ready this tick */ }
  }
}, 10);

function giveRemote(player) {
  try {
    const inv = player.getComponent("minecraft:inventory")?.container;
    if (!inv) return;
    for (let i = 0; i < inv.size; i++) {
      if (inv.getItem(i)?.typeId === REMOTE) return;
    }
    inv.addItem(new ItemStack(REMOTE, 1));
  } catch { /* inventory not ready */ }
}

// Re-apply the saved look on every spawn, so it survives death, rejoin and relaunch.
world.afterEvents.playerSpawn.subscribe((ev) => {
  const player = ev.player;
  system.runTimeout(() => {
    giveRemote(player);
    const saved = readLook(player);
    if (saved > 0) applyLook(player, saved, false);
    if (ev.initialSpawn) {
      player.sendMessage("\\u00a7bSkin Menu: type \\u00a7f!skin\\u00a7b in chat to open the menu.");
    }
  }, 20);
});

// Trigger 4 — a real slash command where supported. Numeric enum values avoid
// importing enums that may not exist on older clients. No cheats required.
system.beforeEvents.startup.subscribe((ev) => {
  try {
    ev.customCommandRegistry.registerCommand({
      name: "${NS}:open",
      description: "Open the in-world skin menu",
      permissionLevel: 0,
      cheatsRequired: false
    }, (origin) => {
      const player = origin.sourceEntity;
      if (!player || player.typeId !== "minecraft:player") return { status: 1, message: "Run this as a player." };
      system.run(() => openMenu(player));
      return { status: 0 };
    });
  } catch { /* older client: chat and the remote still work */ }
});
`);

// ================= Resource pack =================
w(rpDir, 'manifest.json', {
  format_version: 2,
  header: {
    name: 'Skin Menu — Looks',
    description: 'Textures and render controllers for the in-world skin menu.',
    uuid: UUID(3), version: [1, 0, 0], min_engine_version: ENGINE
  },
  modules: [{ type: 'resources', uuid: UUID(4), version: [1, 0, 0] }]
});

w(rpDir, 'textures/item_texture.json', {
  resource_pack_name: 'skinmenu', texture_name: 'atlas.items',
  texture_data: { skinmenu_remote: { textures: 'textures/items/skin_remote' } }
});
w(rpDir, 'texts/en_US.lang', `item.${NS}:remote=Skin Menu Remote\n`);
w(rpDir, 'texts/languages.json', ['en_US']);

// New controllers only; vanilla player controllers are never overwritten.
const vanillaRC = JSON.parse(fs.readFileSync(vfile('player.render_controllers.json'), 'utf8')).render_controllers;
const pairs = [
  ['controller.render.player.first_person', 'controller.render.skinmenu.first_person'],
  ['controller.render.player.third_person', 'controller.render.skinmenu.third_person']
];
const rcOut = {};
for (const [src, dst] of pairs) {
  const body = JSON.parse(JSON.stringify(vanillaRC[src]));
  body.arrays = { textures: { 'Array.looks': LOOKS.map(l => `Texture.look_${l.key}`) } };
  body.textures = [`Array.looks[math.clamp(${IDX} - 1, 0, ${N - 1})]`];
  rcOut[dst] = body;
}
w(rpDir, 'render_controllers/skinmenu.render_controllers.json', { format_version: '1.10.0', render_controllers: rcOut });

// Vanilla client entity preserved; extra textures + property-gated controllers added.
const rpPlayer = JSON.parse(fs.readFileSync(vfile('player.entity.json'), 'utf8'));
const desc = rpPlayer['minecraft:client_entity'].description;
// Keep Persona / classic skin support intact when overriding the player client entity.
desc.min_engine_version = '1.13.0';
for (const l of LOOKS) desc.textures[`look_${l.key}`] = `textures/skinmenu/skins/${l.key}`;
const renamed = Object.fromEntries(pairs);
const controllers = [];
for (const entry of desc.render_controllers) {
  if (typeof entry === 'string') { controllers.push(entry); continue; }
  for (const [name, cond] of Object.entries(entry)) {
    const custom = renamed[name];
    if (!custom) { controllers.push({ [name]: cond }); continue; }
    controllers.push({ [name]: `(${cond}) && (${IDX} == 0)` });
    controllers.push({ [custom]: `(${cond}) && (${IDX} > 0)` });
  }
}
desc.render_controllers = controllers;
w(rpDir, 'entity/player.entity.json', rpPlayer);

// Copy the four frozen Tier B textures byte-for-byte.
const copied = [];
for (const l of LOOKS) {
  const data = fs.readFileSync(path.join(skinSrc, `${l.key}.png`));
  const p = path.join(rpDir, 'textures', 'skinmenu', 'skins', `${l.key}.png`);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, data);
  copied.push({ key: l.key, bytes: data.length, sha256: crypto.createHash('sha256').update(data).digest('hex') });
}

// ================= tiny PNG writer (menu/pack icons only) =================
const T = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = (c & 1) ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (const x of b) c = T[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) { const t = Buffer.from(type), len = Buffer.alloc(4); len.writeUInt32BE(data.length); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([t, data]))); return Buffer.concat([len, t, data, crc]); }
function png(wd, ht, px) {
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(wd, 0); ihdr.writeUInt32BE(ht, 4); ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc((wd * 4 + 1) * ht);
  for (let y = 0; y < ht; y++) { raw[y * (wd * 4 + 1)] = 0; for (let x = 0; x < wd * 4; x++) raw[y * (wd * 4 + 1) + 1 + x] = px[y * wd * 4 + x]; }
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
const PALETTE = { azure: [27, 127, 171], ember: [187, 73, 34], moss: [60, 123, 72], violet: [112, 63, 155] };
for (const l of LOOKS) {
  const c = PALETTE[l.key], px = new Uint8Array(16 * 16 * 4);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const i = (y * 16 + x) * 4, edge = x < 2 || y < 2 || x > 13 || y > 13; px[i] = edge ? 24 : c[0]; px[i + 1] = edge ? 26 : c[1]; px[i + 2] = edge ? 32 : c[2]; px[i + 3] = 255; }
  const p = path.join(rpDir, 'textures', 'skinmenu', 'icons', `${l.key}.png`);
  fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, png(16, 16, px));
}
{
  const px = new Uint8Array(16 * 16 * 4);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const i = (y * 16 + x) * 4, on = x > 2 && x < 13 && y > 1 && y < 14; px[i] = on ? 56 : 14; px[i + 1] = on ? 189 : 16; px[i + 2] = on ? 248 : 20; px[i + 3] = 255; }
  const p = path.join(rpDir, 'textures', 'items', 'skin_remote.png');
  fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, png(16, 16, px));
}
for (const [dir, colour] of [[bpDir, [56, 189, 248]], [rpDir, [112, 63, 155]]]) {
  const px = new Uint8Array(64 * 64 * 4);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) { const i = (y * 64 + x) * 4, s = ((x + y) % 7) - 3; px[i] = Math.max(0, colour[0] + s); px[i + 1] = Math.max(0, colour[1] + s); px[i + 2] = Math.max(0, colour[2] + s); px[i + 3] = 255; }
  fs.writeFileSync(path.join(dir, 'pack_icon.png'), png(64, 64, px));
}

// ================= deterministic .mcaddon =================
function collect(dir, prefix = '') {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    const rel = prefix ? `${prefix}/${e.name}` : e.name, p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...collect(p, rel)); else out.push({ path: rel, data: fs.readFileSync(p) });
  }
  return out;
}
function zip(entries) {
  const locals = [], central = []; let offset = 0;
  const date = ((2026 - 1980) << 9) | (11 << 5) | 12, time = 12 << 11;
  for (const e of entries) {
    const name = Buffer.from(e.path, 'utf8'), comp = zlib.deflateRawSync(e.data, { level: 9 }), crc = crc32(e.data);
    const lh = Buffer.alloc(30 + name.length);
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt16LE(0x0800, 6); lh.writeUInt16LE(8, 8);
    lh.writeUInt16LE(time, 10); lh.writeUInt16LE(date, 12); lh.writeUInt32LE(crc, 14); lh.writeUInt32LE(comp.length, 18);
    lh.writeUInt32LE(e.data.length, 22); lh.writeUInt16LE(name.length, 26); name.copy(lh, 30); locals.push(lh, comp);
    const ch = Buffer.alloc(46 + name.length);
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6); ch.writeUInt16LE(0x0800, 8);
    ch.writeUInt16LE(8, 10); ch.writeUInt16LE(time, 12); ch.writeUInt16LE(date, 14); ch.writeUInt32LE(crc, 16);
    ch.writeUInt32LE(comp.length, 20); ch.writeUInt32LE(e.data.length, 24); ch.writeUInt16LE(name.length, 28);
    ch.writeUInt32LE(offset, 42); name.copy(ch, 46); central.push(ch); offset += lh.length + comp.length;
  }
  const cd = Buffer.concat(central), end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(cd.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, cd, end]);
}
const bpEntries = collect(bpDir), rpEntries = collect(rpDir);
const bpZip = zip(bpEntries), rpZip = zip(rpEntries);
fs.mkdirSync(path.join(root, 'dist'), { recursive: true });
fs.writeFileSync(path.join(root, 'dist', 'skinmenu-behavior.mcpack'), bpZip);
fs.writeFileSync(path.join(root, 'dist', 'skinmenu-resource.mcpack'), rpZip);
const addon = zip([{ path: 'skinmenu_behavior.mcpack', data: bpZip }, { path: 'skinmenu_resource.mcpack', data: rpZip }]);
fs.writeFileSync(path.join(root, 'dist', 'skin-menu.mcaddon'), addon);
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
fs.writeFileSync(path.join(root, 'dist', 'SHA256SUMS.txt'),
  [['skin-menu.mcaddon', addon], ['skinmenu-behavior.mcpack', bpZip], ['skinmenu-resource.mcpack', rpZip]]
    .map(([n, b]) => `${sha(b)}  ${n}`).join('\n') + '\n');
fs.mkdirSync(path.join(root, 'reports'), { recursive: true });
fs.writeFileSync(path.join(root, 'reports', 'build.json'), JSON.stringify({
  vanillaBase: VANILLA,
  vanillaSources: ['player.behavior.json', 'player.entity.json', 'player.render_controllers.json'].map(n => ({
    file: n, sha256: sha(fs.readFileSync(vfile(n)))
  })),
  property: PROP, indexExpression: IDX, looks: LOOKS.map(l => l.key), textures: copied,
  bpFiles: bpEntries.map(e => e.path), rpFiles: rpEntries.map(e => e.path),
  artifacts: [
    { name: 'skin-menu.mcaddon', bytes: addon.length, sha256: sha(addon) },
    { name: 'skinmenu-behavior.mcpack', bytes: bpZip.length, sha256: sha(bpZip) },
    { name: 'skinmenu-resource.mcpack', bytes: rpZip.length, sha256: sha(rpZip) }
  ]
}, null, 2) + '\n');
console.log(`Built skin-menu.mcaddon (${addon.length} bytes) from ${VANILLA} vanilla: BP ${bpEntries.length} files, RP ${rpEntries.length} files`);
