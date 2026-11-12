// Behavioural tests for the menu logic, run against a stub of the Bedrock Script API.
// This proves the script's own logic. It does NOT prove the game renders the texture:
// that requires a real client and is tracked separately as NOT RUN.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = fs.readFileSync(path.join(root, 'packs/skinmenu_behavior/scripts/main.js'), 'utf8');
const LOOKS = ['azure', 'ember', 'moss', 'violet'];

function harness(opts = {}) {
  const subs = { chatSend: [], itemUse: [], playerSpawn: [], startup: [] };
  const intervals = [], timeouts = [], runQueue = [];
  const commands = new Map();
  const messages = [];

  const world = {
    beforeEvents: { chatSend: { subscribe: f => subs.chatSend.push(f) } },
    afterEvents: {
      itemUse: { subscribe: f => subs.itemUse.push(f) },
      playerSpawn: { subscribe: f => subs.playerSpawn.push(f) }
    },
    getAllPlayers: () => opts.players ?? []
  };
  const system = {
    currentTick: 100,
    beforeEvents: { startup: { subscribe: f => subs.startup.push(f) } },
    run: f => runQueue.push(f),
    runTimeout: f => timeouts.push(f),
    runInterval: f => intervals.push(f)
  };
  class ItemStack { constructor(typeId, amount) { this.typeId = typeId; this.amount = amount; } }
  const captured = { form: undefined, player: undefined };
  class ActionFormData {
    constructor() { this.buttons = []; this.titleText = ''; this.bodyText = ''; }
    title(t) { this.titleText = t; return this; }
    body(b) { this.bodyText = b; return this; }
    button(text, icon) { this.buttons.push({ text, icon }); return this; }
    show(player) {
      captured.form = this;
      captured.player = player;
      if (opts.formRejects) return Promise.reject(new Error('user busy'));
      return Promise.resolve({ canceled: opts.canceled === true, selection: opts.selection });
    }
  }

  const stripped = source
    .replace(/^import .*?;\s*$/gms, '')
    .replace(/^\/\/.*$/gm, '');
  const ctx = { world, system, ItemStack, ActionFormData, Map, Math, Promise, console, JSON, Number, String, Object, Array };
  vm.createContext(ctx);
  vm.runInContext(stripped, ctx);

  const flush = () => { while (runQueue.length) runQueue.shift()(); };
  const flushTimeouts = () => { while (timeouts.length) timeouts.shift()(); };

  function makePlayer(id = 'p1') {
    const store = new Map(), props = new Map(), events = [], items = [];
    return {
      id, typeId: 'minecraft:player', isSneaking: false, selectedSlotIndex: 0,
      messages,
      sendMessage(m) { messages.push(m); },
      getDynamicProperty: k => store.get(k),
      setDynamicProperty(k, v) { if (opts.noStorage) throw new Error('no storage'); store.set(k, v); },
      getProperty: k => props.get(k),
      setProperty(k, v) { if (opts.noProperty) throw new Error('unsupported property'); props.set(k, v); },
      triggerEvent(n) { if (opts.noEvents) throw new Error('unsupported event'); events.push(n); },
      getComponent: n => n === 'minecraft:inventory'
        ? { container: { size: 36, getItem: i => items[i], addItem: it => { items.push(it); } } }
        : undefined,
      _props: props, _events: events, _items: items, _store: store
    };
  }

  return { subs, intervals, commands, messages, world, system, flush, flushTimeouts, makePlayer, ctx, captured,
    registerCommands: () => {
      const registry = { registerCommand: (def, cb) => commands.set(def.name, { def, cb }) };
      for (const f of subs.startup) f({ customCommandRegistry: registry });
    } };
}

test('menu lists a reset entry plus all four looks', () => {
  const h = harness();
  const p = h.makePlayer();
  h.subs.chatSend[0]({ message: '!skin', sender: p, cancel: false });
  h.flush();
  const form = h.captured.form;
  assert.equal(form.titleText, 'Skin Menu');
  assert.equal(form.buttons.length, 5);
  assert.match(form.buttons[0].text, /my own skin/);
  for (let i = 0; i < LOOKS.length; i++) {
    assert.equal(form.buttons[i + 1].icon, `textures/skinmenu/icons/${LOOKS[i]}`);
  }
});

test('chat trigger is case/space insensitive and cancels the chat message', () => {
  const h = harness();
  for (const msg of ['!skin', '  !SKIN  ', '!skins', '!look']) {
    const ev = { message: msg, sender: h.makePlayer(), cancel: false };
    h.subs.chatSend[0](ev);
    assert.equal(ev.cancel, true, msg);
  }
  const other = { message: 'hello world', sender: h.makePlayer(), cancel: false };
  h.subs.chatSend[0](other);
  assert.equal(other.cancel, false);
});

test('selecting a look writes both switch channels and saves the choice', async () => {
  const h = harness({ selection: 2 });
  const p = h.makePlayer();
  h.subs.chatSend[0]({ message: '!skin', sender: p, cancel: false });
  h.flush();
  await new Promise(r => setImmediate(r));
  assert.equal(p._props.get('skinmenu:look'), 2);
  assert.deepEqual(p._events, ['skinmenu:set_look_2']);
  assert.equal(p._store.get('skinmenu:saved_look'), 2);
  assert.ok(h.messages.some(m => m.includes('Ember Explorer')));
});

test('reset entry returns index 0 through both channels', async () => {
  const h = harness({ selection: 0 });
  const p = h.makePlayer();
  h.subs.chatSend[0]({ message: '!skin', sender: p, cancel: false });
  h.flush();
  await new Promise(r => setImmediate(r));
  assert.equal(p._props.get('skinmenu:look'), 0);
  assert.deepEqual(p._events, ['skinmenu:set_look_0']);
  assert.ok(h.messages.some(m => m.includes('your own skin')));
});

test('cancelling the menu changes nothing', async () => {
  const h = harness({ canceled: true, selection: 3 });
  const p = h.makePlayer();
  h.subs.chatSend[0]({ message: '!skin', sender: p, cancel: false });
  h.flush();
  await new Promise(r => setImmediate(r));
  assert.equal(p._props.size, 0);
  assert.deepEqual(p._events, []);
});

test('property channel failing still applies via the event channel', async () => {
  const h = harness({ selection: 1, noProperty: true });
  const p = h.makePlayer();
  h.subs.chatSend[0]({ message: '!skin', sender: p, cancel: false });
  h.flush();
  await new Promise(r => setImmediate(r));
  assert.deepEqual(p._events, ['skinmenu:set_look_1']);
  assert.ok(h.messages.some(m => m.includes('Azure Mechanic')));
});

test('event channel failing still applies via the property channel', async () => {
  const h = harness({ selection: 4, noEvents: true });
  const p = h.makePlayer();
  h.subs.chatSend[0]({ message: '!skin', sender: p, cancel: false });
  h.flush();
  await new Promise(r => setImmediate(r));
  assert.equal(p._props.get('skinmenu:look'), 4);
  assert.ok(h.messages.some(m => m.includes('Violet Navigator')));
});

test('both channels failing reports an honest error, never a fake success', async () => {
  const h = harness({ selection: 2, noProperty: true, noEvents: true });
  const p = h.makePlayer();
  h.subs.chatSend[0]({ message: '!skin', sender: p, cancel: false });
  h.flush();
  await new Promise(r => setImmediate(r));
  assert.ok(h.messages.some(m => m.includes('refused the change')));
  assert.ok(!h.messages.some(m => m.includes('Look:')));
});

test('saved look is restored on respawn without a message spam', () => {
  const h = harness();
  const p = h.makePlayer();
  p._store.set('skinmenu:saved_look', 3);
  h.subs.playerSpawn[0]({ player: p, initialSpawn: false });
  h.flushTimeouts();
  assert.equal(p._props.get('skinmenu:look'), 3);
  assert.deepEqual(p._events, ['skinmenu:set_look_3']);
  assert.ok(!h.messages.some(m => m.includes('Look:')));
});

test('no saved look means the player isn\u2019t touched on spawn', () => {
  const h = harness();
  const p = h.makePlayer();
  h.subs.playerSpawn[0]({ player: p, initialSpawn: true });
  h.flushTimeouts();
  assert.equal(p._props.size, 0);
  assert.deepEqual(p._events, []);
});

test('remote is given once and never duplicated', () => {
  const h = harness();
  const p = h.makePlayer();
  h.subs.playerSpawn[0]({ player: p, initialSpawn: true });
  h.flushTimeouts();
  assert.equal(p._items.length, 1);
  assert.equal(p._items[0].typeId, 'skinmenu:remote');
  h.subs.playerSpawn[0]({ player: p, initialSpawn: false });
  h.flushTimeouts();
  assert.equal(p._items.length, 1);
});

test('using the remote opens the menu; other items do not', () => {
  const h = harness();
  const p = h.makePlayer();
  h.subs.itemUse[0]({ itemStack: { typeId: 'minecraft:stick' }, source: p });
  h.flush();
  assert.equal(h.captured.player, undefined);
  h.subs.itemUse[0]({ itemStack: { typeId: 'skinmenu:remote' }, source: p });
  h.flush();
  assert.equal(h.captured.player, p);
});

test('custom command registers with no cheats and any permission level', () => {
  const h = harness();
  h.registerCommands();
  const cmd = h.commands.get('skinmenu:open');
  assert.ok(cmd);
  assert.equal(cmd.def.cheatsRequired, false);
  assert.equal(cmd.def.permissionLevel, 0);
  const p = h.makePlayer();
  const res = cmd.cb({ sourceEntity: p });
  assert.equal(res.status, 0);
  h.flush();
  assert.equal(h.captured.player, p);
});

test('custom command from a non-player source fails cleanly', () => {
  const h = harness();
  h.registerCommands();
  const cmd = h.commands.get('skinmenu:open');
  assert.equal(cmd.cb({}).status, 1);
  assert.equal(cmd.cb({ sourceEntity: { typeId: 'minecraft:zombie' } }).status, 1);
});

test('a busy form does not throw and tells the player how to retry', async () => {
  const h = harness({ formRejects: true });
  const p = h.makePlayer();
  h.subs.chatSend[0]({ message: '!skin', sender: p, cancel: false });
  h.flush();
  await new Promise(r => setImmediate(r));
  assert.ok(h.messages.some(m => m.includes('Type !skin again')));
});

test('current look is shown in the menu body', () => {
  const h = harness();
  const p = h.makePlayer();
  p._store.set('skinmenu:saved_look', 4);
  h.subs.chatSend[0]({ message: '!skin', sender: p, cancel: false });
  h.flush();
  assert.match(h.captured.form.bodyText, /Violet Navigator/);
});

test('sneak gesture opens once per press, not every tick', () => {
  const h = harness();
  const p = h.makePlayer();
  p._items[0] = { typeId: 'skinmenu:remote' };
  h.world.getAllPlayers = () => [p];
  const tick = h.intervals[0];
  p.isSneaking = true;
  h.captured.form = undefined;
  tick(); h.flush();
  assert.ok(h.captured.form, 'first sneak tick opens');
  h.captured.form = undefined;
  tick(); h.flush();
  assert.equal(h.captured.form, undefined, 'held sneak does not reopen');
  p.isSneaking = false; tick(); h.flush();
  p.isSneaking = true; tick(); h.flush();
  assert.ok(h.captured.form, 'a fresh sneak press opens again');
});
