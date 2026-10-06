# Changelog

## 0.3.2

- New look: every pipe is a glass duct with coloured rails on its corners and a small cage only where it bends or
  branches. Each kind has its own pastel colour; the Solid Pipes resource pack swaps the glass for frosted panels.
- Dyes now colour the whole pipe (rails and cages); the glass and end collars keep the pipe's own colour.
- The universal pipe works with Curvy Pipes: in the off hand the mode key cycles Build for me, Curvy: Items,
  Curvy: Fluids and Curvy: Energy.
- New wrench, held like a tool. New upgrade and Filter Card icons; the pipe screens use the pipe colours.
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
- Off-hand pipe modes: the mode key (B by default) switches between Build for Me and Curvy; right-click does the chosen mode's work.
- Build for Me now previews the route as ghost pipes with the number of pipes needed.
- Optional Curvy Pipes 1.15.8 integration: the same Flowline pipes can be laid as curved lines, and a curved line ending on a Flowline pipe connects to that pipe's network.
- New curved pipe textures matching the block pipes.
- Fixed a crash when adding a rule, and Configuration Card pastes now show up in an open screen.
