"""Paths, the pipe palette and the file and colour helpers every generator module uses."""
import json
import os
import struct
import zlib

MODID = "flowline"
ROOT = os.path.join("src", "main", "resources")
# Built-in resource pack (Options > Resource Packs) that swaps the see-through pipe walls for solid ones.
SOLID_PACK = "resourcepacks/solid_pipes"
TYPES = {"item": (0xF4, 0xD5, 0x8D), "fluid": (0x7F, 0xB7, 0xE6), "energy": (0xF2, 0xA0, 0x7B),
         "universal": (0xB7, 0x9C, 0xE8), "chemical": (0x8F, 0xD1, 0xA4)}
ACCENTS = {"item": (0xFF, 0xF1, 0xC9), "fluid": (0xCF, 0xE8, 0xFF), "energy": (0xFF, 0xD3, 0xBC),
           "universal": (0xE4, 0xD6, 0xFF), "chemical": (0xD2, 0xF5, 0xDD)}
# pipes that only exist with another mod installed
OPTIONAL = {"chemical": "mekanism"}


def write(path, text):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        f.write(text)


def write_json(path, obj):
    write(path, json.dumps(obj, indent=2, ensure_ascii=False) + "\n")


def write_png(path, pixels, w=16, h=16):
    """pixels: list of rows, each a list of (r,g,b,a)."""
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "wb") as f:
        f.write(png)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def noise(x, y, seed):
    h = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 0xFF) / 255.0


CLEAR = (0, 0, 0, 0)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def mixa(a, b, t):
    return mix(a, b, t) + (255,)


# Clean/modern look: satin panels with a bright rim, crisp grooves and a dark framed port. TYPES holds each pipe's
# base colour, ACCENTS the lighter tone used for rims and rivets. UV layout (window, collar frame, port) is fixed by the models.
