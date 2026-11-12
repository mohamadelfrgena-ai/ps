// Builds a MINIMAL diagnostic pack: behaviour pack only, no resource pack,
// no player.json override, no custom item, no custom command.
// Purpose: prove whether scripts + forms load at all on the operator's client.
// If this opens a menu, the failure in the full add-on is in the pack files, not scripting.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dir = path.join(root, 'packs', 'skinmenu_probe');
fs.rmSync(dir, { recursive: true, force: true });
const w = (rel, data) => {
  const p = path.join(dir, rel);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, typeof data === 'string' ? data : JSON.stringify(data, null, 2) + '\n');
};

w('manifest.json', {
  format_version: 2,
  header: {
    name: 'Skin Menu PROBE (diagnostic)',
    description: 'Minimal test: type !skin in chat. No skins, no resource pack, no overrides.',
    uuid: '7d2a91c4-3e58-4b06-9f21-6c0d4a7e1b01',
    version: [1, 0, 0],
    min_engine_version: [1, 20, 0]
  },
  modules: [
    { type: 'script', language: 'javascript', uuid: '7d2a91c4-3e58-4b06-9f21-6c0d4a7e1b02', version: [1, 0, 0], entry: 'scripts/main.js' }
  ],
  dependencies: [
    { module_name: '@minecraft/server', version: '1.17.0' },
    { module_name: '@minecraft/server-ui', version: '1.3.0' }
  ]
});

w('scripts/main.js', `// Minimal diagnostic. Deliberately uses only long-stable API surface.
import { world, system } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";

function report(player, text) {
  try { player.sendMessage(text); } catch { /* ignore */ }
}

function openProbe(player) {
  const form = new ActionFormData()
    .title("PROBE OK")
    .body("Scripting and forms work on this client.\\nPick any button to continue the diagnosis.");
  form.button("Test 1: chat reply");
  form.button("Test 2: save a value");
  form.button("Test 3: entity property");
  form.button("Test 4: entity event");

  form.show(player).then((res) => {
    if (res.canceled || res.selection === undefined) return;
    if (res.selection === 0) {
      report(player, "\\u00a7aTEST 1 PASS: chat reply works.");
    } else if (res.selection === 1) {
      try {
        player.setDynamicProperty("probe:value", 42);
        const back = player.getDynamicProperty("probe:value");
        report(player, back === 42 ? "\\u00a7aTEST 2 PASS: saved value read back." : "\\u00a7eTEST 2 ODD: got " + back);
      } catch (e) { report(player, "\\u00a7cTEST 2 FAIL: " + e); }
    } else if (res.selection === 2) {
      try {
        player.setProperty("skinmenu:look", 1);
        report(player, "\\u00a7aTEST 3 PASS: player accepted the entity property.");
      } catch (e) { report(player, "\\u00a7eTEST 3 FAIL (expected without the full pack): " + e); }
    } else {
      try {
        player.triggerEvent("skinmenu:set_look_1");
        report(player, "\\u00a7aTEST 4 PASS: player accepted the entity event.");
      } catch (e) { report(player, "\\u00a7eTEST 4 FAIL (expected without the full pack): " + e); }
    }
  }).catch((e) => report(player, "\\u00a7eForm busy: " + e));
}

world.beforeEvents.chatSend.subscribe((ev) => {
  const msg = ev.message.trim().toLowerCase();
  if (msg !== "!skin" && msg !== "!probe") return;
  ev.cancel = true;
  const player = ev.sender;
  system.run(() => openProbe(player));
});

world.afterEvents.playerSpawn.subscribe((ev) => {
  if (!ev.initialSpawn) return;
  system.runTimeout(() => {
    report(ev.player, "\\u00a7bPROBE loaded. Type \\u00a7f!skin\\u00a7b in chat to test.");
  }, 40);
});
`);

// ---- zip ----
const T = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = (c & 1) ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (const x of b) c = T[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function collect(d, prefix = '') {
  const out = [];
  for (const e of fs.readdirSync(d, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    const rel = prefix ? `${prefix}/${e.name}` : e.name, p = path.join(d, e.name);
    if (e.isDirectory()) out.push(...collect(p, rel)); else out.push({ path: rel, data: fs.readFileSync(p) });
  }
  return out;
}
function zip(entries) {
  const locals = [], central = []; let offset = 0;
  const date = ((2026 - 1980) << 9) | (11 << 5) | 13, time = 12 << 11;
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
const entries = collect(dir);
const buf = zip(entries);
fs.mkdirSync(path.join(root, 'dist'), { recursive: true });
fs.writeFileSync(path.join(root, 'dist', 'skinmenu-probe.mcpack'), buf);
console.log(`Built skinmenu-probe.mcpack (${buf.length} bytes, ${entries.length} files) sha256=${crypto.createHash('sha256').update(buf).digest('hex')}`);
