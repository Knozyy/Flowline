package com.knozyy.flowline.compat;

import com.knozyy.flowline.pipe.PipeBlock;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Standard block placement for the native Item, called through ItemStack.useOn for Forge protection events. */
public final class PipePlacement {
    private PipePlacement() {}

    public static InteractionResult useOn(PipeBlock block, UseOnContext use) {
        BlockPlaceContext context = new BlockPlaceContext(use);
        var level = context.getLevel();
        var player = context.getPlayer();
        var pos = context.getClickedPos();
        var stack = context.getItemInHand();
        if (!block.isEnabled(level.enabledFeatures()) || !context.canPlace()) return InteractionResult.FAIL;
        BlockState state = block.getStateForPlacement(context);
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        if (state == null || !state.canSurvive(level, pos) || !level.isUnobstructed(state, pos, collision)
                || !level.setBlock(pos, state, 11)) return InteractionResult.FAIL;
        state = level.getBlockState(pos);
        if (state.is(block)) {
            CompoundTag tags = stack.getTagElement(BlockItem.BLOCK_STATE_TAG);
            if (tags != null) {
                BlockState tagged = state;
                for (String name : tags.getAllKeys()) {
                    Property<?> property = block.getStateDefinition().getProperty(name);
                    if (property != null) tagged = propertyValue(tagged, property, tags.getString(name));
                }
                if (tagged != state) level.setBlock(pos, tagged, 2);
                state = tagged;
            }
            BlockItem.updateCustomBlockEntityTag(level, player, pos, stack);
            block.setPlacedBy(level, pos, state, player, stack);
            if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
        }
        var sound = state.getSoundType(level, pos, player);
        level.playSound(player, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static <T extends Comparable<T>> BlockState propertyValue(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }
}
