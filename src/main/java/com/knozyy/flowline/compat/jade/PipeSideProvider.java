package com.knozyy.flowline.compat.jade;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.pipe.Conn;
import com.knozyy.flowline.pipe.Pacing;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.RedstoneMode;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/** Server: packs the looked-at side's settings. Client: shows them under the pipe's name. */
public enum PipeSideProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(Flowline.MODID, "pipe_side");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof PipeBlockEntity pipe)) return;
        Direction side = PipeBlock.sideFromHit(accessor.getHitResult(), accessor.getPosition());
        Conn conn = pipe.getBlockState().getValue(PipeBlock.prop(side));
        CompoundTag tag = new CompoundTag();
        tag.putString("side", side.getName());
        tag.putString("conn", conn.getSerializedName());
        tag.putInt("color", pipe.color());
        if (conn.isEndpoint()) {
            SideConfig cfg = pipe.side(side);
            tag.putString("mode", cfg.mode.name());
            tag.putString("distribution", cfg.distribution.name());
            tag.putString("redstone", cfg.redstone.name());
            tag.putInt("priority", cfg.priority);
            tag.putInt("limit", cfg.limit);
            tag.putInt("rate", cfg.rate);
            tag.putInt("interval", cfg.interval < 0 ? Pacing.start(cfg.speedCount) : cfg.interval);
            tag.putBoolean("sleeping", cfg.sleeping);
            tag.putInt("multiplier", Pacing.stackMultiplier(cfg.stackCount));
            tag.putInt("upgrades", pipe.installedUpgrades(side));
            tag.putBoolean("rules", cfg.hasRules());
        }
        data.put("flowline", tag);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData().getCompound("flowline");
        if (tag.isEmpty()) return;
        Component where = Component.translatable("direction.flowline." + tag.getString("side"));
        if (tag.getInt("color") >= 0 && tag.contains("color")) {
            tooltip.add(Component.translatable("jade.flowline.color",
                    Component.translatable("color.minecraft." + DyeColor.byId(tag.getInt("color")).getName()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!tag.contains("mode")) {
            tooltip.add(Component.translatable("jade.flowline.side_" + tag.getString("conn"), where)
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        boolean extract = SideMode.byName(tag.getString("mode")) == SideMode.EXTRACT;
        tooltip.add(Component.translatable("jade.flowline.side", where,
                Component.translatable(extract ? "mode.flowline.extract" : "mode.flowline.insert")
                        .withStyle(extract ? ChatFormatting.GREEN : ChatFormatting.AQUA)));
        if (extract) {
            tooltip.add(Component.translatable("gui.flowline.distribution", Component.translatable(
                    "distribution.flowline." + tag.getString("distribution").toLowerCase(Locale.ROOT)))
                    .withStyle(ChatFormatting.GRAY));
            RedstoneMode redstone = RedstoneMode.byName(tag.getString("redstone"));
            if (redstone != RedstoneMode.IGNORED) {
                tooltip.add(Component.translatable("gui.flowline.redstone", Component.translatable(
                        "redstone.flowline." + redstone.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.translatable(tag.getBoolean("sleeping") ? "jade.flowline.sleeping" : "jade.flowline.pacing",
                    tag.getInt("multiplier"), tag.getInt("interval"), tag.getInt("upgrades")).withStyle(ChatFormatting.GRAY));
            if (tag.getInt("rate") > 0) {
                tooltip.add(Component.translatable("jade.flowline.rate", tag.getInt("rate")).withStyle(ChatFormatting.GRAY));
            }
        } else if (tag.getInt("priority") != 0) {
            tooltip.add(Component.translatable("gui.flowline.priority", tag.getInt("priority")).withStyle(ChatFormatting.GRAY));
        }
        if (tag.getInt("limit") > 0) {
            tooltip.add(Component.translatable(extract ? "jade.flowline.keep" : "jade.flowline.max", tag.getInt("limit"))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (tag.getBoolean("rules")) tooltip.add(Component.translatable("jade.flowline.filtered").withStyle(ChatFormatting.GRAY));
    }
}
