package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Sneak-click a side: cycle normal (insert) -> extract -> disconnected -> normal.
 * Click a side: open its configuration screen.
 * Sneak + scroll on a side: extracting sides cycle their distribution (with Ctrl: their redstone mode), inserting
 * sides change their priority.
 */
public class WrenchItem extends Item implements PipeInteractable {
    /** Wrenches of any mod (Create, Mekanism, Thermal...): they configure pipes too, see {@code CommonEvents}. */
    public static final TagKey<Item> WRENCHES = ItemTags.create(new ResourceLocation("forge", "tools/wrench"));

    /** What a wrench does to a pipe side; shared with other mods' wrenches. */
    public static final PipeInteractable ACTIONS = (pipe, side, conn, player, hand, stack) -> {
        if (player.isShiftKeyDown()) {
            PipeBlock.cycleSide(pipe, side, conn, player);
        } else {
            PipeBlock.openConfig(pipe, side, conn, player);
        }
    };

    public WrenchItem(Properties properties) {
        super(properties);
    }

    public static boolean isWrench(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem || stack.is(WRENCHES);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return PipeInteractable.useOnFromItem(this, ctx);
    }

    @Override
    public void useOnPipe(PipeBlockEntity pipe, Direction side, Conn conn, Player player, InteractionHand hand,
                          ItemStack stack) {
        ACTIONS.useOnPipe(pipe, side, conn, player, hand, stack);
    }

    /** Server side of the sneak + scroll shortcut. */
    public static void scroll(Player player, BlockPos pos, Direction side, boolean forward, boolean redstone) {
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild() || !player.isShiftKeyDown()
                || !isWrench(player.getMainHandItem()) || !player.level().isLoaded(pos)
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64
                || !player.level().mayInteract(player, pos)) return;
        if (!(player.level().getBlockEntity(pos) instanceof PipeBlockEntity pipe)) return;
        if (!pipe.getBlockState().getValue(PipeBlock.prop(side)).isEndpoint()) return;
        SideConfig cfg = pipe.side(side);
        Component where = Component.translatable("direction.flowline." + side.getName());
        Component message;
        if (cfg.mode == SideMode.EXTRACT && redstone) {
            cfg.redstone = forward ? cfg.redstone.next() : cfg.redstone.previous();
            message = Component.translatable("message.flowline.scroll_redstone", where,
                    Component.translatable("redstone.flowline." + cfg.redstone.name().toLowerCase(Locale.ROOT)));
        } else if (cfg.mode == SideMode.EXTRACT) {
            cfg.distribution = forward ? cfg.distribution.next() : cfg.distribution.previous();
            message = Component.translatable("message.flowline.scroll_distribution", where,
                    Component.translatable("distribution.flowline." + cfg.distribution.name().toLowerCase(Locale.ROOT)));
        } else {
            int step = redstone ? 10 : 1;
            cfg.priority = Math.max(-SideConfig.MAX_PRIORITY,
                    Math.min(SideConfig.MAX_PRIORITY, cfg.priority + (forward ? step : -step)));
            message = Component.translatable("message.flowline.scroll_priority", where, cfg.priority);
        }
        cfg.wake();
        pipe.setChanged();
        player.displayClientMessage(message, true);
    }
}
