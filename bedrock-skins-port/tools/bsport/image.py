"""Minimal stdlib-only image toolkit (PNG in/out + TGA in).

The Java mod uses javax.imageio + a hand written TGA reader.  We need the same
capabilities without any third party dependency so the converter runs on a bare
python3 inside GitHub Actions / the user's machine:

  * PNG   -> read (8-bit gray/RGB/palette/RGBA) + write (RGBA)
  * TGA   -> read (24/32bpp, raw + RLE), used by the Persona *_tintmap.tga files
  * Image -> RGBA canvas with SrcOver compositing and rect blits

Everything is top-left origin, matching PNG row order.  The TGA reader flips
bottom-left origin files like the Java `TgaReader` does.
"""

from __future__ import annotations

import struct
import zlib

__all__ = ["Image", "read_png", "write_png", "read_tga", "read_image"]


class Image:
    """Tiny RGBA raster.  `data` is a bytearray of width*height*4 bytes."""

    __slots__ = ("width", "height", "data")

    def __init__(self, width: int, height: int, fill=(0, 0, 0, 0)):
        self.width = int(width)
        self.height = int(height)
        self.data = bytearray(bytes(fill) * (self.width * self.height))

    @classmethod
    def from_rgba_bytes(cls, width: int, height: int, data: bytes) -> "Image":
        img = cls(width, height)
        img.data = bytearray(data)
        return img

    # ------------------------------------------------------------------ pixels
    def get(self, x: int, y: int):
        i = (y * self.width + x) * 4
        return tuple(self.data[i:i + 4])

    def put(self, x: int, y: int, color) -> None:
        if 0 <= x < self.width and 0 <= y < self.height:
            i = (y * self.width + x) * 4
            self.data[i:i + 4] = bytes(color)

    # ------------------------------------------------------------------- blit
    def blit(self, src: "Image", dx: int, dy: int,
             sx: int = 0, sy: int = 0, w: int | None = None, h: int | None = None,
             composite: bool = True) -> None:
        """Copy a rect.  `composite=False` behaves like AlphaComposite.Src."""
        w = src.width if w is None else w
        h = src.height if h is None else h
        for row in range(h):
            ty = dy + row
            if ty < 0 or ty >= self.height:
                continue
            syy = sy + row
            if syy < 0 or syy >= src.height:
                continue
            for col in range(w):
                tx = dx + col
                if tx < 0 or tx >= self.width:
                    continue
                sxx = sx + col
                if sxx < 0 or sxx >= src.width:
                    continue
                si = (syy * src.width + sxx) * 4
                sr, sg, sb, sa = src.data[si], src.data[si + 1], src.data[si + 2], src.data[si + 3]
                if sa == 0 and composite:
                    continue
                di = (ty * self.width + tx) * 4
                if not composite or sa == 255:
                    self.data[di:di + 4] = bytes((sr, sg, sb, sa))
                    continue
                dr, dg, db, da = self.data[di:di + 4]
                # straight-alpha SrcOver
                out_a = sa + da * (255 - sa) // 255
                if out_a == 0:
                    self.data[di:di + 4] = b"\0\0\0\0"
                    continue
                for k, (s, d) in enumerate(((sr, dr), (sg, dg), (sb, db))):
                    val = (s * sa + d * da * (255 - sa) // 255) // out_a
                    self.data[di + k] = max(0, min(255, val))
                self.data[di + 3] = out_a

    def subimage(self, x: int, y: int, w: int, h: int) -> "Image":
        out = Image(w, h)
        out.blit(self, 0, 0, x, y, w, h, composite=False)
        return out

    def tint(self, mask_rgb, color=(255, 255, 255)) -> None:
        """Multiply `color` into every pixel where the mask is opaque.

        Java does this on the GPU with a tint mask texture; for a baked skin
        texture we can fold it in directly as long as we ship one variant per
        colour -- which is exactly what a skin pack needs.
        """
        mr, mg, mb = mask_rgb[:3]
        cr, cg, cb = color[:3]
        for i in range(0, len(self.data), 4):
            a = self.data[i + 3]
            mask_alpha = mask_rgb.data[i + 3] if isinstance(mask_rgb, Image) else 255
            if mr or mg or mb or mask_alpha:
                self.data[i] = self.data[i] * cr // 255
                self.data[i + 1] = self.data[i + 1] * cg // 255
                self.data[i + 2] = self.data[i + 2] * cb // 255
            _ = a


# --------------------------------------------------------------------- PNG read
def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c


def _unpack_subbyte(line, width: int, bit_depth: int):
    per_byte = 8 // bit_depth
    mask = (1 << bit_depth) - 1
    out = []
    for x in range(width):
        byte = line[x // per_byte]
        shift = 8 - bit_depth * ((x % per_byte) + 1)
        out.append((byte >> shift) & mask)
    return out


def read_png(path: str) -> Image:
    with open(path, "rb") as handle:
        blob = handle.read()
    if blob[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"not a PNG: {path}")

    pos = 8
    width = height = 0
    bit_depth = color_type = interlace = 0
    palette = b""
    palette_alpha = b""
    idat = bytearray()
    while pos < len(blob):
        length = struct.unpack(">I", blob[pos:pos + 4])[0]
        ctype = blob[pos + 4:pos + 8]
        body = blob[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            width, height, bit_depth, color_type, _comp, _filt, interlace = struct.unpack(">IIBBBBB", body)
        elif ctype == b"PLTE":
            palette = body
        elif ctype == b"tRNS":
            palette_alpha = body
        elif ctype == b"IDAT":
            idat += body
        elif ctype == b"IEND":
            break

    if interlace != 0:
        raise ValueError(f"unsupported interlaced PNG: {path}")
    if color_type not in (0, 2, 3, 4, 6):
        raise ValueError(f"unsupported PNG colour type {color_type}: {path}")
    if bit_depth not in (1, 2, 4, 8):
        raise ValueError(f"unsupported PNG depth {bit_depth}: {path}")
    if bit_depth < 8 and color_type not in (0, 3):
        raise ValueError(f"sub-byte depth only valid for gray/palette: {path}")

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color_type]
    raw = zlib.decompress(bytes(idat))
    stride = (width * channels * bit_depth + 7) // 8
    prev = bytearray(stride)
    out = bytearray(width * height * 4)
    src = 0
    for y in range(height):
        ftype = raw[src]
        src += 1
        line = bytearray(raw[src:src + stride])
        src += stride
        if ftype == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif ftype == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                upleft = prev[i - channels] if i >= channels else 0
                line[i] = (line[i] + _paeth(left, prev[i], upleft)) & 0xFF
        prev = line

        if bit_depth < 8:
            samples = _unpack_subbyte(line, width, bit_depth)
            base = y * width * 4
            if color_type == 3:
                for x in range(width):
                    idx = samples[x] * 3
                    alpha = palette_alpha[samples[x]] if samples[x] < len(palette_alpha) else 255
                    out[base + x * 4:base + x * 4 + 4] = bytes(
                        (palette[idx], palette[idx + 1], palette[idx + 2], alpha))
            else:
                scale = 255 // ((1 << bit_depth) - 1)
                for x in range(width):
                    v = samples[x] * scale
                    out[base + x * 4:base + x * 4 + 4] = bytes((v, v, v, 255))
            prev = line
            continue

        base = y * width * 4
        if color_type == 6:
            out[base:base + width * 4] = line
        elif color_type == 2:
            for x in range(width):
                out[base + x * 4:base + x * 4 + 3] = line[x * 3:x * 3 + 3]
                out[base + x * 4 + 3] = 255
        elif color_type == 0:
            for x in range(width):
                v = line[x]
                out[base + x * 4:base + x * 4 + 4] = bytes((v, v, v, 255))
        elif color_type == 4:
            for x in range(width):
                v = line[x * 2]
                out[base + x * 4:base + x * 4 + 4] = bytes((v, v, v, line[x * 2 + 1]))
        else:  # palette
            for x in range(width):
                idx = line[x] * 3
                alpha = palette_alpha[line[x]] if line[x] < len(palette_alpha) else 255
                out[base + x * 4:base + x * 4 + 4] = bytes(
                    (palette[idx], palette[idx + 1], palette[idx + 2], alpha))
    return Image.from_rgba_bytes(width, height, out)


# -------------------------------------------------------------------- PNG write
def write_png(path: str, image: Image) -> None:
    raw = bytearray()
    stride = image.width * 4
    for y in range(image.height):
        raw.append(0)  # filter type 0
        raw += image.data[y * stride:(y + 1) * stride]

    def chunk(tag: bytes, body: bytes) -> bytes:
        return (struct.pack(">I", len(body)) + tag + body
                + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", image.width, image.height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as handle:
        handle.write(png)


# --------------------------------------------------------------------- TGA read
def read_tga(path: str) -> Image:
    with open(path, "rb") as handle:
        blob = handle.read()
    if len(blob) < 18:
        raise ValueError(f"truncated TGA: {path}")
    id_len, cmap_type, img_type = blob[0], blob[1], blob[2]
    width, height = struct.unpack("<HH", blob[12:16])
    bpp = blob[16]
    descriptor = blob[17]
    if img_type not in (2, 10) or bpp not in (24, 32):
        raise ValueError(f"unsupported TGA (type={img_type}, bpp={bpp}): {path}")

    pos = 18 + id_len + (struct.unpack("<H", blob[5:7])[0] * (blob[7] // 8) if cmap_type else 0)
    step = bpp // 8
    pixels = bytearray(width * height * step)
    if img_type == 2:
        pixels[:] = blob[pos:pos + len(pixels)]
    else:  # RLE
        out = 0
        while out < len(pixels):
            packet = blob[pos]
            pos += 1
            count = (packet & 0x7F) + 1
            if packet & 0x80:
                px = blob[pos:pos + step]
                pos += step
                for _ in range(count):
                    pixels[out:out + step] = px
                    out += step
            else:
                chunk = blob[pos:pos + step * count]
                pos += step * count
                pixels[out:out + len(chunk)] = chunk
                out += len(chunk)

    img = Image(width, height)
    bottom_up = not (descriptor & 0x20)
    for y in range(height):
        ty = (height - 1 - y) if bottom_up else y
        for x in range(width):
            si = (ty * width + x) * step
            b, g, r = pixels[si], pixels[si + 1], pixels[si + 2]
            a = pixels[si + 3] if step == 4 else 255
            img.put(x, y, (r, g, b, a))
    return img


def read_image(path: str) -> Image:
    lower = path.lower()
    if lower.endswith(".png"):
        return read_png(path)
    if lower.endswith(".tga"):
        return read_tga(path)
    raise ValueError(f"unsupported image type: {path}")
