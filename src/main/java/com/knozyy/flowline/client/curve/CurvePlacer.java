package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.curve.GridMode;
import com.knozyy.flowline.curve.Vec3d;
import com.knozyy.flowline.curved.PlaceRequest;
import com.knozyy.flowline.network.ModNetwork;
import com.knozyy.flowline.network.curve.CurvePlacePayload;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The player's curved pipe drawing: grid mode, snapping, and the chain being laid. {@link #decide} is the pure key
 * table (spec "El düzeni"); the chain is sent to the server when it is finished, and the server has the last word.
 */
public final class CurvePlacer {
    public enum Click { USE, ATTACK, PICK }

    /** What the main hand holds, as far as curved pipes care. */
    public enum HeldKind { EMPTY, PIPE, DYE, WRENCH, CARD, OTHER }

    /** {@code IGNORE} takes the click from vanilla and does nothing; {@code PASS} leaves it to vanilla. */
    public enum Action {
        START, ADD, FINISH, BREAK, WRENCH_BREAK, PAINT, OPEN_END, CYCLE_END, CARD_END, GRID_NEXT, GRID_PREVIOUS, IGNORE, PASS
    }

    private static GridMode grid = GridMode.AXIAL;
    private static boolean noSnap;
    @Nullable
    private static PlaceRequest.End start;
    private static final List<Vec3d> internals = new ArrayList<>();
    private static PipeType drawingType = PipeType.ITEM;
    /** The node the option wheel's Move picked: the next right-click puts it there; -1 when none. */
    private static long moving = -1;
    private static int nextRequest = 1;

    private CurvePlacer() {}

    /**
     * The key table. {@code curvedMode}: a pipe in the off hand in curved mode, and none in the main hand (a main-hand
     * pipe always places one block). Breaking, painting and ends work in every mode.
     */
    public static Action decide(boolean curvedMode, boolean drawing, CurveTarget.Kind target, boolean shift, Click click,
                                HeldKind held) {
        switch (click) {
            case ATTACK:
                return target == CurveTarget.Kind.EDGE || target == CurveTarget.Kind.NODE ? Action.BREAK : Action.PASS;
            case PICK:
                if (!curvedMode) return Action.PASS;
                return shift ? Action.GRID_PREVIOUS : Action.GRID_NEXT;
            default:
                break;
        }
        if (curvedMode && drawing) {
            return switch (target) {
                case NODE, PIPE_FACE, BLOCK_FACE -> Action.FINISH;
                case AIR -> shift ? Action.FINISH : Action.ADD;
                // finishing on an edge splits it there and branches off the new node
                case EDGE -> Action.FINISH;
            };
        }
        if (held == HeldKind.DYE && target == CurveTarget.Kind.EDGE) return Action.PAINT;
        // sneak + right-click with the wrench takes an edge apart at once; on a node it cycles the end's mode, and
        // CurveInput turns that into a break where the node is no end on a block
        if (held == HeldKind.WRENCH && shift && target == CurveTarget.Kind.EDGE) return Action.WRENCH_BREAK;
        if (target == CurveTarget.Kind.NODE) {
            if (held == HeldKind.WRENCH && shift) return Action.CYCLE_END;
            if (held == HeldKind.CARD) return Action.CARD_END;
            if (held == HeldKind.EMPTY && !curvedMode) return Action.OPEN_END;
        }
        if (curvedMode && held == HeldKind.EMPTY) return Action.START;
        return Action.PASS;
    }

    /** No break yet. */
    public static final long NEVER = Long.MIN_VALUE;
    /** Holding attack fires the click every tick; one break per this many ticks. */
    public static final int BREAK_COOLDOWN = 5;

    /**
     * Whether a held attack may break again at game time {@code now}, the last break having been at {@code last}
     * ({@link #NEVER} before the first): never compares by subtracting from {@code NEVER}, which overflows.
     */
    public static boolean breakReady(long now, long last) {
        return last == NEVER || now < last || now - last >= BREAK_COOLDOWN;
    }

    public static HeldKind held(ItemStack stack) {
        if (stack.isEmpty()) return HeldKind.EMPTY;
        if (PipeBlock.isPipe(stack)) return HeldKind.PIPE;
        if (stack.getItem() instanceof net.minecraft.world.item.DyeItem) return HeldKind.DYE;
        if (com.knozyy.flowline.item.WrenchItem.isWrench(stack)) return HeldKind.WRENCH;
        if (stack.getItem() instanceof com.knozyy.flowline.item.FilterCardItem) return HeldKind.CARD;
        return HeldKind.OTHER;
    }

    public static GridMode grid() {
        return grid;
    }

    public static void cycleGrid(boolean forward) {
        grid = forward ? grid.next() : grid.previous();
    }

    public static boolean snapping() {
        return !noSnap;
    }

    static void setNoSnap(boolean held) {
        noSnap = held;
    }

    public static boolean drawing() {
        return start != null;
    }

    @Nullable
    public static PlaceRequest.End start() {
        return start;
    }

    public static List<Vec3d> internals() {
        return List.copyOf(internals);
    }

    public static PipeType drawingType() {
        return drawingType;
    }

    /** The type being drawn, else {@code fallback} (the off-hand pipe), else item pipes; for colours. */
    public static PipeType drawingTypeOr(@Nullable PipeType fallback) {
        return drawing() || fallback == null ? drawingType : fallback;
    }

    public static long moving() {
        return moving;
    }

    public static void startMoving(long node) {
        cancel();
        moving = node;
    }

    public static void stopMoving() {
        moving = -1;
    }

    public static void cancel() {
        start = null;
        internals.clear();
    }

    /** Starts a chain at {@code target} with the off-hand pipe's type. */
    public static void begin(CurveTarget target, PipeType type) {
        cancel();
        start = end(target);
        drawingType = type;
    }

    public static void add(CurveTarget target) {
        internals.add(target.pos);
    }

    /** Sends the chain ending at {@code target}; the server answers with a result the HUD shows. */
    public static void finish(CurveTarget target) {
        if (start == null) return;
        List<Vec3> points = new ArrayList<>();
        for (Vec3d p : internals) points.add(new Vec3(p.x(), p.y(), p.z()));
        ModNetwork.sendToServer(new CurvePlacePayload(nextRequest(),
                new PlaceRequest(drawingType, start, end(target), points)));
        cancel();
    }

    public static int nextRequest() {
        return nextRequest++;
    }

    /** A chain end for {@code target}: a node to continue, a face to fix to, an edge to split, or a free point. */
    public static PlaceRequest.End end(CurveTarget target) {
        Vec3 pos = new Vec3(target.pos.x(), target.pos.y(), target.pos.z());
        OptionalLong node = target.kind == CurveTarget.Kind.NODE ? OptionalLong.of(target.nodeId) : OptionalLong.empty();
        Optional<PlaceRequest.Face> face = target.blockPos != null && target.face != null
                && (target.kind == CurveTarget.Kind.BLOCK_FACE || target.kind == CurveTarget.Kind.PIPE_FACE)
                ? Optional.of(new PlaceRequest.Face(target.blockPos, target.face)) : Optional.empty();
        Optional<PlaceRequest.Split> split = target.kind == CurveTarget.Kind.EDGE
                ? Optional.of(new PlaceRequest.Split(target.edgeId, target.segment, target.along)) : Optional.empty();
        return new PlaceRequest.End(pos, node, face, split);
    }
}
