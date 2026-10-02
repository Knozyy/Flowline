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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

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
                          int budget, boolean balanced, PipeType pipe,
                          @Nullable BiConsumer<Target, ItemStack> onMove) {
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
        boolean[] announced = new boolean[destinations.size()];
        int cap = Integer.MAX_VALUE;
        if (balanced) {
            // split what the source can really give this operation, not the whole budget
            int available = 0;
            List<ItemStack> counted = new ArrayList<>();
            for (int slot = 0; slot < source.getSlots() && available < budget; slot++) {
                ItemStack offered = source.extractItem(slot, budget - available, true);
                if (offered.isEmpty() || !cfg.allowsItem(offered)) continue;
                int amount = offered.getCount();
                int keep = cfg.limitFor(offered);
                if (keep > 0) {
                    ItemStack previous = null;
                    for (ItemStack stack : counted) {
                        if (ItemStack.isSameItemSameTags(stack, offered)) { previous = stack; break; }
                    }
                    long spare = count(source, offered) - keep - (previous == null ? 0 : previous.getCount());
                    amount = (int) Math.max(0, Math.min(amount, spare));
                    if (amount == 0) continue;
                    if (previous == null) counted.add(Stacks.withCount(offered, amount));
                    else previous.grow(amount);
                }
                available += amount;
            }
            cap = Math.max(1, 1 + (Math.min(budget, available) - 1) / destinations.size());
        }
        int total = 0;

        for (int pass = 0; pass < (balanced ? 2 : 1) && budget > 0; pass++) {
            for (int slot = 0; slot < source.getSlots() && budget > 0; slot++) {
                ItemStack offered = source.extractItem(slot, budget, true);
                if (offered.isEmpty()) continue;
                if (!cfg.allowsItem(offered)) continue;
                int keep = cfg.limitFor(offered);
                if (keep > 0) {
                    // regulator (the side's, or a matching rule's amount): leave at least this much in the source
                    long spare = count(source, offered) - keep;
                    if (spare <= 0) continue;
                    if (spare < offered.getCount()) offered = Stacks.withCount(offered, (int) spare);
                }

                for (int i = 0; i < destinations.size() && !offered.isEmpty() && budget > 0; i++) {
                    Dest dest = destinations.get(i);
                    int want = Math.min(offered.getCount(), cap - given[i]);
                    if (want <= 0) continue;
                    if (!dest.cfg().allowsItem(offered)) continue;
                    int max = dest.cfg().limitFor(offered);
                    if (max > 0) {
                        // regulator (the side's, or a matching rule's amount): keep at most this much in the target
                        want = (int) Math.max(0, Math.min(want, max - count(dest.handler(), offered)));
                        if (want <= 0) continue;
                    }

                    ItemStack leftover = ItemHandlerHelper.insertItemStacked(dest.handler(), Stacks.withCount(offered, want),
                            true);
                    int accepted = want - leftover.getCount();
                    if (accepted <= 0) continue;

                    ItemStack extracted = source.extractItem(slot, accepted, false);
                    if (extracted.isEmpty()) continue;
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(dest.handler(), extracted, false);
                    int moved = extracted.getCount() - rest.getCount();
                    if (!rest.isEmpty()) {
                        // Destination changed between simulate and execute: return what did not fit.
                        rest = ItemHandlerHelper.insertItemStacked(source, rest, false);
                        if (!rest.isEmpty()) Block.popResource(level, sourcePos, rest);
                    }
                    if (moved > 0 && onMove != null && !announced[i]) {
                        announced[i] = true;
                        onMove.accept(dest.target(), Stacks.withCount(extracted, moved));
                    }
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

    /** Whether at least one allowed item can be extracted without crossing its reserve. */
    public static boolean hasWork(IItemHandler handler, SideConfig cfg) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.extractItem(slot, 1, true);
            if (!stack.isEmpty() && cfg.allowsItem(stack)
                    && (cfg.limitFor(stack) <= 0 || count(handler, stack) > cfg.limitFor(stack))) return true;
        }
        return false;
    }

    /** How many items like {@code like} (same item and components) the handler holds. */
    static long count(IItemHandler handler, ItemStack like) {
        long count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (ItemStack.isSameItemSameTags(stack, like)) count += stack.getCount();
        }
        return count;
    }
}
