package com.knozyy.flowline.item;

import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.Distribution;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.RedstoneMode;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.registry.ModComponents;
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
import java.util.Locale;

/**
 * Copies one side's settings and pastes them onto other sides. Sneak + right-click a side: copy. Right-click a side:
 * paste. Sneak + right-click the air: clear the card. The Configuration Card carries mode, distribution, redstone,
 * priority, regulator, rate, channels and filter; the Filter Card only the filter rules. Upgrades are items and stay
 * where they are.
 */
public class ConfigCardItem extends Item implements PipeInteractable {
    private final boolean filterOnly;

    public ConfigCardItem(Properties properties, boolean filterOnly) {
        super(properties);
        this.filterOnly = filterOnly;
    }

    public boolean filterOnly() {
        return filterOnly;
    }

    @Nullable
    public static CompoundTag data(ItemStack stack) {
        return stack.get(ModComponents.CARD.get());
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
                stack.remove(ModComponents.CARD.get());
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
            stack.set(ModComponents.CARD.get(), copy(cfg));
            player.displayClientMessage(Component.translatable("message.flowline.card_copied", where), true);
            return;
        }
        CompoundTag data = data(stack);
        if (data == null) {
            player.displayClientMessage(Component.translatable("message.flowline.card_empty"), true);
            return;
        }
        int skipped = paste(pipe, side, data, player);
        player.displayClientMessage(skipped == 0
                ? Component.translatable("message.flowline.card_pasted", where)
                : Component.translatable("message.flowline.card_pasted_skipped", where, skipped), true);
    }

    private CompoundTag copy(SideConfig cfg) {
        CompoundTag tag = filterOnly ? new CompoundTag() : cfg.saveSettings();
        if (!filterOnly) tag.putString("mode", cfg.mode.name());
        tag.putString("pipe", cfg.type.getSerializedName());
        tag.put("filter", cfg.saveFilter());
        return tag;
    }

    /** @return number of rules that do not fit this pipe type and were left out */
    private int paste(PipeBlockEntity pipe, Direction side, CompoundTag data, Player player) {
        SideConfig cfg = pipe.side(side);
        if (!filterOnly) {
            PipeBlock.setMode(pipe, side, SideMode.byName(data.getString("mode")), player);
            cfg.loadSettings(data);
        }
        int skipped = 0;
        if (pipe.type().hasFilter()) {
            cfg.clearFilter();
            for (Tag t : data.getList("filter", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) t;
                FilterEntry rule = FilterEntry.CODEC.parse(NbtOps.INSTANCE, entry.get("rule")).result().orElse(null);
                if (rule == null || rule.problem(pipe.type()) != null) {
                    skipped++;
                    continue;
                }
                cfg.setEntry(entry.getInt("slot"), rule);
            }
        }
        cfg.wake();
        pipe.setChanged();
        return skipped;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag data = data(stack);
        if (data == null) {
            tooltip.add(Component.translatable("item.flowline.card.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.flowline.card.from",
                    Component.translatable("block.flowline." + data.getString("pipe") + "_pipe"))
                    .withStyle(ChatFormatting.GRAY));
            if (!filterOnly) {
                tooltip.add(line("gui.flowline.mode", "mode.flowline." + lower(SideMode.byName(data.getString("mode")).name())));
                tooltip.add(line("gui.flowline.distribution",
                        "distribution.flowline." + lower(Distribution.byName(data.getString("distribution")).name())));
                tooltip.add(line("gui.flowline.redstone",
                        "redstone.flowline." + lower(RedstoneMode.byName(data.getString("redstone")).name())));
                if (data.getInt("priority") != 0) {
                    tooltip.add(Component.translatable("gui.flowline.priority", data.getInt("priority"))
                            .withStyle(ChatFormatting.DARK_AQUA));
                }
                if (data.getInt("limit") != 0) {
                    tooltip.add(Component.translatable("gui.flowline.limit.value", data.getInt("limit"))
                            .withStyle(ChatFormatting.DARK_AQUA));
                }
            }
            ListTag rules = data.getList("filter", Tag.TAG_COMPOUND);
            tooltip.add(Component.translatable("item.flowline.card.rules", rules.size()).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable(filterOnly ? "item.flowline.filter_card.desc" : "item.flowline.config_card.desc")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.flowline.card.usage").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component line(String labelKey, String valueKey) {
        return Component.translatable(labelKey, Component.translatable(valueKey).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.DARK_AQUA);
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
