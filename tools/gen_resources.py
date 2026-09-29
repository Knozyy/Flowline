#!/usr/bin/env python3
"""Generates blockstates, models, textures, recipes, loot tables and lang files under src/main/resources.

Run from the repo root:  python3 tools/gen_resources.py
This branch targets Minecraft 1.20.1 / Forge: plural data folders, {"item": ...} recipe results, forge: conditions.
Pure standard library; textures are simple placeholders meant to be replaced by real art.
"""
import json
import os
import struct
import zlib

MODID = "flowline"
ROOT = os.path.join("src", "main", "resources")
TYPES = {"item": (0xE0, 0x8A, 0x2B), "fluid": (0x2F, 0x7F, 0xE0), "energy": (0xD8, 0x3A, 0x3A),
         "universal": (0x9C, 0x7B, 0xD8), "chemical": (0x7C, 0xD9, 0x57)}
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
    return tuple(max(0, min(255, int(v * f))) for v in c) + (255,)


# ---------------------------------------------------------------- textures
def noise(x, y, seed):
    h = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 0xFF) / 255.0


CLEAR = (0, 0, 0, 0)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def metal(base, x, y, seed):
    """Brushed metal in the pipe colour: soft vertical gradient, fine noise, bevelled tube edges at 5 and 10."""
    steel = (0x9A, 0xA1, 0xAC)
    c = mix(steel, base, 0.72)
    f = 0.9 + 0.12 * noise(x, y, seed) + 0.06 * (1 - abs(7.5 - y) / 7.5)
    if x in (5, 10) or y in (5, 10):
        f *= 0.7                          # tube edges
    elif x in (6, 11) or y in (6, 11):
        f *= 1.18                         # highlight next to the edge
    if x in (0, 15) or y in (0, 15):
        f *= 0.8
    return shade(c, f)


for idx, (name, base) in enumerate(TYPES.items()):
    seed = idx + 1
    # solid pipe wall: brushed metal with a light seam along the middle
    rows = [[metal(base, x, y, seed) for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            if (x in (7, 8) and y <= 10) or (y in (7, 8) and x <= 10):
                rows[y][x] = shade(mix(base, (0xFF, 0xFF, 0xFF), 0.18), 0.95 + 0.1 * noise(x, y, seed + 5))
    if name == "universal":
        # three kind markers in the corners of the core faces
        for (px, py), kind in (((5, 5), "item"), ((10, 5), "fluid"), ((5, 10), "energy")):
            rows[py][px] = shade(TYPES[kind], 1.15)
    write_png(f"assets/{MODID}/textures/block/{name}_pipe.png", rows)

    # solid collar where the pipe meets a block, and for the item model
    collar = [[metal(base, x, y, seed + 7) for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            if x in (4, 11) or y in (4, 11):
                collar[y][x] = shade(mix(base, (0x20, 0x22, 0x28), 0.55), 1.0)
            elif 7 <= x <= 8 and 7 <= y <= 8:
                collar[y][x] = shade(base, 0.4)            # port opening
    write_png(f"assets/{MODID}/textures/block/{name}_pipe_collar.png", collar)

    # extract collar: a bright green ring (same "extract" colour on every pipe) around the pipe's own colour
    green = (0x5A, 0xE0, 0x7A)
    ring_rows = []
    for y in range(16):
        ring_row = []
        for x in range(16):
            inside = 3 <= x <= 12 and 3 <= y <= 12
            edge = inside and (x in (3, 4, 11, 12) or y in (3, 4, 11, 12))
            if edge:
                f = 1.25 if (x == 3 or y == 3) else 0.8 if (x == 12 or y == 12) else 1.0
                ring_row.append(shade(green, f))
            else:
                ring_row.append(collar[y][x])
        ring_rows.append(ring_row)
    write_png(f"assets/{MODID}/textures/block/{name}_pipe_extract.png", ring_rows)

# dye band around the core: a light frame tinted with the pipe's colour (grey when undyed)
band = [[CLEAR] * 16 for _ in range(16)]
for y in range(5, 11):
    for x in range(5, 11):
        if x in (5, 10) or y in (5, 10):
            band[y][x] = shade((0xF0, 0xF0, 0xF0), 1.0 if (x == 5 or y == 5) else 0.82)
write_png(f"assets/{MODID}/textures/block/pipe_band.png", band)

GREY = (0xB4, 0xB4, 0xBC)


def ascii_icon(art, palette):
    """art: 16 strings; '.' is transparent. A dark outline is added around opaque pixels."""
    grid = [[palette[ch] if ch != "." else None for ch in row.ljust(16, ".")[:16]] for row in art]
    out = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            c = grid[y][x]
            if c is not None:
                # light from the top-left: brighten pixels whose upper/left neighbour is empty
                up = grid[y - 1][x] if y > 0 else None
                left = grid[y][x - 1] if x > 0 else None
                f = 1.2 if (up is None or left is None) else 1.0
                out[y][x] = shade(c, f)
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and grid[ny][nx] is not None:
                    out[y][x] = (0x22, 0x22, 0x26, 255)
                    break
    return out


def wrench_texture():
    """Open-end wrench: a ring head open towards the upper right, a steel shaft and a striped orange grip."""
    import math
    grid = [[None] * 16 for _ in range(16)]
    cx, cy, opening = 11.0, 4.6, math.atan2(-1, 1)
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            if 1.7 <= math.hypot(dx, dy) <= 3.9:
                diff = abs((math.atan2(dy, dx) - opening + math.pi) % (2 * math.pi) - math.pi)
                if diff > 0.62:
                    grid[y][x] = "m"
    ax, ay, bx, by = 9.3, 6.7, 2.4, 13.6
    for y in range(16):
        for x in range(16):
            X, Y = x + 0.5, y + 0.5
            t = max(0, min(1, ((X - ax) * (bx - ax) + (Y - ay) * (by - ay)) / ((bx - ax) ** 2 + (by - ay) ** 2)))
            if math.hypot(X - (ax + t * (bx - ax)), Y - (ay + t * (by - ay))) <= 1.15:
                grid[y][x] = "g" if t > 0.45 else "m"
    light, mid, dark = (0xDC, 0xE2, 0xEA), (0xA0, 0xA9, 0xB5), (0x5E, 0x67, 0x73)
    grip, grip_dark, outline = (0xF0, 0x9A, 0x3A), (0xB0, 0x62, 0x1A), (0x1E, 0x20, 0x26, 255)
    out = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            c = grid[y][x]
            if c is None:
                if any(0 <= x + ex < 16 and 0 <= y + ey < 16 and grid[y + ey][x + ex]
                       for ex, ey in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out[y][x] = outline
                continue
            up = grid[y - 1][x] if y > 0 else None
            left = grid[y][x - 1] if x > 0 else None
            down = grid[y + 1][x] if y < 15 else None
            right = grid[y][x + 1] if x < 15 else None
            if c == "m":
                col = light if (up is None or left is None) else dark if (down is None or right is None) else mid
            else:
                col = grip_dark if (x + y) % 3 == 0 else grip
                if up is None or left is None:
                    col = tuple(min(255, int(v * 1.15)) for v in col)
            out[y][x] = col + (255,)
    return out


write_png(f"assets/{MODID}/textures/item/wrench.png", wrench_texture())

def upgrade_chip(symbol):
    """Circuit chip with pins; `symbol` is a set of (x, y) pixels drawn in the accent colour."""
    art = [list("................") for _ in range(16)]
    for y in range(2, 14):
        for x in range(3, 13):
            art[y][x] = "B"
    for y in (3, 6, 9, 12):                    # pins
        art[y][2] = "P"
        art[y][13] = "P"
    for x, y in symbol:
        art[y][x] = "A"
    return ["".join(r) for r in art]


def chevrons(x0s, y0, height):
    half = height // 2
    return {(x0 + half - abs(half - dy), y0 + dy) for x0 in x0s for dy in range(height)}


def bars(rows):
    return {(x, y) for y, (a, b) in rows for x in range(a, b + 1)}


UPGRADE_ART = {
    "speed_upgrade": (chevrons((4, 8), 4, 7), (0x4A, 0xE6, 0xF0)),
    "stack_upgrade": (bars([(4, (6, 11)), (5, (6, 11)), (7, (5, 10)), (8, (5, 10)), (10, (4, 9)), (11, (4, 9))]),
                      (0xF0, 0x9A, 0x3A)),
    "filter_upgrade": (bars([(4, (4, 11)), (5, (5, 10)), (6, (6, 9)), (7, (7, 8)), (8, (7, 8)), (9, (7, 8)),
                             (10, (7, 8))]), (0x5A, 0xE0, 0x7A)),
    "knozy_upgrade": (chevrons((5, 8), 3, 5) | bars([(9, (4, 11)), (11, (4, 11))]), (0xD2, 0x4A, 0xF5)),
}
for up_name, (symbol, accent) in UPGRADE_ART.items():
    write_png(f"assets/{MODID}/textures/item/{up_name}.png",
              ascii_icon(upgrade_chip(symbol), {"B": (0x3A, 0x4A, 0x5A), "P": (0xB0, 0xB0, 0xB8), "A": accent}))


def card(symbol):
    """A punched memory card: rounded body, contact strip at the bottom, `symbol` pixels in the accent colour."""
    art = [list("................") for _ in range(16)]
    for y in range(2, 15):
        for x in range(2, 14):
            if (x, y) in ((2, 2), (13, 2), (2, 14), (13, 14)):
                continue
            art[y][x] = "B"
    for x in range(4, 12, 2):
        art[12][x] = "P"
        art[13][x] = "P"
    art[3][11] = "."                         # punched corner hole
    for x, y in symbol:
        art[y][x] = "A"
    return ["".join(r) for r in art]


CARD_ART = {
    # a gear: copies every setting
    "config_card": ({(7, 4), (8, 4), (5, 5), (10, 5), (6, 5), (9, 5), (5, 6), (6, 6), (9, 6), (10, 6), (4, 7), (5, 7),
                     (10, 7), (11, 7), (4, 8), (5, 8), (10, 8), (11, 8), (5, 9), (6, 9), (9, 9), (10, 9), (6, 10),
                     (9, 10), (7, 10), (8, 10), (7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)},
                    (0x4A, 0xC8, 0xF0)),
    # a funnel: copies only the filter
    "filter_card": (bars([(4, (4, 11)), (5, (5, 10)), (6, (6, 9)), (7, (7, 8)), (8, (7, 8)), (9, (7, 8))]),
                    (0x5A, 0xE0, 0x7A)),
}
for card_name, (symbol, accent) in CARD_ART.items():
    write_png(f"assets/{MODID}/textures/item/{card_name}.png",
              ascii_icon(card(symbol), {"B": (0x2E, 0x36, 0x44), "P": (0xE0, 0xC0, 0x50), "A": accent}))


def facade_texture():
    """A thin panel in a frame, seen at an angle: what a facade looks like before it has a block."""
    c = [[CLEAR] * 16 for _ in range(16)]
    stone = (0x8C, 0x8C, 0x90)
    for y in range(2, 14):
        for x in range(2, 14):
            f = 0.85 + 0.25 * noise(x, y, 99)
            if x in (2, 13) or y in (2, 13):
                c[y][x] = shade((0x5A, 0x60, 0x6A), 1.0)
            elif x in (3, 12) or y in (3, 12):
                c[y][x] = shade((0xC8, 0xCC, 0xD4), 1.0)
            else:
                c[y][x] = shade(stone, f)
    for y in range(4, 12):                 # pipe peeking through the middle
        for x in range(6, 10):
            if y in (7, 8) or x in (7, 8):
                c[y][x] = shade(TYPES["item"], 1.0 if x in (7, 8) and y in (7, 8) else 0.8)
    return c


write_png(f"assets/{MODID}/textures/item/facade.png", facade_texture())

# ---------------------------------------------------------------- models
ALL_FACES = ("down", "up", "north", "south", "west", "east")
CUTOUT = "minecraft:cutout"   # only the dye band around the core has transparent pixels



for name in TYPES:
    tex = f"{MODID}:block/{name}_pipe"
    textures = {"pipe": tex, "collar": f"{tex}_collar", "particle": f"{tex}_collar"}
    write_json(f"assets/{MODID}/models/block/{name}_pipe_core.json", {
        "render_type": CUTOUT,
        "textures": {**textures, "band": f"{MODID}:block/pipe_band"},
        "elements": [
            {"from": [5, 5, 5], "to": [11, 11, 11],
             "faces": {d: {"uv": [5, 5, 11, 11], "texture": "#pipe"} for d in ALL_FACES}},
            # dye band: a frame just outside the core, tinted with the pipe's colour
            {"from": [4.9, 4.9, 4.9], "to": [11.1, 11.1, 11.1],
             "faces": {d: {"uv": [5, 5, 11, 11], "texture": "#band", "tintindex": 0} for d in ALL_FACES}},
        ],
    })
    write_json(f"assets/{MODID}/models/block/{name}_pipe_arm.json", {
        "textures": textures,
        "elements": [{
            "from": [5, 5, 0], "to": [11, 11, 5],
            "faces": {
                "down": {"uv": [5, 0, 11, 5], "texture": "#pipe"},
                "up": {"uv": [5, 0, 11, 5], "texture": "#pipe"},
                "west": {"uv": [0, 5, 5, 11], "texture": "#pipe"},
                "east": {"uv": [0, 5, 5, 11], "texture": "#pipe"},
            },
        }],
    })
    write_json(f"assets/{MODID}/models/block/{name}_pipe_endpoint.json", {
        "textures": textures,
        "elements": [
            {
                "from": [5, 5, 2], "to": [11, 11, 5],
                "faces": {
                    "down": {"uv": [5, 0, 11, 3], "texture": "#pipe"},
                    "up": {"uv": [5, 0, 11, 3], "texture": "#pipe"},
                    "west": {"uv": [0, 5, 3, 11], "texture": "#pipe"},
                    "east": {"uv": [0, 5, 3, 11], "texture": "#pipe"},
                },
            },
            {
                "from": [4, 4, 0], "to": [12, 12, 2],
                "faces": {d: {"uv": [4, 4, 12, 12], "texture": "#collar"} for d in ALL_FACES},
            },
        ],
    })
    # extracting end: a larger, thicker collar with a green ring so it stands out from inserting ends
    write_json(f"assets/{MODID}/models/block/{name}_pipe_extract.json", {
        "textures": {**textures, "ring": f"{tex}_extract"},
        "elements": [
            {
                "from": [5, 5, 2.5], "to": [11, 11, 5],
                "faces": {
                    "down": {"uv": [5, 0, 11, 2.5], "texture": "#pipe"},
                    "up": {"uv": [5, 0, 11, 2.5], "texture": "#pipe"},
                    "west": {"uv": [0, 5, 2.5, 11], "texture": "#pipe"},
                    "east": {"uv": [0, 5, 2.5, 11], "texture": "#pipe"},
                },
            },
            {
                "from": [3, 3, 0], "to": [13, 13, 2.5],
                "faces": {
                    "north": {"uv": [3, 3, 13, 13], "texture": "#ring"},
                    "south": {"uv": [3, 3, 13, 13], "texture": "#ring"},
                    "down": {"uv": [3, 3, 13, 5.5], "texture": "#ring"},
                    "up": {"uv": [3, 3, 13, 5.5], "texture": "#ring"},
                    "west": {"uv": [3, 3, 5.5, 13], "texture": "#ring"},
                    "east": {"uv": [3, 3, 5.5, 13], "texture": "#ring"},
                },
            },
        ],
    })
    # inventory icon: a solid straight tube with collars at both ends
    write_json(f"assets/{MODID}/models/item/{name}_pipe.json", {
        "parent": "minecraft:block/block",
        "textures": {"collar": f"{tex}_collar", "particle": f"{tex}_collar"},
        "elements": [
            {"from": [5, 5, 2], "to": [11, 11, 14],
             "faces": {d: {"uv": [5, 5, 11, 11], "texture": "#collar"} for d in ALL_FACES}},
            {"from": [4, 4, 0], "to": [12, 12, 2],
             "faces": {d: {"uv": [4, 4, 12, 12], "texture": "#collar"} for d in ALL_FACES}},
            {"from": [4, 4, 14], "to": [12, 12, 16],
             "faces": {d: {"uv": [4, 4, 12, 12], "texture": "#collar"} for d in ALL_FACES}},
        ],
    })

    # blockstate: core is always drawn, each side adds an arm (pipe) or an arm+collar (endpoint)
    rot = {
        "north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270},
        "up": {"x": 270}, "down": {"x": 90},
    }
    multipart = [{"apply": {"model": f"{MODID}:block/{name}_pipe_core"}}]
    for side, r in rot.items():
        multipart.append({"when": {side: "pipe"}, "apply": {"model": f"{MODID}:block/{name}_pipe_arm", **r}})
        multipart.append({"when": {side: "endpoint"}, "apply": {"model": f"{MODID}:block/{name}_pipe_endpoint", **r}})
        multipart.append({"when": {side: "extract"}, "apply": {"model": f"{MODID}:block/{name}_pipe_extract", **r}})
    write_json(f"assets/{MODID}/blockstates/{name}_pipe.json", {"multipart": multipart})

    # loot table; optional pipes have none, their block drops itself (see PipeBlock#getDrops)
    if name not in OPTIONAL:
        write_json(f"data/{MODID}/loot_tables/blocks/{name}_pipe.json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1.0,
                "bonus_rolls": 0.0,
                "conditions": [{"condition": "minecraft:survives_explosion"}],
                "entries": [{"type": "minecraft:item", "name": f"{MODID}:{name}_pipe"}],
            }],
        })

for name in ("wrench", "speed_upgrade", "stack_upgrade", "filter_upgrade", "knozy_upgrade", "config_card",
             "filter_card", "facade"):
    write_json(f"assets/{MODID}/models/item/{name}.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{MODID}:item/{name}"},
    })

write_json("data/minecraft/tags/blocks/mineable/pickaxe.json", {
    "replace": False,
    # optional pipes are only registered with their mod, so they must not be required here
    "values": [f"{MODID}:{n}_pipe" if n not in OPTIONAL else {"id": f"{MODID}:{n}_pipe", "required": False}
               for n in TYPES],
})

# ---------------------------------------------------------------- recipes
def ing(item):
    return {"item": item}


def shapeless(name, ingredients, result, count=1, needs=None):
    recipe = {
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [ing(i) for i in ingredients],
        "result": {"item": result, "count": count},
    }
    if needs:
        recipe = {"conditions": [{"type": "forge:mod_loaded", "modid": needs}], **recipe}
    write_json(f"data/{MODID}/recipes/{name}.json", recipe)


def shaped(name, pattern, key, result, count=1):
    write_json(f"data/{MODID}/recipes/{name}.json", {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "pattern": pattern,
        "key": {k: ing(v) for k, v in key.items()},
        "result": {"item": result, "count": count},
    })


shapeless("item_pipe", ["minecraft:iron_ingot"] * 3 + ["minecraft:hopper"], f"{MODID}:item_pipe", 4)
shapeless("fluid_pipe", ["minecraft:iron_ingot"] * 3 + ["minecraft:bucket"], f"{MODID}:fluid_pipe", 4)
shapeless("energy_pipe", ["minecraft:iron_ingot"] * 3 + ["minecraft:redstone_block"], f"{MODID}:energy_pipe", 4)
shapeless("universal_pipe", [f"{MODID}:item_pipe", f"{MODID}:fluid_pipe", f"{MODID}:energy_pipe", "minecraft:gold_ingot"],
          f"{MODID}:universal_pipe", 3)
shaped("wrench", ["I I", " S ", " S "], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}, f"{MODID}:wrench")
shaped("speed_upgrade", ["ISI", "RIR", "ISI"],
       {"I": "minecraft:iron_ingot", "S": "minecraft:sugar", "R": "minecraft:redstone"}, f"{MODID}:speed_upgrade")
shaped("stack_upgrade", ["IRI", "ICI", "IRI"],
       {"I": "minecraft:iron_ingot", "C": "minecraft:chest", "R": "minecraft:redstone"}, f"{MODID}:stack_upgrade")
shaped("filter_upgrade", ["IPI", "PHP", "IPI"],
       {"I": "minecraft:iron_ingot", "P": "minecraft:paper", "H": "minecraft:hopper"}, f"{MODID}:filter_upgrade")
shapeless("knozy_upgrade", [f"{MODID}:speed_upgrade", f"{MODID}:stack_upgrade", f"{MODID}:filter_upgrade",
                            "minecraft:netherite_ingot"], f"{MODID}:knozy_upgrade")
shapeless("chemical_pipe", ["minecraft:iron_ingot"] * 3 + ["mekanism:basic_pressurized_tube"],
          f"{MODID}:chemical_pipe", 4, needs="mekanism")
shaped("config_card", ["PRP", "PGP", "PPP"],
       {"P": "minecraft:paper", "R": "minecraft:redstone", "G": "minecraft:gold_nugget"}, f"{MODID}:config_card")
shaped("filter_card", ["PRP", "PHP", "PPP"],
       {"P": "minecraft:paper", "R": "minecraft:redstone", "H": "minecraft:hopper"}, f"{MODID}:filter_card")
shaped("facade_blank", ["N N", " P ", "N N"], {"N": "minecraft:iron_nugget", "P": "minecraft:paper"},
       f"{MODID}:facade", 8)
# a blank facade and a full block in the crafting grid: a facade of that block (see FacadeRecipe)
write_json(f"data/{MODID}/recipes/facade.json", {"type": f"{MODID}:facade", "category": "misc"})

# ---------------------------------------------------------------- lang
def lang(code, tr):
    write_json(f"assets/{MODID}/lang/{code}.json", tr)


en = {
    "itemGroup.flowline": "Flowline",
    "block.flowline.item_pipe": "Item Pipe",
    "block.flowline.fluid_pipe": "Fluid Pipe",
    "block.flowline.energy_pipe": "Energy Pipe",
    "block.flowline.universal_pipe": "Universal Pipe",
    "message.flowline.side_normal": "%s: normal (Insert)",
    "message.flowline.side_extract": "%s: Extract",
    "message.flowline.side_disconnected": "%s: disconnected",
    "message.flowline.rule_exists": "This rule already exists (#%s)",
    "gui.flowline.editor.error.exists": "The same rule is already on this page",
    "item.flowline.upgrade.effect.speed": "Start interval -%s ticks (never below %s)",
    "item.flowline.upgrade.effect.stack": "Amount per operation: %s",
    "item.flowline.upgrade.effect.filter": "+%s filter rules",
    "item.flowline.upgrade.where": "Goes on Extract sides, up to %s per side",
    "gui.flowline.upgrades.title": "Upgrades (Extract sides, up to %s each):",
    "gui.flowline.upgrades.now": "This side now: start %st, x%s per operation, %s filter rules",
    "item.flowline.wrench": "Flowline Wrench",
    "item.flowline.speed_upgrade": "Speed Upgrade",
    "item.flowline.speed_upgrade.desc": "Starts the side at a shorter interval.",
    "item.flowline.stack_upgrade": "Stack Upgrade",
    "item.flowline.stack_upgrade.desc": "Moves more per operation.",
    "item.flowline.knozy_upgrade": "Knozy Upgrade",
    "item.flowline.filter_upgrade": "Filter Upgrade",
    "item.flowline.filter_upgrade.desc": "Adds more whitelist/blacklist entries.",
    "item.flowline.knozy_upgrade.desc": "Counts as a Speed, a Stack and a Filter Upgrade.",
    "message.flowline.upgrade_installed": "Upgrade installed (%s/%s)",
    "message.flowline.upgrade_slots_full": "All upgrade slots on this side are full.",
    "gui.flowline.upgrade_slot.desc": "Speed: shorter start interval. Stack: more per operation. Filter: more filter entries. Knozy: all three.",
    "gui.flowline.filter_page": "Filter page %s/%s",
    "gui.flowline.filter_capacity": "%s entries (Filter upgrades add more)",
    "gui.flowline.pacing.title": "Pacing",
    "gui.flowline.pacing.counts": "Speed: %s · Stack: %s · Filter: %s",
    "gui.flowline.pacing.amount": "Amount per operation: x%s",
    "gui.flowline.pacing.interval": "Interval: %s ticks (start %s, min %s)",
    "gui.flowline.pacing.hint": "Speeds up while moving, slows down when idle.",
    "gui.flowline.pacing.sleeping": "Sleeping: no targets. Wakes up when the pipe network changes.",
    "message.flowline.no_endpoint": "Nothing to configure on this side.",
    "message.flowline.upgrade_needs_extract": "Upgrades only go on Extract sides (sneak + right-click with the wrench).",
    "message.flowline.gui_needs_extract": "This side is Insert. Sneak + right-click it with the wrench to make it Extract.",
    "gui.flowline.pipe_config": "%s - %s",
    "gui.flowline.distribution": "Distribution: %s",
    "gui.flowline.side_line": "%s side",
    "gui.flowline.section.settings": "Settings",
    "gui.flowline.section.filter": "Filter",
    "gui.flowline.section.upgrade": "Upgrade",
    "gui.flowline.no_filter": "No filter",
    "gui.flowline.clear": "Clear filter",
    "gui.flowline.editor.error.too_many_tags": "Too many tags",
    "gui.flowline.rule.tags_any": "Any of these tags:",
    "gui.flowline.rule.tags_all": "All of these tags:",
    "gui.flowline.rule.more": "  ...and %s more",
    "gui.flowline.library.sample": "Sample",
    "gui.flowline.library.pick": "Pick an item",
    "gui.flowline.library.pick_hint": "Click an item in your inventory below",
    "gui.flowline.library.match_item": "Match this item",
    "gui.flowline.library.tags": "Tags",
    "gui.flowline.library.search": "Search all tags...",
    "gui.flowline.library.any": "OR",
    "gui.flowline.library.any.desc": "The stack needs at least one of the ticked tags.",
    "gui.flowline.library.all": "AND",
    "gui.flowline.library.all.desc": "The stack needs every ticked tag.",
    "gui.flowline.library.no_tags": "Pick a sample or search to list tags",
    "gui.flowline.library.no_results": "No tag matches",
    "gui.flowline.library.data": "Data (NBT)",
    "gui.flowline.library.no_data": "The sample has no extra data",
    "gui.flowline.library.data_on": "Ticked: the stack must have this data",
    "gui.flowline.library.data_off": "Click to require this data",
    "gui.flowline.library.text_mode": "Edit as text (SNBT)",
    "gui.flowline.library.list_mode": "Back to the list",
    "gui.flowline.library.preview_hint": "Hover a tag to see what is in it",
    "gui.flowline.library.members": "%s entries:",
    "gui.flowline.library.inventory": "Inventory: click to use as sample",
    "gui.flowline.clear.desc": "Removes every rule on this side.",
    "gui.flowline.editor.title": "Rule #%s",
    "gui.flowline.editor.cancel": "Cancel",
    "gui.flowline.editor.save": "Save",
    "gui.flowline.editor.delete": "Delete rule",
    "gui.flowline.editor.exact": "Exact",
    "gui.flowline.editor.exact.desc": "The stack's data must be exactly this.",
    "gui.flowline.editor.contains": "Contains",
    "gui.flowline.editor.contains.desc": "The stack's data must contain this; anything else may differ.",
    "gui.flowline.editor.allow": "Allow",
    "gui.flowline.editor.allow.desc": "Matching stacks may pass.",
    "gui.flowline.editor.block": "Block",
    "gui.flowline.editor.block.desc": "Matching stacks never pass.",
    "gui.flowline.editor.error.empty": "Type an id, a #tag or NBT",
    "gui.flowline.editor.error.syntax": "Invalid id",
    "gui.flowline.editor.error.unknown_item": "Unknown item",
    "gui.flowline.editor.error.unknown_fluid": "Unknown fluid",
    "gui.flowline.editor.error.unknown_tag": "Unknown tag",
    "gui.flowline.editor.error.nbt": "Invalid NBT",
    "gui.flowline.rule.empty": "Empty rule",
    "gui.flowline.rule.empty_hint": "Click: open the library · Click with an item: add it",
    "gui.flowline.rule.any": "Anything",
    "gui.flowline.rule.nbt_exact": "NBT (exact): %s",
    "gui.flowline.rule.nbt_contains": "NBT (contains): %s",
    "gui.flowline.rule.allows": "Allows",
    "gui.flowline.rule.blocks": "Blocks",
    "gui.flowline.rule.hint": "Click: edit · Right-click: remove",
    "gui.flowline.upgrade_slot": "Upgrade slot",
    "redstone.flowline.ignored.desc": "Runs regardless of redstone.",
    "redstone.flowline.require_signal.desc": "Only extracts while powered.",
    "redstone.flowline.require_no_signal.desc": "Stops while powered.",
    "distribution.flowline.nearest.desc": "Fills the closest target first.",
    "distribution.flowline.farthest.desc": "Fills the farthest target first.",
    "distribution.flowline.round_robin.desc": "Takes turns between targets.",
    "distribution.flowline.random.desc": "Picks a random target each time.",
    "gui.flowline.redstone": "Redstone: %s",
    "message.flowline.nothing_to_connect": "Nothing to connect on this side.",
    "redstone.flowline.ignored": "Ignored",
    "redstone.flowline.require_signal": "Needs signal",
    "redstone.flowline.require_no_signal": "Needs no signal",
    "direction.flowline.down": "Down", "direction.flowline.up": "Up",
    "direction.flowline.north": "North", "direction.flowline.south": "South",
    "direction.flowline.west": "West", "direction.flowline.east": "East",
    "mode.flowline.insert": "Insert", "mode.flowline.extract": "Extract",
    "distribution.flowline.nearest": "Nearest first", "distribution.flowline.farthest": "Farthest first",
    "distribution.flowline.round_robin": "Round robin", "distribution.flowline.random": "Random",
    # new blocks and items
    "block.flowline.chemical_pipe": "Chemical Pipe",
    "item.flowline.config_card": "Configuration Card",
    "item.flowline.filter_card": "Filter Card",
    "item.flowline.facade": "Facade",
    "item.flowline.facade.blank": "Blank Facade",
    "item.flowline.facade.named": "Facade (%s)",
    "item.flowline.facade.blank.desc": "Craft with a full block to make a facade of it.",
    "item.flowline.facade.desc": "Right-click a pipe to hide it. Sneak + right-click with empty hands to take it off.",
    "item.flowline.card.empty": "Empty",
    "item.flowline.card.from": "Copied from: %s",
    "item.flowline.card.rules": "Rules: %s",
    "item.flowline.config_card.desc": "Copies mode, settings and filter of a side.",
    "item.flowline.filter_card.desc": "Copies only the filter rules of a side.",
    "item.flowline.card.usage": "Sneak + right-click a side: copy · Right-click: paste · Sneak + use in the air: clear",
    # messages
    "message.flowline.color_set": "Pipe dyed %s: it only connects to the same colour or undyed pipes",
    "message.flowline.color_cleared": "Dye washed off",
    "message.flowline.facade_blank": "Craft the facade with a block first",
    "message.flowline.facade_present": "This pipe already has a facade",
    "message.flowline.facade_applied": "Facade: %s",
    "message.flowline.facade_removed": "Facade removed",
    "message.flowline.card_copied": "%s: copied to the card",
    "message.flowline.card_pasted": "%s: pasted from the card",
    "message.flowline.card_pasted_skipped": "%s: pasted, %s rules did not fit this pipe",
    "message.flowline.card_empty": "The card is empty. Sneak + right-click a side to copy it.",
    "message.flowline.card_cleared": "Card cleared",
    "message.flowline.scroll_distribution": "%s: distribution %s",
    "message.flowline.scroll_redstone": "%s: redstone %s",
    "message.flowline.scroll_priority": "%s: priority %s",
    # modes
    "redstone.flowline.pulse": "Pulse",
    "redstone.flowline.pulse.desc": "One operation each time the signal turns on.",
    "distribution.flowline.balanced": "Balanced",
    "distribution.flowline.balanced.desc": "Splits every operation evenly between the targets.",
    "distribution.flowline.priority": "Priority",
    "distribution.flowline.priority.desc": "Fills higher insert priorities first; ties go to the nearest.",
    "gui.flowline.cycle_hint": "Click: next · Right-click: previous",
    "gui.flowline.mode": "Mode: %s",
    "gui.flowline.priority": "Priority: %s",
    "gui.flowline.limit.value": "Regulator: %s",
    # values row
    "gui.flowline.value.priority": "Priority",
    "gui.flowline.value.priority.desc": "Extract sides set to Priority distribution fill higher priorities first.",
    "gui.flowline.value.keep": "Keep >=",
    "gui.flowline.value.keep.desc": "Leave at least this much of each kind in the source (items, mB or FE). 0 = off.",
    "gui.flowline.value.max": "Max <=",
    "gui.flowline.value.max.desc": "Keep at most this much of each kind in the target (items, mB or FE). 0 = off.",
    "gui.flowline.value.rate": "FE/t max",
    "gui.flowline.value.rate.desc": "Energy limit of this side in FE per tick. 0 = no limit.",
    "gui.flowline.value.hint": "Type a number, or scroll (Shift x10, Ctrl x100)",
    "gui.flowline.value.channels": "Channels",
    "gui.flowline.channel.0": "Items", "gui.flowline.channel.1": "Fluids", "gui.flowline.channel.2": "Energy",
    "gui.flowline.channel.on": "Moved by this side (click to turn off)",
    "gui.flowline.channel.off": "Not moved by this side (click to turn on)",
    "gui.flowline.upgrades.insert": "Insert sides take Filter Upgrades only (more insert rules).",
    # rules
    "gui.flowline.rule.kind_item": "Item rule",
    "gui.flowline.rule.kind_fluid": "Fluid rule",
    "gui.flowline.rule.mod": "Mod: %s",
    "gui.flowline.rule.name": "Name matches: %s",
    "gui.flowline.rule.durability": "Durability %s%% - %s%%",
    "gui.flowline.editor.kind.desc": "Universal pipes: click to switch between an item rule and a fluid rule.",
    "gui.flowline.editor.error.unknown_mod": "Unknown mod",
    "gui.flowline.editor.error.regex": "Invalid name pattern",
    "gui.flowline.editor.error.durability": "Durability: min is above max",
    "gui.flowline.library.mod": "Mod (@)",
    "gui.flowline.library.mod.desc": "Only stacks from this mod, e.g. minecraft or create.",
    "gui.flowline.library.name": "Name (regex)",
    "gui.flowline.library.name.desc": "Searched in the display name, ignoring case. Example: ingot|gem",
    "gui.flowline.library.durability": "Durability",
    "gui.flowline.library.durability.desc": "Remaining durability in percent. 0-100 = any; e.g. 0-10 for worn tools.",
    # jade
    "jade.flowline.side": "%s: %s",
    "jade.flowline.side_none": "%s: not connected",
    "jade.flowline.side_pipe": "%s: pipe",
    "jade.flowline.pacing": "x%s per operation · every %s ticks · %s upgrades",
    "jade.flowline.sleeping": "x%s per operation · asleep (no target) · %s ticks · %s upgrades",
    "jade.flowline.rate": "At most %s FE/t",
    "jade.flowline.keep": "Keeps %s in the source",
    "jade.flowline.max": "Fills up to %s",
    "jade.flowline.filtered": "Filtered",
    "jade.flowline.color": "Colour: %s",
    "config.jade.plugin_flowline.pipe_side": "Flowline pipe sides",
}
tr = {
    "itemGroup.flowline": "Flowline",
    "block.flowline.item_pipe": "Eşya Borusu",
    "block.flowline.fluid_pipe": "Sıvı Borusu",
    "block.flowline.energy_pipe": "Enerji Borusu",
    "block.flowline.universal_pipe": "Evrensel Boru",
    "message.flowline.side_normal": "%s: normal (Ekle)",
    "message.flowline.side_extract": "%s: Çek",
    "message.flowline.side_disconnected": "%s: bağlantı kesildi",
    "message.flowline.rule_exists": "Bu kural zaten var (#%s)",
    "gui.flowline.editor.error.exists": "Bu sayfada aynı kural zaten var",
    "item.flowline.upgrade.effect.speed": "Başlangıç aralığı -%s tick (en az %s)",
    "item.flowline.upgrade.effect.stack": "İşlem başına miktar: %s",
    "item.flowline.upgrade.effect.filter": "+%s filtre kuralı",
    "item.flowline.upgrade.where": "Çek tarafına takılır, taraf başına en fazla %s",
    "gui.flowline.upgrades.title": "Yükseltmeler (Çek tarafı, her birine en fazla %s):",
    "gui.flowline.upgrades.now": "Bu taraf şu an: başlangıç %st, işlem başına x%s, %s filtre kuralı",
    "item.flowline.wrench": "Flowline Anahtarı",
    "item.flowline.speed_upgrade": "Speed Upgrade",
    "item.flowline.speed_upgrade.desc": "Tarafı daha kısa bir aralıkla başlatır.",
    "item.flowline.stack_upgrade": "Stack Upgrade",
    "item.flowline.stack_upgrade.desc": "Her işlemde daha fazla taşır.",
    "item.flowline.knozy_upgrade": "Knozy Upgrade",
    "item.flowline.filter_upgrade": "Filter Upgrade",
    "item.flowline.filter_upgrade.desc": "Beyaz/kara listeye daha fazla kayıt ekler.",
    "item.flowline.knozy_upgrade.desc": "Hem Speed, hem Stack, hem de Filter Upgrade sayılır.",
    "message.flowline.upgrade_installed": "Yükseltme takıldı (%s/%s)",
    "message.flowline.upgrade_slots_full": "Bu taraftaki tüm yükseltme yuvaları dolu.",
    "gui.flowline.upgrade_slot.desc": "Speed: daha kısa başlangıç aralığı. Stack: işlem başına daha fazla. Filter: daha fazla filtre kaydı. Knozy: üçü birden.",
    "gui.flowline.filter_page": "Filtre sayfası %s/%s",
    "gui.flowline.filter_capacity": "%s kayıt (Filter Upgrade ile artar)",
    "gui.flowline.pacing.title": "Hız",
    "gui.flowline.pacing.counts": "Speed: %s · Stack: %s · Filter: %s",
    "gui.flowline.pacing.amount": "İşlem başına miktar: x%s",
    "gui.flowline.pacing.interval": "Aralık: %s tick (başlangıç %s, en az %s)",
    "gui.flowline.pacing.hint": "Taşıdıkça hızlanır, boştayken yavaşlar.",
    "gui.flowline.pacing.sleeping": "Uyuyor: hedef yok. Boru ağı değişince uyanır.",
    "message.flowline.no_endpoint": "Bu tarafta ayarlanacak bir şey yok.",
    "message.flowline.upgrade_needs_extract": "Yükseltmeler sadece Çek tarafına takılır (anahtarla Shift + sağ tık).",
    "message.flowline.gui_needs_extract": "Bu taraf Ekle modunda. Çek yapmak için anahtarla Shift + sağ tıkla.",
    "gui.flowline.pipe_config": "%s - %s",
    "gui.flowline.distribution": "Dağıtım: %s",
    "gui.flowline.side_line": "%s tarafı",
    "gui.flowline.section.settings": "Ayarlar",
    "gui.flowline.section.filter": "Filtre",
    "gui.flowline.section.upgrade": "Yükseltme",
    "gui.flowline.no_filter": "Filtre yok",
    "gui.flowline.clear": "Filtreyi temizle",
    "gui.flowline.editor.error.too_many_tags": "Çok fazla etiket",
    "gui.flowline.rule.tags_any": "Bu etiketlerden biri:",
    "gui.flowline.rule.tags_all": "Bu etiketlerin hepsi:",
    "gui.flowline.rule.more": "  ...ve %s tane daha",
    "gui.flowline.library.sample": "Örnek",
    "gui.flowline.library.pick": "Eşya seç",
    "gui.flowline.library.pick_hint": "Aşağıdan envanterindeki bir eşyaya tıkla",
    "gui.flowline.library.match_item": "Bu eşyayı eşleştir",
    "gui.flowline.library.tags": "Etiketler",
    "gui.flowline.library.search": "Tüm etiketlerde ara...",
    "gui.flowline.library.any": "VEYA",
    "gui.flowline.library.any.desc": "Eşyada işaretli etiketlerden en az biri olmalı.",
    "gui.flowline.library.all": "VE",
    "gui.flowline.library.all.desc": "Eşyada işaretli etiketlerin hepsi olmalı.",
    "gui.flowline.library.no_tags": "Etiketleri görmek için örnek seç ya da ara",
    "gui.flowline.library.no_results": "Eşleşen etiket yok",
    "gui.flowline.library.data": "Veri (NBT)",
    "gui.flowline.library.no_data": "Örnekte ek veri yok",
    "gui.flowline.library.data_on": "İşaretli: eşyada bu veri olmalı",
    "gui.flowline.library.data_off": "Bu veriyi şart koşmak için tıkla",
    "gui.flowline.library.text_mode": "Metin olarak düzenle (SNBT)",
    "gui.flowline.library.list_mode": "Listeye dön",
    "gui.flowline.library.preview_hint": "İçindekileri görmek için bir etiketin üzerine gel",
    "gui.flowline.library.members": "%s kayıt:",
    "gui.flowline.library.inventory": "Envanter: örnek olarak kullanmak için tıkla",
    "gui.flowline.clear.desc": "Bu taraftaki tüm kuralları siler.",
    "gui.flowline.editor.title": "Kural #%s",
    "gui.flowline.editor.cancel": "İptal",
    "gui.flowline.editor.save": "Kaydet",
    "gui.flowline.editor.delete": "Kuralı sil",
    "gui.flowline.editor.exact": "Birebir",
    "gui.flowline.editor.exact.desc": "Eşyanın verisi tam olarak bu olmalı.",
    "gui.flowline.editor.contains": "İçerir",
    "gui.flowline.editor.contains.desc": "Eşyanın verisi bunu içermeli; geri kalanı farklı olabilir.",
    "gui.flowline.editor.allow": "İzin ver",
    "gui.flowline.editor.allow.desc": "Eşleşen eşyalar geçebilir.",
    "gui.flowline.editor.block": "Engelle",
    "gui.flowline.editor.block.desc": "Eşleşen eşyalar asla geçmez.",
    "gui.flowline.editor.error.empty": "Bir kimlik, #etiket ya da NBT yaz",
    "gui.flowline.editor.error.syntax": "Geçersiz kimlik",
    "gui.flowline.editor.error.unknown_item": "Bilinmeyen eşya",
    "gui.flowline.editor.error.unknown_fluid": "Bilinmeyen sıvı",
    "gui.flowline.editor.error.unknown_tag": "Bilinmeyen etiket",
    "gui.flowline.editor.error.nbt": "Geçersiz NBT",
    "gui.flowline.rule.empty": "Boş kural",
    "gui.flowline.rule.empty_hint": "Tıkla: kütüphaneyi aç · Eşyayla tıkla: ekle",
    "gui.flowline.rule.any": "Her şey",
    "gui.flowline.rule.nbt_exact": "NBT (birebir): %s",
    "gui.flowline.rule.nbt_contains": "NBT (içerir): %s",
    "gui.flowline.rule.allows": "İzin verir",
    "gui.flowline.rule.blocks": "Engeller",
    "gui.flowline.rule.hint": "Tıkla: düzenle · Sağ tık: sil",
    "gui.flowline.upgrade_slot": "Yükseltme yuvası",
    "redstone.flowline.ignored.desc": "Redstone'a bakmadan çalışır.",
    "redstone.flowline.require_signal.desc": "Sadece sinyal varken çeker.",
    "redstone.flowline.require_no_signal.desc": "Sinyal varken durur.",
    "distribution.flowline.nearest.desc": "Önce en yakın hedefi doldurur.",
    "distribution.flowline.farthest.desc": "Önce en uzak hedefi doldurur.",
    "distribution.flowline.round_robin.desc": "Hedefler arasında sırayla dağıtır.",
    "distribution.flowline.random.desc": "Her seferinde rastgele bir hedef seçer.",
    "gui.flowline.redstone": "Redstone: %s",
    "message.flowline.nothing_to_connect": "Bu tarafta bağlanacak bir şey yok.",
    "redstone.flowline.ignored": "Yok sayılır",
    "redstone.flowline.require_signal": "Sinyal gerekir",
    "redstone.flowline.require_no_signal": "Sinyal olmamalı",
    "direction.flowline.down": "Aşağı", "direction.flowline.up": "Yukarı",
    "direction.flowline.north": "Kuzey", "direction.flowline.south": "Güney",
    "direction.flowline.west": "Batı", "direction.flowline.east": "Doğu",
    "mode.flowline.insert": "Ekle", "mode.flowline.extract": "Çek",
    "distribution.flowline.nearest": "En yakın önce", "distribution.flowline.farthest": "En uzak önce",
    "distribution.flowline.round_robin": "Sırayla", "distribution.flowline.random": "Rastgele",
    # yeni bloklar ve eşyalar
    "block.flowline.chemical_pipe": "Kimyasal Boru",
    "item.flowline.config_card": "Konfigürasyon Kartı",
    "item.flowline.filter_card": "Filtre Kartı",
    "item.flowline.facade": "Kaplama",
    "item.flowline.facade.blank": "Boş Kaplama",
    "item.flowline.facade.named": "Kaplama (%s)",
    "item.flowline.facade.blank.desc": "Bir tam blokla birlikte üretince o bloğun kaplaması olur.",
    "item.flowline.facade.desc": "Boruyu gizlemek için boruya sağ tıkla. Çıkarmak için elin boşken Shift + sağ tık.",
    "item.flowline.card.empty": "Boş",
    "item.flowline.card.from": "Kopyalanan: %s",
    "item.flowline.card.rules": "Kurallar: %s",
    "item.flowline.config_card.desc": "Bir tarafın modunu, ayarlarını ve filtresini kopyalar.",
    "item.flowline.filter_card.desc": "Bir tarafın yalnızca filtre kurallarını kopyalar.",
    "item.flowline.card.usage": "Tarafa Shift + sağ tık: kopyala · Sağ tık: yapıştır · Havada Shift + kullan: temizle",
    # mesajlar
    "message.flowline.color_set": "Boru %s boyandı: sadece aynı renk ya da boyasız borulara bağlanır",
    "message.flowline.color_cleared": "Boya temizlendi",
    "message.flowline.facade_blank": "Önce kaplamayı bir blokla üret",
    "message.flowline.facade_present": "Bu boruda zaten kaplama var",
    "message.flowline.facade_applied": "Kaplama: %s",
    "message.flowline.facade_removed": "Kaplama çıkarıldı",
    "message.flowline.card_copied": "%s: karta kopyalandı",
    "message.flowline.card_pasted": "%s: karttan yapıştırıldı",
    "message.flowline.card_pasted_skipped": "%s: yapıştırıldı, %s kural bu boruya uymadı",
    "message.flowline.card_empty": "Kart boş. Kopyalamak için bir tarafa Shift + sağ tıkla.",
    "message.flowline.card_cleared": "Kart temizlendi",
    "message.flowline.scroll_distribution": "%s: dağıtım %s",
    "message.flowline.scroll_redstone": "%s: redstone %s",
    "message.flowline.scroll_priority": "%s: öncelik %s",
    # modlar
    "redstone.flowline.pulse": "Darbe",
    "redstone.flowline.pulse.desc": "Sinyal her açıldığında tek bir işlem yapar.",
    "distribution.flowline.balanced": "Dengeli",
    "distribution.flowline.balanced.desc": "Her işlemi hedefler arasında eşit böler.",
    "distribution.flowline.priority": "Öncelik",
    "distribution.flowline.priority.desc": "Önce yüksek öncelikli hedefleri doldurur; eşitlikte en yakın.",
    "gui.flowline.cycle_hint": "Tık: sonraki · Sağ tık: önceki",
    "gui.flowline.mode": "Mod: %s",
    "gui.flowline.priority": "Öncelik: %s",
    "gui.flowline.limit.value": "Regülatör: %s",
    # değer satırı
    "gui.flowline.value.priority": "Öncelik",
    "gui.flowline.value.priority.desc": "Öncelik dağıtımındaki çekme tarafları önce yüksek önceliği doldurur.",
    "gui.flowline.value.keep": "En az",
    "gui.flowline.value.keep.desc": "Kaynakta her türden en az bu kadar bırakır (eşya, mB ya da FE). 0 = kapalı.",
    "gui.flowline.value.max": "En çok",
    "gui.flowline.value.max.desc": "Hedefte her türden en fazla bu kadar tutar (eşya, mB ya da FE). 0 = kapalı.",
    "gui.flowline.value.rate": "FE/t sınır",
    "gui.flowline.value.rate.desc": "Bu tarafın tick başına FE sınırı. 0 = sınırsız.",
    "gui.flowline.value.hint": "Sayı yaz ya da tekerlekle değiştir (Shift x10, Ctrl x100)",
    "gui.flowline.value.channels": "Kanallar",
    "gui.flowline.channel.0": "Eşya", "gui.flowline.channel.1": "Sıvı", "gui.flowline.channel.2": "Enerji",
    "gui.flowline.channel.on": "Bu taraf taşır (kapatmak için tıkla)",
    "gui.flowline.channel.off": "Bu taraf taşımaz (açmak için tıkla)",
    "gui.flowline.upgrades.insert": "Ekle tarafları sadece Filter Upgrade alır (daha fazla ekleme kuralı).",
    # kurallar
    "gui.flowline.rule.kind_item": "Eşya kuralı",
    "gui.flowline.rule.kind_fluid": "Sıvı kuralı",
    "gui.flowline.rule.mod": "Mod: %s",
    "gui.flowline.rule.name": "İsim eşleşir: %s",
    "gui.flowline.rule.durability": "Dayanıklılık %%%s - %%%s",
    "gui.flowline.editor.kind.desc": "Evrensel borular: eşya kuralı ile sıvı kuralı arasında geçmek için tıkla.",
    "gui.flowline.editor.error.unknown_mod": "Bilinmeyen mod",
    "gui.flowline.editor.error.regex": "Geçersiz isim kalıbı",
    "gui.flowline.editor.error.durability": "Dayanıklılık: en az, en çoktan büyük",
    "gui.flowline.library.mod": "Mod (@)",
    "gui.flowline.library.mod.desc": "Sadece bu moddan gelenler, ör. minecraft ya da create.",
    "gui.flowline.library.name": "İsim (regex)",
    "gui.flowline.library.name.desc": "Görünen isimde aranır, büyük/küçük harf fark etmez. Örnek: ingot|gem",
    "gui.flowline.library.durability": "Dayanıklılık",
    "gui.flowline.library.durability.desc": "Kalan dayanıklılık yüzdesi. 0-100 = hepsi; ör. aşınmış aletler için 0-10.",
    # jade
    "jade.flowline.side": "%s: %s",
    "jade.flowline.side_none": "%s: bağlı değil",
    "jade.flowline.side_pipe": "%s: boru",
    "jade.flowline.pacing": "İşlem başına x%s · her %s tick · %s yükseltme",
    "jade.flowline.sleeping": "İşlem başına x%s · uyuyor (hedef yok) · %s tick · %s yükseltme",
    "jade.flowline.rate": "En fazla %s FE/t",
    "jade.flowline.keep": "Kaynakta %s bırakır",
    "jade.flowline.max": "En fazla %s doldurur",
    "jade.flowline.filtered": "Filtreli",
    "jade.flowline.color": "Renk: %s",
    "config.jade.plugin_flowline.pipe_side": "Flowline boru tarafları",
}
lang("en_us", en)
lang("tr_tr", tr)

# ---------------------------------------------------------------- gui icons
class Canvas:
    def __init__(self):
        self.px = [[None] * 16 for _ in range(16)]

    def dot(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[y][x] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.dot(x, y, c)

    def line(self, x0, y0, x1, y1, c, w=1):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            x = round(x0 + (x1 - x0) * i / n)
            y = round(y0 + (y1 - y0) * i / n)
            for dx in range(w):
                for dy in range(w):
                    self.dot(x + dx, y + dy, c)

    def pixels(self, outline=True):
        out = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
        for y in range(16):
            for x in range(16):
                c = self.px[y][x]
                if c is not None:
                    out[y][x] = tuple(c) + (255,)
                elif outline and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and self.px[y + dy][x + dx] is not None
                                     for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out[y][x] = (0x10, 0x12, 0x16, 255)
        return out


RED, RED_HI, RED_DK = (0xE8, 0x30, 0x30), (0xFF, 0xA0, 0x90), (0x5A, 0x22, 0x22)
STICK, GREY, GREY_DK = (0x9A, 0x6A, 0x3A), (0xA8, 0xAE, 0xB8), (0x5A, 0x60, 0x6A)
GREEN, GOLD, WHITE, CYAN = (0x4C, 0xD9, 0x64), (0xF2, 0xC2, 0x3A), (0xF0, 0xF2, 0xF5), (0x5A, 0xC8, 0xF0)


def torch(lit):
    c = Canvas()
    c.rect(7, 7, 8, 14, STICK)
    head = RED if lit else RED_DK
    c.rect(6, 3, 9, 6, head)
    if lit:
        c.rect(7, 4, 8, 5, RED_HI)
        for x, y in ((4, 2), (11, 2), (3, 6), (12, 6), (7, 0)):
            c.dot(x, y, RED)
    return c


def rs_ignored():
    c = Canvas()
    for x, y in ((4, 9), (5, 8), (5, 9), (5, 10), (6, 9), (9, 5), (10, 4), (10, 5), (10, 6), (11, 5),
                 (9, 11), (10, 10), (10, 11), (10, 12), (11, 11), (4, 4), (5, 4), (4, 5)):
        c.dot(x, y, RED)
    c.line(2, 13, 13, 2, GREY, 2)
    return c


def distribution(kind):
    c = Canvas()
    if kind in ("nearest", "farthest"):
        c.rect(1, 9, 3, 12, GREY)                      # source
        for x in (6, 10, 14):                           # targets
            c.rect(x - 1, 10, x, 11, GREY_DK)
        tx = 6 if kind == "nearest" else 14
        c.rect(tx - 1, 10, tx, 11, CYAN)
        c.line(2, 5, tx - 1, 5, WHITE)                  # arrow over the row
        c.line(2, 5, 2, 8, WHITE)
        c.line(tx - 1, 5, tx - 1, 8, WHITE)
        c.dot(tx - 2, 7, WHITE)
        c.dot(tx, 7, WHITE)
    elif kind == "round_robin":
        ring = [(6, 2), (7, 2), (8, 2), (9, 2), (11, 3), (12, 4), (13, 6), (13, 7), (13, 8), (13, 9), (12, 11),
                (11, 12), (9, 13), (8, 13), (7, 13), (6, 13), (4, 12), (3, 11), (2, 9), (2, 8), (2, 7), (2, 6),
                (3, 4), (4, 3), (10, 2), (5, 13)]
        for x, y in ring:
            c.dot(x, y, WHITE)
        for x, y in ((10, 0), (10, 1), (11, 1), (11, 2), (10, 3), (10, 4)):   # arrow head, top
            c.dot(x, y, CYAN)
        for x, y in ((5, 15), (5, 14), (4, 14), (4, 13), (5, 12), (5, 11)):   # arrow head, bottom
            c.dot(x, y, CYAN)
        c.rect(7, 7, 8, 8, CYAN)
    else:  # random: a die
        c.rect(2, 2, 13, 13, WHITE)
        for x, y in ((4, 4), (10, 4), (7, 7), (4, 10), (10, 10)):
            c.rect(x, y, x + 1, y + 1, (0x22, 0x26, 0x2E))
    return c


def check():
    c = Canvas()
    c.line(2, 8, 5, 11, GREEN, 2)
    c.line(6, 11, 12, 3, GREEN, 2)
    return c


def cross():
    c = Canvas()
    c.line(3, 3, 11, 11, RED, 2)
    c.line(11, 3, 3, 11, RED, 2)
    return c


def tag(active):
    c = Canvas()
    col = GOLD if active else GREY_DK
    c.rect(6, 4, 13, 11, col)
    for i in range(4):
        c.line(5 - i, 5 + i, 5 - i, 10 - i, col)
    c.dot(5, 7, (0x22, 0x26, 0x2E))
    c.dot(5, 8, (0x22, 0x26, 0x2E))
    c.line(7, 6, 12, 6, WHITE if active else GREY)
    c.line(7, 9, 11, 9, WHITE if active else GREY)
    if not active:
        c.line(2, 14, 14, 2, RED, 2)
    return c


def trash():
    c = Canvas()
    c.rect(3, 3, 12, 4, GREY)
    c.rect(6, 1, 9, 2, GREY)
    c.rect(4, 5, 11, 14, GREY_DK)
    for x in (6, 9):
        c.line(x, 7, x, 12, GREY)
    return c


def lock():
    c = Canvas()
    for x, y in ((5, 7), (5, 6), (5, 5), (5, 4), (6, 3), (7, 2), (8, 2), (9, 3), (10, 4), (10, 5), (10, 6), (10, 7)):
        c.dot(x, y, GREY)
    c.rect(3, 8, 12, 14, GOLD)
    c.rect(7, 10, 8, 12, (0x5A, 0x44, 0x10))
    return c


def page_arrow(right):
    c = Canvas()
    for dy in range(9):
        off = 4 - abs(4 - dy)
        x = 5 + off if right else 10 - off
        c.line(x, 3 + dy, x + 1, 3 + dy, WHITE)
    return c


def help_icon():
    c = Canvas()
    for x, y in ((6, 3), (7, 2), (8, 2), (9, 2), (10, 3), (10, 4), (10, 5), (9, 6), (8, 7), (8, 8), (8, 9)):
        c.rect(x, y, x, y, WHITE)
        c.rect(x - 1 if x > 8 else x, y, x - 1 if x > 8 else x, y, WHITE)
    c.rect(7, 11, 8, 12, WHITE)
    return c


ORANGE, BLUE = (0xE0, 0x8A, 0x2B), (0x3A, 0x8C, 0xF0)


def pulse():
    """A torch next to a square wave: one operation per rising edge."""
    c = torch(True)
    wave = [(0, 13), (1, 13), (2, 13), (2, 12), (2, 11), (2, 10), (3, 10), (4, 10), (4, 11), (4, 12), (4, 13), (5, 13)]
    for x, y in wave:
        c.dot(x + 9, y, GOLD)
    return c


def balanced():
    c = Canvas()
    c.rect(1, 6, 3, 9, GREY)                               # source
    for y in (2, 7, 12):                                   # three equal targets
        c.rect(12, y, 14, y + 1, CYAN)
        c.line(4, 7, 11, y, WHITE)
    return c


def priority_icon():
    c = Canvas()
    for x, h, col in ((2, 4, GREY_DK), (7, 10, GOLD), (12, 7, GREY_DK)):   # podium: the highest wins
        c.rect(x, 14 - h, x + 2, 14, col)
    c.dot(8, 1, WHITE)
    c.rect(7, 2, 9, 2, WHITE)
    return c


def box_icon(col):
    c = Canvas()
    c.rect(3, 5, 12, 13, col)
    c.rect(3, 3, 12, 4, tuple(min(255, int(v * 1.25)) for v in col))
    c.line(7, 3, 7, 13, tuple(int(v * 0.7) for v in col))
    c.line(8, 3, 8, 13, tuple(int(v * 0.7) for v in col))
    return c


def drop_icon(col):
    c = Canvas()
    for y in range(2, 14):
        half = 0 if y < 5 else min(4, (y - 3) // 2) if y < 11 else 4 - (y - 10)
        c.rect(7 - half, y, 8 + half, y, col)
    c.dot(6, 9, WHITE)
    c.dot(6, 10, WHITE)
    return c


def bolt_icon(col):
    c = Canvas()
    for x, y in ((9, 1), (8, 2), (9, 2), (7, 3), (8, 3), (6, 4), (7, 4), (5, 5), (6, 5), (5, 6), (6, 6), (7, 6), (8, 6),
                 (9, 6), (10, 6), (9, 7), (10, 7), (8, 8), (9, 8), (7, 9), (8, 9), (6, 10), (7, 10), (6, 11), (5, 12),
                 (6, 12), (5, 13)):
        c.dot(x, y, col)
    return c


GUI_ICONS = {
    "redstone_pulse": pulse(),
    "distribution_balanced": balanced(),
    "distribution_priority": priority_icon(),
    "channel_items": box_icon(ORANGE),
    "channel_fluids": drop_icon(BLUE),
    "channel_energy": bolt_icon(RED),
    "rule_item": box_icon(ORANGE),
    "rule_fluid": drop_icon(BLUE),
    "help": help_icon(),
    "page_prev": page_arrow(False),
    "page_next": page_arrow(True),
    "redstone_ignored": rs_ignored(),
    "redstone_require_signal": torch(True),
    "redstone_require_no_signal": torch(False),
    "distribution_nearest": distribution("nearest"),
    "distribution_farthest": distribution("farthest"),
    "distribution_round_robin": distribution("round_robin"),
    "distribution_random": distribution("random"),
    "whitelist": check(),
    "blacklist": cross(),
    "match_components": tag(True),
    "ignore_components": tag(False),
    "clear": trash(),
}
for icon_name, canvas in GUI_ICONS.items():
    write_png(f"assets/{MODID}/textures/gui/icon/{icon_name}.png", canvas.pixels())

# ---------------------------------------------------------------- gametest structure
def nbt_empty_structure(size):
    """Minimal gzipped structure NBT: air-only palette, no blocks, no entities."""
    import gzip

    def name(n):
        b = n.encode()
        return struct.pack(">H", len(b)) + b

    def int_list(key, values):
        return b"\x09" + name(key) + b"\x03" + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)

    def empty_list(key):
        return b"\x09" + name(key) + b"\x00" + struct.pack(">i", 0)

    palette = (b"\x09" + name("palette") + b"\x0a" + struct.pack(">i", 1)
               + b"\x08" + name("Name") + name("minecraft:air") + b"\x00")
    body = (b"\x03" + name("DataVersion") + struct.pack(">i", 3465)
            + int_list("size", size) + palette + empty_list("blocks") + empty_list("entities"))
    return gzip.compress(b"\x0a" + name("") + body + b"\x00", mtime=0)


full = os.path.join(ROOT, "data", MODID, "structures", "empty.nbt")
os.makedirs(os.path.dirname(full), exist_ok=True)
with open(full, "wb") as f:
    f.write(nbt_empty_structure([6, 3, 3]))

# pack.mcmeta is not needed for NeoForge mods (metadata comes from neoforge.mods.toml)
print("resources generated")
