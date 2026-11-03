"""Persona emote -> Bedrock player animations.

Java counterparts:
  * pack/persona/PersonaEmoteLoader.java   (discovery + duration)
  * client/appearance/emote/EmoteManager.java (slots, playback clock)
  * pack/persona/DressingRoomAnimationLoader.java (wardrobe reactions)

Bedrock already speaks the same animation format, and persona emotes are
authored against the same bone names the player rig exposes (head, body,
leftArm, rightArm, leftLeg, rightLeg).  So the conversion is a *registration*
job:

  1. copy each `*.animation.json` payload under `animations/`.
  2. register every animation in `entity/player.entity.json`
     (`/playanimation` and `player.playAnimation()` both need that).
  3. emit one `.mcfunction` per emote so consoles can fire them without
     scripting at all.
  4. emit a JS registry for the emote wheel / server-ui menu.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field

from .util import ensure_dir, log, read_json, slug, warn, write_json

PLAYER_BONES = {"root", "body", "waist", "head", "hat", "cape", "jacket",
                "leftarm", "leftsleeve", "leftitem", "rightarm", "rightsleeve",
                "rightitem", "leftleg", "leftpants", "rightleg", "rightpants"}

# Persona authors emote on a rig with a `hip` bone.  Bedrock's player rig calls
# the same pelvis bone `waist` (see geometry.humanoid.custom: root -> waist ->
# body -> head/arms), so retarget instead of dropping the channel.
# Java side: EmoteManager.applyBone() merges "hip" and "waist" into one case.
BONE_REMAP = {"hip": "waist"}


@dataclass
class Emote:
    id: str
    display: str
    animation_name: str
    payload: dict
    duration: float
    pack: str = ""
    uid: str = ""
    thumbnail: str | None = None
    bones: list[str] = field(default_factory=list)

    @property
    def bedrock_name(self) -> str:
        return f"animation.bs.emote.{slug(self.id)}"

    @property
    def registry_key(self) -> str:
        return f"bs:{slug(self.id)}"


def _max_time(value) -> float:
    best = 0.0
    if isinstance(value, dict):
        for key, child in value.items():
            try:
                best = max(best, float(key))
            except (TypeError, ValueError):
                pass
            best = max(best, _max_time(child))
    elif isinstance(value, list):
        for child in value:
            best = max(best, _max_time(child))
    return best


def _display_name(directory: str, fallback: str) -> str:
    texts = os.path.join(directory, "texts")
    if not os.path.isdir(texts):
        return fallback
    for lang in ("en_US", "en_us", "en_GB"):
        path = os.path.join(texts, f"{lang}.lang")
        if not os.path.isfile(path):
            continue
        with open(path, "r", encoding="utf-8", errors="replace") as handle:
            for line in handle:
                key, _, value = line.strip().partition("=")
                if key in (f"pack.{fallback}", fallback) and value:
                    return value.strip()
    return fallback


def load_emote(directory: str) -> Emote | None:
    metas = [n for n in os.listdir(directory) if n.lower().endswith(".meta.json")]
    if not metas:
        return None
    try:
        meta = read_json(os.path.join(directory, metas[0]))
    except ValueError:
        return None
    if meta.get("piece_type") != "persona_emote":
        return None

    sources = meta.get("animation_sources") or []
    if not sources:
        return None
    source = sources[0]
    filename = source.get("animationFile") or source.get("animation_file")
    if not filename:
        return None
    path = os.path.join(directory, filename)
    if not os.path.isfile(path):
        path = os.path.join(directory, filename.lower())
    if not os.path.isfile(path):
        warn(f"emote {os.path.basename(directory)}: missing {filename}")
        return None

    try:
        root = read_json(path)
    except ValueError:
        return None
    animations = root.get("animations") or {}
    if not animations:
        return None
    name = source.get("name")
    if not name or name not in animations:
        name = next(iter(animations))
    payload = animations[name]
    duration = float(payload.get("animation_length") or _max_time(payload) or 0.0)
    if duration <= 0:
        warn(f"emote {os.path.basename(directory)}: zero length, skipped")
        return None

    piece_name = meta.get("piece_name") or os.path.basename(directory)
    thumb = os.path.join(directory, "thumbnail.png")
    return Emote(
        id=piece_name,
        uid=meta.get("piece_id") or piece_name,
        display=_display_name(directory, piece_name),
        animation_name=name,
        payload=payload,
        duration=duration,
        thumbnail=thumb if os.path.isfile(thumb) else None,
        bones=sorted({str(b).lower() for b in (payload.get("bones") or {})}),
    )


def discover(root: str) -> list[Emote]:
    if not os.path.isdir(root):
        warn(f"persona root not found: {root}")
        return []
    emotes: list[Emote] = []
    for name in sorted(os.listdir(root)):
        directory = os.path.join(root, name)
        if not os.path.isdir(directory):
            continue
        emote = load_emote(directory)
        if emote:
            emotes.append(emote)
    return emotes


def load_dressing_room(root: str) -> dict[str, dict]:
    """`animations/dressing_room/*.anim.json` -> wardrobe reaction animations."""
    directory = os.path.join(root, "animations", "dressing_room")
    out: dict[str, dict] = {}
    if not os.path.isdir(directory):
        return out
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".json"):
            continue
        try:
            payload = read_json(os.path.join(directory, name))
        except ValueError:
            continue
        for key, value in (payload.get("animations") or {}).items():
            out[f"animation.bs.dressing_room.{slug(os.path.splitext(name)[0])}"] = value
    return out


def normalise(emote: Emote) -> dict:
    """Make a persona payload safe for the vanilla player rig."""
    payload = json.loads(json.dumps(emote.payload))
    payload.setdefault("loop", False)
    payload["animation_length"] = round(max(emote.duration, 0.05), 4)

    bones = payload.get("bones") or {}
    retargeted: dict[str, dict] = {}
    dropped: list[str] = []
    for name, value in bones.items():
        target = BONE_REMAP.get(str(name).lower(), name)
        if str(target).lower() in PLAYER_BONES:
            retargeted[target] = value
        else:
            dropped.append(str(name))
    payload["bones"] = retargeted
    if dropped:
        # Non-player bones (persona props) cannot be driven on the vanilla rig.
        warn(f"emote {emote.display}: dropped bones {', '.join(dropped)}")
    return payload


def build_animation_file(emotes: list[Emote], dressing_room: dict[str, dict] | None = None) -> dict:
    animations: dict[str, dict] = {}
    for emote in emotes:
        animations[emote.bedrock_name] = normalise(emote)
    for key, value in (dressing_room or {}).items():
        animations[key] = value
    return {"format_version": "1.8.0", "animations": animations}


def register_in_player_entity(template: dict, emotes: list[Emote],
                              dressing_room: dict[str, dict] | None = None) -> dict:
    """Merge our animations into Mojang's vanilla player.entity.json."""
    entity = json.loads(json.dumps(template))
    description = entity["minecraft:client_entity"]["description"]
    animations = description.setdefault("animations", {})
    for emote in emotes:
        animations[f"bs_{slug(emote.id)}"] = emote.bedrock_name
    for key in (dressing_room or {}):
        animations[key.replace("animation.", "").replace(".", "_")] = key
    return entity


def write_functions(bp_dir: str, emotes: list[Emote]) -> list[str]:
    """One mcfunction per emote + an index function.  No scripting required."""
    folder = ensure_dir(os.path.join(bp_dir, "functions", "bs", "emote"))
    written: list[str] = []
    for emote in emotes:
        name = slug(emote.id)
        body = (
            f"# {emote.display}\n"
            f"# usage: /function bs/emote/{name}\n"
            f'playanimation @s "{emote.bedrock_name}" 0 0.15\n'
        )
        path = os.path.join(folder, f"{name}.mcfunction")
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(body)
        written.append(f"bs/emote/{name}")

    lines = ["# Bedrock Skins -- emote index", "# /function bs/emotes"]
    lines += [f"#   /function {entry}" for entry in written]
    with open(os.path.join(bp_dir, "functions", "bs", "emotes.mcfunction"),
              "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")
    return written


def registry_js(emotes: list[Emote]) -> str:
    rows = []
    for emote in emotes:
        rows.append(
            "  {"
            f' id: "bs:{slug(emote.id)}",'
            f' animation: "{emote.bedrock_name}",'
            f' label: "{emote.display.replace(chr(34), "")}",'
            f' seconds: {round(emote.duration, 3)}'
            " }"
        )
    return (
        "// AUTO-GENERATED by tools/build_addon.py -- do not edit.\n"
        "export const EMOTES = [\n" + ",\n".join(rows) + "\n];\n"
    )


def summary(emotes: list[Emote]) -> str:
    return f"{len(emotes)} emotes, {round(sum(e.duration for e in emotes), 1)}s authored"
