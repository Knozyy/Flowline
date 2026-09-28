package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * Hold the filter in one hand and a sample in the other (an item, or a bucket/tank for fluid pipes).
 * Click: toggle the sample in the filter. Click with an empty off hand: clear the filter.
 * Sneak-click: switch between whitelist and blacklist.
 */
public class FilterItem extends Item implements PipeInteractable {
    public FilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public void useOnPipe(PipeBlockEntity pipe, Direction side, Player player, InteractionHand hand, ItemStack stack) {
        SideConfig cfg = pipe.side(side);

        if (pipe.type() == PipeType.ENERGY) {
            player.displayClientMessage(Component.translatable("message.flowline.no_filter_energy"), true);
            return;
        }

        if (player.isShiftKeyDown()) {
            cfg.whitelist = !cfg.whitelist;
            pipe.setChanged();
            player.displayClientMessage(Component.translatable(cfg.whitelist
                    ? "message.flowline.filter_whitelist" : "message.flowline.filter_blacklist"), true);
            return;
        }

        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack sample = player.getItemInHand(other);
        if (sample.isEmpty()) {
            cfg.filter.clear();
            pipe.setChanged();
            player.displayClientMessage(Component.translatable("message.flowline.filter_cleared"), true);
            return;
        }

        ResourceLocation id = sampleId(pipe.type(), sample);
        if (id == null) {
            player.displayClientMessage(Component.translatable("message.flowline.filter_invalid_sample"), true);
            return;
        }
        boolean added = cfg.toggleFilter(id);
        pipe.setChanged();
        player.displayClientMessage(Component.translatable(
                added ? "message.flowline.filter_added" : "message.flowline.filter_removed", id.toString()), true);
    }

    private static ResourceLocation sampleId(PipeType type, ItemStack sample) {
        if (type == PipeType.FLUID) {
            return FluidUtil.getFluidContained(sample)
                    .map(f -> BuiltInRegistries.FLUID.getKey(f.getFluid()))
                    .orElse(null);
        }
        return BuiltInRegistries.ITEM.getKey(sample.getItem());
    }
}
