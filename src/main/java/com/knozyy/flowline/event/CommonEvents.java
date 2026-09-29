package com.knozyy.flowline.event;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.item.PipeInteractable;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.pipe.PipeBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Flowline.MODID)
public final class CommonEvents {
    private CommonEvents() {}

    /**
     * Other mods' wrenches (tagged {@code forge:tools/wrench}) configure pipes like the Flowline Wrench, sneaking
     * included, instead of doing their own thing (rotating, dismantling) to the pipe.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof PipeInteractable || !WrenchItem.isWrench(stack)) return;
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof PipeBlock)) return;
        PipeBlock.useTool(WrenchItem.ACTIONS, stack, state, level, event.getPos(), event.getEntity(), event.getHand(),
                event.getHitVec());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
