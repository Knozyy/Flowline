# Flowline

Pipez-style transport mod for **Forge 1.20.1** (Java 17): item, fluid and energy pipes with per-side
modes, filters, distribution modes and speed upgrades.

Curved item, fluid and FE pipes use the **actual Curvy Pipes 1.15.8 native engine**: its renderer,
placement preview, picking, collision, alignment grid, radial editor, endpoint menus, transport and world data.
Curvy Pipes is optional. When 1.15.8 is installed on client and server, the **same Flowline item/fluid/energy
pipe items** place curved lines too: a pipe in the **main hand always places a block pipe**; a pipe in the **off
hand** has two modes, **Build for me** and **Curvy**, switched with the mode key (**B** by default) and shown under
the crosshair, and right-click does the chosen mode's work. Without Curvy, the off hand only has Build for me.
There are no separate curved items or conversion recipes. The Build for me introduction appears once for eight
seconds and stays dismissed across restarts. The independent Flowline curve implementation has been removed.

A Curvy line whose end sits on a Flowline pipe connects to that pipe's network: Curvy taking from it pulls from the
network's Extract sides (under their filters and "keep" amounts), Curvy giving to it delivers to the network's
Insert sides. A line between two Flowline pipes is therefore a bridge between two networks, configured in Flowline's
screens. One end still has to be set to **Extract** in Curvy's endpoint menu, since Curvy only moves from active
ends ([requested upstream](https://github.com/cyb0124/CurvyPipes-Issues/issues/27)).
See [curved-pipe controls and migration](docs/CURVED_PIPES_TR.md) and [Curvy's ID regex research](docs/CURVY_REGEX_TR.md).

> This branch is the 1.20.1 Forge backport of the NeoForge 1.21.1 version. Differences: rule "data" matches the
> stack's NBT tag (1.20.1 has no data components), and the Chemical Pipe moves Mekanism 10.4's gases, infuse types,
> pigments and slurries.

## Gameplay

| Item | Use |
| --- | --- |
| Item / Fluid / Energy Pipe | Connects to pipes of the same type and to any block exposing the matching capability. |
| Universal Pipe | Moves items, fluids and energy at once. Each side can switch its **channels** (items / fluids / energy) on and off; rules are item rules unless marked as fluid rules. 3 from one of each pipe + a diamond. |
| Chemical Pipe | Only with **Mekanism** installed: moves Mekanism chemicals (gases, infuse types, pigments, slurries). |
| Flowline Wrench | **Sneak + right-click** a side to cycle: normal (Insert) → **Extract** → **disconnected** → normal. Right-click a side: open its screen. **Sneak + scroll** on a side: Extract sides cycle their distribution (with Ctrl: their redstone mode), Insert sides change their priority (Ctrl: ±10). Wrenches from other mods (tagged `forge:tools/wrench`: Create, Mekanism, Thermal...) do the same on pipes. |
| Empty hand | Right-click a side: opens its config screen. Sneak + right-click with both hands empty: take a facade off. |
| Pipe in the off hand, Build for me mode | Look at a block (up to `buildRange` = 32 blocks away): the route back to you is shown as **ghost pipes** with the number of pipes needed, around obstacles and with as few turns as possible. **Right-click** lays it. Each pipe is placed normally (protection mods apply, survival uses up the off-hand stack; ghosts past what you carry are drawn faint). The mode key (**B**, changeable in Controls) switches to Curvy. |
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
block-pipe renderer covers fluid pipes; normal universal, energy and chemical pipes do not use it. Curved lines use Curvy's own rendering. Facades
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

The side screen's header shows what the side is doing (Working, Asleep: no target, Waiting: redstone, Stuck:
targets full, Idle: source empty) with the current interval and amount per operation (hover for details), and tabs
for the pipe's six faces: click an attached face to open its screen without closing this one.

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
`maxTravellingItems`, `ticksPerPipe`, `renderNetworkView`, `renderFluidInPipes`, `maxFluidPipes`, `fluidRenderRange`. The internal `buildForMeHintSeen` flag saves whether the one-time introduction was shown.

The network protocol is version 6: clients and servers need the same Flowline version for these visual packets.

Pipes are see-through by default. Players who prefer solid pipes can enable the built-in **Flowline: Solid Pipes**
resource pack (Options > Resource Packs).

## Building

```
./gradlew build        # deploy build/libs/flowline-<version>.jar (not the -slim.jar)
./gradlew runClient    # dev client
./gradlew runGameTestServer   # headless in-world tests (src/main/java/.../test)
```

CI (`.github/workflows/build.yml`) builds the jar, uploads it as the `flowline-jar` artifact and runs the in-world tests
both without Curvy and with `-PwithCurvy`.

Curvy Pipes 1.15.8 can be installed separately to enable curved pipes. The YAML parser is embedded in the
distribution jar. Dev runs omit Curvy by default; add `-PwithCurvy` to test its native integration through the
[official CurseMaven coordinate](https://www.curseforge.com/minecraft/mc-mods/curvy-pipes/files/8822563).

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
