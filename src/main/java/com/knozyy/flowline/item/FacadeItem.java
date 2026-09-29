package com.knozyy.flowline.item;

import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.registry.ModComponents;
import com.knozyy.flowline.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Hides a pipe behind a full block. Blank facades are combined with a block in the crafting grid (see
 * {@link com.knozyy.flowline.recipe.FacadeRecipe}); right-click a pipe to put one on, sneak-click the pipe with both
 * hands empty to take it off again. The pipe keeps working behind it.
 */
public class FacadeItem extends Item implements PipeInteractable {
    public FacadeItem(Properties properties) {
        super(properties);
    }

    /** A facade showing {@code state}. */
    public static ItemStack of(BlockState state) {
        ItemStack stack = new ItemStack(ModItems.FACADE.get());
        stack.set(ModComponents.FACADE.get(), state);
        return stack;
    }

    @Nullable
    public static BlockState stateOf(ItemStack stack) {
        return stack.get(ModComponents.FACADE.get());
    }

    /** Full, plain blocks only: no block entities, no pipes, a normal model and a full-cube shape. */
    public static boolean isValid(BlockState state) {
        Block block = state.getBlock();
        return !state.isAir() && !state.hasBlockEntity() && !(block instanceof PipeBlock)
                && state.getRenderShape() == RenderShape.MODEL
                && Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    /** The facade state a block item would make, or null. */
    @Nullable
    public static BlockState stateFor(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        BlockState state = blockItem.getBlock().defaultBlockState();
        return isValid(state) ? state : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        BlockState state = stateOf(stack);
        return state == null ? Component.translatable("item.flowline.facade.blank")
                : Component.translatable("item.flowline.facade.named", state.getBlock().getName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(stateOf(stack) == null ? "item.flowline.facade.blank.desc"
                : "item.flowline.facade.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return PipeInteractable.useOnFromItem(this, ctx);
    }

    @Override
    public void useOnPipe(PipeBlockEntity pipe, Direction side, Conn conn, Player player, InteractionHand hand,
                          ItemStack stack) {
        BlockState state = stateOf(stack);
        if (state == null) {
            player.displayClientMessage(Component.translatable("message.flowline.facade_blank"), true);
            return;
        }
        if (pipe.facade() != null) {
            player.displayClientMessage(Component.translatable("message.flowline.facade_present"), true);
            return;
        }
        pipe.setFacade(state);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("message.flowline.facade_applied", state.getBlock().getName()),
                true);
    }
}
