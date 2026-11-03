"""Shared helpers: JSON IO, stable UUIDv5 ids, slugs, progress logging."""

from __future__ import annotations

import json
import os
import re
import uuid

NAMESPACE = uuid.uuid5(uuid.NAMESPACE_DNS, "bedrockskins.port.mohamadelfrgena")

_LOG_PREFIX = "[bsport]"


def log(message: str) -> None:
    print(f"{_LOG_PREFIX} {message}", flush=True)


def warn(message: str) -> None:
    print(f"{_LOG_PREFIX} !! {message}", flush=True)


def ensure_dir(path: str) -> str:
    os.makedirs(path, exist_ok=True)
    return path


def read_json(path: str):
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: str, payload, *, pretty: bool = True) -> None:
    ensure_dir(os.path.dirname(path) or ".")
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(payload, handle, indent=2 if pretty else None,
                  ensure_ascii=False, separators=None if pretty else (",", ":"))
        handle.write("\n")


def stable_uuid(*parts: str) -> str:
    """Deterministic UUID so re-running the build never breaks a world."""
    return str(uuid.uuid5(NAMESPACE, "/".join(parts)))


def slug(value: str, *, lower: bool = True) -> str:
    cleaned = re.sub(r"[^a-zA-Z0-9_.\-]+", "_", value.strip())
    cleaned = re.sub(r"_+", "_", cleaned).strip("_")
    if lower:
        cleaned = cleaned.lower()
    return cleaned or "unnamed"


def snake(value: str) -> str:
    value = re.sub(r"(?<!^)(?=[A-Z])", "_", value)
    return slug(value)


def rel(path: str) -> str:
    return path.replace(os.sep, "/")


def human_size(count: int) -> str:
    size = float(count)
    for unit in ("B", "KB", "MB", "GB"):
        if size < 1024 or unit == "GB":
            return f"{size:.1f}{unit}"
        size /= 1024
    return f"{size:.1f}GB"


class Progress:
    """Very small counter so long builds print something readable."""

    def __init__(self, total: int, label: str):
        self.total = max(1, total)
        self.done = 0
        self.label = label
        self.step = max(1, self.total // 10)

    def tick(self, note: str = "") -> None:
        self.done += 1
        if self.done % self.step == 0 or self.done == self.total:
            pct = 100 * self.done // self.total
            suffix = f" {note}" if note else ""
            log(f"{self.label}: {self.done}/{self.total} ({pct}%){suffix}")
