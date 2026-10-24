# Changelog

## 0.3.25

- Curved pipes take up room like blocks: a block cannot be placed across a curve, sand or gravel landing on one
  drops as an item instead of breaking it, sneaking stops at its edge and a crouching player under one stays crouched.
- Pipe recipes make 8 pipes instead of 4 (universal pipes still 3).
- Sneak + right-click a curved pipe with a wrench to take it apart at once; its pipes drop in survival. On a curved
  end on a block the same click still cycles Insert and Extract.
- The Flowline Wrench is a 3D hook wrench, held like a tool. It is in the common wrench tags (`forge:tools/wrench`,
  `forge:wrenches`, `forge:tools/wrenches`, `c:wrenches`, `c:tools/wrench`), and wrenches from any of them work on
  pipes.
- Where curved pipes meet sits a square glass box with a metal frame on its edges instead of the round metal joint
  and its flanges.

## 0.3.20

- **Curved pipes are part of Flowline itself.** The off hand's mode key switches between Build for me and Curved;
  the same pipe items lay curved lines of every pipe type. Lines bend, join, avoid collisions and cost pipes, and
  look like the block pipes: a square glass duct with
  coloured corner rails, collars and extract rings at the ends, and round metal joints with a flange on each pipe.
- A curved line is part of the network it touches: one Extract side drives the whole network, curves included. A
  curved end on a chest, tank or machine is a pipe side with the same screen, wrench, Filter Card and upgrades.
- Laying and editing: right-click to start, add points and finish, also on another curved pipe (it splits into a joint there); a ghost tube previews the curve and the HUD shows
  its cost and the server's answer. Six grid modes on the middle button, left Ctrl to place freely, an option wheel
  on left Alt (Remove, Branch, Extend, Move, Force joint, Settings), hold left-click to break, dyes to colour.
- Curved pipes cost pipes from the off hand; broken in survival (held like a block) they drop their pipes, in creative they give nothing back; protection mods are asked. Blocks put in
  a curve's way break it and drop its pipes; entities stand on curved pipes.
- Flowline no longer needs any other mod for curved pipes and no longer bundles snakeyaml. Curved lines from older
  worlds are not carried over.
- The guide book has three new pages on curved pipes.
- Network protocol 14: server and clients need the same Flowline version.
- Worlds from before 0.3 open without Forge's missing-entries warning: Configuration Cards become Filter Cards
  (their filter rules stay) and facade items are dropped.

## 0.3.5

- New look: every pipe is a glass duct with coloured rails on its corners and a small cage only where it bends or
  branches. Each kind has its own pastel colour; the Solid Pipes resource pack swaps the glass for frosted panels.
- Dyes now colour the whole pipe (rails and cages); the glass and end collars keep the pipe's own colour.
- The universal pipe lays curved lines too: in the off hand the mode key cycles Build for me, Curved: Items,
  Curved: Fluids and Curved: Energy.
- New wrench, held like a tool. Upgrades are drawn as cards in their own colours and the Filter Card as a light card
  with gold contacts, so the two no longer look alike. The pipe screens use the pipe colours.
- Right-clicking a pipe side opens its screen whatever you hold, like a chest; sneak + right-click places a block
  against the pipe instead, and a pipe in hand keeps laying pipes.
- The tag list's Any/All tooltip now names the same option as its button.
- JEI and EMI show short how-to pages on the pipes, the wrench, the upgrades and the Filter Card.
- Faster: pipes that are not attached to any block no longer tick, and only the glass is drawn as translucent.
  Pipes draw no effects or animations any more, so the server sends no visual packets.
- Removed: Facades, the Configuration Card (the Filter Card stays) and the network view (wrench + Shift).
  **Existing worlds:** facades on pipes and these items are lost; the pipes underneath keep working.
- Network protocol 10: server and clients need the same Flowline version.
- Chemical Pipes have filters: Allow/Block rules by chemical id, mod or name pattern, on Extract and Insert sides.
- The Chemical Pipe's loot table is now a real data file, loaded only when Mekanism is present.
- The One Probe and WTHIT show the looked-at side's settings, like Jade.
- A `flowline:no_connect` block tag for data packs and KubeJS.
- An in-game Patchouli guide (English and Turkish), crafted with a book and an item pipe.
- Added an MIT `LICENSE` file.


## 0.1.9

- Redesigned pipe screens: side tabs, a status header (Working / Asleep / Waiting / Stuck / Idle) and a new rule editor with tag and data previews.
- Off-hand pipe modes: the mode key (B by default) switches between Build for Me and Curved; right-click does the chosen mode's work.
- Build for Me now previews the route as ghost pipes with the number of pipes needed.
- Curved lines: the same Flowline pipes can be laid as curved lines, and a curved line ending on a Flowline pipe connects to that pipe's network.
- New curved pipe textures matching the block pipes.
- Fixed a crash when adding a rule, and Configuration Card pastes now show up in an open screen.
