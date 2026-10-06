"""Block and item textures: pipe rails, glass, collars, Curvy atlases, wrench, upgrade modules."""
from gen.common import *  # noqa: F401,F403


DARK = (0x1E, 0x21, 0x2A)
EXTRACT_GREEN = (0x5D, 0xDB, 0x8A)


def body(base, acc, x, y):
    if x in (0, 15) or y in (0, 15):
        return mixa(base, acc, 0.35)                 # outer highlight rim
    if x in (1, 14) or y in (1, 14):
        return shade(base, 1.04)
    if x in (5, 10) or y in (5, 10):
        return shade(base, 0.72)                     # groove
    if x in (4, 11) or y in (4, 11):
        return mixa(base, acc, 0.18)                 # light lip next to the groove
    return shade(base, 0.96)


def pipe_rail(base, acc):
    """Metal of the corner rails: the given colour with a small light bolt every few pixels along it."""
    return [[mixa(base, acc, 0.45) if y % 4 == 0 and x % 4 == 0 else shade(base, 0.86 if (x + y) % 2 else 0.92)
             for x in range(16)] for y in range(16)]


GLASS = (0xE6, 0xF2, 0xFF)


def streak(x, y):
    """Glass highlights: two short diagonal streaks like vanilla glass, the rest is clear."""
    return (x + y == 15 and 2 <= x <= 5) or (x + y == 16 and 3 <= x <= 4) or (x + y == 13 and 9 <= x <= 11)


def pipe_glass(base, solid):
    """Glass panel between the rails: a faint wash of the pipe's colour with brighter highlights (the models are
    translucent); the Solid Pipes pack swaps it for a frosted, opaque panel."""
    if solid:
        return [[shade(GLASS, 0.95) if streak(x, y) else mixa(mix(DARK, base, 0.45), GLASS, 0.12)
                 for x in range(16)] for y in range(16)]
    tint = mix(GLASS, base, 0.35)
    return [[shade(GLASS, 1.0)[:3] + (190,) if streak(x, y) else tint + (60,) for x in range(16)] for y in range(16)]


def pipe_collar(base, acc):
    """Solid collar where the pipe meets a block, and for the item model: dark frame, rivets, dark port."""
    px = [[body(base, acc, x, y) for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            if x in (4, 11) or y in (4, 11):
                if 4 <= x <= 11 and 4 <= y <= 11:
                    px[y][x] = mixa(DARK, base, 0.12)
            elif 5 <= x <= 10 and 5 <= y <= 10:
                px[y][x] = mixa(base, acc, 0.12) if (x in (5, 10) or y in (5, 10)) else shade(base, 0.98)
            if 7 <= x <= 8 and 7 <= y <= 8:
                px[y][x] = shade(DARK, 0.9)          # port opening
            elif x in (6, 9) and y in (6, 9):
                px[y][x] = mixa(base, acc, 0.55)     # corner rivets
    return px


def pipe_extract(base, acc):
    """Collar with a bright green ring (same "extract" colour on every pipe)."""
    px = pipe_collar(base, acc)
    for y in range(3, 13):
        for x in range(3, 13):
            if x in (3, 4, 11, 12) or y in (3, 4, 11, 12):
                outer = x in (3, 12) or y in (3, 12)
                px[y][x] = shade(EXTRACT_GREEN, 0.7) if outer else mixa(EXTRACT_GREEN, (255, 255, 255), 0.18)
    return px


for idx, (name, base) in enumerate(TYPES.items()):
    seed = idx + 1
    acc = ACCENTS[name]
    write_png(f"assets/{MODID}/textures/block/{name}_pipe.png", pipe_rail(base, ACCENTS[name]))
    write_png(f"assets/{MODID}/textures/block/{name}_pipe_glass.png", pipe_glass(base, False))
    write_png(f"{SOLID_PACK}/assets/{MODID}/textures/block/{name}_pipe_glass.png", pipe_glass(base, True))
    # the inventory / in-hand model shows light frosted glass: see-through glass reads as empty in a slot
    write_png(f"assets/{MODID}/textures/item/{name}_pipe_frosted.png",
              [[shade(GLASS, 1.0) if streak(x, y) else shade(mix(GLASS, base, 0.4), 0.95) for x in range(16)]
               for y in range(16)])
    write_png(f"assets/{MODID}/textures/block/{name}_pipe_collar.png", pipe_collar(base, acc))
    write_png(f"assets/{MODID}/textures/block/{name}_pipe_extract.png", pipe_extract(base, acc))


# Curvy uses its own native mesh and transparency toggle with Flowline materials.
def curvy_atlas(name, base, stripe=None):
    """Curvy's 32x32 pipe atlas: the top 32x16 strip wraps around the tube (x along it, y around it), the
    bottom-left 16x16 square caps its ends. Same material as the block pipes: four coloured rails along a pale glass
    tube, a dark joint once per repeat, and the block pipes' collar as the end cap. A universal pipe's channels add a
    stripe in the channel's colour."""
    acc = ACCENTS[name]
    glass = mix(GLASS, base, 0.22)
    px = [[CLEAR] * 32 for _ in range(32)]
    for y in range(16):
        for x in range(32):
            c = shade(glass, 1.0 if (x + y) % 9 else 1.06)
            if y in (0, 4, 8, 12):
                c = shade(base, 0.9)                       # rail
            elif y in (1, 5, 9, 13):
                c = mixa(base, acc, 0.4)                   # lit side of the rail
            if stripe and y in (2, 10):
                c = shade(stripe, 1.0)
            if x <= 1:
                c = mixa(DARK, base, 0.12)
            elif x == 2:
                c = mixa(base, acc, 0.3)
            px[y][x] = c
    cap = pipe_collar(base, acc)
    for y in range(16):
        for x in range(16):
            px[16 + y][x] = cap[y][x]
    return px


for name in ("item", "fluid", "energy"):
    write_png(f"assets/{MODID}/textures/block/curvy_{name}_pipe.png", curvy_atlas(name, TYPES[name]), 32, 32)
    # the universal pipe's Curvy channels: its own colour with a stripe in the channel's colour
    write_png(f"assets/{MODID}/textures/block/curvy_universal_{name}_pipe.png",
              curvy_atlas("universal", TYPES["universal"], stripe=TYPES[name]), 32, 32)

# node cage: greyscale metal, tinted in game with the pipe's dye colour (or its own colour when undyed)
cage = [[shade((0xF2, 0xF2, 0xF2), 1.0 if (x + y) % 2 else 0.9) for x in range(16)] for y in range(16)]
for y in range(0, 16, 4):
    for x in range(0, 16, 4):
        cage[y][x] = shade((0xFF, 0xFF, 0xFF), 1.0)
write_png(f"assets/{MODID}/textures/block/pipe_cage.png", cage)
# corner rails on blocks: greyscale like the cage, tinted the same way
write_png(f"assets/{MODID}/textures/block/pipe_rail.png", pipe_rail((0xF2, 0xF2, 0xF2), (0xFF, 0xFF, 0xFF)))


GREY = (0xB4, 0xB4, 0xBC)


def wrench_texture():
    """Flowline wrench: an open hex jaw at the upper right with the pipes' glass in its throat, a dark collar, a steel
    shaft with a glass inlay and a banded pastel grip with an end cap. Handle at the lower left, so `item/handheld`
    holds it like a tool. Drawn as material masks, then outlined and lit from the upper left."""
    import math
    g = [[None] * 16 for _ in range(16)]

    def put(test, mat):
        for y in range(16):
            for x in range(16):
                if test(x + 0.5, y + 0.5):
                    g[y][x] = mat

    def segment(ax, ay, bx, by, w, mat, near=None, split=None):
        """A straight bar from a to b (square ends); `near` replaces `mat` on the part before `split`."""
        for y in range(16):
            for x in range(16):
                X, Y = x + 0.5, y + 0.5
                t = ((X - ax) * (bx - ax) + (Y - ay) * (by - ay)) / ((bx - ax) ** 2 + (by - ay) ** 2)
                if 0 <= t <= 1 and math.hypot(X - (ax + t * (bx - ax)), Y - (ay + t * (by - ay))) <= w / 2:
                    g[y][x] = near if split is not None and t < split else mat

    cx, cy = 11.2, 4.8
    segment(2.2, 13.8, 9.2, 6.8, 2.6, "m", near="r", split=0.5)       # shaft, grip on the lower half
    segment(1.4, 14.6, 2.6, 13.4, 2.8, "k")                            # end cap
    put(lambda X, Y: max(abs(X - cx) * 0.866 + abs(Y - cy) * 0.5, abs(Y - cy)) <= 3.9, "m")   # hex head
    s2 = math.sqrt(0.5)
    put(lambda X, Y: -0.2 <= ((X - cx) - (Y - cy)) * s2 <= 6 and abs((X - cx) + (Y - cy)) * s2 <= 1.2, None)  # jaw
    put(lambda X, Y: math.hypot(X - 10.3, Y - 5.7) <= 0.9, "c")        # glass in the throat
    segment(8.6, 7.4, 9.6, 6.4, 3.2, "h")                              # collar
    segment(5.6, 10.4, 7.9, 8.1, 0.9, "c")                             # glass inlay
    for y in range(16):                                                 # bands on the grip
        for x in range(16):
            if g[y][x] == "r" and (x - y) % 3 == 0:
                g[y][x] = "R"
    palette = {
        "m": ((0xE4, 0xE9, 0xF0), (0xA7, 0xB0, 0xBC), (0x5E, 0x67, 0x73)),
        "h": ((0xA0, 0xA8, 0xB4), (0x6E, 0x76, 0x82), (0x40, 0x46, 0x50)),
        "k": ((0x6E, 0x76, 0x82), (0x50, 0x56, 0x60), (0x34, 0x38, 0x40)),
        "c": ((0xF2, 0xFF, 0xFF), (0x8F, 0xE6, 0xF0), (0x3E, 0xA8, 0xBC)),
        "r": ((0xFF, 0xD3, 0xBC), (0xF2, 0xA0, 0x7B), (0xB0, 0x60, 0x40)),
        "R": ((0xB0, 0x60, 0x40), (0x8C, 0x48, 0x30), (0x60, 0x30, 0x20)),
    }
    outline = (0x1E, 0x20, 0x26, 255)
    out = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            m = g[y][x]
            if m is None:
                if any(0 <= x + ex < 16 and 0 <= y + ey < 16 and g[y + ey][x + ex]
                       for ex, ey in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out[y][x] = outline
                continue
            light, mid, dark = palette[m]
            up = g[y - 1][x] if y > 0 else None
            left = g[y][x - 1] if x > 0 else None
            down = g[y + 1][x] if y < 15 else None
            right = g[y][x + 1] if x < 15 else None
            c = light if (up is None or left is None) else dark if (down is None or right is None) else mid
            out[y][x] = c + (255,)
    return out


write_png(f"assets/{MODID}/textures/item/wrench.png", wrench_texture())

def module_icon(symbol, accent, card=False):
    """A pipe seen end-on, like the glass ducts: dark frame, the four corner rails in the accent colour, dark glass with
    `symbol` (a set of (x, y) pixels) in the accent colour. A card has a punched corner hole instead of a rail."""
    frame, glass_c = (0x4A, 0x52, 0x5E), mix(DARK, accent, 0.18)
    grid = [[None] * 16 for _ in range(16)]
    for y in range(2, 14):
        for x in range(2, 14):
            grid[y][x] = glass_c if 4 <= x <= 11 and 4 <= y <= 11 else frame
    rails = ((2, 2), (3, 2), (2, 3), (13, 2), (12, 2), (13, 3), (2, 13), (2, 12), (3, 13), (13, 13), (12, 13), (13, 12))
    for x, y in rails:
        grid[y][x] = accent
    if card:
        for x, y in ((13, 2), (12, 2), (13, 3)):
            grid[y][x] = None
    for x, y in symbol:
        grid[y][x] = accent
    out = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            c = grid[y][x]
            if c is None:
                if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and grid[y + dy][x + dx] is not None
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out[y][x] = (0x1E, 0x20, 0x26, 255)
                continue
            up = grid[y - 1][x] if y > 0 else None
            left = grid[y][x - 1] if x > 0 else None
            down = grid[y + 1][x] if y < 15 else None
            right = grid[y][x + 1] if x < 15 else None
            out[y][x] = shade(c, 1.18 if (up is None or left is None) else 0.8 if (down is None or right is None) else 1.0)
    return out


def chevrons(x0s, y0, height):
    half = height // 2
    return {(x0 + half - abs(half - dy), y0 + dy) for x0 in x0s for dy in range(height)}


def bars(rows):
    return {(x, y) for y, (a, b) in rows for x in range(a, b + 1)}


UPGRADE_ART = {
    "speed_upgrade": (chevrons((4, 8), 4, 7), TYPES["fluid"]),
    "stack_upgrade": (bars([(4, (6, 11)), (5, (6, 11)), (7, (5, 10)), (8, (5, 10)), (10, (4, 9)), (11, (4, 9))]),
                      TYPES["energy"]),
    "filter_upgrade": (bars([(4, (4, 11)), (5, (5, 10)), (6, (6, 9)), (7, (7, 8)), (8, (7, 8)), (9, (7, 8)),
                             (10, (7, 8))]), TYPES["chemical"]),
    "knozy_upgrade": (chevrons((5, 8), 3, 5) | bars([(9, (4, 11)), (11, (4, 11))]), TYPES["universal"]),
}
for up_name, (symbol, accent) in UPGRADE_ART.items():
    write_png(f"assets/{MODID}/textures/item/{up_name}.png", module_icon(symbol, accent))


CARD_ART = {
    # a funnel: copies only the filter
    "filter_card": (bars([(4, (4, 11)), (5, (5, 10)), (6, (6, 9)), (7, (7, 8)), (8, (7, 8)), (9, (7, 8))]),
                    TYPES["chemical"]),
}
for card_name, (symbol, accent) in CARD_ART.items():
    write_png(f"assets/{MODID}/textures/item/{card_name}.png", module_icon(symbol, accent, card=True))
