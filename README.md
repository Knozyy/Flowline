# Flowline

Pipez-style transport mod for **Forge 1.20.1** (Java 17): item, fluid and energy pipes with per-side
modes, filters, distribution modes and speed upgrades.

> This branch is the 1.20.1 Forge backport of the NeoForge 1.21.1 version. Differences: rule "data" matches the
> stack's NBT tag (1.20.1 has no data components), and the Chemical Pipe moves Mekanism 10.4's gases, infuse types,
> pigments and slurries.

## Gameplay

| Item | Use |
| --- | --- |
| Item / Fluid / Energy Pipe | Connects to pipes of the same type and to any block exposing the matching capability. |
| Universal Pipe | Moves items, fluids and energy at once. Each side can switch its **channels** (items / fluids / energy) on and off; rules are item rules unless marked as fluid rules. 3 from one of each pipe + a gold ingot. |
| Chemical Pipe | Only with **Mekanism** installed: moves Mekanism chemicals (gases, infuse types, pigments, slurries). |
| Flowline Wrench | **Sneak + right-click** a side to cycle: normal (Insert) → **Extract** → **disconnected** → normal. Right-click a side: open its screen. **Sneak + scroll** on a side: Extract sides cycle their distribution (with Ctrl: their redstone mode), Insert sides change their priority (Ctrl: ±10). |
| Empty hand | Right-click a side: opens its config screen. Sneak + right-click with both hands empty: take a facade off. |
| Speed / Stack / Filter / Knozy Upgrade | Six upgrade slots per side (GUI, or right-click the side). **Speed** lowers the starting interval, **Stack** multiplies the amount per operation, **Filter** adds filter entries, **Knozy** counts as all three. Insert sides only take Filter upgrades (for more insert rules); the rest is returned when a side goes back to Insert. Everything drops when the pipe is broken. |
| Dye | Right-click a pipe with a dye: pipes of **two different colours never connect**, undyed pipes connect to every colour. Using the pipe's own colour again washes it off. |
| Configuration Card | Sneak + right-click a side: copy its mode, settings and filter. Right-click another side: paste. Sneak + use in the air: clear. |
| Filter Card | Same, but only the filter rules. |
| Facade | Craft 8 blank facades (4 iron nuggets + paper), then a blank facade + any full block → a facade of that block. Right-click a pipe to hide it behind the block; the pipe keeps working. |

Pipes can be **waterlogged**. Pipe walls have a window, and items moved by a pipe are drawn **travelling through
it** (server: `sendItemAnimations`, client: `renderTravellingItems`, both can be turned off).

### Sides

Every side defaults to **Insert**; sneak-click the side facing your source chest/tank/generator with the wrench to
make it **Extract**. Both kinds have a screen:

- **Extract**: redstone mode, distribution, filter, upgrades, **Keep ≥** (regulator: leave at least this much of
  each kind in the source), and on energy-moving pipes **FE/t max** (rate limit).
- **Insert**: **Priority**, an **insert filter** (only matching stacks go into this target), **Max ≤** (regulator:
  keep at most this much of each kind in the target) and Filter upgrades.

Distributions: Nearest, Farthest, Round robin, Random, **Balanced** (splits every operation evenly between the
targets) and **Priority** (highest insert priority first, ties to the nearest). Redstone: ignored, needs signal,
needs no signal, **Pulse** (one operation per rising edge).

### Networks and pacing (TPS friendly)

Pipes of one type that touch form a **cached network graph**, shared by every Extract side in it. Only changes at or
next to a pipe (placing, breaking, loading, dyeing, cutting, switching modes) invalidate the graphs there;
unrelated networks keep their caches. Target lists are computed from the cached graph, and every source/target keeps
a NeoForge `BlockCapabilityCache`, so an operation does not look block entities up again.

Each Extract side runs on an adaptive interval, similar to AE2's tick rate modulation:

- it starts at **30 ticks** (each Speed upgrade removes 4, never below the minimum),
- every operation that moves something makes it **2 ticks faster**, down to **5 ticks**,
- every operation that moves nothing **doubles** it (exponential backoff), up to **100 ticks**,
- a side with **no target at all sleeps** and costs nothing until its network changes,
- a neighbouring block change (e.g. items arriving in the source chest) wakes it back to its starting interval,
- sides are staggered so pipes placed together do not all run on the same tick.

The badge in the GUI header shows the amount multiplier and the current interval (hover for details).

### Filter and rule library

Each side (not on energy or chemical pipes) has **9 rules** by default and **9 more per Filter upgrade** (Knozy
counts). A rule can combine:

- an **item** (or fluid) id,
- any number of **tags**, matched as **OR** (any of them) or **AND** (all of them),
- **data components** (NBT), matched as **Contains** (default) or **Exact**,
- a **mod** (`@create`: everything from that mod),
- a **name pattern** (case-insensitive regular expression searched in the display name, renames included),
- a **durability range** in percent (e.g. 0–10 for nearly broken tools),
- **Allow** or **Block**.

A stack matching any Block rule never passes; if there are Allow rules it must match one of them; with only Block
rules everything else passes.

Click a rule slot to open the **rule library**: click an item in your inventory to use it as the sample, then tick
the tags it belongs to (or search every known tag, and hover a tag to see what is in it) and tick the data
components it must have (enchantments, damage, name...). Data can also be edited as SNBT text. Clicking a rule slot
with an item still adds that item as a rule directly; **shift + left-click** removes a rule. The same rule cannot be
added twice. On fluid pipes, rules show the fluid itself (not a bucket). Slots show `#`/`#n` for tag rules, `@` for
mod rules, `Aa` for name rules, a cyan mark for durability, a purple corner for NBT and a red bar for Block rules.
Older rules load automatically.

### Integrations (all optional)

- **JEI / EMI**: drag an item or fluid from the list onto a filter slot.
- **Jade**: looking at a pipe shows the side's mode, distribution, pacing, priority and regulator.
- **Mekanism** (10.4): the Chemical Pipe (gases, infuse types, pigments, slurries).

## Config

Config is per world (`<world>/serverconfig/flowline-server.toml`; put a copy in `defaultconfigs/` to seed new
worlds) and is synced to clients, so tooltips show the server's numbers. While a single player world is open it can
also be edited in game from **Mods > Flowline > Config**; changes apply without a restart. The old
`config/flowline-common.toml` is no longer read.

It holds per-operation amounts and a Stack multiplier list for each kind, indexed by
the number of Stack upgrades: items 16 × `[1, 2, 4 ... 64]` (16 to 1024 items), fluids and chemicals 1000 mB ×
`[1, 2, 4 ... 64]` (1 to 64 buckets), energy 1000 FE × `[1, 8, 16, 32, 64, 96, 128]` (amounts are capped at
2147483647 per operation), filter entries (`baseFilterSlots` = 9, `filterSlotsPerUpgrade` = 9), every pacing value
above (`idleBackoffFactor` = 2), the max network size (`[network]`) and `sendItemAnimations` (`[animations]`).

`config/flowline-client.toml` (per player, editable from the same screen at any time): `renderTravellingItems`,
`maxTravellingItems`, `ticksPerPipe`.

Pipes are see-through by default. Players who prefer solid pipes can enable the built-in **Flowline: Solid Pipes**
resource pack (Options > Resource Packs).

## Building

```
./gradlew build        # jar in build/libs
./gradlew runClient    # dev client
./gradlew runGameTestServer   # headless in-world tests (src/main/java/.../test)
```

CI (`.github/workflows/build.yml`) builds the jar, uploads it as the `flowline-jar` artifact and runs the in-world tests.

Requires access to `maven.minecraftforge.net`, Mojang's asset/library hosts and, for the optional integration APIs,
`maven.blamejared.com` (JEI), `maven.terraformersmc.com` (EMI), `www.cursemaven.com` (Jade) and `modmaven.dev`
(Mekanism).

## Resources

Textures/models/recipes/lang are generated by `python3 tools/gen_resources.py` (procedural pixel art, pure
standard library).

## Status / TODO

- Fluid and energy pipes have no in-world tests (vanilla has no tank or energy block to test against).
- No pipe tiers/materials yet.
