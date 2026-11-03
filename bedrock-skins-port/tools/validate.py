#!/usr/bin/env python3
"""Static sanity check for everything tools/build_addon.py emits.

  python3 tools/validate.py dist/

Checks (no Minecraft required):
  * every .json parses
  * every skin pack: manifest type, skins.json -> geometry identifier exists,
    texture path exists inside the pack
  * resource pack: player.entity.json registers every animation that the
    behavior pack's functions/scripts reference
  * behavior pack: manifest dependencies, one function per emote
  * main.js: registry injected, balanced braces, every emote referenced
"""

from __future__ import annotations

import json
import os
import re
import sys

errors: list[str] = []
notes: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def note(message: str) -> None:
    notes.append(message)


def load(path: str):
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError) as exc:
        fail(f"JSON error {path}: {exc}")
        return None


def check_skin_pack(directory: str) -> int:
    skins = load(os.path.join(directory, "skins.json"))
    manifest = load(os.path.join(directory, "manifest.json"))
    if not skins or manifest is None:
        return 0
    module_types = {m.get("type") for m in manifest.get("modules", [])}
    if "skin_pack" not in module_types:
        fail(f"{directory}: manifest is not a skin_pack ({module_types})")

    geometries = load(os.path.join(directory, "geometry.json")) or {"minecraft:geometry": []}
    ids = {str((g.get("description") or {}).get("identifier", "")).lower()
           for g in geometries.get("minecraft:geometry", [])}

    entries = skins.get("skins") or []
    for entry in entries:
        geometry_id = str(entry.get("geometry", "")).lower()
        if geometry_id not in ids:
            fail(f"{directory}: skin {entry.get('localization_name')} -> unknown geometry {geometry_id}")
        texture = entry.get("texture")
        if texture and ":" not in texture and not os.path.isfile(os.path.join(directory, texture)):
            fail(f"{directory}: skin {entry.get('localization_name')} -> missing texture {texture}")
        cape = entry.get("cape")
        if cape and ":" not in cape and not os.path.isfile(os.path.join(directory, cape)):
            fail(f"{directory}: skin {entry.get('localization_name')} -> missing cape {cape}")
    return len(entries)


def check_rp(directory: str, bp_dir: str | None) -> None:
    manifest = load(os.path.join(directory, "manifest.json"))
    if manifest and {m.get("type") for m in manifest.get("modules", [])} != {"resources"}:
        fail(f"{directory}: resource manifest modules wrong")

    entity_path = os.path.join(directory, "entity", "player.entity.json")
    declared: set[str] = set()
    if os.path.isfile(entity_path):
        entity = load(entity_path) or {}
        description = ((entity.get("minecraft:client_entity") or {}).get("description") or {})
        declared = {str(v) for v in (description.get("animations") or {}).values()}
        if description.get("identifier") != "minecraft:player":
            fail("RP player.entity.json does not target minecraft:player")

    animation_files = []
    anim_dir = os.path.join(directory, "animations")
    if os.path.isdir(anim_dir):
        animation_files = [os.path.join(anim_dir, n) for n in os.listdir(anim_dir) if n.endswith(".json")]
    defined: set[str] = set()
    for path in animation_files:
        payload = load(path) or {}
        if payload.get("format_version") is None:
            fail(f"{path}: missing format_version")
        for key, value in (payload.get("animations") or {}).items():
            defined.add(key)
            if not isinstance(value, dict):
                fail(f"{path}: animation {key} is not an object")
            bones = (value.get("bones") or {})
            for bone in bones:
                if str(bone).lower() in ("hip",):
                    fail(f"{path}: {key} still animates the Java-only 'hip' bone")
    for name in declared:
        if name.startswith("animation.bs.") and name not in defined:
            fail(f"player.entity.json declares undeclared animation {name}")

    # build a behaviour-pack view of what is referenced
    if bp_dir and os.path.isdir(bp_dir):
        referenced = set()
        for root, _dirs, files in os.walk(bp_dir):
            for name in files:
                path = os.path.join(root, name)
                if name.endswith((".mcfunction", ".js")):
                    with open(path, "r", encoding="utf-8") as handle:
                        found = set(re.findall(r"animation\.bs\.[a-z0-9_]+(?:\.[a-z0-9_]+)+",
                                               handle.read()))
                        # drop template fragments such as animation.bs.emote.${key}
                        referenced |= {name for name in found if "${" not in name}
        for name in referenced:
            if name not in defined:
                fail(f"BP references undefined animation {name}")
            if name not in declared:
                fail(f"BP references animation not registered on the player: {name}")
        note(f"cross-checked {len(referenced)} BP-referenced animations")


def _strip_jsonc(text: str) -> str:
    out, i, n, in_string = [], 0, len(text), False
    while i < n:
        char = text[i]
        if char == '"' and (i == 0 or text[i - 1] != "\\"):
            in_string = not in_string
        if not in_string and char == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        out.append(char)
        i += 1
    return "".join(out)


def check_ui(rp_dir: str, bp_dir: str | None) -> None:
    """JSON-UI overlay: panel exists, one button per emote function."""
    path = os.path.join(rp_dir, "ui", "pause_screen.json")
    if not os.path.isfile(path):
        note("no JSON-UI overlay (built with --no-ui)")
        return
    try:
        with open(path, "r", encoding="utf-8") as handle:
            screen = json.loads(_strip_jsonc(handle.read()))
    except ValueError as exc:
        fail(f"ui/pause_screen.json does not parse: {exc}")
        return

    if screen.get("namespace") != "pause":
        fail("ui/pause_screen.json must keep the `pause` namespace")
    panel = screen.get("bs_emote_panel")
    if not isinstance(panel, dict):
        fail("ui/pause_screen.json: bs_emote_panel missing")
        return
    if not any("bs_emote_panel@pause.bs_emote_panel" in control
               for control in screen.get("pause_screen_content", {}).get("controls", [])):
        fail("ui/pause_screen.json: panel was not injected into pause_screen_content")

    buttons = []
    for column in panel["controls"][1]["bs_emote_grid"]["controls"]:
        for cell in list(column.values())[0]["controls"]:
            buttons.append(list(cell.values())[0])
    if not buttons:
        fail("ui/pause_screen.json: no emote buttons")
        return
    for button in buttons:
        if not button.get("$pressed_button_name", "").startswith("button.bs_emote_"):
            fail(f"UI button without a bs action id: {button.get('$button_text')}")
        if not button.get("button_mappings"):
            fail(f"UI button without button_mappings: {button.get('$button_text')}")

    functions = None
    if bp_dir and os.path.isdir(os.path.join(bp_dir, "functions", "bs", "emote")):
        functions = len(os.listdir(os.path.join(bp_dir, "functions", "bs", "emote")))
    if functions is not None and len(buttons) != functions:
        fail(f"UI has {len(buttons)} buttons but {functions} emote functions")
    note(f"JSON-UI: {len(buttons)} controller-mapped emote buttons")


def check_entities(bp_dir: str) -> None:
    entity_dir = os.path.join(bp_dir, "entities")
    if not os.path.isdir(entity_dir):
        return
    for name in sorted(os.listdir(entity_dir)):
        if not name.endswith(".json"):
            continue
        path = os.path.join(entity_dir, name)
        payload = load(path)
        if payload is None:
            continue
        entity = payload.get("minecraft:entity") or {}
        interactions = ((entity.get("components") or {})
                        .get("minecraft:interact", {}).get("interactions", []))
        events = entity.get("events") or {}
        for entry in interactions:
            event = (entry.get("on_interact") or {}).get("event")
            if event and event not in events:
                fail(f"{name}: interaction {event} has no event definition")
        for event, body in events.items():
            command = (body or {}).get("run_command", {}).get("command")
            if not command:
                fail(f"{name}: event {event} has no run_command")
                continue
            for line in command:
                if "function bs/emote/" not in line:
                    fail(f"{name}: event {event} does not call an emote function: {line}")
        note(f"{name}: {len(interactions)} script-free interactions")


def check_bp(directory: str) -> None:
    manifest = load(os.path.join(directory, "manifest.json"))
    if manifest is None:
        return
    types = [m.get("type") for m in manifest.get("modules", [])]
    if "data" not in types:
        fail(f"{directory}: no data module")
    if "script" not in types:
        fail(f"{directory}: no script module")
        return
    entry = next(m.get("entry") for m in manifest["modules"] if m.get("type") == "script")
    script_path = os.path.join(directory, entry)
    if not os.path.isfile(script_path):
        fail(f"{directory}: script entry {entry} missing")
        return
    with open(script_path, "r", encoding="utf-8") as handle:
        source = handle.read()
    if "__BS_EMOTE_REGISTRY__" in source:
        fail("main.js registry placeholder was never replaced")
    if source.count("{") != source.count("}"):
        fail("main.js has unbalanced braces")
    for module in ("@minecraft/server", "@minecraft/server-ui"):
        if module not in source:
            fail(f"main.js does not import {module}")
    for token in ("ModalFormData", "itemUse", "bs:emote_slots", "playAnimation"):
        if token not in source:
            fail(f"main.js is missing the '{token}' wiring")

    items_dir = os.path.join(directory, "items")
    if os.path.isdir(items_dir):
        identifiers = set()
        for name in os.listdir(items_dir):
            payload = load(os.path.join(items_dir, name)) or {}
            identifier = (((payload.get("minecraft:item") or {}).get("description") or {})
                          .get("identifier"))
            if identifier:
                identifiers.add(identifier)
        if "bs:emote_wheel" not in identifiers:
            fail("behavior pack has no bs:emote_wheel item for the binder")
        missing = [f"bs_emote_slot_{i}" for i in range(1, 7)
                   if f"bs:emote_slot_{i}" not in identifiers]
        if missing:
            note(f"slot items missing: {', '.join(missing)}")
        else:
            note("binder item + 6 one-press slot items present")


def main(argv: list[str]) -> int:
    root = argv[1] if len(argv) > 1 else "dist"
    if not os.path.isdir(root):
        print(f"not a directory: {root}")
        return 2

    rp_dir = os.path.join(root, "BedrockSkins_RP")
    bp_dir = os.path.join(root, "BedrockSkins_BP")

    json_files = 0
    for current, _dirs, files in os.walk(root):
        for name in files:
            if name.endswith(".json"):
                json_files += 1
                load(os.path.join(current, name))

    skins_root = os.path.join(root, "skins")
    total_skins = 0
    packs = 0
    if os.path.isdir(skins_root):
        for name in sorted(os.listdir(skins_root)):
            directory = os.path.join(skins_root, name)
            if os.path.isdir(directory):
                packs += 1
                total_skins += check_skin_pack(directory)

    if os.path.isdir(rp_dir):
        check_rp(rp_dir, bp_dir)
        check_ui(rp_dir, bp_dir)
    elif os.path.isdir(bp_dir):
        fail("missing BedrockSkins_RP")
    else:
        note("behavior/resource packs not built (skins-only run)")

    if os.path.isdir(bp_dir):
        check_bp(bp_dir)
        check_entities(bp_dir)

    print(f"json files parsed : {json_files}")
    print(f"skin packs        : {packs} ({total_skins} skins)")
    for line in notes:
        print(f"note              : {line}")
    if errors:
        print(f"\n{len(errors)} problem(s):")
        for line in errors[:60]:
            print(f"  x {line}")
        if len(errors) > 60:
            print(f"  ... and {len(errors) - 60} more")
        return 1
    print("\nOK -- add-on structure is valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
