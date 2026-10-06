"""Crafting recipes."""
from gen.common import *  # noqa: F401,F403
from gen.textures import *  # noqa: F401,F403
from gen.models import *  # noqa: F401,F403


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


def shaped(name, pattern, key, result, count=1, needs=None):
    recipe = {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "pattern": pattern,
        "key": {k: ing(v) for k, v in key.items()},
        "result": {"item": result, "count": count},
    }
    if needs:
        recipe = {"conditions": [{"type": "forge:mod_loaded", "modid": needs}], **recipe}
    write_json(f"data/{MODID}/recipes/{name}.json", recipe)


# pipes: iron walls with gold and redstone around what the pipe carries (hopper, bucket, redstone block)
PIPE = ["IGI", "RCR", "IGI"]
PIPE_KEY = {"I": "minecraft:iron_ingot", "G": "minecraft:gold_ingot", "R": "minecraft:redstone"}
shaped("item_pipe", PIPE, {**PIPE_KEY, "C": "minecraft:hopper"}, f"{MODID}:item_pipe", 4)
shaped("fluid_pipe", PIPE, {**PIPE_KEY, "C": "minecraft:bucket"}, f"{MODID}:fluid_pipe", 4)
shaped("energy_pipe", PIPE, {**PIPE_KEY, "C": "minecraft:redstone_block"}, f"{MODID}:energy_pipe", 4)
shapeless("universal_pipe", [f"{MODID}:item_pipe", f"{MODID}:fluid_pipe", f"{MODID}:energy_pipe", "minecraft:diamond"],
          f"{MODID}:universal_pipe", 3)
shaped("wrench", ["I I", " S ", " S "], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}, f"{MODID}:wrench")
shaped("speed_upgrade", ["GSG", "RCR", "GSG"],
       {"G": "minecraft:gold_ingot", "S": "minecraft:sugar", "R": "minecraft:redstone", "C": "minecraft:clock"},
       f"{MODID}:speed_upgrade")
shaped("stack_upgrade", ["IRI", "CDC", "IRI"],
       {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "C": "minecraft:chest", "D": "minecraft:diamond"},
       f"{MODID}:stack_upgrade")
shaped("filter_upgrade", ["IPI", "PCP", "IPI"],
       {"I": "minecraft:iron_ingot", "P": "minecraft:paper", "C": "minecraft:comparator"}, f"{MODID}:filter_upgrade")
shapeless("knozy_upgrade", [f"{MODID}:speed_upgrade", f"{MODID}:stack_upgrade", f"{MODID}:filter_upgrade",
                            "minecraft:netherite_ingot"], f"{MODID}:knozy_upgrade")
shaped("chemical_pipe", PIPE, {**PIPE_KEY, "C": "mekanism:basic_pressurized_tube"}, f"{MODID}:chemical_pipe", 4,
       needs="mekanism")
shaped("filter_card", ["PRP", "PHP", "PPP"],
       {"P": "minecraft:paper", "R": "minecraft:redstone", "H": "minecraft:hopper"}, f"{MODID}:filter_card")
