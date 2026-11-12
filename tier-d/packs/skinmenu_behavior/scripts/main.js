// Skin Menu — in-world look selector.
// Only long-stable exports are imported, so a newer enum cannot break the whole module.
import { world, system, ItemStack } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";

const PROP = "skinmenu:look";
const REMOTE = "skinmenu:remote";
const LOOKS = [{"key":"azure","label":"§bAzure Mechanic","name":"Azure Mechanic"},{"key":"ember","label":"§6Ember Explorer","name":"Ember Explorer"},{"key":"moss","label":"§aMoss Gardener","name":"Moss Gardener"},{"key":"violet","label":"§dViolet Navigator","name":"Violet Navigator"}];
const TRIGGERS = ["!skin", "!skins", "!look"];
const SAVE_KEY = "skinmenu:saved_look";

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
    player.triggerEvent("skinmenu:set_look_" + i);
    ok = true;
  } catch { /* event channel unavailable */ }

  if (!announce) return;
  if (ok) {
    player.sendMessage("\u00a7aLook: \u00a7f" + labelOf(i) + "\u00a7a. Press F5 to see yourself.");
  } else {
    player.sendMessage("\u00a7cThis client refused the change. Both packs must be active on the world.");
  }
}

function openMenu(player) {
  const active = readLook(player);
  const form = new ActionFormData()
    .title("Skin Menu")
    .body("Choose a look for your character in this world.\nCurrent: " + labelOf(active));

  form.button("\u00a7fUse my own skin", "textures/ui/icon_steve");
  for (const look of LOOKS) form.button(look.label, "textures/skinmenu/icons/" + look.key);

  form.show(player).then((res) => {
    if (res.canceled || res.selection === undefined) return;
    applyLook(player, res.selection, true);
  }).catch(() => {
    player.sendMessage("\u00a7eMenu busy. Type !skin again in a moment.");
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
      player.sendMessage("\u00a7bSkin Menu: type \u00a7f!skin\u00a7b in chat to open the menu.");
    }
  }, 20);
});

// Trigger 4 — a real slash command where supported. The entire subscription is
// guarded: on clients without this API the property access itself would throw at
// module load and kill every other trigger. Numeric enum values avoid imports.
try {
  const startup = system.beforeEvents && system.beforeEvents.startup;
  if (startup && typeof startup.subscribe === "function") startup.subscribe((ev) => {
    try {
      ev.customCommandRegistry.registerCommand({
        name: "skinmenu:open",
        description: "Open the in-world skin menu",
        permissionLevel: 0,
        cheatsRequired: false
      }, (origin) => {
        const player = origin.sourceEntity;
        if (!player || player.typeId !== "minecraft:player") return { status: 1, message: "Run this as a player." };
        system.run(() => openMenu(player));
        return { status: 0 };
      });
    } catch { /* command API present but registration refused */ }
  });
} catch { /* no command API on this client: chat, item and sneak still work */ }
