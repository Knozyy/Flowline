"""Icons of the pipe screens."""
from gen.common import *  # noqa: F401,F403
from gen.textures import *  # noqa: F401,F403
from gen.models import *  # noqa: F401,F403
from gen.recipes import *  # noqa: F401,F403
from gen.lang import *  # noqa: F401,F403


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
GREEN, GOLD, WHITE, CYAN = (0x7F, 0xD8, 0x96), (0xF2, 0xC8, 0x6E), (0xF0, 0xF2, 0xF5), (0x8F, 0xE6, 0xF0)
SOFT_RED = (0xE8, 0x6A, 0x6A)   # "no" marks; RED stays the vanilla redstone colour of the torches


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
    c.line(3, 3, 11, 11, SOFT_RED, 2)
    c.line(11, 3, 3, 11, SOFT_RED, 2)
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
        c.line(2, 14, 14, 2, SOFT_RED, 2)
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


ORANGE, BLUE, PEACH = TYPES["item"], TYPES["fluid"], TYPES["energy"]   # item / fluid / energy, as the pipes


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


def overflow_icon():
    """A full box spilling into a spare one."""
    c = Canvas()
    c.rect(1, 2, 8, 8, ORANGE)
    c.rect(1, 2, 8, 3, tuple(min(255, int(v * 1.25)) for v in ORANGE))
    c.rect(8, 11, 14, 14, GREY)
    c.line(9, 5, 12, 5, WHITE)
    c.line(12, 5, 12, 9, WHITE)
    c.dot(11, 8, WHITE)
    c.dot(13, 8, WHITE)
    return c


def signal_icon(kind):
    c = torch(kind != "off")
    if kind == "moving":
        c.line(11, 11, 14, 11, GREEN)
        c.dot(13, 10, GREEN)
        c.dot(13, 12, GREEN)
    elif kind == "stuck":
        c.rect(11, 9, 14, 13, GREY_DK)
        c.line(11, 9, 14, 13, SOFT_RED)
    return c


GUI_ICONS = {
    "overflow": overflow_icon(),
    "signal_off": signal_icon("off"),
    "signal_moving": signal_icon("moving"),
    "signal_stuck": signal_icon("stuck"),
    "redstone_pulse": pulse(),
    "distribution_balanced": balanced(),
    "distribution_priority": priority_icon(),
    "channel_items": box_icon(ORANGE),
    "channel_fluids": drop_icon(BLUE),
    "channel_energy": bolt_icon(PEACH),
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
