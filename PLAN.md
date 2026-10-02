# Flowline 1.20.1: planned work

Base version: 0.1.1+1.20.1. Earlier completed work includes build for me, other mods' wrenches,
overflow targets, per-rule amounts and redstone output. The redstone output still has no in-world test.

Implemented in the current source: **A. network view**, **B. fluid inside pipes**, and the additional curved-pipe
placement, Build for Me hint, and matte pipe appearance described below.
Choices: Shift with a wrench; normal fluid pipes only for B. Client rendering and FPS checks remain pending.

## C. Curved pipes and discoverability

- Holding a Flowline pipe in the main hand activates free placement. Right click places points, a block or existing
  node finishes the line, the wheel changes reach, middle click cycles snapping, and Enter finishes an open line.
  Shift keeps ordinary block placement available. Ctrl + right click opens editing actions.
- A dimension-owned node/edge graph provides independent lines, branches, movement, joints, collision, persistence,
  and direct machine endpoints using the existing filters, upgrades and item/fluid/energy/universal transfer code.
  Server validation covers reach, aim, ownership, loaded chunks, world boundaries, collisions, Forge placement
  events, graph limits and survival materials. Creative placement records no refundable material cost.
- Curves use Flowline's own Hermite geometry. There is no dependency on Curvy Pipes and no native code copied from it.
  Normal block networks and curved networks do not merge; AE2 ME cable placement is outside this implementation.
- Looking at a block at least five blocks away while holding a pipe shows Build for Me's actual assigned key and
  instructions, within the configured building range. The hint explains moving the pipe to the off hand when needed.
- Both block textures and curved meshes have matte bodies and thin transparent windows, with item yellow, fluid
  blue, energy orange and universal purple. Curved geometry and collar meshes are cached between graph updates.
- Usage and remaining checks: [curved-pipe guide](docs/CURVED_PIPES_TR.md). The installed Curvy/AE2 investigation
  is in [the research notes](docs/CURVY_PIPES_AE2_RESEARCH_TR.md); [regex findings](docs/CURVY_REGEX_TR.md) explain
  Curvy's registry-ID matching and how it differs from Flowline's existing display-name regex. ID regex was not added.

## A. Network view

Holding a wrench and looking at a pipe shows that network's sources, targets and pipes as coloured outlines, also
through walls.

- Colours: source (the block an Extract side pulls from) green, target orange, overflow target purple, pipes a
  pale outline, higher priority brighter.
- Server: `NetworkView` finds the looked-at pipe's graph (`PipeNetwork.graphAt`) and collects the pipe positions
  (at most `maxNetworkSize`) and each endpoint's role.
- Packets: the client sends `NetworkQueryPayload(pos)` while looking at a pipe (at most once a second, refreshing
  changes in the same network too). The server checks the wrench, Shift, distance, loaded chunk and raycast, and
  applies its own one-query-per-second limit. `NetworkViewPayload` is capped at 2048 entries (about 27 KB);
  the view reports when this cap is reached. Responses include the dimension and queried position.
- Rendering: `RenderLevelStageEvent`, box outlines with a custom `RenderType` that has the depth test off.
- Trigger: hold Shift with a wrench.
- Config: server `allowNetworkView` (servers may not want to show what is behind walls) and `networkViewRange`;
  client `renderNetworkView`.
- Tests: an in-world test that a two-target layout yields the right sources and targets. The drawing cannot be
  tested in CI, so it needs a manual check.
- Risk: Shift + click with the wrench also cycles sides, so the outlines show up then too. Harmless, even useful.

## B. Fluid inside pipes

While fluid moves, the see-through pipe shows that fluid's colour inside, flowing along the pipe.

- Server: give `FluidTransfer.run` an `onMove` callback like `ItemTransfer` has. When fluid goes to a target, send
  `FluidFlowPayload(fluid, path)` to nearby players. Same range and limits as the item animation (48 blocks, nothing
  sent when nobody is near). Visual packets are capped at 16 per extracting-side operation and 260 path positions.
- Client state: per pipe, "fluid flowing through": fluid, direction, end time; it fades about a second after the
  last flow.
- Rendering: a `BlockEntityRenderer` for pipes. It only draws pipes that have flow, so distance culling comes for
  free and it does not depend on one global event like the item animation. A fill just inside the inner wall, with
  the fluid's still texture and tint (`IClientFluidTypeExtensions`), the texture scrolled along the flow.
- Config: server `sendFluidAnimations`, client `renderFluidInPipes` (both default on).
- Tests: test-only chest block entities expose fluid handlers, verifying that completed transfers notify the
  callback and full targets neither drain the source nor produce animations. Rendering still needs a manual check.
- Risks: FPS with many pipes, so `maxFluidPipes` defaults to 128 and `fluidRenderRange` to 32 blocks. With the
  Solid Pipes pack the fluid is hidden but still drawn, a small waste we accept. Energy and chemical pipes are out
  of scope.

## Order and size

| Order | Work | Size |
|---|---|---|
| 1 | A. Network view | medium: about 6 files and 2 packets |
| 2 | B. Fluid inside pipes | medium: about 5 files, 1 packet and a renderer |

Validate with `./gradlew compileJava` and `./gradlew runGameTestServer`, then perform the client checks below.

## Resolved choices

1. **A, trigger:** Shift with the wrench in hand, including other mods' tagged wrenches.
2. **B, scope:** fluid pipes only in this version.

## Manual client checks remaining

Local validation on 2026-10-02: Java compilation passed; all 57 required GameTests passed, including network
roles/priority, server permission and raycast checks, packet bounds, fluid transfer notifications, and eleven curved-pipe
tests covering graph separation, persistence, physical collision, materials, endpoint reattachment, creative costs,
actual item/fluid/energy transfer, and starting/finishing a line by clicking machine faces.
The client rendering and FPS checks below are still pending.

- Sources, normal targets and overflow targets keep their colours behind walls; different priorities change
  brightness. Release Shift, switch the held tool, look away, change dimension, or disable the server/client
  option: the overlay must disappear. Changes to the same network should appear within the next refresh.
- Move water and lava through straight, bent and vertical fluid pipes. Check tint, flow direction, fading,
  facades, the Solid Pipes pack and both animation switches. Universal pipes should not draw fluid.
- Compare FPS on a large active network with rendering enabled/disabled and with the client limit set to zero.
- Check curved placement, selection and editing with remapped keys and small GUI scales; check the distant-block
  Build for Me hint, matte windows, travelling items and fluids, collars, dimension changes and dense curved networks.

## Other ideas that were not chosen (for later)

- Flow gauge: what a side actually moved over the last seconds (items/s, mB/s, FE/t) in the pipe screen and Jade.
- More languages (German, Russian, Chinese, Spanish, Portuguese; ideally checked by native speakers).
- In-game guide book.
- Port everything to `1.21.1` (NeoForge); it is still on the old state.
