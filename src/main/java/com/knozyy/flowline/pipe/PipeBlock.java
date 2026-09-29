package com.knozyy.flowline.pipe;

import com.knozyy.flowline.item.FacadeItem;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class PipeBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

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
        registerDefaultState(def.setValue(WATERLOGGED, false));
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
        builder.add(WATERLOGGED);
    }

    // ---- water ------------------------------------------------------------------------------------------------

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
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
                PipeBlockEntity nb = level.getBlockEntity(neighbor) instanceof PipeBlockEntity be ? be : null;
                boolean otherCut = nb != null && nb.isDisconnected(dir.getOpposite());
                boolean otherColor = nb != null && self != null && !PipeBlockEntity.colorsMatch(self.color(), nb.color());
                conn = otherCut || otherColor ? Conn.NONE : Conn.PIPE;
            } else if (type.hasEndpoint(level, neighbor, dir.getOpposite())) {
                conn = self != null && self.side(dir).mode == SideMode.EXTRACT ? Conn.EXTRACT : Conn.ENDPOINT;
            } else {
                conn = Conn.NONE;
            }
            state = state.setValue(prop(dir), conn);
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean water = ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER;
        return withConnections(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState().setValue(WATERLOGGED, water));
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
            if (be.facade() != null) popResource(level, pos, FacadeItem.of(be.facade()));
            PipeNetwork.invalidate(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        refresh(state, level, pos);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity be) {
            be.wake();
            be.onSignal(level.hasNeighborSignal(pos));
        }
    }

    /** A neighbouring block entity changed (e.g. a chest's contents): extracting sides should look again soon. */
    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PipeBlockEntity be) be.wake();
    }

    private void refresh(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide) return;
        BlockState updated = withConnections(level, pos, state);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
            PipeNetwork.invalidate(level, pos);
        }
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
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity be && be.facade() != null) return Shapes.block();
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
        if (stack.getItem() instanceof DyeItem dye) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity be) paint(be, dye.getDyeColor(), player, stack);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Any other held item keeps its normal behaviour (e.g. placing a block against the pipe).
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Dyes the pipe: pipes of two different colours never connect, undyed pipes connect to every colour. Using the
     * pipe's own colour again washes it off.
     */
    private static void paint(PipeBlockEntity be, DyeColor dye, Player player, ItemStack stack) {
        boolean wash = be.color() == dye.getId();
        be.setColor(wash ? PipeBlockEntity.NO_COLOR : dye.getId());
        if (!wash && !player.getAbilities().instabuild) stack.shrink(1);
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        updateConnections(level, pos);
        for (Direction dir : Direction.values()) updateConnections(level, pos.relative(dir));
        PipeNetwork.invalidate(level, pos);
        player.displayClientMessage(wash ? Component.translatable("message.flowline.color_cleared")
                : Component.translatable("message.flowline.color_set",
                Component.translatable("color.minecraft." + dye.getName())), true);
    }

    /**
     * Shared entry point for tools. Called from {@link #useItemOn} and, because vanilla skips the block when the
     * player sneaks with an item in hand, from the tools' own {@code useOn} as well.
     */
    public static void useTool(PipeInteractable tool, ItemStack stack, BlockState state, Level level, BlockPos pos,
                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof PipeBlockEntity be)) return;
        Direction side = sideFromHit(hit, pos, be.facade() != null);
        tool.useOnPipe(be, side, state.getValue(prop(side)), player, hand, stack);
    }

    /**
     * Empty-handed click on an endpoint side opens that side's configuration screen; sneaking with both hands empty
     * takes a facade off.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        PipeBlockEntity be = level.getBlockEntity(pos) instanceof PipeBlockEntity pipe ? pipe : null;
        if (be != null && be.facade() != null && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack facade = FacadeItem.of(be.facade());
                be.setFacade(null);
                if (!player.getAbilities().instabuild) player.getInventory().placeItemBackInInventory(facade);
                player.displayClientMessage(Component.translatable("message.flowline.facade_removed"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        Direction side = sideFromHit(hit, pos, be != null && be.facade() != null);
        Conn conn = state.getValue(prop(side));
        if (!conn.isEndpoint()) return InteractionResult.PASS;
        if (!level.isClientSide && be != null) openConfig(be, side, conn, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Opens the configuration screen of an extracting side, or says why it cannot. Server side. */
    public static void openConfig(PipeBlockEntity be, Direction side, Conn conn, Player player) {
        if (!conn.isEndpoint()) {
            player.displayClientMessage(Component.translatable("message.flowline.no_endpoint"), true);
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Component title = Component.translatable("gui.flowline.pipe_config", be.getBlockState().getBlock().getName(),
                Component.translatable("direction.flowline." + side.getName()));
        BlockPos pos = be.getBlockPos();
        serverPlayer.openMenu(
                new SimpleMenuProvider((id, inventory, p) -> new PipeMenu(id, inventory, be, side), title),
                buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeEnum(side);
                    buf.writeEnum(be.type());
                });
    }

    /**
     * Wrench sneak-click cycle for one side: normal (insert) -> extract -> disconnected -> normal. Pipe-to-pipe
     * sides only toggle between connected and disconnected.
     */
    public static void cycleSide(PipeBlockEntity be, Direction side, Conn conn, Player player) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockPos pos = be.getBlockPos();
        Component where = Component.translatable("direction.flowline." + side.getName());
        SideConfig cfg = be.side(side);

        if (isCut(level, pos, side)) {
            toggleConnection(level, pos, side);
            player.displayClientMessage(Component.translatable("message.flowline.side_normal", where), true);
        } else if (conn.isEndpoint() && cfg.mode == SideMode.INSERT) {
            setMode(be, side, SideMode.EXTRACT, player);
            player.displayClientMessage(Component.translatable("message.flowline.side_extract", where), true);
        } else if (conn != Conn.NONE) {
            if (cfg.mode == SideMode.EXTRACT) setMode(be, side, SideMode.INSERT, player);
            toggleConnection(level, pos, side);
            player.displayClientMessage(Component.translatable("message.flowline.side_disconnected", where), true);
        } else {
            player.displayClientMessage(Component.translatable("message.flowline.nothing_to_connect"), true);
        }
    }

    /** Whether the wrench has cut this side (on this pipe, or on the neighbouring pipe towards it). */
    public static boolean isCut(Level level, BlockPos pos, Direction side) {
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity be && be.isDisconnected(side)) return true;
        return level.getBlockEntity(pos.relative(side)) instanceof PipeBlockEntity other
                && level.getBlockEntity(pos) instanceof PipeBlockEntity self
                && other.type() == self.type() && other.isDisconnected(side.getOpposite());
    }

    /**
     * Sets a side's mode. Upgrades only work on extracting sides, so switching to insert hands the installed
     * upgrades back to the player.
     */
    public static void setMode(PipeBlockEntity be, Direction side, SideMode mode, @Nullable Player player) {
        SideConfig cfg = be.side(side);
        if (cfg.mode == mode) return;
        cfg.mode = mode;
        if (mode == SideMode.INSERT) {
            // inserting sides keep Filter upgrades (for their insert rules); the rest goes back to the player
            for (ItemStack upgrade : be.removeExtractOnlyUpgrades(side)) {
                if (player != null) {
                    player.getInventory().placeItemBackInInventory(upgrade);
                } else if (be.getLevel() != null) {
                    popResource(be.getLevel(), be.getBlockPos(), upgrade);
                }
            }
        }
        be.setChanged();
        if (be.getLevel() != null) PipeNetwork.invalidate(be.getLevel(), be.getBlockPos());
        // redraw the side: extracting ends carry a green ring
        if (be.getLevel() != null) updateConnections(be.getLevel(), be.getBlockPos());
    }

    /**
     * Resolves which of the six sides was clicked. Hitting an arm selects that arm's side; hitting the central
     * cube selects the face that was hit. Behind a facade the whole block is one cube, so the hit face counts.
     */
    public static Direction sideFromHit(BlockHitResult hit, BlockPos pos, boolean facade) {
        if (facade) return hit.getDirection();
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
