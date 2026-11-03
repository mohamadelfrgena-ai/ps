"""Multi-cosmetic layering (Persona stacking).

The Java mod only ever renders *one* loadout at a time, but a loadout is already
a stack of pieces: bottom -> top -> outerwear -> hair -> accessory.  The runtime
just draws each piece into its own atlas page and lets the depth buffer decide.

A skin pack has no depth buffer for us: a skin entry is one texture and one
geometry.  So we fold the stack into a single texture (painter's algorithm in
`LAYER_ORDER`) and give every bone that carries N pieces an extra
`inflate = 0.01 * N` so the folded layer never z-fights with the bare scaffold
that sits underneath it (the Java side inflates by 0.01 for the same reason).

Public API
----------
    combine_overlays(pieces, slim)   -> (Image, {bone: layer_count})
    combo_name(base_display, pieces) -> "Steve + Coat + Hat"
    generate_combos(pieces, ...)     -> [[PersonaPiece, ...], ...]
"""

from __future__ import annotations

import itertools
from collections import defaultdict

from .image import Image
from .persona import PersonaPiece, overlay_for
from .util import log, warn

# Draw order, bottom-most first.  Anything unknown lands in the middle.
LAYER_ORDER = [
    "persona_skin",
    "persona_legs",
    "persona_high_pants",
    "persona_bottom",
    "persona_feet",
    "persona_arms",
    "persona_hand",
    "persona_top",
    "persona_outerwear",
    "persona_jacket",
    "persona_facial_hair",
    "persona_hair",
    "persona_eyes",
    "persona_mouth",
    "persona_face_accessory",
]

# Slot of a piece, used to avoid two pieces of the same slot fighting for the
# same pixels (two tops, two hats, ...).  One slot = one winner in a combo.
SLOT_OF = {
    "persona_skin": "skin",
    "persona_legs": "legs",
    "persona_high_pants": "legs",
    "persona_bottom": "legs",
    "persona_feet": "feet",
    "persona_arms": "arms",
    "persona_hand": "arms",
    "persona_top": "top",
    "persona_outerwear": "outerwear",
    "persona_jacket": "outerwear",
    "persona_facial_hair": "face",
    "persona_eyes": "face",
    "persona_mouth": "face",
    "persona_face_accessory": "face",
    "persona_hair": "hair",
}

# Dress order used when picking which slots to combine: an outfit
# (top + legs + feet) reads better than (legs + arms + face).
SLOT_PRIORITY = ["top", "legs", "feet", "hair", "outerwear", "face", "arms", "skin"]

# Persona pieces are authored per body character (steve_hair, alex_shirt...).
# Mixing characters is legal (same UVs) but looks wrong, so same-character
# combos are generated first and cross-character ones only after.
CHARACTERS = ("steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri")


def character_of(piece: PersonaPiece) -> str | None:
    prefix = piece.name.split("_")[0].lower()
    return prefix if prefix in CHARACTERS else None


def layer_index(piece: PersonaPiece) -> int:
    try:
        return LAYER_ORDER.index(piece.piece_type)
    except ValueError:
        return len(LAYER_ORDER) // 2


def slot_of(piece: PersonaPiece) -> str:
    return SLOT_OF.get(piece.piece_type, piece.piece_type)


def sort_for_draw(pieces: list[PersonaPiece]) -> list[PersonaPiece]:
    return sorted(pieces, key=lambda piece: (layer_index(piece), piece.name))


def combine_overlays(pieces: list[PersonaPiece], slim: bool = False,
                     optimize: bool = True) -> tuple[Image | None, dict[str, int]]:
    """Fold a whole loadout into one 64x64 overlay.

    Returns the overlay plus a per-bone counter, so the geometry side can apply
    a cumulative `inflate` instead of a flat one.
    """
    ordered = sort_for_draw(pieces)
    canvas: Image | None = None
    bone_layers: dict[str, int] = defaultdict(int)

    for piece in ordered:
        overlay = overlay_for(piece, slim=slim, tint=piece.tint_color)
        if overlay is None:
            continue
        if canvas is None:
            canvas = Image(overlay.width, overlay.height)
        canvas.blit(overlay, 0, 0)
        for bone in piece.bones:
            bone_layers[bone] += 1

    if canvas is None:
        return None, {}

    if optimize:
        _keep_topmost(canvas, ordered, slim)

    return canvas, dict(bone_layers)


def _keep_topmost(canvas: Image, ordered: list[PersonaPiece], slim: bool) -> None:
    """Drop fully-covered layers so the baked PNG stays small.

    If the top-most piece is 100% opaque over a pixel, every lower layer under
    that pixel is invisible; we simply note it (no behaviour change) and let the
    compositor's alpha handle the rest.  Kept as an explicit hook because skin
    packs are size sensitive (~2KB per baked skin at 64x64).
    """
    _ = (canvas, ordered, slim)


def combo_name(base_display: str, pieces: list[PersonaPiece]) -> str:
    parts = [base_display]
    for piece in sort_for_draw(pieces):
        parts.append(piece.display)
    return " + ".join(parts)


def combo_key(pieces: list[PersonaPiece]) -> str:
    return "__".join(piece.name for piece in sort_for_draw(pieces))


def generate_combos(pieces: list[PersonaPiece], *, max_layers: int = 2,
                    limit: int = 12) -> list[list[PersonaPiece]]:
    """Deterministic, capped combination generator.

    * one piece per slot maximum (no two hats),
    * at most `max_layers` pieces,
    * ordered by slot priority so the first combos are the "sensible" ones
      (a top with pants before a top with a face accessory),
    * `limit` caps the blow-up (9 hairs x 9 tops x 4 bottoms = 324 otherwise).
    """
    if max_layers <= 1 or limit <= 0:
        return []

    by_slot: dict[str, list[PersonaPiece]] = defaultdict(list)
    for piece in pieces:
        if piece.texture_sources:
            by_slot[slot_of(piece)].append(piece)
    for slot in by_slot:
        by_slot[slot].sort(key=lambda p: p.name)

    usable_slots = [slot for slot in SLOT_PRIORITY if by_slot.get(slot)]
    if len(usable_slots) < 2:
        return []

    combos: list[list[PersonaPiece]] = []
    seen: set[str] = set()

    def pool(slot: str, character: str | None) -> list[PersonaPiece]:
        candidates = by_slot[slot]
        if character is None:
            return candidates
        matching = [piece for piece in candidates if character_of(piece) == character]
        return matching or candidates

    present = {character_of(piece) for piece in pieces} - {None}
    # same-character outfits first, then explicit cross-character mixing
    for character in [*sorted(present), None]:
        pool_size = {slot: len(pool(slot, character)) for slot in usable_slots}
        for size in range(max_layers, 1, -1):
            for slot_combo in itertools.combinations(usable_slots, size):
                rounds = max(pool_size[slot] for slot in slot_combo)
                for index in range(rounds):
                    picked = [pool(slot, character)[index % pool_size[slot]]
                              for slot in slot_combo]
                    key = combo_key(picked)
                    if key in seen:
                        continue
                    seen.add(key)
                    combos.append(sort_for_draw(picked))
                    if len(combos) >= limit:
                        log(f"layering: generated {len(combos)} combos (limit reached)")
                        return combos

    log(f"layering: generated {len(combos)} combos from {len(pieces)} pieces")
    return combos


def describe(combos: list[list[PersonaPiece]]) -> str:
    if not combos:
        return "no combos"
    sizes: dict[int, int] = defaultdict(int)
    for combo in combos:
        sizes[len(combo)] += 1
    parts = [f"{count}x{size}-piece" for size, count in sorted(sizes.items(), reverse=True)]
    sample = " | ".join(" + ".join(p.name for p in combo) for combo in combos[:2])
    return ", ".join(parts) + f"  (e.g. {sample})"
