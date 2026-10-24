# Flowline

Pipez-style transport mod for **Forge 1.20.1** (Java 17): item, fluid, energy and (with Mekanism) chemical pipes,
plus a universal pipe, with per-side modes, filters, distribution modes and speed upgrades. MIT licensed.

**Curved pipes** are part of Flowline itself, no other mod needed: the same pipe items lay block pipes or curved
lines, for every pipe type (item, fluid, energy, universal and chemical). A pipe in the **main hand always places a
block pipe**; a pipe in the **off hand** has two modes, **Build for me** and **Curved**, switched with the mode key
(**B** by default) and shown under the crosshair. A curved line is solved into a smooth curve (bends, joints, collisions,
cost), drawn with the block pipe's square glass duct, and paid for with pipes from the off hand (given back when it breaks).

A curved line belongs to the **same network** as the block pipes it touches: one Extract side on the source chest
moves things through the whole network, curves included. A curved end on a chest, tank or machine face **is a pipe
side**, with the same screen, wrench, Filter Card and upgrades; an end on a block pipe face just carries. Curved
pipes only join pipes of their own type and colour. See the [curved pipe guide](docs/CURVED_PIPES_TR.md) (in
Turkish).

> This branch is the 1.20.1 Forge backport of the NeoForge 1.21.1 version. Differences: rule "data" matches the
> stack's NBT tag (1.20.1 has no data components), and the Chemical Pipe moves Mekanism 10.4's gases, infuse types,
> pigments and slurries. Mekanism's heat is not supported.

## Gameplay

| Item | Use |
| --- | --- |
| Item / Fluid / Energy Pipe | Connects to pipes of the same type and to any block exposing the matching capability. |
| Universal Pipe | Moves items, fluids and energy at once. Each side can switch its **channels** (items / fluids / energy) on and off; rules are item rules unless marked as fluid rules. 3 from one of each pipe + a diamond. |
| Chemical Pipe | Only with **Mekanism** installed: moves Mekanism chemicals (gases, infuse types, pigments, slurries), with filters. |
| Flowline Wrench | **Sneak + right-click** a side to cycle: normal (Insert) → **Extract** → **disconnected** → normal. Right-click a side: open its screen. **Sneak + scroll** on a side: Extract sides cycle their distribution (with Ctrl: their redstone mode), Insert sides change their priority (Ctrl: ±10). On a curved pipe, **sneak + right-click** takes the edge apart at once (its pipes drop in survival); on a curved end on a block it cycles the mode. Wrenches from other mods (tagged `forge:tools/wrench`, `forge:wrenches`, `forge:tools/wrenches`, `c:wrenches` or `c:tools/wrench`: Create, Mekanism, Thermal...) do the same on pipes, and the Flowline Wrench is in all of those tags for other mods' blocks. |
| Anything else in hand | Right-click a side: opens its config screen, like a chest. Sneak + right-click to place a block against the pipe instead; a pipe in hand keeps placing pipes. |
| Pipe in the off hand, Build for me mode | Look at a block (up to `buildRange` = 32 blocks away): the route back to you is shown as **ghost pipes** with the number of pipes needed, around obstacles and with as few turns as possible. **Right-click** lays it. Each pipe is placed normally (protection mods apply, survival uses up the off-hand stack; ghosts past what you carry are drawn faint). The mode key (**B**, changeable in Controls) switches to Curved. |
| Pipe in the off hand, Curved mode | **Right-click** starts a line on a block face, a pipe face, a curved node, a curved pipe (split there into a joint) or in the air, adds points in the air and finishes on a face, a node or a curved pipe; **Shift + right-click** finishes in the air. A ghost tube previews the curve (red when it would be refused) and the line under the crosshair shows the cost and the server's answer. **Middle click** / Shift + middle click turns the grid (off, axial, outer/inner corners, outer/inner edges); hold **left Ctrl** to place freely; hold **left Alt** and scroll for the option wheel (Remove, Branch, Extend, Move, Force joint, Settings); **Esc** or the mode key drops a half-drawn line. All keys are in Controls. |
| Curved pipe, any mode | **Hold left-click** on a curved pipe to break it like a block: in survival it takes the pipe's mining time and its pipes drop; in creative it breaks at once and drops nothing. Where three or more pipes meet (or a line bends sharply) sits a square glass box with a metal frame, where the pipes run in. On a curved end: right-click opens its screen, sneak + right-click with a wrench cycles its mode, a Filter Card copies/pastes rules, like a block pipe side. Entities stand on curved pipes and sneaking stops at their edge; a block item is not placed across a curve and falling sand landing on one drops as an item, while a block pushed or set into a curve's way breaks it and drops its pipes. |
| Speed / Stack / Filter / Knozy Upgrade | Six upgrade slots per side (GUI, or right-click the side). **Speed** lowers the starting interval, **Stack** multiplies the amount per operation, **Filter** adds filter entries, **Knozy** counts as all three. Insert sides only take Filter upgrades (for more insert rules); the rest is returned when a side goes back to Insert. Everything drops when the pipe is broken. |
| Dye | Right-click a pipe with a dye: its corner rails and node cage take the colour (the glass and end collars keep the pipe's own colour). Pipes of **two different colours never connect**, undyed pipes connect to every colour. Using the pipe's own colour again washes it off. Curved pipes are dyed the same way. |
| Filter Card | Sneak + right-click a side: copy its filter rules. Right-click another side: paste them. Sneak + use in the air: clear. Mode, settings and upgrades are not copied. |

With JEI or EMI installed, the pipes, the wrench, the upgrades and the Filter Card have short how-to pages.

Pipes can be **waterlogged**. A pipe is a glass duct: four coloured rails on the corners, glass between them and a
small cage where the pipe bends or branches. Pipes draw **no effects or animations** (nothing travels, flows or
glows inside them), so they cost nothing beyond their block model.

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
a capability cache, so an operation does not look block entities up again.

Each Extract side runs on an adaptive interval, similar to AE2's tick rate modulation:

- it starts at **30 ticks** (each Speed upgrade removes 4, never below the minimum),
- every operation that moves something makes it **2 ticks faster**, down to **5 ticks**,
- every operation that moves nothing **doubles** it (exponential backoff), up to **100 ticks**,
- a side with **no target at all sleeps** and costs nothing until its network changes,
- a neighbouring block change (e.g. items arriving in the source chest) wakes it back to its starting interval,
- sides are staggered so pipes placed together do not all run on the same tick.

The side screen's header shows what the side is doing (Working, Asleep: no target, Waiting: redstone, Stuck:
targets full, Idle: source empty) with the current interval and amount per operation (hover for details), and tabs
for the pipe's six faces: click an attached face to open its screen without closing this one.

### Filter and rule library

Each side (not on energy pipes) has **9 rules** by default and **9 more per Filter upgrade** (Knozy
counts). A rule can combine:

- an **item** (or fluid) id,
- any number of **tags**, matched as **OR** (any of them) or **AND** (all of them),
- **data** (the stack's NBT), matched as **Contains** (default) or **Exact**,
- a **mod** (`@create`: everything from that mod),
- a **name pattern** (case-insensitive regular expression searched in the display name, renames included),
- a **durability range** in percent (e.g. 0–10 for nearly broken tools),
- an **amount** (Allow rules): a regulator just for matching stacks, used instead of the side's own. Extract
  sides leave at least this many of each matching kind in the source, Insert sides keep at most this many in the
  target (mB for fluids),
- **Allow** or **Block**.

A stack matching any Block rule never passes; if there are Allow rules it must match one of them; with only Block
rules everything else passes.

**Chemical pipes** filter too, but chemical rules only know a chemical's **id** (click a Mekanism tank in your
inventory to fill it in, or type it), its **mod** and a **name pattern**; tags, data, durability and amounts do not
apply. A chemical pipe holds gases, infuse types, pigments and slurries in one list of rules.

The side screen lists the rules as a scrollable list, each with a plain-words line ("Tag, any of them", "Everything
from the mod"...) and an **Allow/Block** chip: click the chip to switch it. Add a rule by **shift-clicking** an item
in your inventory, by clicking the list with an item in hand, or with "+ Add filter". **Shift + left-click** or
right-click removes a rule. The same rule cannot be added twice. On fluid pipes, rules show the fluid itself.

Click a rule to open the **rule editor**: click an item in your inventory (or an item in the tag preview) to use it
as the sample, then tick the tags it belongs to (or search every known tag; the preview shows the hovered tag's
items, otherwise what the ticked tags select) and tick the data it must have (enchantments, damage, name...). Data
can also be edited as SNBT text. A summary line says what the rule does and whether the sample matches it, and if
not, which condition fails. Older rules load automatically.

### Integrations (all optional)

- **JEI / EMI**: drag an item or fluid from the list onto a filter slot. In the rule editor, drop it on the sample
  (use it as the rule's item), on the tag list (list its tags to tick) or on the mod box (fill in its mod).
- **Jade**, **The One Probe** and **WTHIT**: looking at a pipe shows the side's mode, distribution, pacing, priority
  and regulator.
- **Mekanism** (10.4): the Chemical Pipe (gases, infuse types, pigments, slurries) and its filters.
- **Patchouli**: an in-game guide, **Flowline Guide** (English and Turkish). Craft it with a book and an item pipe.
- **KubeJS** and data packs: see below.

### Data packs and KubeJS

One block tag, empty by default, can be filled by a data pack or by KubeJS's tag event:

- `flowline:no_connect`: pipes never connect to these blocks, whatever capabilities they have.

```js
ServerEvents.tags('block', event => {
  event.add('flowline:no_connect', 'minecraft:chest')
})
```

Recipes are ordinary data pack recipes (KubeJS's recipe events work on them), and every number is in the config below.
There is no KubeJS-specific binding.

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
above (`idleBackoffFactor` = 2), the max network size (`[network]`) and the Build for me range (`[building]`).

`config/flowline-client.toml` only holds the internal `buildForMeHintSeen` flag, which saves whether the one-time
Build for me introduction was shown.

The network protocol is version 14: clients and servers need the same Flowline version.

Pipes are see-through by default. Players who prefer solid pipes can enable the built-in **Flowline: Solid Pipes**
resource pack (Options > Resource Packs): it swaps the glass for frosted, opaque panels.

## Building

```
./gradlew build        # deploy build/libs/flowline-<version>.jar (not the -slim.jar)
./gradlew runClient    # dev client
./gradlew runGameTestServer   # headless in-world tests (src/main/java/.../test)
```

CI (`.github/workflows/build.yml`) builds the jar, uploads it as the `flowline-jar` artifact and runs the in-world tests
both without Mekanism and with `-PwithMekanism`.

Curved pipes hook three vanilla methods with Mixin (block changes in a chunk, entity collision, the client's middle
click); the refmap is built by MixinGradle.

Requires access to `maven.minecraftforge.net`, `repo.spongepowered.org` (MixinGradle), Mojang's asset/library hosts and, for the optional integration APIs,
`maven.blamejared.com` (JEI), `maven.terraformersmc.com` (EMI), `www.cursemaven.com` (Jade), `modmaven.dev`
(Mekanism), `maven.k-4u.nl` (The One Probe) and `maven.bai.lol` (WTHIT).

## Resources

Textures/models/recipes/lang are generated by `python3 tools/gen_resources.py` (procedural pixel art, pure
standard library). The Patchouli guide is generated by `python3 tools/gen_patchouli.py`.

## Status / TODO

- Fluid transfer notifications are tested with test-only fluid handlers, alongside network endpoint roles and
  packet limits. Rendering needs a manual client check.
- Curved pipes: the geometry, placement rules, network, editing, painting, world changes and entity collision are
  covered by in-world tests. Item, fluid and energy transfer through curves is tested both ways (a block pipe
  extracting through a curve, and a curved end extracting into block pipes), and a universal curve carries all three
  at once; chemical curves use the same network code but have no in-world test yet. Their look, preview, keys and
  option wheel still need a manual client check. Entity collision on a tilted pipe can reach up to two pixels past its glass.
- Pipe tiers/materials are not planned.
