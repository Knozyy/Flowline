#!/usr/bin/env python3
"""Generates blockstates, models, textures, recipes, loot tables and lang files under src/main/resources.

Run from the repo root:  python3 tools/gen_resources.py
Pure standard library; textures are simple placeholders meant to be replaced by real art.
"""
import json
import os
import struct
import zlib

MODID = "flowline"
ROOT = os.path.join("src", "main", "resources")
TYPES = {"item": (0xE0, 0x8A, 0x2B), "fluid": (0x2F, 0x7F, 0xE0), "energy": (0xD8, 0x3A, 0x3A)}


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
for name, base in TYPES.items():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            stripe = (x + y) % 8 == 0
            row.append(shade(base, 0.6 if edge else (1.15 if stripe else 1.0)))
        rows.append(row)
    write_png(f"assets/{MODID}/textures/block/{name}_pipe.png", rows)


def icon(fn):
    return [[fn(x, y) for x in range(16)] for y in range(16)]


T = (0, 0, 0, 0)
GREY = (0x9A, 0x9A, 0xA0, 255)
DARK = (0x55, 0x55, 0x5C, 255)
BROWN = (0x7A, 0x52, 0x2E, 255)

# wrench: diagonal handle with a C-shaped head
write_png(f"assets/{MODID}/textures/item/wrench.png", icon(
    lambda x, y: GREY if (abs(x - (15 - y)) <= 1 and 4 <= y <= 14) or (y <= 5 and 9 <= x <= 14 and not (y in (2, 3) and x in (11, 12))) else T))
# filter: funnel
write_png(f"assets/{MODID}/textures/item/filter.png", icon(
    lambda x, y: GREY if (2 <= y <= 7 and (2 + (y - 2) * 0.0) <= x <= 13 - (y - 2) * 0.0 and 3 + (y - 2) <= x <= 12 - (y - 2)) or (8 <= y <= 13 and 7 <= x <= 8) else T))
for tier, col in ((1, (0xC0, 0xC0, 0xC8)), (2, (0xE6, 0xC2, 0x3A)), (3, (0x4A, 0xE0, 0xE6))):
    write_png(f"assets/{MODID}/textures/item/speed_upgrade_{tier}.png", icon(
        lambda x, y, c=col, t=tier: shade(c, 1.0) if (2 <= x <= 13 and 2 <= y <= 13 and not (x in (2, 13) or y in (2, 13)) and (y - 2) // 3 < t + 1 and (y % 3) != 0 or (2 <= x <= 13 and y in (2, 13)) or (x in (2, 13) and 2 <= y <= 13)) else T))

# ---------------------------------------------------------------- models
for name in TYPES:
    tex = f"{MODID}:block/{name}_pipe"
    write_json(f"assets/{MODID}/models/block/{name}_pipe_core.json", {
        "textures": {"pipe": tex, "particle": tex},
        "elements": [{
            "from": [5, 5, 5], "to": [11, 11, 11],
            "faces": {d: {"uv": [5, 5, 11, 11], "texture": "#pipe"} for d in ("down", "up", "north", "south", "west", "east")},
        }],
    })
    write_json(f"assets/{MODID}/models/block/{name}_pipe_arm.json", {
        "textures": {"pipe": tex, "particle": tex},
        "elements": [{
            "from": [5, 5, 0], "to": [11, 11, 5],
            "faces": {
                "down": {"uv": [5, 0, 11, 5], "texture": "#pipe"},
                "up": {"uv": [5, 0, 11, 5], "texture": "#pipe"},
                "north": {"uv": [5, 5, 11, 11], "texture": "#pipe"},
                "west": {"uv": [0, 5, 5, 11], "texture": "#pipe"},
                "east": {"uv": [0, 5, 5, 11], "texture": "#pipe"},
            },
        }],
    })
    write_json(f"assets/{MODID}/models/block/{name}_pipe_endpoint.json", {
        "textures": {"pipe": tex, "particle": tex},
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
                "faces": {d: {"uv": [4, 4, 12, 12], "texture": "#pipe"} for d in ("down", "up", "north", "south", "west", "east")},
            },
        ],
    })
    write_json(f"assets/{MODID}/models/item/{name}_pipe.json", {
        "parent": "minecraft:block/block",
        "textures": {"pipe": tex, "particle": tex},
        "display": {
            "gui": {"rotation": [30, 45, 0], "scale": 1.2},
            "fixed": {"scale": 1.2},
        },
        "elements": [
            {"from": [5, 5, 0], "to": [11, 11, 16],
             "faces": {d: {"uv": [5, 5, 11, 11], "texture": "#pipe"} for d in ("down", "up", "north", "south", "west", "east")}},
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
    write_json(f"assets/{MODID}/blockstates/{name}_pipe.json", {"multipart": multipart})

    # loot table + recipes
    write_json(f"data/{MODID}/loot_table/blocks/{name}_pipe.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "bonus_rolls": 0.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"{MODID}:{name}_pipe"}],
        }],
    })

for name in ("wrench", "filter", "speed_upgrade_1", "speed_upgrade_2", "speed_upgrade_3"):
    write_json(f"assets/{MODID}/models/item/{name}.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{MODID}:item/{name}"},
    })

write_json("data/minecraft/tags/block/mineable/pickaxe.json", {
    "replace": False,
    "values": [f"{MODID}:{n}_pipe" for n in TYPES],
})

# ---------------------------------------------------------------- recipes
def ing(item):
    return {"item": item}


def shapeless(name, ingredients, result, count=1):
    write_json(f"data/{MODID}/recipe/{name}.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [ing(i) for i in ingredients],
        "result": {"id": result, "count": count},
    })


def shaped(name, pattern, key, result, count=1):
    write_json(f"data/{MODID}/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "pattern": pattern,
        "key": {k: ing(v) for k, v in key.items()},
        "result": {"id": result, "count": count},
    })


shapeless("item_pipe", ["minecraft:iron_ingot"] * 3 + ["minecraft:hopper"], f"{MODID}:item_pipe", 4)
shapeless("fluid_pipe", ["minecraft:iron_ingot"] * 3 + ["minecraft:bucket"], f"{MODID}:fluid_pipe", 4)
shapeless("energy_pipe", ["minecraft:iron_ingot"] * 3 + ["minecraft:redstone_block"], f"{MODID}:energy_pipe", 4)
shaped("wrench", ["I I", " S ", " S "], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}, f"{MODID}:wrench")
shapeless("filter", ["minecraft:paper", "minecraft:iron_ingot", "minecraft:hopper"], f"{MODID}:filter")
shaped("speed_upgrade_1", ["III", "IRI", "III"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone"}, f"{MODID}:speed_upgrade_1")
shaped("speed_upgrade_2", ["GGG", "GUG", "GGG"], {"G": "minecraft:gold_ingot", "U": f"{MODID}:speed_upgrade_1"}, f"{MODID}:speed_upgrade_2")
shaped("speed_upgrade_3", ["DDD", "DUD", "DDD"], {"D": "minecraft:diamond", "U": f"{MODID}:speed_upgrade_2"}, f"{MODID}:speed_upgrade_3")

# ---------------------------------------------------------------- lang
def lang(code, tr):
    write_json(f"assets/{MODID}/lang/{code}.json", tr)


en = {
    "itemGroup.flowline": "Flowline",
    "block.flowline.item_pipe": "Item Pipe",
    "block.flowline.fluid_pipe": "Fluid Pipe",
    "block.flowline.energy_pipe": "Energy Pipe",
    "item.flowline.wrench": "Flowline Wrench",
    "item.flowline.filter": "Flowline Filter",
    "item.flowline.speed_upgrade_1": "Speed Upgrade (Tier 1)",
    "item.flowline.speed_upgrade_2": "Speed Upgrade (Tier 2)",
    "item.flowline.speed_upgrade_3": "Speed Upgrade (Tier 3)",
    "message.flowline.no_endpoint": "Nothing to configure on this side.",
    "message.flowline.side_info": "%s: %s, %s, speed x%s",
    "message.flowline.no_filter_energy": "Energy pipes have no filter.",
    "message.flowline.filter_whitelist": "Filter: whitelist",
    "message.flowline.filter_blacklist": "Filter: blacklist",
    "message.flowline.filter_cleared": "Filter cleared",
    "message.flowline.filter_invalid_sample": "Hold a valid sample in the other hand (a bucket for fluids).",
    "message.flowline.filter_added": "Added to filter: %s",
    "message.flowline.filter_removed": "Removed from filter: %s",
    "message.flowline.upgrade_not_better": "This side already has an equal or better upgrade.",
    "message.flowline.upgrade_installed": "Speed upgrade installed (x%s)",
    "direction.flowline.down": "Down", "direction.flowline.up": "Up",
    "direction.flowline.north": "North", "direction.flowline.south": "South",
    "direction.flowline.west": "West", "direction.flowline.east": "East",
    "mode.flowline.insert": "Insert", "mode.flowline.extract": "Extract", "mode.flowline.disabled": "Disabled",
    "distribution.flowline.nearest": "Nearest first", "distribution.flowline.farthest": "Farthest first",
    "distribution.flowline.round_robin": "Round robin", "distribution.flowline.random": "Random",
}
tr = {
    "itemGroup.flowline": "Flowline",
    "block.flowline.item_pipe": "Eşya Borusu",
    "block.flowline.fluid_pipe": "Sıvı Borusu",
    "block.flowline.energy_pipe": "Enerji Borusu",
    "item.flowline.wrench": "Flowline Anahtarı",
    "item.flowline.filter": "Flowline Filtresi",
    "item.flowline.speed_upgrade_1": "Hız Yükseltmesi (Seviye 1)",
    "item.flowline.speed_upgrade_2": "Hız Yükseltmesi (Seviye 2)",
    "item.flowline.speed_upgrade_3": "Hız Yükseltmesi (Seviye 3)",
    "message.flowline.no_endpoint": "Bu tarafta ayarlanacak bir şey yok.",
    "message.flowline.side_info": "%s: %s, %s, hız x%s",
    "message.flowline.no_filter_energy": "Enerji borularında filtre yoktur.",
    "message.flowline.filter_whitelist": "Filtre: beyaz liste",
    "message.flowline.filter_blacklist": "Filtre: kara liste",
    "message.flowline.filter_cleared": "Filtre temizlendi",
    "message.flowline.filter_invalid_sample": "Diğer elinde geçerli bir örnek tut (sıvılar için kova).",
    "message.flowline.filter_added": "Filtreye eklendi: %s",
    "message.flowline.filter_removed": "Filtreden çıkarıldı: %s",
    "message.flowline.upgrade_not_better": "Bu tarafta zaten eşit veya daha iyi bir yükseltme var.",
    "message.flowline.upgrade_installed": "Hız yükseltmesi takıldı (x%s)",
    "direction.flowline.down": "Aşağı", "direction.flowline.up": "Yukarı",
    "direction.flowline.north": "Kuzey", "direction.flowline.south": "Güney",
    "direction.flowline.west": "Batı", "direction.flowline.east": "Doğu",
    "mode.flowline.insert": "Ekle", "mode.flowline.extract": "Çek", "mode.flowline.disabled": "Kapalı",
    "distribution.flowline.nearest": "En yakın önce", "distribution.flowline.farthest": "En uzak önce",
    "distribution.flowline.round_robin": "Sırayla", "distribution.flowline.random": "Rastgele",
}
lang("en_us", en)
lang("tr_tr", tr)

# pack.mcmeta is not needed for NeoForge mods (metadata comes from neoforge.mods.toml)
print("resources generated")
