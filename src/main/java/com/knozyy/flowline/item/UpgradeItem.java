package com.knozyy.flowline.item;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.Pacing;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Upgrade for an extracting side (see {@link UpgradeType}). Up to six per side, installed through the side's GUI
 * or by right-clicking the side.
 */
public class UpgradeItem extends Item implements PipeInteractable {
    private final UpgradeType type;

    /** Type of the upgrade in {@code stack}, or null if it is not an upgrade. */
    @Nullable
    public static UpgradeType typeOf(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem upgrade ? upgrade.type : null;
    }

    public UpgradeItem(Properties properties, UpgradeType type) {
        super(properties);
        this.type = type;
    }

    public UpgradeType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.addAll(effectLines(type));
        tooltip.add(Component.translatable("item.flowline.upgrade.where", SideConfig.UPGRADE_SLOTS)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** What one upgrade of {@code type} does, with the numbers from the config. */
    public static List<Component> effectLines(UpgradeType type) {
        List<Component> lines = new ArrayList<>();
        if (type.speed > 0) {
            lines.add(Component.translatable("item.flowline.upgrade.effect.speed",
                    FlowlineConfig.SPEED_REDUCTION.get(), Pacing.min()).withColor(SPEED_COLOR));
        }
        if (type.stack > 0) {
            String curve = FlowlineConfig.STACK_MULTIPLIERS.get().stream().map(m -> "x" + m)
                    .reduce((a, b) -> a + " > " + b).orElse("x1");
            lines.add(Component.translatable("item.flowline.upgrade.effect.stack", curve).withColor(STACK_COLOR));
        }
        if (type.filter > 0) {
            lines.add(Component.translatable("item.flowline.upgrade.effect.filter",
                    FlowlineConfig.FILTER_SLOTS_PER_UPGRADE.get()).withColor(FILTER_COLOR));
        }
        return lines;
    }

    public static final int SPEED_COLOR = 0x4AE6F0, STACK_COLOR = 0xF09A3A, FILTER_COLOR = 0x5AE07A, KNOZY_COLOR = 0xD24AF5;

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return PipeInteractable.useOnFromItem(this, ctx);
    }

    @Override
    public void useOnPipe(PipeBlockEntity pipe, Direction side, Conn conn, Player player, InteractionHand hand,
                          ItemStack stack) {
        if (!conn.isEndpoint()) {
            player.displayClientMessage(Component.translatable("message.flowline.no_endpoint"), true);
            return;
        }
        if (pipe.side(side).mode != SideMode.EXTRACT) {
            player.displayClientMessage(Component.translatable("message.flowline.upgrade_needs_extract"), true);
            return;
        }
        if (!pipe.installUpgrade(side, stack)) {
            player.displayClientMessage(Component.translatable("message.flowline.upgrade_slots_full"), true);
            return;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("message.flowline.upgrade_installed",
                pipe.installedUpgrades(side), SideConfig.UPGRADE_SLOTS), true);
    }
}
