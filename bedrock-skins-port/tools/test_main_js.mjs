// Headless smoke test for the generated Behavior Pack runtime.
//
//   node tools/test_main_js.mjs dist/BedrockSkins_BP/scripts/main.js
//
// Mocks @minecraft/server + @minecraft/server-ui, loads the *built* main.js
// (imports stripped, stubs injected) and drives it the same way the game
// would: scriptevent -> binder form -> persisted slot -> wheel -> playback.
import fs from "node:fs";
import vm from "node:vm";

const file = process.argv[2] ?? "dist/BedrockSkins_BP/scripts/main.js";
const source = fs.readFileSync(file, "utf8").replace(/^import .*$/gm, "");

const calls = { played: [], actionBars: [], commands: [], forms: [], messages: [] };
const handlers = { script: [], item: [], interact: [], spawn: [] };

function makePlayer(props = {}) {
  const store = new Map(Object.entries(props));
  return {
    typeId: "minecraft:player",
    name: "Tester",
    getDynamicProperty: (key) => store.get(key),
    setDynamicProperty: (key, value) => store.set(key, value),
    onScreenDisplay: { setActionBar: (text) => calls.actionBars.push(text) },
    playAnimation: (name) => calls.played.push(name),
    runCommand: (cmd) => calls.commands.push(cmd),
    stopAllAnimations: () => {},
    __store: store,
  };
}

const world = {
  afterEvents: {
    itemUse: { subscribe: (fn) => handlers.item.push(fn) },
    playerInteractWithEntity: { subscribe: (fn) => handlers.interact.push(fn) },
    playerSpawn: { subscribe: (fn) => handlers.spawn.push(fn) },
  },
  getAllPlayers: () => [player],
  sendMessage: (text) => calls.messages.push(text),
};

const system = {
  afterEvents: { scriptEventReceive: { subscribe: (fn) => handlers.script.push(fn) } },
};

class ActionFormData {
  constructor() { this.buttons = []; this._selection = 0; }
  title() { return this; }
  body() { return this; }
  button(text) { this.buttons.push(text); return this; }
  show() {
    calls.forms.push({ type: "action", buttons: [...this.buttons] });
    return Promise.resolve({ canceled: false, selection: this._selection });
  }
}

class ModalFormData {
  constructor() { this._values = []; }
  title() { return this; }
  dropdown(label, options, index) {
    calls.forms.push({ type: "modal", dropdown: label, options, index });
    this._values.push(index ?? 0);
    return this;
  }
  toggle(label, value) {
    calls.forms.push({ type: "modal", toggle: label, value });
    this._values.push(value ?? false);
    return this;
  }
  show() { return Promise.resolve({ canceled: false, formValues: [...this._values] }); }
}

let player = makePlayer();
const sandbox = {
  world, system, ActionFormData, ModalFormData, console,
  ItemStack: class {},
  // main.js does `event.sourceEntity instanceof Player`
  Player: class Player { constructor() { return player; } },
};
sandbox.Player = player.constructor;
// instanceof must succeed for our fake player
Object.setPrototypeOf(player, sandbox.Player.prototype);

const context = vm.createContext(sandbox);
vm.runInContext(source, context, { filename: file });

const results = [];
function check(name, condition, detail = "") {
  results.push({ name, ok: !!condition, detail });
}

// 1. the script subscribed to everything we need
check("subscribes to scriptevent", handlers.script.length === 1);
check("subscribes to itemUse", handlers.item.length === 1);

// 2. first spawn fills slots 1..6 from the registry
handlers.spawn[0]({ player, initialSpawn: true });
const initial = JSON.parse(player.getDynamicProperty("bs:emote_slots"));
check("default slots filled", Array.isArray(initial) && initial.length === 6 && initial[0],
  JSON.stringify(initial?.slice(0, 2)));

// 3. /scriptevent bs:bind overhead_wave 3 -> deterministic slot write
handlers.script[0]({ id: "bs:bind", message: "overhead_wave 3", sourceEntity: player });
const bound = JSON.parse(player.getDynamicProperty("bs:emote_slots"));
check("bind writes slot 3", bound[2] === "bs:overhead_wave", `slot3=${bound[2]}`);
check("emote removed from its old slot", bound.filter((id) => id === "bs:overhead_wave").length === 1,
  JSON.stringify(bound));

// 4. /scriptevent bs:bind <emote> without a slot opens the modal binder
calls.forms.length = 0;
handlers.script[0]({ id: "bs:bind", message: "faceplant", sourceEntity: player });
await Promise.resolve();
check("modal binder opened", calls.forms.some((form) => form.type === "modal"),
  calls.forms.map((form) => form.type).join(","));

// 5. the wheel fires the bound animation
calls.played.length = 0;
handlers.script[0]({ id: "bs:slot", message: "3", sourceEntity: player });
check("slot 3 plays its animation", calls.played[0] === "animation.bs.emote.overhead_wave",
  calls.played[0]);
check("action bar confirms", calls.actionBars.some((text) => text.includes("overhead_wave") || text.includes("Wave")),
  calls.actionBars.at(-1));

// 6. right trigger on the binder item opens the wheel
calls.forms.length = 0;
handlers.item[0]({ source: player, itemStack: { typeId: "bs:emote_wheel" } });
await Promise.resolve();
check("item opens the 6-slot wheel",
  calls.forms.some((form) => form.type === "action" && form.buttons.length >= 6),
  calls.forms.at(-1)?.buttons?.join(" | "));

// 7. a per-slot item plays immediately (one press, no menus)
calls.played.length = 0;
calls.forms.length = 0;
handlers.item[0]({ source: player, itemStack: { typeId: "bs:emote_slot_1" } });
check("slot item fires without a menu", calls.played.length === 1 && calls.forms.length === 0,
  calls.played[0]);

// 8. unknown emote is rejected, not crashed
calls.actionBars.length = 0;
handlers.script[0]({ id: "bs:bind", message: "nope 2", sourceEntity: player });
const afterBad = JSON.parse(player.getDynamicProperty("bs:emote_slots"));
check("unknown emote rejected", afterBad[1] !== "nope" && calls.actionBars.some((t) => t.includes("Unknown")),
  calls.actionBars.at(-1));

let failed = 0;
for (const result of results) {
  if (!result.ok) failed += 1;
  console.log(`${result.ok ? "PASS" : "FAIL"}  ${result.name}${result.detail ? `  -> ${result.detail}` : ""}`);
}
console.log(`\n${results.length - failed}/${results.length} checks passed`);
process.exit(failed ? 1 : 0);
