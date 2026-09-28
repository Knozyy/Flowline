package com.knozyy.flowline.pipe;

import com.knozyy.flowline.item.PipeInteractable;
import com.knozyy.flowline.menu.PipeMenu;
import com.knozyy.flowline.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class PipeBlock extends Block implements EntityBlock {
    public static final MapCodec<PipeBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            propertiesCodec(),
            StringRepresentable.fromEnum(PipeType::values).fieldOf("pipe_type").forGetter(PipeBlock::type)
    ).apply(i, PipeBlock::new));

    private static final Map<Direction, EnumProperty<Conn>> PROPS = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) PROPS.put(d, EnumProperty.create(d.getName(), Conn.class));
    }

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape[] ARMS = new VoxelShape[6];

    static {
        ARMS[Direction.DOWN.ordinal()] = Block.box(5, 0, 5, 11, 5, 11);
        ARMS[Direction.UP.ordinal()] = Block.box(5, 11, 5, 11, 16, 11);
        ARMS[Direction.NORTH.ordinal()] = Block.box(5, 5, 0, 11, 11, 5);
        ARMS[Direction.SOUTH.ordinal()] = Block.box(5, 5, 11, 11, 11, 16);
        ARMS[Direction.WEST.ordinal()] = Block.box(0, 5, 5, 5, 11, 11);
        ARMS[Direction.EAST.ordinal()] = Block.box(11, 5, 5, 16, 11, 11);
    }

    private static final double MIN = 5 / 16.0;
    private static final double MAX = 11 / 16.0;

    private final PipeType type;

    public PipeBlock(Properties properties, PipeType type) {
        super(properties);
        this.type = type;
        BlockState def = stateDefinition.any();
        for (Direction d : Direction.values()) def = def.setValue(prop(d), Conn.NONE);
        registerDefaultState(def);
    }

    public PipeType type() {
        return type;
    }

    public static EnumProperty<Conn> prop(Direction dir) {
        return PROPS.get(dir);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction d : Direction.values()) builder.add(prop(d));
    }

    // ---- connections ------------------------------------------------------------------------------------------

    private BlockState withConnections(Level level, BlockPos pos, BlockState state) {
        PipeBlockEntity self = level.getBlockEntity(pos) instanceof PipeBlockEntity be ? be : null;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState ns = level.getBlockState(neighbor);
            Conn conn;
            if (self != null && self.isDisconnected(dir)) {
                conn = Conn.NONE;
            } else if (ns.getBlock() instanceof PipeBlock other && other.type == type) {
                boolean otherCut = level.getBlockEntity(neighbor) instanceof PipeBlockEntity nb
                        && nb.isDisconnected(dir.getOpposite());
                conn = otherCut ? Conn.NONE : Conn.PIPE;
            } else if (type.hasEndpoint(level, neighbor, dir.getOpposite())) {
                conn = Conn.ENDPOINT;
            } else {
                conn = Conn.NONE;
            }
            state = state.setValue(prop(dir), conn);
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return withConnections(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            // A freshly placed pipe reconnects neighbours that were cut towards this position earlier.
            for (Direction dir : Direction.values()) {
                BlockPos n = pos.relative(dir);
                if (level.getBlockEntity(n) instanceof PipeBlockEntity nb && nb.type() == type
                        && nb.isDisconnected(dir.getOpposite())) {
                    nb.setDisconnected(dir.getOpposite(), false);
                    updateConnections(level, n);
                }
            }
        }
        // Deferred: updating the state from inside onPlace nests a setBlock into vanilla's, which then hands the
        // block entity the stale pre-update state.
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        refresh(state, level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PipeBlockEntity be) {
            Containers.dropContents(level, pos, be.upgrades());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        refresh(state, level, pos);
    }

    private void refresh(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide) return;
        BlockState updated = withConnections(level, pos, state);
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
    }

    /** Recomputes the connection properties of the pipe at {@code pos}, if there is one. */
    public static void updateConnections(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PipeBlock pipe) pipe.refresh(state, level, pos);
    }

    /**
     * Wrench action: cuts or restores the connection on one side. Pipe-to-pipe connections are cut on both
     * pipes so neither side reconnects on its own.
     *
     * @return the new state: true if the side is now connected, false if it is cut. Null if there is nothing
     * on that side to connect to.
     */
    @Nullable
    public static Boolean toggleConnection(Level level, BlockPos pos, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity be)) return null;
        BlockPos n = pos.relative(side);
        PipeBlockEntity other = level.getBlockEntity(n) instanceof PipeBlockEntity nb && nb.type() == be.type()
                ? nb : null;

        boolean cut = be.isDisconnected(side) || (other != null && other.isDisconnected(side.getOpposite()));
        if (!cut && other == null && !be.type().hasEndpoint(level, n, side.getOpposite())) return null;

        boolean cutNow = !cut;
        be.setDisconnected(side, cutNow);
        if (other != null) {
            other.setDisconnected(side.getOpposite(), cutNow);
            updateConnections(level, n);
        }
        updateConnections(level, pos);
        return !cutNow;
    }

    // ---- shape ------------------------------------------------------------------------------------------------

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape shape = CORE;
        for (Direction d : Direction.values()) {
            if (state.getValue(prop(d)) != Conn.NONE) shape = Shapes.or(shape, ARMS[d.ordinal()]);
        }
        return shape;
    }

    // ---- interaction ------------------------------------------------------------------------------------------

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof PipeInteractable tool) {
            useTool(tool, stack, state, level, pos, player, hand, hit);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Any other held item keeps its normal behaviour (e.g. placing a block against the pipe).
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Shared entry point for tools. Called from {@link #useItemOn} and, because vanilla skips the block when the
     * player sneaks with an item in hand, from the tools' own {@code useOn} as well.
     */
    public static void useTool(PipeInteractable tool, ItemStack stack, BlockState state, Level level, BlockPos pos,
                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof PipeBlockEntity be)) return;
        Direction side = sideFromHit(hit, pos);
        tool.useOnPipe(be, side, state.getValue(prop(side)), player, hand, stack);
    }

    /** Empty-handed click on an endpoint side opens that side's configuration screen. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        Direction side = sideFromHit(hit, pos);
        if (state.getValue(prop(side)) != Conn.ENDPOINT) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity be
                && be.side(side).mode != SideMode.EXTRACT) {
            player.displayClientMessage(Component.translatable("message.flowline.gui_needs_extract"), true);
        } else if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof PipeBlockEntity be) {
            Component title = Component.translatable("gui.flowline.pipe_config", getName(),
                    Component.translatable("direction.flowline." + side.getName()));
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, p) -> new PipeMenu(id, inventory, be, side), title),
                    buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeEnum(side);
                        buf.writeEnum(be.type());
                    });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Switches a side between insert and extract and tells the player which one it is now. Upgrades only work on
     * extracting sides, so switching to insert hands the installed upgrade back.
     */
    public static void toggleMode(PipeBlockEntity be, Direction side, Player player) {
        SideConfig cfg = be.side(side);
        cfg.mode = cfg.mode.toggle();
        if (cfg.mode == SideMode.INSERT && !be.getUpgrade(side).isEmpty()) {
            player.getInventory().placeItemBackInInventory(be.removeUpgrade(side));
        }
        be.setChanged();
        player.displayClientMessage(Component.translatable("message.flowline.mode_set",
                Component.translatable("direction.flowline." + side.getName()),
                Component.translatable("mode.flowline." + cfg.mode.name().toLowerCase())), true);
    }

    /**
     * Resolves which of the six sides was clicked. Hitting an arm selects that arm's side; hitting the central
     * cube selects the face that was hit.
     */
    static Direction sideFromHit(BlockHitResult hit, BlockPos pos) {
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        double eps = 1.0E-4;
        if (local.x < MIN - eps) return Direction.WEST;
        if (local.x > MAX + eps) return Direction.EAST;
        if (local.y < MIN - eps) return Direction.DOWN;
        if (local.y > MAX + eps) return Direction.UP;
        if (local.z < MIN - eps) return Direction.NORTH;
        if (local.z > MAX + eps) return Direction.SOUTH;
        return hit.getDirection();
    }

    // ---- block entity -----------------------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> beType) {
        if (level.isClientSide || beType != ModBlockEntities.PIPE.get()) return null;
        return (lvl, p, s, be) -> ((PipeBlockEntity) be).serverTick((ServerLevel) lvl);
    }
}
