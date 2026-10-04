package com.knozyy.flowline.compat;

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
import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the tooltip mods (Jade, The One Probe, WTHIT) show for a looked-at pipe side: {@link #collect} packs the side's
 * settings on the server, {@link #lines} turns them into tooltip lines on the client. Mentions no mod's types.
 */
public final class PipeSideInfo {
    private PipeSideInfo() {}

    public static CompoundTag collect(PipeBlockEntity pipe, Direction side) {
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
        return tag;
    }

    public static List<Component> lines(CompoundTag tag) {
        List<Component> out = new ArrayList<>();
        if (tag.isEmpty()) return out;
        Component where = Component.translatable("direction.flowline." + tag.getString("side"));
        if (tag.getInt("color") >= 0 && tag.contains("color")) {
            out.add(Component.translatable("jade.flowline.color",
                    Component.translatable("color.minecraft." + DyeColor.byId(tag.getInt("color")).getName()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!tag.contains("mode")) {
            out.add(Component.translatable("jade.flowline.side_" + tag.getString("conn"), where)
                    .withStyle(ChatFormatting.GRAY));
            return out;
        }
        boolean extract = SideMode.byName(tag.getString("mode")) == SideMode.EXTRACT;
        out.add(Component.translatable("jade.flowline.side", where,
                Component.translatable(extract ? "mode.flowline.extract" : "mode.flowline.insert")
                        .withStyle(extract ? ChatFormatting.GREEN : ChatFormatting.AQUA)));
        if (extract) {
            out.add(Component.translatable("gui.flowline.distribution", Component.translatable(
                    "distribution.flowline." + tag.getString("distribution").toLowerCase(Locale.ROOT)))
                    .withStyle(ChatFormatting.GRAY));
            RedstoneMode redstone = RedstoneMode.byName(tag.getString("redstone"));
            if (redstone != RedstoneMode.IGNORED) {
                out.add(Component.translatable("gui.flowline.redstone", Component.translatable(
                        "redstone.flowline." + redstone.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY));
            }
            out.add(Component.translatable(tag.getBoolean("sleeping") ? "jade.flowline.sleeping" : "jade.flowline.pacing",
                    tag.getInt("multiplier"), tag.getInt("interval"), tag.getInt("upgrades")).withStyle(ChatFormatting.GRAY));
            if (tag.getInt("rate") > 0) {
                out.add(Component.translatable("jade.flowline.rate", tag.getInt("rate")).withStyle(ChatFormatting.GRAY));
            }
        } else if (tag.getInt("priority") != 0) {
            out.add(Component.translatable("gui.flowline.priority", tag.getInt("priority")).withStyle(ChatFormatting.GRAY));
        }
        if (tag.getInt("limit") > 0) {
            out.add(Component.translatable(extract ? "jade.flowline.keep" : "jade.flowline.max", tag.getInt("limit"))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (tag.getBoolean("rules")) out.add(Component.translatable("jade.flowline.filtered").withStyle(ChatFormatting.GRAY));
        return out;
    }
}
