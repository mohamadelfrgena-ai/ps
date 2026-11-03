#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""قارئ أرشيف ZIP مقسّم إلى أقراص (multi-disk spanning ZIP).

الخلفية:
    الملفات news.z01 .. news.z07 + news.zip هي **أقراص** أرشيف واحد، وليس أجزاءً
    يُدمج بعضها ببعض: كل مدخل في الفهرس المركزي يحمل رقم القرص الذي يقع فيه
    وإزاحةً مرجعها بداية ذلك القرص. لذلك:
      • دمج الأجزاء في ملف واحد (cat) يكسر كل الإزاحات،
      • و`unzip` الحديث لا يدعم الأقراص المتعددة فعلياً (يُحذّر ثم يفشل).

الحل هنا: نقرأ الفهرس من القرص الأخير، ثم نقرأ بيانات كل ملف من قرصه الصحيح
مع دعم الملفات التي تعبر حدود قرصين، ونفكّ الضغط بـzlib.

الاستخدام من سطر الأوامر:
    python3 tools/spanned_zip.py <مجلد الأرشيف> <news.zip> <مجلد الإخراج>
"""
import os
import struct
import sys
import zlib

EOCD = b"PK\x05\x06"
EOCD64_LOC = b"PK\x06\x07"
EOCD64 = b"PK\x06\x06"
CENTRAL = b"PK\x01\x02"
LOCAL = b"PK\x03\x04"


class SpannedZip:
    """أرشيف مقسّم: القرص 0 = news.z01 … والقرص الأخير = news.zip."""

    def __init__(self, folder: str, last_name: str):
        self.folder = folder
        self.last_name = last_name
        self.disk_files = self._disk_names(folder, last_name)
        self._disk_sizes = {}
        self._handles = {}
        self._read_eocd()
        self.entries = self._read_central_directory()

    # ------------------------------------------------------------------ الأقراص
    @staticmethod
    def _disk_names(folder: str, last_name: str):
        base = last_name[:-4] if last_name.lower().endswith(".zip") else last_name
        names, i = [], 1
        while os.path.exists(os.path.join(folder, f"{base}.z{i:02d}")):
            names.append(f"{base}.z{i:02d}")
            i += 1
        names.append(last_name)
        return names

    def _handle(self, disk: int):
        if disk not in self._handles:
            self._handles[disk] = open(os.path.join(self.folder, self.disk_files[disk]), "rb")
        return self._handles[disk]

    def disk_size(self, disk: int) -> int:
        if disk not in self._disk_sizes:
            self._disk_sizes[disk] = os.path.getsize(os.path.join(self.folder, self.disk_files[disk]))
        return self._disk_sizes[disk]

    def read(self, disk: int, offset: int, length: int) -> bytes:
        """قراءة متصلة تتجاوز نهاية قرص إلى بداية القرص التالي."""
        out = bytearray()
        while length > 0:
            if disk >= len(self.disk_files):
                raise EOFError("انتهت أقراص الأرشيف قبل إكمال القراءة")
            available = self.disk_size(disk) - offset
            if available <= 0:
                disk, offset = disk + 1, 0
                continue
            take = min(available, length)
            fh = self._handle(disk)
            fh.seek(offset)
            chunk = fh.read(take)
            if not chunk:
                raise EOFError("قراءة فارغة من القرص")
            out += chunk
            length -= len(chunk)
            offset += len(chunk)
        return bytes(out)

    def close(self):
        for fh in self._handles.values():
            fh.close()
        self._handles.clear()

    # ------------------------------------------------------------ الفهرس المركزي
    def _read_eocd(self):
        last = len(self.disk_files) - 1
        tail_len = min(self.disk_size(last), 1 << 20)
        tail = self.read(last, self.disk_size(last) - tail_len, tail_len)
        i = tail.rfind(EOCD)
        if i < 0:
            raise ValueError("لم أجد نهاية الفهرس (EOCD) في القرص الأخير")
        (_sig, _disk_no, cd_disk, _n_disk, n_total, cd_size, cd_off, _comment) = struct.unpack(
            "<IHHHHIIH", tail[i:i + 22]
        )
        # دعم ZIP64 إن كانت القيم مُشبعة
        if cd_off == 0xFFFFFFFF or n_total == 0xFFFF:
            j = tail.rfind(EOCD64_LOC, 0, i)
            if j >= 0:
                loc_disk, loc_off = struct.unpack("<IQ", tail[j + 4:j + 16])
                rec = self.read(loc_disk, loc_off, 56)
                if rec[:4] == EOCD64:
                    n_total = struct.unpack("<Q", rec[32:40])[0]
                    cd_size = struct.unpack("<Q", rec[40:48])[0]
                    cd_off = struct.unpack("<Q", rec[48:56])[0]
        self.n_total, self.cd_size, self.cd_off, self.cd_disk = n_total, cd_size, cd_off, cd_disk

    def _read_central_directory(self):
        raw = self.read(self.cd_disk, self.cd_off, self.cd_size)
        entries, pos = [], 0
        while pos + 46 <= len(raw) and raw[pos:pos + 4] == CENTRAL:
            (_sig, _vm, _vn, _fl, method, _t, _d, crc, csize, usize, nlen, elen, clen,
             disk_start, _iattr, _eattr, lho) = struct.unpack("<IHHHHHHIIIHHHHHII", raw[pos:pos + 46])
            name = raw[pos + 46:pos + 46 + nlen].decode("utf-8", "replace")
            extra = raw[pos + 46 + nlen:pos + 46 + nlen + elen]
            pos += 46 + nlen + elen + clen

            if csize == 0xFFFFFFFF or usize == 0xFFFFFFFF or lho == 0xFFFFFFFF or disk_start == 0xFFFF:
                k = 0
                while k + 4 <= len(extra):
                    hid, hlen = struct.unpack("<HH", extra[k:k + 4])
                    body = extra[k + 4:k + 4 + hlen]
                    if hid == 0x0001:
                        q = 0
                        if usize == 0xFFFFFFFF:
                            usize = struct.unpack("<Q", body[q:q + 8])[0]; q += 8
                        if csize == 0xFFFFFFFF:
                            csize = struct.unpack("<Q", body[q:q + 8])[0]; q += 8
                        if lho == 0xFFFFFFFF:
                            lho = struct.unpack("<Q", body[q:q + 8])[0]; q += 8
                        if disk_start == 0xFFFF:
                            disk_start = struct.unpack("<I", body[q:q + 4])[0]
                    k += 4 + hlen

            entries.append(dict(name=name, method=method, crc=crc, csize=csize,
                                usize=usize, disk=disk_start, offset=lho))
        return entries

    # --------------------------------------------------------------- الاستخراج
    def read_entry(self, entry: dict) -> bytes:
        head = self.read(entry["disk"], entry["offset"], 30)
        if head[:4] != LOCAL:
            raise ValueError("رأس ملف محلي غير صالح")
        nlen, elen = struct.unpack("<HH", head[26:30])
        data_off = entry["offset"] + 30 + nlen + elen
        blob = self.read(entry["disk"], data_off, entry["csize"])
        if entry["method"] == 0:
            return blob
        if entry["method"] == 8:
            return zlib.decompressobj(-15).decompress(blob)
        raise ValueError(f"طريقة ضغط غير مدعومة: {entry['method']}")

    def extract(self, out_dir: str, on_error=None):
        ok = failed = 0
        for entry in self.entries:
            target = os.path.join(out_dir, entry["name"])
            if entry["name"].endswith("/"):
                os.makedirs(target, exist_ok=True)
                continue
            os.makedirs(os.path.dirname(target), exist_ok=True)
            try:
                content = self.read_entry(entry)
                with open(target, "wb") as fh:
                    fh.write(content)
                ok += 1
            except Exception as exc:  # noqa: BLE001
                failed += 1
                if on_error:
                    on_error(entry["name"], exc)
        return ok, failed


def main() -> int:
    folder = sys.argv[1] if len(sys.argv) > 1 else "."
    last = sys.argv[2] if len(sys.argv) > 2 else "news.zip"
    out = sys.argv[3] if len(sys.argv) > 3 else "extracted"
    z = SpannedZip(folder, last)
    print(f"الأقراص: {len(z.disk_files)} · الملفات: {len(z.entries)}")
    os.makedirs(out, exist_ok=True)
    ok, failed = z.extract(out, on_error=lambda n, e: print(f"  ✗ {n} → {e}"))
    z.close()
    print(f"✓ استُخرج {ok} ملفاً · فشل {failed}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
