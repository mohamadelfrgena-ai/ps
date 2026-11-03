"""Persona -> Bedrock skin baker.

Faithful port of `PersonaPieceLoader` (Java) for the parts that matter when the
output has to be a plain Bedrock *skin pack* (no scripting, works on console):

    drawPersonaBodyLayer()   Persona 128x128 sheet -> vanilla 64x64 layout
    drawPersonaFaceLayer()   Persona 32x32 face    -> head + hat UVs
    copyPersonaBox()         the generic box blit
    tintSpec() + tint maps   authored colour of a tintable piece

The Java mod renders one player at a time, so it can keep a runtime atlas and
tint mask.  A skin pack is static, so we *bake* the overlay and the tint into
the texture, and inflate the bones the piece covers (0.02px) exactly like the
Java side inflates the base scaffold by 0.01px to avoid z-fighting.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from functools import lru_cache

from .image import Image, read_image
from .util import log, slug, warn

# ---------------------------------------------------------------------------
# Piece types -> the vanilla bones the texture is allowed to paint.
# Mirrors `applyPersonaTextureUvs()` bodyBones handling in PersonaPieceLoader.
# ---------------------------------------------------------------------------
HEAD_BONES = ("head", "hat")
ARM_BONES = ("leftArm", "leftSleeve", "rightArm", "rightSleeve")
BODY_BONES = ("body", "jacket")
LEG_BONES = ("leftLeg", "leftPants", "rightLeg", "rightPants")

PIECE_BONES = {
    "persona_hair": HEAD_BONES,
    "persona_facial_hair": HEAD_BONES,
    "persona_face_accessory": HEAD_BONES,
    "persona_eyes": HEAD_BONES,
    "persona_mouth": HEAD_BONES,
    "persona_skin": HEAD_BONES,
    "persona_top": BODY_BONES + ARM_BONES,
    "persona_outerwear": BODY_BONES + ARM_BONES,
    "persona_jacket": BODY_BONES + ARM_BONES,
    "persona_arms": ARM_BONES,
    "persona_hand": ARM_BONES,
    "persona_bottom": LEG_BONES,
    "persona_high_pants": LEG_BONES,
    "persona_legs": LEG_BONES,
    "persona_feet": LEG_BONES,
}
DEFAULT_BONES = BODY_BONES + ARM_BONES + LEG_BONES + HEAD_BONES

# source u, source v, target u, target v, width, height, depth  (medium / wide)
WIDE_BOXES = (
    (16, 0, 16, 16, 8, 12, 4),   # body
    (16, 16, 16, 32, 8, 12, 4),  # jacket
    (0, 0, 40, 16, 4, 12, 4),    # right arm
    (0, 16, 40, 32, 4, 12, 4),   # right sleeve
    (40, 0, 32, 48, 4, 12, 4),   # left arm
    (40, 16, 48, 48, 4, 12, 4),  # left sleeve
    (16, 32, 0, 16, 4, 12, 4),   # right leg
    (0, 32, 0, 32, 4, 12, 4),    # right pants
    (32, 32, 16, 48, 4, 12, 4),  # left leg
    (48, 32, 0, 48, 4, 12, 4),   # left pants
)
# slim arms live across y=48 in the same sheet; torso/legs are shared.
SLIM_BOXES = (
    (16, 0, 16, 16, 8, 12, 4),
    (16, 16, 16, 32, 8, 12, 4),
    (14, 48, 40, 16, 3, 12, 4),  # right arm
    (0, 48, 40, 32, 3, 12, 4),   # right sleeve
    (28, 48, 32, 48, 3, 12, 4),  # left arm
    (42, 48, 48, 48, 3, 12, 4),  # left sleeve
    (16, 32, 0, 16, 4, 12, 4),
    (0, 32, 0, 32, 4, 12, 4),
    (32, 32, 16, 48, 4, 12, 4),
    (48, 32, 0, 48, 4, 12, 4),
)
FACE_BOXES = (
    (0, 0, 0, 0, 8, 8, 8),     # head
    (0, 16, 32, 0, 8, 8, 8),   # hat
)


def copy_persona_box(target: Image, source: Image, su: int, sv: int,
                     tu: int, tv: int, width: int, height: int, depth: int) -> None:
    box_w = depth + width + depth + width
    box_h = depth + height
    target.blit(source, tu, tv, su, sv, box_w, box_h, composite=True)


def draw_body_layer(target: Image, source: Image, slim: bool = False) -> None:
    if source.width < 128 or source.height < 128:
        # Already in player layout -- straight copy (Java does the same).
        target.blit(source, 0, 0, composite=True)
        return
    for box in (SLIM_BOXES if slim else WIDE_BOXES):
        copy_persona_box(target, source, *box)


def draw_face_layer(target: Image, source: Image) -> None:
    if source.width < 32 or source.height < 32:
        target.blit(source, 0, 0, composite=True)
        return
    for box in FACE_BOXES:
        copy_persona_box(target, source, *box)


def parse_color(value) -> tuple[int, int, int] | None:
    if not isinstance(value, str):
        return None
    text = value.strip().lstrip("#")
    if len(text) == 3:
        text = "".join(c * 2 for c in text)
    if len(text) != 6:
        return None
    try:
        return (int(text[0:2], 16), int(text[2:4], 16), int(text[4:6], 16))
    except ValueError:
        return None


@dataclass
class PersonaPiece:
    id: str
    name: str
    display: str
    piece_type: str
    directory: str
    zones: list[str] = field(default_factory=list)
    texture_sources: list[dict] = field(default_factory=list)
    geometry_sources: list[dict] = field(default_factory=list)
    tint_color: tuple[int, int, int] | None = None

    @property
    def bones(self) -> tuple[str, ...]:
        return PIECE_BONES.get(self.piece_type, DEFAULT_BONES)

    @property
    def slim_ready(self) -> bool:
        """Face pieces are arm-independent, so they can go on both models."""
        return self.piece_type in PIECE_BONES and self.piece_type in (
            "persona_hair", "persona_facial_hair", "persona_face_accessory",
            "persona_eyes", "persona_mouth", "persona_skin",
        )


def _read_texts(directory: str) -> dict[str, str]:
    """Persona pieces carry their own texts/<lang>.lang files."""
    out: dict[str, str] = {}
    texts = os.path.join(directory, "texts")
    if not os.path.isdir(texts):
        return out
    for name in os.listdir(texts):
        if not name.endswith(".lang"):
            continue
        lang = os.path.splitext(name)[0]
        table = {}
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
        out[lang] = table
    return out


def load_piece(directory: str) -> PersonaPiece | None:
    metas = [n for n in os.listdir(directory) if n.lower().endswith(".meta.json")]
    if not metas:
        return None
    try:
        with open(os.path.join(directory, metas[0]), "r", encoding="utf-8") as handle:
            meta = json.load(handle)
    except (OSError, ValueError) as exc:
        warn(f"persona: cannot read {metas[0]}: {exc}")
        return None

    piece_type = meta.get("piece_type") or ""
    if not piece_type.startswith("persona_") or piece_type == "persona_emote":
        return None

    name = meta.get("piece_name") or os.path.basename(directory)
    zones: list[str] = []
    for value in (meta.get("zone"), *[s.get("zone") for s in meta.get("geometry_sources") or []]):
        if isinstance(value, list):
            zones.extend(str(v).lower() for v in value)
        elif isinstance(value, str):
            zones.append(value.lower())

    tint = None
    for key in ("tint_color", "tint_base_color"):
        block = meta.get(key) or {}
        for channel in ("r_color", "g_color", "b_color"):
            parsed = parse_color(block.get(channel))
            if parsed:
                tint = parsed
                break
        if tint:
            break

    display = name
    texts = _read_texts(directory)
    for lang in ("en_US", "en_us", "en_GB"):
        table = texts.get(lang) or {}
        if table.get(f"pack.{name}") or table.get(name):
            display = table.get(f"pack.{name}") or table[name]
            break
    else:
        for table in texts.values():
            if table.get(f"pack.{name}") or table.get(name):
                display = table.get(f"pack.{name}") or table[name]
                break

    return PersonaPiece(
        id=meta.get("piece_id") or slug(name),
        name=name,
        display=display,
        piece_type=piece_type,
        directory=directory,
        zones=sorted(set(zones)),
        texture_sources=meta.get("texture_sources") or [],
        geometry_sources=meta.get("geometry_sources") or [],
        tint_color=tint,
    )


def overlay_for(piece: PersonaPiece, slim: bool = False,
                tint: tuple[int, int, int] | None = None) -> Image | None:
    """Cached wrapper -- baking 47 pieces against 300 skins is not cheap."""
    cached = _overlay_cached(piece.directory, piece.piece_type, slim, tint)
    return cached.subimage(0, 0, cached.width, cached.height) if cached else None


@lru_cache(maxsize=256)
def _overlay_cached(directory: str, _piece_type: str, slim: bool,
                    tint: tuple[int, int, int] | None) -> Image | None:
    """Render the piece's textures into a 64x64 vanilla-layout overlay."""
    piece = load_piece(directory)
    if piece is None or not piece.texture_sources:
        return None
    canvas = Image(64, 64)
    drew = False
    for source in piece.texture_sources:
        tex_name = source.get("texture")
        if not tex_name:
            continue
        tex_path = os.path.join(piece.directory, tex_name)
        if not os.path.isfile(tex_path):
            warn(f"persona: missing texture {tex_name} in {piece.name}")
            continue
        layer = read_image(tex_path)
        face = bool(source.get("use_face_uv"))
        if face:
            draw_face_layer(canvas, layer)
        else:
            draw_body_layer(canvas, layer, slim=slim)
        drew = True

        tint_map = source.get("tint_map")
        if tint_map and tint:
            mask_path = os.path.join(piece.directory, tint_map)
            if os.path.isfile(mask_path):
                mask = Image(64, 64)
                mask_layer = read_image(mask_path)
                if face:
                    draw_face_layer(mask, mask_layer)
                else:
                    draw_body_layer(mask, mask_layer, slim=slim)
                _multiply_tint(canvas, mask, tint)
    return canvas if drew else None


def _multiply_tint(canvas: Image, mask: Image, tint: tuple[int, int, int]) -> None:
    """Multiply the authored colour into masked pixels (GPU tint fold-down)."""
    tr, tg, tb = tint
    data = canvas.data
    mdata = mask.data
    for i in range(0, len(data), 4):
        if mdata[i + 3] < 8:
            continue
        # brightness of the mask steers how much colour the pixel takes
        strength = (mdata[i] + mdata[i + 1] + mdata[i + 2]) / (3 * 255)
        if strength <= 0:
            continue
        for k, channel in enumerate((tr, tg, tb)):
            base = data[i + k]
            data[i + k] = max(0, min(255, int(base * (1 - strength) + base * channel / 255 * strength)))


def bake_skin(base: Image, overlay: Image, *, opacity: float = 1.0) -> Image:
    """Composite the persona overlay on top of a base skin texture."""
    out = Image(base.width, base.height)
    out.blit(base, 0, 0, composite=False)
    if opacity < 1.0:
        faded = Image(overlay.width, overlay.height)
        faded.blit(overlay, 0, 0, composite=False)
        for i in range(3, len(faded.data), 4):
            faded.data[i] = int(faded.data[i] * opacity)
        out.blit(faded, 0, 0)
    else:
        out.blit(overlay, 0, 0)
    return out


def inflate_bones(geometry: dict, bones: tuple[str, ...], amount: float = 0.02) -> dict:
    """Bump every cube of the given bones outwards (Java: inflate + 0.01)."""
    wanted = {b.lower() for b in bones}
    for bone in geometry.get("bones", []):
        if str(bone.get("name", "")).lower() not in wanted:
            continue
        for cube in bone.get("cubes", []):
            current = float(cube.get("inflate", 0.0))
            cube["inflate"] = round(current + amount, 4)
    return geometry


def inflate_bones_amounts(geometry: dict, amounts: dict[str, float]) -> dict:
    """Cumulative inflate: one amount per bone, derived from the layer stack.

    A bone carrying three stacked pieces gets `0.01 * 3 + 0.01`; the extra
    constant is the same safety margin the Java side adds (it inflates the base
    scaffold by 0.01) so the folded layer never z-fights with the skin below.
    """
    wanted = {str(bone).lower(): value for bone, value in amounts.items()}
    for bone in geometry.get("bones", []):
        amount = wanted.get(str(bone.get("name", "")).lower())
        if not amount:
            continue
        for cube in bone.get("cubes", []):
            current = float(cube.get("inflate", 0.0))
            cube["inflate"] = round(current + amount, 4)
    return geometry


def summarize(pieces: list[PersonaPiece]) -> str:
    from collections import Counter
    counts = Counter(p.piece_type for p in pieces)
    return ", ".join(f"{k}={v}" for k, v in sorted(counts.items()))
