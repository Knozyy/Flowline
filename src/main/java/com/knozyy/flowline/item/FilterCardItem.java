package com.knozyy.flowline.item;

import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.SideConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Copies one side's filter rules and pastes them onto other sides. Sneak + right-click a side: copy. Right-click a
 * side: paste. Sneak + right-click the air: clear the card. Only the rules travel; mode, distribution, redstone and
 * upgrades stay where they are.
 */
public class FilterCardItem extends Item implements PipeInteractable {
    public FilterCardItem(Properties properties) {
        super(properties);
    }

    /** NBT key of the copied rules on the card. */
    private static final String KEY = "flowline_card";

    @Nullable
    public static CompoundTag data(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(KEY) ? tag.getCompound(KEY) : null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return data(stack) != null;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return PipeInteractable.useOnFromItem(this, ctx);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && data(stack) != null) {
            if (!level.isClientSide) {
                stack.removeTagKey(KEY);
                player.displayClientMessage(Component.translatable("message.flowline.card_cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void useOnPipe(PipeBlockEntity pipe, Direction side, Conn conn, Player player, InteractionHand hand,
                          ItemStack stack) {
        Component where = Component.translatable("direction.flowline." + side.getName());
        if (!conn.isEndpoint()) {
            player.displayClientMessage(Component.translatable("message.flowline.no_endpoint"), true);
            return;
        }
        SideConfig cfg = pipe.side(side);
        if (player.isShiftKeyDown()) {
            stack.getOrCreateTag().put(KEY, copy(cfg));
            player.displayClientMessage(Component.translatable("message.flowline.card_copied", where), true);
            return;
        }
        CompoundTag data = data(stack);
        if (data == null) {
            player.displayClientMessage(Component.translatable("message.flowline.card_empty"), true);
            return;
        }
        int skipped = paste(pipe, side, data);
        player.displayClientMessage(skipped == 0
                ? Component.translatable("message.flowline.card_pasted", where)
                : Component.translatable("message.flowline.card_pasted_skipped", where, skipped), true);
    }

    private static CompoundTag copy(SideConfig cfg) {
        CompoundTag tag = new CompoundTag();
        tag.putString("pipe", cfg.type.getSerializedName());
        tag.put("filter", cfg.saveFilter());
        return tag;
    }

    /** @return number of rules that do not fit this pipe type and were left out */
    private static int paste(PipeBlockEntity pipe, Direction side, CompoundTag data) {
        SideConfig cfg = pipe.side(side);
        int skipped = 0;
        if (pipe.type().hasFilter()) {
            cfg.clearFilter();
            for (Tag t : data.getList("filter", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) t;
                int slot = entry.getInt("slot");
                if (slot < 0 || slot > 1024) {
                    skipped++;
                    continue;
                }
                FilterEntry rule = FilterEntry.CODEC.parse(NbtOps.INSTANCE, entry.get("rule")).result().orElse(null);
                if (rule == null || rule.problem(pipe.type()) != null) {
                    skipped++;
                    continue;
                }
                cfg.setEntry(slot, rule);
            }
        }
        cfg.wake();
        pipe.setChanged();
        return skipped;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag data = data(stack);
        if (data == null) {
            tooltip.add(Component.translatable("item.flowline.card.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.flowline.card.from",
                    Component.translatable("block.flowline." + data.getString("pipe") + "_pipe"))
                    .withStyle(ChatFormatting.GRAY));
            ListTag rules = data.getList("filter", Tag.TAG_COMPOUND);
            tooltip.add(Component.translatable("item.flowline.card.rules", rules.size()).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("item.flowline.filter_card.desc")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.flowline.card.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
