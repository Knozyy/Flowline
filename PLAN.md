# Flowline 1.20.1: planned work

Status when this was written (0.1.1+1.20.1 in progress). Done and pushed: build for me, other mods' wrenches,
overflow targets, per-rule amounts, redstone output. The CI of the last two commits (per-rule amounts `fac9728`,
redstone output `37a8072`) had not been seen finished: check them first. The redstone output has no in-world test.

Not done yet: **A. network view** and **B. fluid inside pipes**.

## A. Network view

Holding a wrench and looking at a pipe shows that network's sources, targets and pipes as coloured outlines, also
through walls.

- Colours: source (the block an Extract side pulls from) green, target orange, overflow target purple, pipes a
  pale outline, higher priority brighter.
- Server: `NetworkView` finds the looked-at pipe's graph (`PipeNetwork.graphAt`) and collects the pipe positions
  (at most `maxNetworkSize`) and each endpoint's role.
- Packets: the client sends `NetworkQueryPayload(pos)` when the looked-at pipe changes (at most once a second); the
  server checks the distance and answers with `NetworkViewPayload`. Around 4 KB at most.
- Rendering: `RenderLevelStageEvent`, box outlines with a custom `RenderType` that has the depth test off.
- Trigger: see the open questions.
- Config: server `allowNetworkView` (servers may not want to show what is behind walls) and `networkViewRange`;
  client on/off.
- Tests: an in-world test that a two-target layout yields the right sources and targets. The drawing cannot be
  tested in CI, so it needs a manual check.
- Risk: Shift + click with the wrench also cycles sides, so the outlines show up then too. Harmless, even useful.

## B. Fluid inside pipes

While fluid moves, the see-through pipe shows that fluid's colour inside, flowing along the pipe.

- Server: give `FluidTransfer.run` an `onMove` callback like `ItemTransfer` has. When fluid goes to a target, send
  `FluidFlowPayload(fluid, path)` to nearby players. Same range and limits as the item animation (48 blocks, nothing
  sent when nobody is near).
- Client state: per pipe, "fluid flowing through": fluid, direction, end time; it fades about a second after the
  last flow.
- Rendering: a `BlockEntityRenderer` for pipes. It only draws pipes that have flow, so distance culling comes for
  free and it does not depend on one global event like the item animation. A fill just inside the inner wall, with
  the fluid's still texture and tint (`IClientFluidTypeExtensions`), the texture scrolled along the flow.
- Config: server `sendFluidAnimations`, client `renderFluidInPipes` (both default on).
- Tests: vanilla has no fluid-holding block, so no in-world test (the README says so too). Compile and check by hand.
- Risks: FPS with many pipes, so cap the pipes drawn at once (about 128) and the distance (about 32 blocks). With the
  Solid Pipes pack the fluid is hidden but still drawn, a small waste we accept. Energy and chemical pipes are out
  of scope.

## Order and size

| Order | Work | Size |
|---|---|---|
| 1 | A. Network view | medium: about 6 files and 2 packets |
| 2 | B. Fluid inside pipes | medium: about 5 files, 1 packet and a renderer |

One commit and one CI run each, so a failure in one does not hold up the other.

## Open questions

1. **A, trigger:** Shift with the wrench in hand, or a separate key (rebindable, e.g. N)? Recommended: Shift.
2. **B, scope:** fluid pipes only, or also the fluid in universal pipes in the first version? Universal pipes can
   move items and fluid at once, so the drawing is a bit more involved. Recommended: fluid pipes first.

## Other ideas that were not chosen (for later)

- Flow gauge: what a side actually moved over the last seconds (items/s, mB/s, FE/t) in the pipe screen and Jade.
- More languages (German, Russian, Chinese, Spanish, Portuguese; ideally checked by native speakers).
- In-game guide book.
- Port everything to `1.21.1` (NeoForge); it is still on the old state.
