// Minimal diagnostic. Deliberately uses only long-stable API surface.
import { world, system } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";

function report(player, text) {
  try { player.sendMessage(text); } catch { /* ignore */ }
}

function openProbe(player) {
  const form = new ActionFormData()
    .title("PROBE OK")
    .body("Scripting and forms work on this client.\nPick any button to continue the diagnosis.");
  form.button("Test 1: chat reply");
  form.button("Test 2: save a value");
  form.button("Test 3: entity property");
  form.button("Test 4: entity event");

  form.show(player).then((res) => {
    if (res.canceled || res.selection === undefined) return;
    if (res.selection === 0) {
      report(player, "\u00a7aTEST 1 PASS: chat reply works.");
    } else if (res.selection === 1) {
      try {
        player.setDynamicProperty("probe:value", 42);
        const back = player.getDynamicProperty("probe:value");
        report(player, back === 42 ? "\u00a7aTEST 2 PASS: saved value read back." : "\u00a7eTEST 2 ODD: got " + back);
      } catch (e) { report(player, "\u00a7cTEST 2 FAIL: " + e); }
    } else if (res.selection === 2) {
      try {
        player.setProperty("skinmenu:look", 1);
        report(player, "\u00a7aTEST 3 PASS: player accepted the entity property.");
      } catch (e) { report(player, "\u00a7eTEST 3 FAIL (expected without the full pack): " + e); }
    } else {
      try {
        player.triggerEvent("skinmenu:set_look_1");
        report(player, "\u00a7aTEST 4 PASS: player accepted the entity event.");
      } catch (e) { report(player, "\u00a7eTEST 4 FAIL (expected without the full pack): " + e); }
    }
  }).catch((e) => report(player, "\u00a7eForm busy: " + e));
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
    report(ev.player, "\u00a7bPROBE loaded. Type \u00a7f!skin\u00a7b in chat to test.");
  }, 40);
});
