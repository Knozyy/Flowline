"""Block models (metal and glass parts), item models, blockstates, loot tables and tags."""
from gen.common import *  # noqa: F401,F403
from gen.textures import *  # noqa: F401,F403


ALL_FACES = ("down", "up", "north", "south", "west", "east")
RENDER_TYPE = "minecraft:translucent"   # the glass between the rails is see-through, not just cut out


def box(f, t, faces, texture, tint=False, uv=None):
    out = {}
    for d in faces:
        face = {"texture": texture}
        if uv is not None:
            face["uv"] = uv(d)
        if tint:
            face["tintindex"] = 0
        out[d] = face
    return {"from": f, "to": t, "faces": out}


def inward(lo, hi, z0, z1, texture):
    """Zero-thickness planes facing into a tube along z, so the far glass panels show their highlights too."""
    return [
        {"from": [lo, lo, z0], "to": [lo, hi, z1], "shade": False, "faces": {"east": {"texture": texture}}},
        {"from": [hi, lo, z0], "to": [hi, hi, z1], "shade": False, "faces": {"west": {"texture": texture}}},
        {"from": [lo, lo, z0], "to": [hi, lo, z1], "shade": False, "faces": {"up": {"texture": texture}}},
        {"from": [lo, hi, z0], "to": [hi, hi, z1], "shade": False, "faces": {"down": {"texture": texture}}},
    ]


SIDES = ("down", "up", "west", "east")
# Glass duct: four metal rails on the corners of the 6x6 pipe, glass panels set 0.5 px inside them.
RAIL_LO, RAIL_HI, GLASS_LO, GLASS_HI = 5, 11, 5.5, 10.5


def rails(z0, z1, tinted=True):
    """The four corner rails of a stretch of duct along z. On blocks they are greyscale and tinted like the node cage
    (dye colour, or the pipe's own colour when undyed); the item model has no tint and uses the coloured texture."""
    return [box([x, y, z0], [x + 1, y + 1, z1], SIDES, "#rail" if tinted else "#pipe", tint=tinted)
            for x in (RAIL_LO, RAIL_HI - 1) for y in (RAIL_LO, RAIL_HI - 1)]


def glass(z0, z1):
    """The glass between the rails of a stretch of duct along z, outside and inside faces."""
    return [box([GLASS_LO, GLASS_LO, z0], [GLASS_HI, GLASS_HI, z1], SIDES, "#glass")] + \
        inward(GLASS_LO, GLASS_HI, z0, z1, "#glass")


def duct(z0, z1, tinted=True):
    return rails(z0, z1, tinted) + glass(z0, z1)


def cage_bars(lo, hi, w):
    """The 12 edge bars of the node, tinted (dye colour, or the pipe's own colour when undyed)."""
    out = []
    for axis in range(3):
        b, c = [i for i in range(3) if i != axis]
        for p in (lo, hi - w):
            for q in (lo, hi - w):
                f, t = [0, 0, 0], [0, 0, 0]
                f[axis], t[axis] = lo, hi
                f[b], t[b], f[c], t[c] = p, p + w, q, q + w
                out.append(box(f, t, ALL_FACES, "#cage", tint=True))
    return out


def collar_uv(lo, hi, depth):
    def uv(d):
        if d in ("north", "south"):
            return [lo, lo, hi, hi]
        return [lo, lo, hi, lo + depth] if d in ("down", "up") else [lo, lo, lo + depth, hi]
    return uv


SIDE_NAMES = ("north", "south", "east", "west", "up", "down")
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}
LINKED = "pipe|endpoint|extract"


def straight_conditions():
    """(when, rotation) for the three straight runs: two opposite sides linked, the other four not."""
    out = []
    for a, b, r in (("north", "south", {}), ("east", "west", {"y": 90}), ("up", "down", {"x": 90})):
        out.append(({s: (LINKED if s in (a, b) else "none") for s in SIDE_NAMES}, r))
    return out


def cage_conditions():
    """Every state that is not a straight run: two linked sides that are not opposite (bends, junctions), or at
    most one linked side (a dead end, or a lone pipe)."""
    out = []
    for i, a in enumerate(SIDE_NAMES):
        for b in SIDE_NAMES[i + 1:]:
            if OPPOSITE[a] != b:
                out.append({a: LINKED, b: LINKED})
    out.append({s: "none" for s in SIDE_NAMES})
    for a in SIDE_NAMES:
        out.append({s: "none" for s in SIDE_NAMES if s != a})
    return out


# Every part of a pipe comes as two models: the metal (rails, cage, collars) on the cheap solid layer, and the glass
# on the translucent layer, so only the glass pays for translucency sorting.
SOLID = "minecraft:solid"
PARTS = ("core", "straight", "arm", "endpoint", "extract")


def wall_models(name):
    """Metal and glass models of each part of a glass-duct pipe."""
    tex = f"{MODID}:block/{name}_pipe"
    metal_tex = {"rail": f"{MODID}:block/pipe_rail", "collar": f"{tex}_collar", "particle": f"{tex}_collar"}
    glass_tex = {"glass": f"{tex}_glass", "particle": f"{tex}_collar"}
    parts = {
        "core": (cage_bars(5, 11, 1), [box([GLASS_LO] * 3, [GLASS_HI] * 3, ALL_FACES, "#glass")],
                 {"cage": f"{MODID}:block/pipe_cage"}),
        # the middle of a straight run: the duct just carries on (no cage), see the blockstate below
        "straight": (rails(5, 11), glass(5, 11), {}),
        "arm": (rails(0, 5), glass(0, 5), {}),
        "endpoint": (rails(2, 5) + [box([4, 4, 0], [12, 12, 2], ALL_FACES, "#collar", uv=collar_uv(4, 12, 2))],
                     glass(2, 5), {}),
        # extracting end: a larger, thicker collar with a green ring so it stands out from inserting ends
        "extract": (rails(2.5, 5) + [box([3, 3, 0], [13, 13, 2.5], ALL_FACES, "#ring", uv=collar_uv(3, 13, 2.5))],
                    glass(2.5, 5), {"ring": f"{tex}_extract"}),
    }
    for part, (metal, panes, extra) in parts.items():
        write_json(f"assets/{MODID}/models/block/{name}_pipe_{part}.json", {
            "render_type": SOLID, "textures": {**metal_tex, **extra}, "elements": metal,
        })
        write_json(f"assets/{MODID}/models/block/{name}_pipe_{part}_glass.json", {
            "render_type": RENDER_TYPE, "textures": glass_tex, "elements": panes,
        })


write_json(f"{SOLID_PACK}/pack.mcmeta", {
    "pack": {"pack_format": 15, "description": {"translate": "pack.flowline.solid_pipes.description"}},
})

for name in TYPES:
    tex = f"{MODID}:block/{name}_pipe"
    wall_models(name)
    # inventory icon: a straight stretch of duct with collars at both ends
    write_json(f"assets/{MODID}/models/item/{name}_pipe.json", {
        "parent": "minecraft:block/block",
        "render_type": SOLID,
        "textures": {"pipe": tex, "glass": f"{MODID}:item/{name}_pipe_frosted", "collar": f"{tex}_collar",
                     "particle": f"{tex}_collar"},
        "elements": rails(2, 14, tinted=False) + glass(2, 14)[:1] + [
            box([4, 4, 0], [12, 12, 2], ALL_FACES, "#collar", uv=collar_uv(4, 12, 2)),
            box([4, 4, 14], [12, 12, 16], ALL_FACES, "#collar", uv=collar_uv(4, 12, 2)),
        ],
    })

    # blockstate: each side adds an arm (pipe) or an arm+collar (endpoint). The middle is the cage, except on a
    # straight run (exactly two opposite sides connected) where the duct carries straight on.
    rot = {
        "north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270},
        "up": {"x": 270}, "down": {"x": 90},
    }
    multipart = []

    def part(when, model, r=None):
        """A part is drawn twice under the same condition: its metal model and its glass model."""
        for suffix in ("", "_glass"):
            multipart.append({"when": when, "apply": {"model": f"{MODID}:block/{name}_pipe_{model}{suffix}", **(r or {})}})

    for when, r in straight_conditions():
        part(when, "straight", r)
    part({"OR": cage_conditions()}, "core")
    for side, r in rot.items():
        part({side: "pipe"}, "arm", r)
        part({side: "endpoint"}, "endpoint", r)
        part({side: "extract"}, "extract", r)
    write_json(f"assets/{MODID}/blockstates/{name}_pipe.json", {"multipart": multipart})

    # Loot table. Forge 1.20.1 has no load conditions for loot tables, so an optional pipe's table (naming an item
    # that only exists with its mod) lives in a data pack that is only added when the mod is loaded
    # (see OptionalPacks): resources/optional/<mod>/.
    table = {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "bonus_rolls": 0.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"{MODID}:{name}_pipe"}],
        }],
    }
    if name in OPTIONAL:
        write_json(f"optional/{OPTIONAL[name]}/data/{MODID}/loot_tables/blocks/{name}_pipe.json", table)
        write_json(f"optional/{OPTIONAL[name]}/pack.mcmeta", {"pack": {
            "pack_format": 15, "description": f"{MODID} data for {OPTIONAL[name]}"}})
    else:
        write_json(f"data/{MODID}/loot_tables/blocks/{name}_pipe.json", table)

write_json(f"assets/{MODID}/models/item/wrench.json", {
    "parent": "minecraft:item/handheld",
    "textures": {"layer0": f"{MODID}:item/wrench"},
})
for name in ("speed_upgrade", "stack_upgrade", "filter_upgrade", "knozy_upgrade",
             "filter_card"):
    write_json(f"assets/{MODID}/models/item/{name}.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{MODID}:item/{name}"},
    })

# the common wrench tag: other mods treat the Flowline Wrench as a wrench, and their wrenches work on pipes
write_json("data/forge/tags/items/tools/wrench.json", {"replace": False, "values": [f"{MODID}:wrench"]})

write_json("data/minecraft/tags/blocks/mineable/pickaxe.json", {
    "replace": False,
    # optional pipes are only registered with their mod, so they must not be required here
    "values": [f"{MODID}:{n}_pipe" if n not in OPTIONAL else {"id": f"{MODID}:{n}_pipe", "required": False}
               for n in TYPES],
})
