"""JSON-UI overlay for the vanilla pause screen.

WHAT THIS DOES
--------------
Takes Mojang's own `ui/pause_screen.json` (vendored in packs/src_rp/ui) and
injects a *left anchored* panel with one button per generated emote, wired for
controller navigation (focus chain + `button_mappings`).

WHAT THIS CANNOT DO -- read this before changing anything
---------------------------------------------------------
Bedrock's JSON-UI is a *declarative layout* language.  It has no binding that
runs a slash command: a button can only
    * play a sound / hover texture,
    * change its own visual state,
    * push or pop another screen,
    * remap a raw input (`button_mappings`) onto another UI action id.
`button_mappings` maps **input ids** (`button.menu_select`,
`button.controller_secondary_select`, ...) to **UI action ids**, it does not
carry a command string.  Any pack claiming "JSON-UI runs /commands" is either
using the Script API behind the scenes or shipping a client mod.

So this file gives the *discoverable, controller friendly* half of the feature:
the panel documents every emote and its exact trigger.  The *execution* half is
provided script-free by the `bs:emote_console` entity (see addon.py), whose
`minecraft:interact` events run `execute as @p ... run function bs/emote/<id>`,
and script-backed by scripts/main.js for worlds that allow scripting.
"""

from __future__ import annotations

import json
import os

from .util import ensure_dir, log, read_json, warn, write_json

PANEL_NAMESPACE = "pause"
COLUMNS = 3
ROWS = 5

# Compact labels: persona ids are long and unreadable on a controller.
LABEL_OVERRIDES = {
    "communication_follow_me": "Follow Me",
    "communication_point": "Point",
    "the_ultimate_handshake": "Handshake",
    "celebration_diamond": "Diamond",
    "basic_clap": "Clap",
    "beam_me_up": "Beam Me Up",
    "fake_death": "Fake Death",
    "free_diver": "Free Diver",
    "overhead_wave": "Wave",
    "thepickaxe": "Pickaxe",
    "tiptoeing": "Tiptoe",
    "tracking_scents": "Sniff",
    "woodpunch": "Wood Punch",
    "faceplant": "Faceplant",
    "emote": "Emote",
}


def short_label(emote_id: str, fallback: str = "") -> str:
    key = emote_id.split(":")[-1]
    if key in LABEL_OVERRIDES:
        return LABEL_OVERRIDES[key]
    text = fallback or key
    return " ".join(part.capitalize() for part in text.replace("_", " ").split())[:16]


def strip_jsonc(text: str) -> str:
    """Remove // comments while respecting string literals (Mojang ships JSONC)."""
    out: list[str] = []
    i, n = 0, len(text)
    in_string = False
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


def load_screen(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as handle:
        return json.loads(strip_jsonc(handle.read()))


def _button(emote, index: int, total: int) -> dict:
    """One grid cell: a light text button that owns its controller mappings."""
    column, row = index % COLUMNS, index // COLUMNS
    above = index - COLUMNS
    below = index + COLUMNS
    up = above if row > 0 else (index + (ROWS - 1) * COLUMNS if index + (ROWS - 1) * COLUMNS < total else index)
    down = below if below < total else (above if row > 0 else index)
    left = index - 1 if column > 0 else index
    right = index + 1 if column < COLUMNS - 1 and index + 1 < total else index
    return {
        f"bs_emote_btn_{index}@common_buttons.light_text_button": {
            "$button_text": f"{index + 1}. {short_label(emote.id, emote.display)}",
            "$pressed_button_name": f"button.bs_emote_{index}",
            "size": [58, 20],
            "focus_identifier": f"bs_emote_{index}",
            "focus_change_up": f"bs_emote_{up}",
            "focus_change_down": f"bs_emote_{down}",
            "focus_change_left": f"bs_emote_{left}",
            "focus_change_right": f"bs_emote_{right}",
            "button_mappings": [
                {
                    "from_button_id": "button.menu_select",
                    "to_button_id": f"button.bs_emote_{index}",
                    "mapping_type": "global",
                    "scope": "view",
                },
                {
                    # D-pad/A on console: secondary select jumps straight to the
                    # emote instead of walking the whole focus chain
                    "from_button_id": "button.controller_secondary_select",
                    "to_button_id": f"button.bs_emote_{index}",
                    "mapping_type": "global",
                    "scope": "controller",
                },
            ],
        }
    }


def build_panel_controls(emotes) -> dict:
    """3 x 5 grid of buttons plus a header and a hint line."""
    cells = []
    for index, emote in enumerate(emotes[: COLUMNS * ROWS]):
        cells.append(_button(emote, index, len(emotes)))

    columns = []
    for col in range(COLUMNS):
        stack = {
            "type": "stack_panel",
            "orientation": "vertical",
            "size": ["default", "default"],
            "controls": [cells[index] for index in range(col, len(cells), COLUMNS)],
        }
        columns.append({f"bs_emote_col_{col}": stack})

    return {
        # reusable button template (kept in the pause namespace so packs can
        # restyle it without touching our generator)
        "bs_emote_panel": {
            "type": "stack_panel",
            "orientation": "vertical",
            "size": ["default", "default"],
            "anchor_from": "left_middle",
            "anchor_to": "left_middle",
            "offset": [4, 0],
            "layer": 3,
            "alpha": 0.92,
            "controls": [
                {
                    "bs_emote_title": {
                        "type": "label",
                        "text": "§eEmote Slots §7(1-6)",
                        "size": ["default", "default"],
                        "font_scale_factor": 0.9,
                        "shadow": True,
                    }
                },
                {
                    "bs_emote_grid": {
                        "type": "stack_panel",
                        "orientation": "horizontal",
                        "size": ["default", "default"],
                        "controls": columns,
                    }
                },
                {
                    "bs_emote_hint": {
                        "type": "label",
                        "text": "§8Slot binder: §7/function bs/give_wheel",
                        "size": ["default", "default"],
                        "font_scale_factor": 0.7,
                        "shadow": True,
                    }
                },
            ],
            "button_mappings": [
                {
                    # keep the pause menu's own "resume" binding from firing when
                    # the player is navigating our panel with the controller
                    "from_button_id": "button.menu_select",
                    "to_button_id": "button.bs_emote_select",
                    "mapping_type": "global",
                    "scope": "view",
                }
            ],
        }
    }


def inject_pause_screen(vanilla_path: str, emotes, out_path: str) -> dict:
    screen = load_screen(vanilla_path)
    if screen.get("namespace") != PANEL_NAMESPACE:
        warn(f"unexpected namespace in {vanilla_path}: {screen.get('namespace')}")
        screen["namespace"] = PANEL_NAMESPACE

    content = screen.get("pause_screen_content")
    if not isinstance(content, dict) or "controls" not in content:
        raise SystemExit("pause_screen.json: pause_screen_content missing -- vanilla file changed?")

    screen.update(build_panel_controls(emotes))

    # insert the panel once (idempotent re-builds)
    controls = [c for c in content["controls"] if "bs_emote_panel@pause.bs_emote_panel" not in c]
    controls.append({"bs_emote_panel@pause.bs_emote_panel": {}})
    content["controls"] = controls

    # make sure the controller can always reach the panel
    screen["pause_screen@common.base_screen"].setdefault("button_mappings", []).append({
        "from_button_id": "button.menu_tab_left",
        "to_button_id": "button.bs_emote_0",
        "mapping_type": "global",
        "scope": "view",
    })

    ensure_dir(os.path.dirname(out_path) or ".")
    write_json(out_path, screen)
    log(f"ui: injected {min(len(emotes), COLUMNS * ROWS)} emote buttons into pause_screen.json")
    return {
        "buttons": min(len(emotes), COLUMNS * ROWS),
        "namespace": PANEL_NAMESPACE,
        "path": out_path,
    }
