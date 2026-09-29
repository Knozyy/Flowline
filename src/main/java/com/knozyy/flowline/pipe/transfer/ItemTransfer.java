package com.knozyy.flowline.pipe.transfer;

import com.knozyy.flowline.util.Stacks;
import com.knozyy.flowline.pipe.Caps;
import com.knozyy.flowline.pipe.PipeNetwork.Target;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

public final class ItemTransfer {
    private ItemTransfer() {}

    private record Dest(Target target, IItemHandler handler, SideConfig cfg) {}

    /**
     * Moves up to {@code budget} items. Balanced: first every target gets at most an equal share, then a second pass
     * hands out what is left to whoever still accepts.
     *
     * @return number of items moved
     */
    public static int run(Level level, BlockPos sourcePos, Caps sourceCaps, SideConfig cfg, List<Target> targets,
                          int budget, boolean balanced, PipeType pipe) {
        IItemHandler source = sourceCaps.itemHandler();
        if (source == null) return 0;

        List<Dest> destinations = new ArrayList<>();
        for (Target t : targets) {
            IItemHandler h = t.caps().itemHandler();
            SideConfig insert = t.insert();
            if (h != null && h != source && insert.channel(PipeType.CH_ITEMS, pipe)) {
                destinations.add(new Dest(t, h, insert));
            }
        }
        if (destinations.isEmpty()) return 0;

        int[] given = new int[destinations.size()];
        int cap = balanced ? Math.max(1, (budget + destinations.size() - 1) / destinations.size()) : Integer.MAX_VALUE;
        int total = 0;

        for (int pass = 0; pass < (balanced ? 2 : 1) && budget > 0; pass++) {
            for (int slot = 0; slot < source.getSlots() && budget > 0; slot++) {
                ItemStack offered = source.extractItem(slot, budget, true);
                if (offered.isEmpty()) continue;
                if (!cfg.allowsItem(offered)) continue;
                if (cfg.limit > 0) {
                    // regulator: leave at least `limit` of this item in the source
                    int spare = count(source, offered) - cfg.limit;
                    if (spare <= 0) continue;
                    if (spare < offered.getCount()) offered = Stacks.withCount(offered, spare);
                }

                for (int i = 0; i < destinations.size() && !offered.isEmpty() && budget > 0; i++) {
                    Dest dest = destinations.get(i);
                    int want = Math.min(offered.getCount(), cap - given[i]);
                    if (want <= 0) continue;
                    if (!dest.cfg().allowsItem(offered)) continue;
                    if (dest.cfg().limit > 0) {
                        // regulator: keep at most `limit` of this item in the target
                        want = Math.min(want, dest.cfg().limit - count(dest.handler(), offered));
                        if (want <= 0) continue;
                    }

                    ItemStack leftover = ItemHandlerHelper.insertItemStacked(dest.handler(), Stacks.withCount(offered, want),
                            true);
                    int accepted = want - leftover.getCount();
                    if (accepted <= 0) continue;

                    ItemStack extracted = source.extractItem(slot, accepted, false);
                    if (extracted.isEmpty()) continue;
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(dest.handler(), extracted, false);
                    if (!rest.isEmpty()) {
                        // Destination changed between simulate and execute: return what did not fit.
                        rest = ItemHandlerHelper.insertItemStacked(source, rest, false);
                        if (!rest.isEmpty()) Block.popResource(level, sourcePos, rest);
                    }
                    int moved = extracted.getCount() - rest.getCount();
                    given[i] += moved;
                    total += moved;
                    budget -= moved;
                    offered = Stacks.withCount(offered, offered.getCount() - moved);
                }
            }
            cap = Integer.MAX_VALUE;
        }
        return total;
    }

    /** How many items like {@code like} (same item and components) the handler holds. */
    static int count(IItemHandler handler, ItemStack like) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (ItemStack.isSameItemSameTags(stack, like)) count += stack.getCount();
        }
        return count;
    }
}
