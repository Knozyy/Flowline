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
def noise(x, y, seed):
    h = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 0xFF) / 255.0


for idx, (name, base) in enumerate(TYPES.items()):
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            f = 0.92 + 0.16 * noise(x, y, idx + 1)
            if x in (0, 15) or y in (0, 15):
                f = 0.62
            elif x in (1, 14) or y in (1, 14):
                f *= 1.12
            if (x, y) in ((5, 5), (10, 5), (5, 10), (10, 10)):
                f = 0.55          # rivets
            elif (x, y) in ((4, 4), (9, 4), (4, 9), (9, 9)):
                f = 1.3           # rivet highlights
            row.append(shade(base, f))
        rows.append(row)
    write_png(f"assets/{MODID}/textures/block/{name}_pipe.png", rows)

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


WRENCH = [
    "................",
    "..........XXX...",
    ".........XXXXX..",
    ".........XXX.XX.",
    ".........XX..XX.",
    ".........XXX.XX.",
    "..........XXXX..",
    ".........XXXXX..",
    "........XXXXX...",
    ".......XXXXX....",
    "......HHHHH.....",
    ".....HHHHH......",
    "....HHHHH.......",
    "...HHHHH........",
    "...HHHH.........",
    "................",
]
write_png(f"assets/{MODID}/textures/item/wrench.png",
          ascii_icon(WRENCH, {"X": GREY, "H": (0xC0, 0x50, 0x3C)}))

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

for name in ("wrench", "speed_upgrade", "stack_upgrade", "filter_upgrade", "knozy_upgrade"):
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
shaped("speed_upgrade", ["ISI", "RIR", "ISI"],
       {"I": "minecraft:iron_ingot", "S": "minecraft:sugar", "R": "minecraft:redstone"}, f"{MODID}:speed_upgrade")
shaped("stack_upgrade", ["IRI", "ICI", "IRI"],
       {"I": "minecraft:iron_ingot", "C": "minecraft:chest", "R": "minecraft:redstone"}, f"{MODID}:stack_upgrade")
shaped("filter_upgrade", ["IPI", "PHP", "IPI"],
       {"I": "minecraft:iron_ingot", "P": "minecraft:paper", "H": "minecraft:hopper"}, f"{MODID}:filter_upgrade")
shapeless("knozy_upgrade", [f"{MODID}:speed_upgrade", f"{MODID}:stack_upgrade", f"{MODID}:filter_upgrade",
                            "minecraft:netherite_ingot"], f"{MODID}:knozy_upgrade")

# ---------------------------------------------------------------- lang
def lang(code, tr):
    write_json(f"assets/{MODID}/lang/{code}.json", tr)


en = {
    "itemGroup.flowline": "Flowline",
    "block.flowline.item_pipe": "Item Pipe",
    "block.flowline.fluid_pipe": "Fluid Pipe",
    "block.flowline.energy_pipe": "Energy Pipe",
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
    "message.flowline.mode_set": "%s: %s",
    "message.flowline.upgrade_needs_extract": "Upgrades only go on Extract sides (sneak + right-click with the wrench).",
    "message.flowline.gui_needs_extract": "This side is Insert. Sneak + right-click it with the wrench to make it Extract.",
    "gui.flowline.pipe_config": "%s - %s",
    "gui.flowline.distribution": "Distribution: %s",
    "gui.flowline.side_line": "%s side · %s",
    "gui.flowline.section.settings": "Settings",
    "gui.flowline.section.filter": "Filter",
    "gui.flowline.section.upgrade": "Upgrade",
    "gui.flowline.no_filter": "No filter",
    "gui.flowline.filter_mode": "Filter mode: %s",
    "gui.flowline.nbt": "NBT: %s",
    "gui.flowline.clear": "Clear filter",
    "gui.flowline.upgrade_slot": "Upgrade slot",
    "gui.flowline.whitelist": "Whitelist",
    "gui.flowline.whitelist.desc": "Only listed entries pass.",
    "gui.flowline.blacklist": "Blacklist",
    "gui.flowline.blacklist.desc": "Everything except listed entries passes.",
    "gui.flowline.match_components": "Match",
    "gui.flowline.match_components.desc": "Enchantments, damage, names and other data must match too.",
    "gui.flowline.ignore_components": "Ignore",
    "gui.flowline.ignore_components.desc": "Only the item or fluid type counts.",
    "redstone.flowline.ignored.desc": "Runs regardless of redstone.",
    "redstone.flowline.require_signal.desc": "Only extracts while powered.",
    "redstone.flowline.require_no_signal.desc": "Stops while powered.",
    "distribution.flowline.nearest.desc": "Fills the closest target first.",
    "distribution.flowline.farthest.desc": "Fills the farthest target first.",
    "distribution.flowline.round_robin.desc": "Takes turns between targets.",
    "distribution.flowline.random.desc": "Picks a random target each time.",
    "gui.flowline.redstone": "Redstone: %s",
    "message.flowline.connected": "Side connected",
    "message.flowline.disconnected": "Side disconnected",
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
}
tr = {
    "itemGroup.flowline": "Flowline",
    "block.flowline.item_pipe": "Eşya Borusu",
    "block.flowline.fluid_pipe": "Sıvı Borusu",
    "block.flowline.energy_pipe": "Enerji Borusu",
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
    "message.flowline.mode_set": "%s: %s",
    "message.flowline.upgrade_needs_extract": "Yükseltmeler sadece Çek tarafına takılır (anahtarla Shift + sağ tık).",
    "message.flowline.gui_needs_extract": "Bu taraf Ekle modunda. Çek yapmak için anahtarla Shift + sağ tıkla.",
    "gui.flowline.pipe_config": "%s - %s",
    "gui.flowline.distribution": "Dağıtım: %s",
    "gui.flowline.side_line": "%s tarafı · %s",
    "gui.flowline.section.settings": "Ayarlar",
    "gui.flowline.section.filter": "Filtre",
    "gui.flowline.section.upgrade": "Yükseltme",
    "gui.flowline.no_filter": "Filtre yok",
    "gui.flowline.filter_mode": "Filtre modu: %s",
    "gui.flowline.nbt": "NBT: %s",
    "gui.flowline.clear": "Filtreyi temizle",
    "gui.flowline.upgrade_slot": "Yükseltme yuvası",
    "gui.flowline.whitelist": "Beyaz liste",
    "gui.flowline.whitelist.desc": "Sadece listedekiler geçer.",
    "gui.flowline.blacklist": "Kara liste",
    "gui.flowline.blacklist.desc": "Listedekiler hariç her şey geçer.",
    "gui.flowline.match_components": "Eşleş",
    "gui.flowline.match_components.desc": "Büyü, hasar, isim gibi veriler de aynı olmalı.",
    "gui.flowline.ignore_components": "Yok say",
    "gui.flowline.ignore_components.desc": "Sadece eşya ya da sıvı türüne bakılır.",
    "redstone.flowline.ignored.desc": "Redstone'a bakmadan çalışır.",
    "redstone.flowline.require_signal.desc": "Sadece sinyal varken çeker.",
    "redstone.flowline.require_no_signal.desc": "Sinyal varken durur.",
    "distribution.flowline.nearest.desc": "Önce en yakın hedefi doldurur.",
    "distribution.flowline.farthest.desc": "Önce en uzak hedefi doldurur.",
    "distribution.flowline.round_robin.desc": "Hedefler arasında sırayla dağıtır.",
    "distribution.flowline.random.desc": "Her seferinde rastgele bir hedef seçer.",
    "gui.flowline.redstone": "Redstone: %s",
    "message.flowline.connected": "Bağlantı açıldı",
    "message.flowline.disconnected": "Bağlantı kesildi",
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


GUI_ICONS = {
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
    body = (b"\x03" + name("DataVersion") + struct.pack(">i", 3955)
            + int_list("size", size) + palette + empty_list("blocks") + empty_list("entities"))
    return gzip.compress(b"\x0a" + name("") + body + b"\x00", mtime=0)


full = os.path.join(ROOT, "data", MODID, "structure", "empty.nbt")
os.makedirs(os.path.dirname(full), exist_ok=True)
with open(full, "wb") as f:
    f.write(nbt_empty_structure([6, 3, 3]))

# pack.mcmeta is not needed for NeoForge mods (metadata comes from neoforge.mods.toml)
print("resources generated")
