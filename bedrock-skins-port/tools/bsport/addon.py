"""Assemble the Bedrock Behavior Pack + Resource Pack.

What ends up in the add-on (everything works with the network cable unplugged):

  Resource pack                          Behavior pack
  ├─ entity/player.entity.json   (1)     ├─ functions/bs/emote/*.mcfunction (4)
  ├─ animations/bs_emotes...json (2)     ├─ scripts/main.js                 (5)
  ├─ attachables/bs_cape_*.json  (3)     ├─ items/bs_cape_*.json            (3)
  ├─ models/entity/bs_cape.geo   (3)     └─ texts/
  └─ texts/

  (1) vanilla player definition + our animations registered
  (2) persona emotes retargeted to the player rig
  (3) optional "dynamic capes" (wearable item + attachable) for capes that are
      not baked into a skin entry
  (4) console-friendly emote triggers, one function per emote
  (5) server-ui emote wheel + scriptevent bridge, fully offline
"""

from __future__ import annotations

import json
import os
import shutil
import zipfile

from . import geometry as geo
from . import ui as ui_overlay
from .emotes import (Emote, build_animation_file, register_in_player_entity,
                     registry_js, summary, write_functions)
from .image import Image, write_png
from .util import (ensure_dir, log, read_json, rel, slug, stable_uuid, warn,
                   write_json)

DEFAULT_BP = "pack.bedrockskins.bp"
DEFAULT_RP = "pack.bedrockskins.rp"


# --------------------------------------------------------------------- icons
def _version_tuple(value) -> tuple[int, ...]:
    parts = []
    for chunk in str(value).split("."):
        try:
            parts.append(int(chunk))
        except ValueError:
            parts.append(0)
    return tuple(parts)


def make_icon(path: str, accent=(58, 173, 90)) -> None:
    """Deterministic 128x128 pack icon (a pixel head, no binary blobs in git)."""
    size = 128
    img = Image(size, size, (24, 26, 32, 255))
    # border
    for i in range(size):
        for j in (0, 1, size - 2, size - 1):
            img.put(i, j, accent + (255,))
            img.put(j, i, accent + (255,))
    # 8x8 head grid, upscaled 12x
    head = [
        "..3333..",
        ".333333.",
        "33333333",
        "33..33..",
        "3.3.3.33",
        "33333333",
        ".3....3.",
        "..3333..",
    ]
    palette = {"3": (89, 60, 41, 255), ".": (192, 145, 110, 255)}
    cell = 12
    offset = (size - 8 * cell) // 2
    for y, row in enumerate(head):
        for x, ch in enumerate(row):
            color = palette.get(ch, palette["."])
            for dy in range(cell):
                for dx in range(cell):
                    img.put(offset + x * cell + dx, offset - 8 + y * cell + dy, color)
    write_png(path, img)


# ------------------------------------------------------------------- helpers
def manifest(name: str, description: str, module_type: str, uuid_key: str,
             min_engine: tuple[int, int, int], *, script_entry: str | None = None,
             dependencies: list[dict] | None = None) -> dict:
    modules = [{
        "type": module_type,
        "uuid": stable_uuid(uuid_key, "module"),
        "version": [1, 0, 0],
    }]
    if module_type == "data" and script_entry:
        modules.append({
            "type": "script",
            "language": "javascript",
            "uuid": stable_uuid(uuid_key, "script"),
            "version": [1, 0, 0],
            "entry": script_entry,
        })
    out = {
        "format_version": 2,
        "header": {
            "name": name,
            "description": description,
            "uuid": stable_uuid(uuid_key, "header"),
            "version": [1, 0, 0],
            "min_engine_version": list(min_engine),
        },
        "modules": modules,
    }
    if dependencies:
        out["dependencies"] = dependencies
    return out


def zip_dir(source: str, target: str) -> None:
    ensure_dir(os.path.dirname(target) or ".")
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for root, _dirs, files in os.walk(source):
            for name in sorted(files):
                full = os.path.join(root, name)
                archive.write(full, rel(os.path.relpath(full, source)))
    log(f"wrote {target} ({os.path.getsize(target) // 1024} KB)")


# ------------------------------------------------------------------------ RP
CAPE_GEOMETRY = {
    "description": {
        "identifier": "geometry.bs.cape",
        "texture_width": 64,
        "texture_height": 32,
        "visible_bounds_width": 2,
        "visible_bounds_height": 3,
        "visible_bounds_offset": [0, 1, 0],
    },
    "bones": [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 24, 0]},
        {"name": "cape", "parent": "body", "pivot": [0, 24, 3],
         "cubes": [{"origin": [-5, 8, 3], "size": [10, 16, 1], "uv": [0, 0]}]},
    ],
}


CAPE_ANIMATIONS = {
    # gentle idle sway; keeps the vanilla cape silhouette alive without the
    # vanilla "animation.player.cape" (which only drives the built-in cape bone)
    "animation.bs.cape_sway": {
        "loop": True,
        "animation_length": 2.4,
        "bones": {
            "cape": {
                "rotation": {
                    "0.0": [2.0, 0.0, 0.0],
                    "1.2": [-1.0, 0.0, 0.0],
                    "2.4": [2.0, 0.0, 0.0],
                }
            }
        },
    },
    "animation.bs.cape_move": {
        "loop": True,
        "bones": {
            "cape": {
                "rotation": [0.0, 0.0, "math.clamp(query.modified_move_speed * 26.0, 0.0, 26.0)"],
            }
        },
    },
}


def build_rp(rp_dir: str, *, emotes: list[Emote], dressing_room: dict[str, dict] | None,
             template_dir: str, options: dict, capes: list[tuple[str, str]]) -> dict:
    ensure_dir(rp_dir)
    name = options.get("name", "Bedrock Skins (Offline)")
    description = options.get("description", "Skins, capes, cosmetics and emotes -- 100% offline.")
    write_json(os.path.join(rp_dir, "manifest.json"), manifest(
        f"{name} RP", description, "resources", DEFAULT_RP,
        options.get("min_engine", (1, 21, 0)),
    ))
    make_icon(os.path.join(rp_dir, "pack_icon.png"))

    # 1. animations
    animations_dir = ensure_dir(os.path.join(rp_dir, "animations"))
    animation_payload = build_animation_file(emotes, dressing_room)
    animation_payload["animations"].update(CAPE_ANIMATIONS)
    write_json(os.path.join(animations_dir, "bs_emotes.animation.json"), animation_payload)

    # 2. player entity (only when we actually have emotes to register)
    if emotes:
        template_path = os.path.join(template_dir, "entity", "player.entity.json")
        template = read_json(template_path)
        target = options.get("entity_format_version")
        if target and _version_tuple(template.get("format_version", "0")) > _version_tuple(target):
            # A client-entity definition newer than the target game is ignored
            # (or breaks the player render) on older builds -- pin it back.
            warn(f"player.entity.json format_version {template.get('format_version')} "
                 f"-> {target} (target client)")
            template["format_version"] = target
        write_json(os.path.join(ensure_dir(os.path.join(rp_dir, "entity")), "player.entity.json"),
                   register_in_player_entity(template, emotes, dressing_room))

    # 3. optional dynamic capes (item + attachable pair)
    stats = {"attachments": 0}
    icon_map: dict[str, str] = {}
    if capes:
        attach_dir = ensure_dir(os.path.join(rp_dir, "attachables"))
        model_dir = ensure_dir(os.path.join(rp_dir, "models", "entity"))
        tex_dir = ensure_dir(os.path.join(rp_dir, "textures", "bs", "capes"))
        write_json(os.path.join(model_dir, "bs_cape.geo.json"),
                   geo.wrap([CAPE_GEOMETRY]))
        for key, texture_path in capes:
            if not texture_path or not os.path.isfile(texture_path):
                continue
            tex_name = f"textures/bs/capes/{key}.png"
            shutil.copyfile(texture_path, os.path.join(rp_dir, tex_name))
            write_json(os.path.join(attach_dir, f"bs_cape_{key}.attachable.json"), {
                "format_version": "1.10.0",
                "minecraft:attachable": {
                    "description": {
                        "identifier": f"bs:cape_{key}",
                        "materials": {"default": "entity_alphatest", "enchanted": "entity_alphatest_glint"},
                        "textures": {"default": tex_name, "enchanted": "textures/misc/enchanted_item_glint"},
                        "geometry": {"default": "geometry.bs.cape"},
                        "animations": {"cape_sway": "animation.bs.cape_sway", "cape_move": "animation.bs.cape_move"},
                        "scripts": {"animate": [{"cape_move": "query.modified_move_speed"}, "cape_sway"]},
                        "render_controllers": ["controller.render.armor"],
                    }
                }
            })
            stats["attachments"] += 1
            icon_map[f"bs_cape_{key}"] = tex_name

    if options.get("ui_overlay", True) and emotes:
        ui_overlay.inject_pause_screen(
            os.path.join(template_dir, "ui", "pause_screen.vanilla.json"),
            emotes,
            os.path.join(ensure_dir(os.path.join(rp_dir, "ui")), "pause_screen.json"),
        )
        stats["ui_buttons"] = min(len(emotes), ui_overlay.COLUMNS * ui_overlay.ROWS)

    # emote wheel item icon (procedural, no binary asset in git)
    item_tex_dir = ensure_dir(os.path.join(rp_dir, "textures", "items"))
    make_wheel_icon(os.path.join(item_tex_dir, "bs_emote_wheel.png"))
    icon_map["bs_emote_wheel"] = "textures/items/bs_emote_wheel"

    if icon_map:
        write_json(os.path.join(ensure_dir(os.path.join(rp_dir, "textures")), "item_texture.json"), {
            "resource_pack_name": "bedrockskins",
            "texture_name": "atlas.items",
            "texture_data": {key: {"textures": value} for key, value in icon_map.items()},
        })

    # texts
    texts_dir = ensure_dir(os.path.join(rp_dir, "texts"))
    with open(os.path.join(texts_dir, "en_US.lang"), "w", encoding="utf-8", newline="\n") as handle:
        handle.write("pack.name=Bedrock Skins Offline\n")
        handle.write("pack.description=Skins, capes, cosmetics and emotes without internet.\n")
    with open(os.path.join(texts_dir, "ar_SA.lang"), "w", encoding="utf-8", newline="\n") as handle:
        handle.write("pack.name=بيدروك سكنز أوفلاين\n")
        handle.write("pack.description=سكنات وأجنحة وملابس وحركات بدون إنترنت.\n")
    write_json(os.path.join(texts_dir, "languages.json"), ["en_US", "ar_SA"])
    stats["emotes"] = len(emotes)
    return stats


# ------------------------------------------------------------------------ BP
def build_bp(bp_dir: str, *, emotes: list[Emote], template_dir: str, options: dict,
             capes: list[tuple[str, str]] | None = None) -> dict:
    ensure_dir(bp_dir)
    name = options.get("name", "Bedrock Skins (Offline)")
    description = options.get("description", "Skins, capes, cosmetics and emotes -- 100% offline.")
    server_version = options.get("server_module", "1.13.0")
    ui_version = options.get("server_ui_module", "1.2.0")

    write_json(os.path.join(bp_dir, "manifest.json"), manifest(
        f"{name} BP", description, "data", DEFAULT_BP,
        options.get("min_engine", (1, 21, 0)),
        script_entry="scripts/main.js",
        dependencies=[
            {"module_name": "@minecraft/server", "version": server_version},
            {"module_name": "@minecraft/server-ui", "version": ui_version},
        ],
    ))
    make_icon(os.path.join(bp_dir, "pack_icon.png"))

    written = write_functions(bp_dir, emotes)

    scripts_dir = ensure_dir(os.path.join(bp_dir, "scripts"))
    template = open(os.path.join(template_dir, "scripts", "main.js"), "r", encoding="utf-8").read()
    registry = registry_js(emotes).split("= [", 1)[1].rsplit("];", 1)[0]
    rows = [line.strip().rstrip(",") for line in registry.strip().splitlines() if line.strip()]
    body = "[\n" + ",\n".join("  " + row for row in rows) + "\n]"
    template = template.replace("__BS_EMOTE_REGISTRY__", body)
    with open(os.path.join(scripts_dir, "main.js"), "w", encoding="utf-8", newline="\n") as handle:
        handle.write(template)

    stats = {"emotes": len(emotes), "functions": len(written), "items": 0,
             "interactions": build_emote_console(bp_dir, emotes)}
    stats["slot_items"] = build_wheel_items(bp_dir)
    if capes:
        item_dir = ensure_dir(os.path.join(bp_dir, "items"))
        for key, _texture in capes:
            write_json(os.path.join(item_dir, f"bs_cape_{key}.item.json"), {
                "format_version": "1.20.50",
                "minecraft:item": {
                    "description": {
                        "identifier": f"bs:cape_{key}",
                        "menu_category": {"category": "equipment", "group": "itemGroup.name.cape"},
                    },
                    "components": {
                        "minecraft:icon": {"texture": f"bs_cape_{key}"},
                        "minecraft:max_stack_size": 1,
                        "minecraft:wearable": {"slot": "slot.armor.chest", "protection": 0},
                    },
                },
            })
            stats["items"] += 1

    texts_dir = ensure_dir(os.path.join(bp_dir, "texts"))
    with open(os.path.join(texts_dir, "en_US.lang"), "w", encoding="utf-8", newline="\n") as handle:
        handle.write("pack.name=Bedrock Skins Offline\n")
        handle.write("pack.description=Skins, capes, cosmetics and emotes without internet.\n")
        handle.write("## Emote commands\n")
        handle.write("## /scriptevent bs:wheel\n## /function bs/emotes\n")
    write_json(os.path.join(texts_dir, "languages.json"), ["en_US"])
    return stats


def build_emote_console(bp_dir: str, emotes: list[Emote]) -> int:
    """`bs:emote_console` -- run any emote without a single line of scripting.

    Bedrock behavior entities can run commands from an entity *event*, and
    `minecraft:interact` turns an interaction into such an event.  Pressing the
    interact button on the console opens one entry per emote (controller
    friendly list), and the chosen entry runs:

        execute as @p[distance=..8] run function bs/emote/<id>

    That is the whole trick: no GameTest, no JS, works on a modified console.
    """
    if not emotes:
        return 0
    interactions = []
    events = {}
    for emote in emotes:
        key = f"bs:play_{slug(emote.id)}"
        interactions.append({
            "on_interact": {
                "filters": {"test": "is_family", "subject": "other", "value": "player"},
                "event": key,
                "target": "self",
            },
            "interact_text": f"action.interact.bs_{slug(emote.id)}",
            "sound_event": "random.orb",
            "cooldown": 0.1,
            "use_item": False,
        })
        events[key] = {
            "run_command": {
                "command": [f"execute as @p[distance=..8] run function bs/emote/{slug(emote.id)}"],
                "target": "self",
            }
        }

    write_json(os.path.join(ensure_dir(os.path.join(bp_dir, "entities")),
                            "bs_emote_console.behavior.json"), {
        "format_version": "1.16.0",
        "minecraft:entity": {
            "description": {
                "identifier": "bs:emote_console",
                "is_spawnable": True,
                "is_summonable": True,
                "is_experimental": False,
            },
            "component_groups": {},
            "components": {
                "minecraft:type_family": {"family": ["bs_emote_console", "inanimate"]},
                "minecraft:collision_box": {"width": 0.6, "height": 0.9},
                "minecraft:health": {"value": 20, "max": 20},
                "minecraft:physics": {},
                "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
                "minecraft:damage_sensor": {
                    "triggers": [{"cause": "all", "deals_damage": False}]
                },
                "minecraft:knockback_resistance": {"value": 100},
                "minecraft:interact": {"interactions": interactions},
            },
            "events": events,
        },
    })

    ensure_dir(os.path.join(bp_dir, "functions", "bs"))
    with open(os.path.join(bp_dir, "functions", "bs", "summon_console.mcfunction"),
              "w", encoding="utf-8", newline="\n") as handle:
        handle.write("# Place the script-free emote console\n")
        handle.write("summon bs:emote_console ~ ~ ~\n")

    ensure_dir(os.path.join(bp_dir, "texts"))
    lang_lines = ["## Emote console interaction labels"]
    lang_lines += [f"action.interact.bs_{slug(emote.id)}={emote.display}" for emote in emotes]
    with open(os.path.join(bp_dir, "texts", "en_US.lang"), "a", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lang_lines) + "\n")
    return len(interactions)


def make_wheel_icon(path: str, accent=(58, 173, 90)) -> None:
    """16x16 radial-wheel glyph for the binder item."""
    size = 16
    img = Image(size, size, (0, 0, 0, 0))
    center = 7.5
    for y in range(size):
        for x in range(size):
            dx, dy = x - center, y - center
            radius = (dx * dx + dy * dy) ** 0.5
            if 5.4 < radius <= 7.0:
                img.put(x, y, accent + (255,))
            elif radius <= 2.2:
                img.put(x, y, (240, 240, 240, 255))
            elif radius <= 3.4:
                img.put(x, y, accent + (255,))
    # six ticks, one per slot
    import math
    for index in range(6):
        angle = math.radians(-90 + index * 60)
        for step in range(3):
            radius = 3.9 + step * 0.9
            x = int(round(center + math.cos(angle) * radius))
            y = int(round(center + math.sin(angle) * radius))
            img.put(x, y, (255, 214, 92, 255))
    write_png(path, img)


def build_wheel_items(bp_dir: str) -> int:
    """`bs:emote_wheel` (binder) + `bs:emote_slot_1..6` (true one-press).

    Scripts listen on `world.afterEvents.itemUse`, so pressing right trigger /
    right click with one of these items in hand is a single console input --
    the closest legal equivalent to the vanilla radial emote wheel.
    """
    items_dir = ensure_dir(os.path.join(bp_dir, "items"))
    written = 0
    definitions = [("bs:emote_wheel", "wheel")] + [
        (f"bs:emote_slot_{index}", f"slot {index}") for index in range(1, 7)
    ]
    for identifier, _label in definitions:
        short = identifier.split(":")[1]
        write_json(os.path.join(items_dir, f"{short}.item.json"), {
            "format_version": "1.20.50",
            "minecraft:item": {
                "description": {
                    "identifier": identifier,
                    "menu_category": {"category": "equipment"},
                },
                "components": {
                    "minecraft:icon": {"texture": "bs_emote_wheel"},
                    "minecraft:max_stack_size": 1,
                    "minecraft:hand_equipped": False,
                },
            },
        })
        written += 1

    functions_dir = ensure_dir(os.path.join(bp_dir, "functions", "bs"))
    with open(os.path.join(functions_dir, "give_wheel.mcfunction"), "w",
              encoding="utf-8", newline="\n") as handle:
        handle.write("# Emote binder item (right trigger / right click)\n")
        handle.write("give @s bs:emote_wheel 1\n")
    with open(os.path.join(functions_dir, "give_slots.mcfunction"), "w",
              encoding="utf-8", newline="\n") as handle:
        handle.write("# One item per slot = one press per emote\n")
        for index in range(1, 7):
            handle.write(f"give @s bs:emote_slot_{index} 1\n")

    with open(os.path.join(bp_dir, "texts", "en_US.lang"), "a",
              encoding="utf-8", newline="\n") as handle:
        handle.write("\n## Emote binder items\n")
        handle.write("item.bs:emote_wheel=Emote Binder\n")
        for index in range(1, 7):
            handle.write(f"item.bs:emote_slot_{index}=Emote Slot {index}\n")
    return written


def write_skin_pack_manifest(pack_dir: str, name: str, description: str, uuid_key: str,
                             icon_source: str | None = None) -> None:
    write_json(os.path.join(pack_dir, "manifest.json"), {
        "format_version": 2,
        "header": {
            "name": name,
            "description": description,
            "uuid": stable_uuid(uuid_key, "header"),
            "version": [1, 0, 0],
        },
        "modules": [{
            "type": "skin_pack",
            "uuid": stable_uuid(uuid_key, "module"),
            "version": [1, 0, 0],
        }],
    })
    if icon_source and os.path.isfile(icon_source):
        shutil.copyfile(icon_source, os.path.join(pack_dir, "pack_icon.png"))
