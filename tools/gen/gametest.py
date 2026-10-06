"""The empty GameTest structure."""
from gen.common import *  # noqa: F401,F403
from gen.textures import *  # noqa: F401,F403
from gen.models import *  # noqa: F401,F403
from gen.recipes import *  # noqa: F401,F403
from gen.lang import *  # noqa: F401,F403
from gen.gui_icons import *  # noqa: F401,F403


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
