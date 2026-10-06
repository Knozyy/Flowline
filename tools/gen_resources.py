#!/usr/bin/env python3
"""Generates blockstates, models, textures, recipes, loot tables and lang files under src/main/resources.

Run from the repo root:  python3 tools/gen_resources.py
This branch targets Minecraft 1.20.1 / Forge: plural data folders, {"item": ...} recipe results, forge: conditions.
Pure standard library. The work is split by kind under tools/gen/ (common helpers first, then textures, models,
recipes, lang, GUI icons and the GameTest structure).
"""
import os
import sys

# run from the repo root; the modules write under src/main/resources in this order
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen.textures  # noqa: E402,F401
import gen.models  # noqa: E402,F401
import gen.recipes  # noqa: E402,F401
import gen.lang  # noqa: E402,F401
import gen.gui_icons  # noqa: E402,F401
import gen.gametest  # noqa: E402,F401

print("resources generated")
