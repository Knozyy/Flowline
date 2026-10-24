"""Block and item textures: pipe rails, glass, collars, wrench, upgrade modules."""
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
    """The Flowline wrench's 3D model (models.py) paints its cubes from 4x4 swatches here: steel, dark steel, the
    pastel grip, the grip's bands (top row) and the pipes' glass (second row). Each swatch is lit from the upper left."""
    swatches = {
        (0, 0): ((0xE4, 0xE9, 0xF0), (0xA7, 0xB0, 0xBC), (0x5E, 0x67, 0x73)),   # steel
        (4, 0): ((0x6E, 0x76, 0x82), (0x50, 0x56, 0x60), (0x34, 0x38, 0x40)),   # dark steel
        (8, 0): ((0xFF, 0xD3, 0xBC), (0xF2, 0xA0, 0x7B), (0xB0, 0x60, 0x40)),   # grip
        (12, 0): ((0xB0, 0x60, 0x40), (0x8C, 0x48, 0x30), (0x60, 0x30, 0x20)),  # grip band
        (0, 4): ((0xF2, 0xFF, 0xFF), (0x8F, 0xE6, 0xF0), (0x3E, 0xA8, 0xBC)),   # glass
    }
    out = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for (u, v), (light, mid, dark) in swatches.items():
        for y in range(4):
            for x in range(4):
                c = light if x == 0 or y == 0 else dark if x == 3 or y == 3 else mid
                out[v + y][u + x] = c + (255,)
    return out


write_png(f"assets/{MODID}/textures/item/wrench.png", wrench_texture())

CARD_CUT = ((10, 1), (11, 1), (12, 1), (11, 2), (12, 2), (12, 3))   # the cut top-right corner


def card_icon(body, symbol, symbol_colour, stripe=None, contacts=False):
    """A portrait card with a cut corner (upgrades and the Filter Card): `symbol` pixels on it, and either a coloured
    stripe along the bottom (upgrades) or gold contacts (the Filter Card). Lit from the upper left, dark outline."""
    grid = [[None] * 16 for _ in range(16)]
    for y in range(1, 15):
        for x in range(3, 13):
            grid[y][x] = body
    for x, y in CARD_CUT:
        grid[y][x] = None
    if stripe:
        for x in range(3, 13):
            grid[13][x] = grid[14][x] = stripe
    if contacts:
        for x in range(4, 12, 2):
            grid[12][x] = grid[13][x] = (0xE8, 0xC4, 0x6A)
    for x, y in symbol:
        if y <= 12:
            grid[y][x] = symbol_colour
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
# upgrades: dark cards with a big symbol and a stripe in their colour
for up_name, (symbol, accent) in UPGRADE_ART.items():
    write_png(f"assets/{MODID}/textures/item/{up_name}.png",
              card_icon((0x4A, 0x52, 0x5E), symbol, accent, stripe=tuple(int(c * 0.75) for c in accent)))


# the Filter Card: a light card with gold contacts and a small funnel, so it never looks like the Filter Upgrade
write_png(f"assets/{MODID}/textures/item/filter_card.png",
          card_icon((0xC8, 0xCE, 0xD6), bars([(5, (5, 9)), (6, (6, 8)), (7, (7, 7)), (8, (7, 7))]), (0x3E, 0x9A, 0x5E),
                    contacts=True))
