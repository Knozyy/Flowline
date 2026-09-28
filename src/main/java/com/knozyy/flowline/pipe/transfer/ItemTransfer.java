package com.knozyy.flowline.pipe.transfer;

import com.knozyy.flowline.pipe.PipeNetwork.Target;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

public final class ItemTransfer {
    private ItemTransfer() {}

    /** @return number of items moved. */
    public static int run(Level level, BlockPos sourcePos, BlockCapabilityCache<?, Direction> sourceCache,
                          SideConfig cfg, List<Target> targets, int budget) {
        if (!(sourceCache.getCapability() instanceof IItemHandler source)) return 0;
        int total = 0;

        List<IItemHandler> destinations = new ArrayList<>();
        for (Target t : targets) {
            if (t.cache().getCapability() instanceof IItemHandler h && h != source) destinations.add(h);
        }
        if (destinations.isEmpty()) return 0;

        for (int slot = 0; slot < source.getSlots() && budget > 0; slot++) {
            ItemStack offered = source.extractItem(slot, budget, true);
            if (offered.isEmpty()) continue;
            if (!cfg.allowsItem(offered, level.registryAccess())) continue;

            for (IItemHandler dest : destinations) {
                ItemStack leftover = ItemHandlerHelper.insertItemStacked(dest, offered, true);
                int accepted = offered.getCount() - leftover.getCount();
                if (accepted <= 0) continue;

                ItemStack extracted = source.extractItem(slot, accepted, false);
                if (extracted.isEmpty()) continue;
                ItemStack rest = ItemHandlerHelper.insertItemStacked(dest, extracted, false);
                if (!rest.isEmpty()) {
                    // Destination changed between simulate and execute: return what did not fit.
                    rest = ItemHandlerHelper.insertItemStacked(source, rest, false);
                    if (!rest.isEmpty()) Block.popResource(level, sourcePos, rest);
                }
                int moved = extracted.getCount() - rest.getCount();
                total += moved;
                budget -= moved;
                offered.shrink(moved);
                if (offered.isEmpty() || budget <= 0) break;
            }
        }
        return total;
    }
}
