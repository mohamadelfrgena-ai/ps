// Bedrock Skins -- offline runtime (Behavior Pack)
// Ported from client/appearance/emote/EmoteManager.java + persistence/StateManager.java
//
// Scope of this file
// ------------------
//  * emote SLOT BINDER (6 slots, saved per player, fully offline)
//  * the emote wheel (server-ui) that fires a bound slot in one press
//  * scriptevent API for command blocks / NPCs / functions
//
// What this file can NOT do (see docs/04)
// ---------------------------------------
//  * receive a press from the JSON-UI pause panel. Bedrock has no
//    "custom UI -> script" event: the UI engine is client side and declarative,
//    the Script API is server side, and there is no binding between them.
//  * inject entries into the vanilla radial emote wheel. That wheel is built
//    from the player's own persona emotes and is not extensible by add-ons.
//  Therefore the binder is opened by things that *can* reach a script:
//    - the `bs:emote_wheel` item (right trigger / right click -> one press),
//    - /scriptevent bs:bind <emote> <1-6>,
//    - /function bs/bind/... (generated in the behavior pack),
//    - any entity/command-block event that runs one of the above.
import { world, system, Player } from "@minecraft/server";
import { ActionFormData, ModalFormData } from "@minecraft/server-ui";

/** @type {{id:string, animation:string, label:string, seconds:number}[]} */
const EMOTES = __BS_EMOTE_REGISTRY__;

const SLOT_COUNT = 6;
const STORE_KEY = "bs:emote_slots";
const PREFIX = "bs:";
const WHEEL_ITEM = "bs:emote_wheel";

const byId = new Map(EMOTES.map((emote) => [emote.id, emote]));
const byKey = new Map();
for (const emote of EMOTES) {
  byKey.set(emote.id.toLowerCase(), emote);
  byKey.set(emote.id.replace(/^bs:/, "").toLowerCase(), emote);
  byKey.set(emote.animation.toLowerCase(), emote);
}

// ---------------------------------------------------------------- persistence
/** @returns {(string|null)[]} slot index -> emote id */
function loadSlots(player) {
  const slots = new Array(SLOT_COUNT).fill(null);
  try {
    const raw = player.getDynamicProperty(STORE_KEY);
    if (typeof raw === "string" && raw.length) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        for (let i = 0; i < SLOT_COUNT; i += 1) {
          const value = parsed[i];
          if (typeof value === "string" && byId.has(value)) slots[i] = value;
        }
        return slots;
      }
    }
  } catch (error) {
    console.warn(`[bedrockskins] slot read failed: ${error}`);
  }
  // First run: same behaviour as EmoteManager.reload() -- fill 1..6 in order.
  for (let i = 0; i < Math.min(SLOT_COUNT, EMOTES.length); i += 1) slots[i] = EMOTES[i].id;
  saveSlots(player, slots);
  return slots;
}

function saveSlots(player, slots) {
  try {
    player.setDynamicProperty(STORE_KEY, JSON.stringify(slots.slice(0, SLOT_COUNT)));
  } catch (error) {
    console.warn(`[bedrockskins] slot save failed: ${error}`);
  }
}

function slotLabel(slots, index) {
  const emote = byId.get(slots[index]);
  return emote ? `${index + 1}. ${emote.label}` : `${index + 1}. (empty)`;
}

// ------------------------------------------------------------------- playback
function playEmote(player, emote) {
  if (!emote) return false;
  try {
    player.playAnimation(emote.animation, { blendOutTime: 0.15 });
    return true;
  } catch (error) {
    console.warn(`[bedrockskins] playAnimation failed (${emote.animation}): ${error}`);
  }
  try {
    player.runCommand(`playanimation @s "${emote.animation}" 0 0.15`);
    return true;
  } catch (error) {
    console.warn(`[bedrockskins] playanimation fallback failed: ${error}`);
    return false;
  }
}

function playSlot(player, index) {
  const slots = loadSlots(player);
  const emote = byId.get(slots[index]);
  if (!emote) {
    player.onScreenDisplay.setActionBar(`§7Slot ${index + 1} is empty`);
    return false;
  }
  const ok = playEmote(player, emote);
  if (ok) player.onScreenDisplay.setActionBar(`§e${emote.label} §7(slot ${index + 1})`);
  return ok;
}

// ------------------------------------------------------------------- binder
/**
 * Ask which slot the emote should be pinned to.
 * Modal form = one dropdown, works with a controller (no text input).
 */
function openBinder(player, emote) {
  const slots = loadSlots(player);
  const emoteIndex = Math.max(0, EMOTES.findIndex((entry) => entry.id === emote.id));
  const current = Math.max(0, slots.findIndex((id) => id === emote.id));

  const form = new ModalFormData()
    .title("Emote Slot Binder")
    .dropdown("Emote", EMOTES.map((entry) => entry.label), emoteIndex)
    .dropdown("Pin to slot", ["1", "2", "3", "4", "5", "6"], current)
    .toggle("Play it now", true);

  form.show(player).then((result) => {
    if (result.canceled || !result.formValues) return;
    const [emoteSelection, slotSelection, playNow] = result.formValues;
    const chosen = EMOTES[Number(emoteSelection) || 0];
    const slot = Math.max(0, Math.min(SLOT_COUNT - 1, Number(slotSelection) || 0));
    if (!chosen) return;

    const updated = loadSlots(player);
    const previousOwner = updated.findIndex((id) => id === chosen.id);
    if (previousOwner >= 0 && previousOwner !== slot) updated[previousOwner] = null;
    updated[slot] = chosen.id;
    saveSlots(player, updated);

    player.onScreenDisplay.setActionBar(`§a${chosen.label} §7-> slot §e${slot + 1}`);
    if (playNow) playEmote(player, chosen);
  }).catch((error) => console.warn(`[bedrockskins] binder failed: ${error}`));
}

// --------------------------------------------------------------------- wheel
function openWheel(player) {
  const slots = loadSlots(player);
  const form = new ActionFormData()
    .title("Emotes")
    .body("Press a slot to play it. Long-press to rebind: use the binder item.");

  for (let i = 0; i < SLOT_COUNT; i += 1) form.button(slotLabel(slots, i));
  form.button("§7Rebind a slot...");
  form.button("§7Clear this player's slots");

  form.show(player).then((result) => {
    if (result.canceled || result.selection === undefined) return;
    if (result.selection < SLOT_COUNT) {
      playSlot(player, result.selection);
      return;
    }
    if (result.selection === SLOT_COUNT) {
      openLibrary(player);
      return;
    }
    saveSlots(player, new Array(SLOT_COUNT).fill(null));
    player.onScreenDisplay.setActionBar("§7Emote slots cleared");
  }).catch((error) => console.warn(`[bedrockskins] wheel failed: ${error}`));
}

function openLibrary(player) {
  const page = EMOTES.slice(0, 32);
  const form = new ActionFormData().title("Pick an emote").body(`${EMOTES.length} emotes installed.`);
  for (const emote of page) form.button(emote.label);
  form.show(player).then((result) => {
    if (result.canceled || result.selection === undefined) return;
    const emote = page[result.selection];
    if (emote) openBinder(player, emote);
  }).catch((error) => console.warn(`[bedrockskins] library failed: ${error}`));
}

// ------------------------------------------------------------- item triggers
// `bs:emote_wheel` in the hotbar -> right trigger / right click opens the wheel
// on consoles this is the closest thing to "one button during gameplay".
world.afterEvents.itemUse.subscribe((event) => {
  const player = event.source;
  if (!(player instanceof Player) || !event.itemStack) return;
  if (event.itemStack.typeId === WHEEL_ITEM) {
    openWheel(player);
    return;
  }
  const slotMatch = /^bs:emote_slot_([1-6])$/.exec(event.itemStack.typeId);
  if (slotMatch) playSlot(player, Number(slotMatch[1]) - 1);
});

// Interacting with the script-free console entity: open the wheel instead of
// the vanilla interaction list when scripts are available.
try {
  world.afterEvents.playerInteractWithEntity.subscribe((event) => {
    if (event.target?.typeId === "bs:emote_console" && event.player instanceof Player) {
      openWheel(event.player);
    }
  });
} catch (error) {
  console.warn(`[bedrockskins] playerInteractWithEntity unavailable: ${error}`);
}

// ------------------------------------------------------------- scriptevents
function resolveEmote(token) {
  if (!token) return undefined;
  const key = String(token).toLowerCase().trim();
  return byKey.get(key) ?? byKey.get(`${PREFIX}${key}`) ?? byKey.get(`animation.bs.emote.${key}`);
}

system.afterEvents.scriptEventReceive.subscribe((event) => {
  const id = event.id.toLowerCase();
  if (!id.startsWith(PREFIX)) return;
  const action = id.slice(PREFIX.length);
  const raw = (event.message ?? "").trim();
  const args = raw.length ? raw.split(/\s+/) : [];
  const player = event.sourceEntity instanceof Player ? event.sourceEntity : undefined;

  const targets = player ? [player] : world.getAllPlayers();

  switch (action) {
    case "wheel":
      if (player) openWheel(player);
      return;
    case "bind": {
      const emote = resolveEmote(args[0]);
      if (!player) return;
      if (!emote) {
        player.onScreenDisplay.setActionBar("§cUnknown emote");
        return;
      }
      if (args[1] !== undefined) {
        // non-interactive form: /scriptevent bs:bind <emote> <1-6>
        const slot = Number(args[1]) - 1;
        if (!Number.isInteger(slot) || slot < 0 || slot >= SLOT_COUNT) {
          player.onScreenDisplay.setActionBar("§cSlot must be 1-6");
          return;
        }
        const slots = loadSlots(player);
        const previous = slots.findIndex((value) => value === emote.id);
        if (previous >= 0 && previous !== slot) slots[previous] = null;
        slots[slot] = emote.id;
        saveSlots(player, slots);
        player.onScreenDisplay.setActionBar(`§a${emote.label} §7-> slot §e${slot + 1}`);
        return;
      }
      openBinder(player, emote);
      return;
    }
    case "slot": {
      const index = Number(args[0]) - 1;
      if (!Number.isInteger(index) || index < 0 || index >= SLOT_COUNT) return;
      for (const target of targets) playSlot(target, index);
      return;
    }
    case "list":
      world.sendMessage(`Emotes: ${EMOTES.map((emote) => emote.id).join(", ")}`);
      return;
    case "stop":
      for (const target of targets) {
        try {
          target.stopAllAnimations();
        } catch (error) {
          console.warn(`[bedrockskins] ${error}`);
        }
      }
      return;
    default: {
      // bs:emote <id> -- legacy fire-and-forget form
      const emote = resolveEmote(action.split("/").pop()) ?? resolveEmote(args[0]);
      if (emote) for (const target of targets) playEmote(target, emote);
    }
  }
}, { namespaces: ["bs"] });

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (initialSpawn) loadSlots(player);
});

console.log(`[bedrockskins] offline runtime ready (${EMOTES.length} emotes, ${SLOT_COUNT} slots)`);
