package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Implemented by items that configure one side of a pipe when used on it. */
public interface PipeInteractable {
    /** Server side only. {@code conn} is what the clicked side is currently attached to. */
    void useOnPipe(PipeBlockEntity pipe, Direction side, Conn conn, Player player, InteractionHand hand,
                   ItemStack stack);

    /**
     * Call from {@code Item#useOn}. Vanilla does not call the block when a player sneaks with an item in hand,
     * so sneak actions arrive here instead of through {@link PipeBlock#useItemOn}.
     */
    static InteractionResult useOnFromItem(PipeInteractable tool, UseOnContext ctx) {
        BlockState state = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (!(state.getBlock() instanceof PipeBlock) || ctx.getPlayer() == null) return InteractionResult.PASS;
        BlockHitResult hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), ctx.getClickedPos(),
                ctx.isInside());
        PipeBlock.useTool(tool, ctx.getItemInHand(), state, ctx.getLevel(), ctx.getClickedPos(), ctx.getPlayer(),
                ctx.getHand(), hit);
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }
}
