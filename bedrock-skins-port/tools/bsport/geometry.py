"""Bedrock geometry helpers (port of `bedrock/*.java` + `resolveGeometry`)."""

from __future__ import annotations

import copy
import json
import os

from .util import log, write_json

FORMAT_VERSION = "1.12.0"


def load_geometry_file(path: str) -> list[dict]:
    with open(path, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    geometries = data.get("minecraft:geometry")
    if geometries is None and "geometry" in data:  # very old packs
        geometries = data["geometry"]
    return geometries or []


def index_by_identifier(geometries: list[dict]) -> dict[str, dict]:
    out: dict[str, dict] = {}
    for geo in geometries:
        ident = (geo.get("description") or {}).get("identifier")
        if ident:
            out[ident.lower()] = geo
    return out


def find(geometries: list[dict], name: str) -> dict | None:
    if not name:
        return None
    want = name.lower()
    for geo in geometries:
        ident = str((geo.get("description") or {}).get("identifier", "")).lower()
        if ident == want:
            return geo
    # Bedrock packs sometimes ship "geometry.humanoid.custom" as "geometry.custom"
    short = want.split("geometry.")[-1]
    for geo in geometries:
        ident = str((geo.get("description") or {}).get("identifier", "")).lower()
        if ident.split("geometry.")[-1] == short:
            return geo
    return None


def clone(geometry: dict) -> dict:
    return copy.deepcopy(geometry)


def rename(geometry: dict, identifier: str) -> dict:
    geometry.setdefault("description", {})["identifier"] = identifier
    return geometry


def set_texture_size(geometry: dict, width: int, height: int) -> dict:
    description = geometry.setdefault("description", {})
    description["texture_width"] = int(width)
    description["texture_height"] = int(height)
    return geometry


def is_slim(geometry: dict) -> bool:
    ident = str((geometry.get("description") or {}).get("identifier", "")).lower()
    return "slim" in ident


def wrap(geometries: list[dict], fmt: str = FORMAT_VERSION) -> dict:
    return {"format_version": fmt, "minecraft:geometry": geometries}


def write_pack_file(path: str, geometries: list[dict]) -> None:
    write_json(path, wrap(geometries))


def translate_y(bone: dict, delta: float) -> None:
    """Shift a bone vertically; used when retargeting non-player rigs."""
    pivot = bone.get("pivot")
    if isinstance(pivot, list) and len(pivot) >= 2:
        pivot[1] = pivot[1] + delta
    for cube in bone.get("cubes", []):
        origin = cube.get("origin")
        if isinstance(origin, list) and len(origin) >= 2:
            origin[1] = origin[1] + delta


PLAYER_BONES = ("root", "body", "waist", "head", "hat", "cape", "jacket",
                "leftArm", "leftSleeve", "leftItem", "rightArm", "rightSleeve",
                "rightItem", "leftLeg", "leftPants", "rightLeg", "rightPants")


def missing_player_bones(geometry: dict) -> list[str]:
    present = {str(b.get("name", "")).lower() for b in geometry.get("bones", [])}
    return [b for b in PLAYER_BONES if b.lower() not in present]


def ensure_player_scaffold(geometry: dict) -> dict:
    """Port of `BedrockModelBuilder.addMissingParts()`.

    A Bedrock skin pack may ship a geometry with a partial bone list; the
    vanilla player rig still needs the standard bones so animations and armour
    keep working.  We add empty placeholder bones (never cubes).
    """
    parents = {
        "head": "body", "hat": "head", "body": None, "jacket": "body",
        "leftArm": "body", "leftSleeve": "leftArm", "rightArm": "body",
        "rightSleeve": "rightArm", "leftLeg": "body", "leftPants": "leftLeg",
        "rightLeg": "body", "rightPants": "rightLeg",
    }
    bones = geometry.setdefault("bones", [])
    present = {str(b.get("name", "")).lower(): b for b in bones}
    for name, parent in parents.items():
        if name in present:
            continue
        bone = {"name": name, "pivot": [0, 0, 0]}
        if parent:
            bone["parent"] = parent
        bones.append(bone)
        present[name] = bone
    return geometry


def skin_animations(bones: list[str] | None = None) -> dict:
    """`animation.*` flags a skin geometry may declare (Bedrock screenshots/pose).

    These are the flags `BedrockGeometry.java` reads (`animationArmsDown`,
    `animationStatueOfLibertyArms`, ...).  Bedrock understands them directly on
    a custom geometry inside a skin pack, so they are passed through verbatim.
    """
    return {name: False for name in (
        "animationArmsDown", "animationArmsOutFront", "animationStatueOfLibertyArms",
        "animationStationaryLegs", "animationSingleLegAnimation", "animationSingleArmAnimation",
        "animationInvertedCrouch", "animationDontShowArmor",
    )}
