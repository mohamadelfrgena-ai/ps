"""Skin pack loader/builder.

Java counterpart: `pack/loader/SkinPackLoader.java`.

The Java mod reads `<game>/skin_packs/<pack>/skins.json` + `geometry.json` +
loose textures and builds a runtime player model.  Bedrock's own skin packs use
the exact same three files, which means a Bedrock port does not have to
reinvent anything for skins -- it only has to normalise what the Java loader
tolerates (missing geometry, external `minecraft:` textures, `cape` entries,
`.pck` archives) and then emit a valid `skin_pack` module.
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
from dataclasses import dataclass, field

from . import geometry as geo
from .image import read_png, write_png
from .layering import combo_key, combo_name, combine_overlays
from .persona import (PersonaPiece, bake_skin, inflate_bones,
                      inflate_bones_amounts, overlay_for)
from .util import ensure_dir, log, read_json, rel, slug, warn, write_json

SKIN_ICON_NAMES = ("pack_icon.png", "pack.png", "icon.png")


@dataclass
class SkinEntry:
    localization_name: str
    geometry: str
    texture: str
    cape: str | None = None
    kind: str = "free"
    extra: dict = field(default_factory=dict)

    def as_json(self) -> dict:
        out = {
            "localization_name": self.localization_name,
            "geometry": self.geometry,
            "texture": self.texture,
            "type": self.kind,
        }
        if self.cape:
            out["cape"] = self.cape
        for key, value in self.extra.items():
            out.setdefault(key, value)
        return out


@dataclass
class SkinPack:
    directory: str
    serialize_name: str
    localization_name: str
    entries: list[SkinEntry]
    geometries: list[dict]
    texts: dict[str, dict]

    @property
    def key(self) -> str:
        return slug(os.path.basename(self.directory) or self.serialize_name)


def _read_texts(directory: str) -> dict[str, dict]:
    """`texts/en_US.lang` -> {"en_us": {"key": "value"}}"""
    out: dict[str, dict] = {}
    texts = os.path.join(directory, "texts")
    if not os.path.isdir(texts):
        return out
    for name in sorted(os.listdir(texts)):
        if not name.endswith(".lang"):
            continue
        table: dict[str, str] = {}
        try:
            with open(os.path.join(texts, name), "r", encoding="utf-8", errors="replace") as handle:
                for line in handle:
                    line = line.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    key, _, value = line.partition("=")
                    table[key.strip()] = value.strip()
        except OSError:
            continue
        out[name[:-5]] = table
    return out


def load_pack(directory: str) -> SkinPack | None:
    skins_path = os.path.join(directory, "skins.json")
    if not os.path.isfile(skins_path):
        return None
    try:
        manifest = read_json(skins_path)
    except ValueError as exc:
        warn(f"skin pack {os.path.basename(directory)}: skins.json broken ({exc})")
        return None

    entries: list[SkinEntry] = []
    for raw in manifest.get("skins") or []:
        name = raw.get("localization_name") or raw.get("name")
        if not name:
            continue
        entries.append(SkinEntry(
            localization_name=name,
            geometry=raw.get("geometry", "geometry.humanoid.custom"),
            texture=raw.get("texture", ""),
            cape=raw.get("cape"),
            kind=raw.get("type", "free"),
        ))

    geometries: list[dict] = []
    geo_path = os.path.join(directory, "geometry.json")
    if os.path.isfile(geo_path):
        try:
            geometries = geo.load_geometry_file(geo_path)
        except ValueError as exc:
            warn(f"skin pack {os.path.basename(directory)}: geometry.json broken ({exc})")

    return SkinPack(
        directory=directory,
        serialize_name=manifest.get("serialize_name") or os.path.basename(directory),
        localization_name=manifest.get("localization_name") or os.path.basename(directory),
        entries=entries,
        geometries=geometries,
        texts=_read_texts(directory),
    )


def discover(root: str) -> list[SkinPack]:
    if not os.path.isdir(root):
        warn(f"skin pack root not found: {root}")
        return []
    packs = []
    for name in sorted(os.listdir(root)):
        directory = os.path.join(root, name)
        if not os.path.isdir(directory):
            continue  # .pck archives are handled by the Java importer, not here
        pack = load_pack(directory)
        if pack and pack.entries:
            packs.append(pack)
    return packs


def _resolve_texture(pack: SkinPack, value: str) -> str | None:
    """Return a local file for a skins.json texture value, or None for built-ins."""
    if not value or ":" in value:
        return None  # `minecraft:textures/...` -> ship as-is, no local copy
    candidate = os.path.join(pack.directory, value)
    if os.path.isfile(candidate):
        return candidate
    lowered = os.path.join(pack.directory, value.lower())
    return lowered if os.path.isfile(lowered) else None


def _icon(directory: str) -> str | None:
    for name in SKIN_ICON_NAMES:
        path = os.path.join(directory, name)
        if os.path.isfile(path):
            return path
    return None


def build_pack(pack: SkinPack, out_dir: str, *, cosmetics: list[PersonaPiece] | None = None,
               bake_cosmetics: bool = False, geometry_fallback: list[dict] | None = None,
               max_cosmetics_per_skin: int = 0,
               combos: list[list[PersonaPiece]] | None = None) -> dict:
    """Write one Bedrock skin pack folder; returns a stats dict."""
    ensure_dir(out_dir)
    textures_dir = ensure_dir(os.path.join(out_dir, "textures"))
    texts_dir = ensure_dir(os.path.join(out_dir, "texts"))

    emitted: list[dict] = []
    extra_lang: dict[str, str] = {}
    used_geometries: dict[str, dict] = {}
    stats = {"skins": 0, "capes": 0, "cosmetics": 0, "missing_textures": 0,
             "max_layers": 1}
    # multi-piece loadouts win over single pieces when both are requested
    merged_sets: list[list[PersonaPiece]] = list(combos or [])
    if not merged_sets and bake_cosmetics and cosmetics:
        merged_sets = [[piece] for piece in cosmetics]

    fallback = geometry_fallback or []
    base_geometries = pack.geometries or fallback

    for entry in pack.entries:
        texture_src = _resolve_texture(pack, entry.texture)
        texture_out = entry.texture
        if texture_src:
            texture_out = f"textures/{os.path.basename(texture_src)}"
            shutil.copyfile(texture_src, os.path.join(out_dir, texture_out))

        cape_out = None
        if entry.cape:
            cape_src = _resolve_texture(pack, entry.cape)
            if cape_src:
                name = os.path.basename(cape_src)
                if not name.startswith(("cape_", "dlccape")):
                    name = "cape_" + name
                cape_out = f"textures/{name}"
                shutil.copyfile(cape_src, os.path.join(out_dir, cape_out))
                stats["capes"] += 1

        geometry_ident = entry.geometry
        source_geometry = geo.find(base_geometries, entry.geometry)
        if source_geometry is not None:
            used_geometries.setdefault(geometry_ident.lower(), source_geometry)

        emitted.append({
            "localization_name": entry.localization_name,
            "geometry": geometry_ident,
            "texture": texture_out,
            "type": entry.kind,
            **({"cape": cape_out} if cape_out else {}),
        })
        stats["skins"] += 1

        # ---- optional cosmetic mergers (skin + N persona pieces as one skin) ----
        if not merged_sets or not texture_src:
            continue
        if source_geometry is None:
            continue
        slim = geo.is_slim(source_geometry)
        base_image = read_png(texture_src)
        en_table = pack.texts.get("en_US") or pack.texts.get("en_us") or {}
        base_display = en_table.get(f"skin.{pack.serialize_name}.{entry.localization_name}") \
            or entry.localization_name
        baked = 0
        for piece_set in merged_sets:
            if max_cosmetics_per_skin and baked >= max_cosmetics_per_skin:
                break
            set_pieces = list(piece_set)
            if not set_pieces:
                continue
            if slim and any(not piece.slim_ready and piece.piece_type.endswith(
                    ("top", "arms", "hand")) for piece in set_pieces):
                continue  # authored for wide arms only
            overlay, bone_layers = combine_overlays(set_pieces, slim=slim)
            if overlay is None:
                continue
            merged = bake_skin(base_image, overlay)
            # The baked rig only depends on (base rig, piece stack).  DLC packs
            # give every skin its own geometry identifier even when the bones
            # are byte-identical, so we dedupe on a content hash, not the name.
            rig_hash = hashlib.sha1(json.dumps(source_geometry.get("bones"), sort_keys=True)
                                    .encode("utf-8")).hexdigest()[:10]
            stack_key = combo_key(set_pieces)[:60]
            ident = f"geometry.bs.{slug(stack_key)}.{rig_hash}"
            if ident.lower() not in used_geometries:
                merged_geometry = geo.clone(source_geometry)
                # cumulative inflate: 0.01 per stacked layer + the 0.01 safety
                # margin that keeps the folded stack off the bare scaffold
                amounts = {bone: round(0.01 * count + 0.01, 4)
                           for bone, count in bone_layers.items()}
                inflate_bones_amounts(merged_geometry, amounts)
                geo.rename(merged_geometry, ident)
                geo.set_texture_size(merged_geometry, merged.width, merged.height)
                used_geometries[ident.lower()] = merged_geometry

            tex_name = (f"textures/merged/{pack.key}__{slug(entry.localization_name)}"
                        f"__{slug(stack_key)}.png")
            ensure_dir(os.path.dirname(os.path.join(out_dir, tex_name)))
            write_png(os.path.join(out_dir, tex_name), merged)
            merged_name = combo_name(base_display, set_pieces)
            emitted.append({
                "localization_name": merged_name,
                "geometry": ident,
                "texture": tex_name,
                "type": entry.kind,
            })
            extra_lang[f"skin.{pack.serialize_name}.{merged_name}"] = merged_name
            stats["cosmetics"] += 1
            stats["max_layers"] = max(stats["max_layers"], len(set_pieces))
            baked += 1

    # Every geometry the emitted skins reference must exist in geometry.json.
    for ident in {e["geometry"].lower() for e in emitted}:
        if ident in used_geometries:
            continue
        fallback_geo = geo.find(fallback, ident)
        if fallback_geo is not None:
            used_geometries[ident] = fallback_geo
        else:
            warn(f"skin pack {pack.key}: missing geometry '{ident}', falling back to wide humanoid")
            wide = geo.find(fallback, "geometry.humanoid.custom")
            if wide is None:
                continue
            clone = geo.clone(wide)
            geo.rename(clone, ident)
            used_geometries[ident] = clone

    geometries_out = [used_geometries[k] for k in sorted(used_geometries)]
    geo.write_pack_file(os.path.join(out_dir, "geometry.json"), geometries_out)

    write_json(os.path.join(out_dir, "skins.json"), {
        "skins": emitted,
        "serialize_name": pack.serialize_name,
        "localization_name": pack.localization_name,
    })

    # texts: keep every language the source pack shipped
    source_texts = dict(pack.texts)
    if "en_US" not in source_texts and "en_us" not in source_texts:
        source_texts["en_US"] = {"pack.name": pack.localization_name}
    if extra_lang:
        en = source_texts.setdefault("en_US", {})
        en.update(extra_lang)
    for lang, table in source_texts.items():
        lines = [f"{k}={v}" for k, v in table.items()]
        with open(os.path.join(texts_dir, f"{lang}.lang"), "w", encoding="utf-8", newline="\n") as handle:
            handle.write("\n".join(lines) + "\n")
    write_json(os.path.join(texts_dir, "languages.json"), sorted(source_texts.keys()))

    icon = _icon(pack.directory)
    if icon:
        shutil.copyfile(icon, os.path.join(out_dir, "pack_icon.png"))

    stats["geometries"] = len(geometries_out)
    return stats
