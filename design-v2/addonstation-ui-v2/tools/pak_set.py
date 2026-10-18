#!/usr/bin/env python3
"""Add or replace entries in assets/ui.pak (format ASUI0001) without rebuilding it.

  python3 tools/pak_set.py assets/ui.pak bg_winter=assets/bg_winter.jpg \
          bg_splash=assets/bg_splash.png qr_app=my_qr.png

Names used by UI v2:
  bg_winter  winter-sale banner (Home hero, Deals header, Store strip)
  bg_welcome welcome page art (never mirrored)
  bg_splash  email sign-in background
  bg_cave    code-link screen background
  qr_app     QR code of the app-download page (Home card + Plans screen). Square PNG, 512x512 or larger.
"""
import struct, sys

def read_pak(path):
    d = open(path, "rb").read()
    assert d[:8] == b"ASUI0001", "not an ASUI0001 pak"
    n = struct.unpack_from("<I", d, 8)[0]
    off, items = 12, []
    for _ in range(n):
        nl, dl = struct.unpack_from("<HI", d, off); off += 6
        name = d[off:off + nl].decode(); off += nl
        items.append([name, d[off:off + dl]]); off += dl
    return items

def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    path, sets = sys.argv[1], sys.argv[2:]
    items = read_pak(path)
    index = {n: i for i, (n, _) in enumerate(items)}
    for s in sets:
        name, file = s.split("=", 1)
        data = open(file, "rb").read()
        if name in index: items[index[name]][1] = data; print("replaced", name, len(data), "bytes")
        else: items.append([name, data]); index[name] = len(items) - 1; print("added   ", name, len(data), "bytes")
    out = b"ASUI0001" + struct.pack("<I", len(items))
    for n, d in items:
        nb = n.encode(); out += struct.pack("<HI", len(nb), len(d)) + nb + d
    open(path, "wb").write(out)
    print("ok:", len(items), "entries")

main()
