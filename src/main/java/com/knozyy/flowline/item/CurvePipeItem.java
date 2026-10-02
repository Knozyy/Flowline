package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.PipeBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Main hand edits curves. Sneak or off hand keeps vanilla placement, including Build for Me. */
public final class CurvePipeItem extends BlockItem {
    public CurvePipeItem(PipeBlock block, Properties properties){super(block,properties);}
    @Override public InteractionResult useOn(UseOnContext ctx){
        if(ctx.getHand()==InteractionHand.MAIN_HAND && ctx.getPlayer()!=null && !ctx.getPlayer().isShiftKeyDown())
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        return super.useOn(ctx);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand){
        return hand==InteractionHand.MAIN_HAND&&!player.isShiftKeyDown()
                ?InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide):super.use(level,player,hand);
    }
}
