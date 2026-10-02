# Flowline

Pipez-style transport mod for **Forge 1.20.1** (Java 17): item, fluid and energy pipes with per-side
modes, filters, distribution modes and speed upgrades.

Main-hand pipes now open a free curve editor: right-click adds nodes, aiming at a block finishes an endpoint,
and Ctrl+right-click opens node/edge editing. Sneak keeps ordinary block placement; off-hand pipes retain
Build for Me. Distant looked-at blocks show the current build key and a short explanation.
Pipes use matte yellow/blue/orange/purple materials with narrow windows. See
[curved-pipe controls and limits](docs/CURVED_PIPES_TR.md) and [Curvy's ID regex research](docs/CURVY_REGEX_TR.md).

> This branch is the 1.20.1 Forge backport of the NeoForge 1.21.1 version. Differences: rule "data" matches the
> stack's NBT tag (1.20.1 has no data components), and the Chemical Pipe moves Mekanism 10.4's gases, infuse types,
> pigments and slurries.

## Gameplay

| Item | Use |
| --- | --- |
| Item / Fluid / Energy Pipe | Connects to pipes of the same type and to any block exposing the matching capability. |
| Pipe in the main hand | Opens the curved-pipe editor. Right-click starts/adds a node; click a node to extend or connect; Ctrl+right-click edits. Scroll sets point distance, middle click cycles alignment grids, Enter finishes, X cancels. Sneak places normal block pipes. |
| Universal Pipe | Moves items, fluids and energy at once. Each side can switch its **channels** (items / fluids / energy) on and off; rules are item rules unless marked as fluid rules. 3 from one of each pipe + a diamond. |
| Chemical Pipe | Only with **Mekanism** installed: moves Mekanism chemicals (gases, infuse types, pigments, slurries). |
| Flowline Wrench | **Sneak + right-click** a side to cycle: normal (Insert) → **Extract** → **disconnected** → normal. Right-click a side: open its screen. **Sneak + scroll** on a side: Extract sides cycle their distribution (with Ctrl: their redstone mode), Insert sides change their priority (Ctrl: ±10). Wrenches from other mods (tagged `forge:tools/wrench`: Create, Mekanism, Thermal...) do the same on pipes. |
| Empty hand | Right-click a side: opens its config screen. Sneak + right-click with both hands empty: take a facade off. |
| Pipe in the off hand + **B** | **Build for me**: lays pipes from the block you look at (up to `buildRange` = 32 blocks away) back to you, around obstacles and with as few turns as possible. Each pipe is placed normally (protection mods apply, survival uses up the off-hand stack). The key can be changed in Controls. |
| Speed / Stack / Filter / Knozy Upgrade | Six upgrade slots per side (GUI, or right-click the side). **Speed** lowers the starting interval, **Stack** multiplies the amount per operation, **Filter** adds filter entries, **Knozy** counts as all three. Insert sides only take Filter upgrades (for more insert rules); the rest is returned when a side goes back to Insert. Everything drops when the pipe is broken. |
| Dye | Right-click a pipe with a dye: pipes of **two different colours never connect**, undyed pipes connect to every colour. Using the pipe's own colour again washes it off. |
| Configuration Card | Sneak + right-click a side: copy its mode, settings and filter. Right-click another side: paste. Sneak + use in the air: clear. |
| Filter Card | Same, but only the filter rules. |
| Facade | Craft 8 blank facades (4 iron nuggets + paper), then a blank facade + any full block → a facade of that block. Right-click a pipe to hide it behind the block; the pipe keeps working. |

Pipes can be **waterlogged**. Pipe walls have a window, and items moved by a pipe are drawn **travelling through
it** (server: `sendItemAnimations`, client: `renderTravellingItems`, both can be turned off).

### See the network and fluid flow

Hold a wrench, hold **Shift**, and look at a pipe to see its connected network through walls. Sources are green,
targets orange, overflow targets purple, and pipes pale. Higher-priority targets are brighter; a small legend
explains the colours. The view refreshes once per second and disappears when you release Shift or look away.
The server can disable it with `network.allowNetworkView` or limit its distance with `network.networkViewRange`
(32 blocks by default). Each player can turn it off with `renderNetworkView`.

Moving fluid is drawn **inside fluid pipes**, using the fluid's own texture and tint. Its texture scrolls in the
transfer direction, including bends and vertical runs, and fades about a second after the last transfer. This
block-pipe renderer covers fluid pipes; normal universal, energy and chemical pipes do not use it. Curved fluid and
universal lines show their fluid through the narrow windows too. Facades
hide the drawing. The server switch is `animations.sendFluidAnimations`; the client switch is `renderFluidInPipes`.
Client limits default to **128 animated pipes** (`maxFluidPipes`) and **32 blocks** (`fluidRenderRange`).

### Sides

Every side defaults to **Insert**; sneak-click the side facing your source chest/tank/generator with the wrench to
make it **Extract**. Both kinds have a screen:

- **Extract**: redstone mode, distribution, **redstone output** (off / while moving / while stuck: the pipe powers
  its neighbours, and only such pipes pull redstone dust), filter, upgrades, **Keep ≥** (regulator: leave at least this much of
  each kind in the source), and on energy-moving pipes **FE/t max** (rate limit).
- **Insert**: **Priority**, an **insert filter** (only matching stacks go into this target), **Max ≤** (regulator:
  keep at most this much of each kind in the target) and Filter upgrades, and **Overflow** (this target only gets what the other targets
  could not take, like a spare chest).

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
- an **amount** (Allow rules): a regulator just for matching stacks, used instead of the side's own. Extract
  sides leave at least this many of each matching kind in the source, Insert sides keep at most this many in the
  target (mB for fluids),
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

- **JEI / EMI**: drag an item or fluid from the list onto a filter slot. In the rule editor, drop it on the sample
  (use it as the rule's item), on the tag list (list its tags to tick) or on the mod box (fill in its mod).
- **Jade**: looking at a pipe shows the side's mode, distribution, pacing, priority and regulator.
- **Mekanism** (10.4): the Chemical Pipe (gases, infuse types, pigments, slurries).

## Recipes

Pipes (4 each): iron ingots and gold ingots around redstone and what the pipe carries, `IGI / RCR / IGI` with C =
hopper (item), bucket (fluid), redstone block (energy) or Mekanism's basic pressurized tube (chemical). Universal
Pipe: one of each pipe + a diamond (3). Speed upgrade: gold, sugar, redstone and a clock. Stack upgrade: iron,
redstone, two chests and a diamond. Filter upgrade: iron, paper and a comparator. Knozy: one of each upgrade + a
netherite ingot. JEI/EMI show them all.

## Config

Config is per world (`<world>/serverconfig/flowline-server.toml`; put a copy in `defaultconfigs/` to seed new
worlds) and is synced to clients, so tooltips show the server's numbers. While a single player world is open it can
also be edited in game from **Mods > Flowline > Config**; changes apply without a restart. The old
`config/flowline-common.toml` is no longer read.

It holds per-operation amounts and a Stack multiplier list for each kind, indexed by
the number of Stack upgrades: items 16 × `[1, 2, 4 ... 64]` (16 to 1024 items), fluids and chemicals 1000 mB ×
`[1, 2, 4 ... 64]` (1 to 64 buckets), energy 8000 FE/t × `[1, 4, 16, 64, 128, 256, 1024]` (8000 to 8192000 FE/t,
a bit above Mekanism's basic to ultimate cables; energy pipes work every tick while they have something to move;
amounts are capped at 2147483647 per operation), filter entries (`baseFilterSlots` = 9, `filterSlotsPerUpgrade` = 9), every pacing value
above (`idleBackoffFactor` = 2), the max network size and network view permission/range (`[network]`),
and `sendItemAnimations` / `sendFluidAnimations` (`[animations]`).

`config/flowline-client.toml` (per player, editable from the same screen at any time): `renderTravellingItems`,
`maxTravellingItems`, `ticksPerPipe`, `renderNetworkView`, `renderFluidInPipes`, `maxFluidPipes`, `fluidRenderRange`.

The network protocol is version 4: clients and servers need the same Flowline version for these visual packets.

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

- Fluid transfer notifications are tested with test-only fluid handlers, alongside network endpoint roles and
  packet limits. Rendering needs a manual client check; energy pipes still have no in-world tests.
- No pipe tiers/materials yet.
