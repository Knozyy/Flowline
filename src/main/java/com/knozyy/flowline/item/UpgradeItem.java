package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.Conn;
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

import java.util.List;
import java.util.Locale;

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
        String key = "item.flowline." + type.name().toLowerCase(Locale.ROOT) + "_upgrade.desc";
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return PipeInteractable.useOnFromItem(this, ctx);
    }

    @Override
    public void useOnPipe(PipeBlockEntity pipe, Direction side, Conn conn, Player player, InteractionHand hand,
                          ItemStack stack) {
        if (conn != Conn.ENDPOINT) {
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
