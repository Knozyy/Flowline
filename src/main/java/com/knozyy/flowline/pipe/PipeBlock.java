package com.knozyy.flowline.pipe;

import com.knozyy.flowline.item.PipeInteractable;
import com.knozyy.flowline.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
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
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState ns = level.getBlockState(neighbor);
            Conn conn;
            if (ns.getBlock() instanceof PipeBlock other && other.type == type) {
                conn = Conn.PIPE;
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
        refresh(state, level, pos);
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
        if (!(stack.getItem() instanceof PipeInteractable tool)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity be) {
            Direction side = sideFromHit(hit, pos);
            if (state.getValue(prop(side)) != Conn.ENDPOINT) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.flowline.no_endpoint"), true);
            } else {
                tool.useOnPipe(be, side, player, hand, stack);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
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
        return (lvl, p, s, be) -> ((PipeBlockEntity) be).serverTick((net.minecraft.server.level.ServerLevel) lvl);
    }
}
