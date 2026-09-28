package com.knozyy.flowline.filter;

import com.knozyy.flowline.pipe.PipeType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One filter rule, Pipez style.
 *
 * @param target   an item/fluid id ({@code minecraft:stone}), a tag ({@code #minecraft:logs}), or empty for "anything"
 * @param nbt      optional data components (as the stack's component patch in NBT form) that must be present
 * @param exactNbt true: the stack's components must equal {@code nbt}; false: {@code nbt} must be contained in them
 * @param invert   true: stacks matching this rule are blocked
 */
public record FilterEntry(String target, Optional<CompoundTag> nbt, boolean exactNbt, boolean invert) {
    public static final Codec<FilterEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("target", "").forGetter(FilterEntry::target),
            CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(FilterEntry::nbt),
            Codec.BOOL.optionalFieldOf("exact_nbt", false).forGetter(FilterEntry::exactNbt),
            Codec.BOOL.optionalFieldOf("invert", false).forGetter(FilterEntry::invert)
    ).apply(i, FilterEntry::new));

    public static final StreamCodec<ByteBuf, FilterEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), FilterEntry::target,
            ByteBufCodecs.optional(ByteBufCodecs.COMPOUND_TAG), FilterEntry::nbt,
            ByteBufCodecs.BOOL, FilterEntry::exactNbt,
            ByteBufCodecs.BOOL, FilterEntry::invert,
            FilterEntry::new);

    public boolean isTag() {
        return target.startsWith("#");
    }

    /** The id or tag location in {@link #target}, or null if it is empty or malformed. */
    @Nullable
    public ResourceLocation location() {
        if (target.isEmpty()) return null;
        return ResourceLocation.tryParse(isTag() ? target.substring(1) : target);
    }

    public FilterEntry withInvert(boolean value) {
        return new FilterEntry(target, nbt, exactNbt, value);
    }

    // ---- validation ---------------------------------------------------------------------------------------

    /** @return a translation key describing what is wrong, or null if the entry can be used on {@code type}. */
    @Nullable
    public String problem(PipeType type) {
        if (target.isEmpty()) return nbt.isPresent() ? null : "gui.flowline.editor.error.empty";
        ResourceLocation location = location();
        if (location == null) return "gui.flowline.editor.error.syntax";
        if (type == PipeType.FLUID) {
            return isTag() ? (BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, location)).isPresent()
                    ? null : "gui.flowline.editor.error.unknown_tag")
                    : (BuiltInRegistries.FLUID.containsKey(location) ? null : "gui.flowline.editor.error.unknown_fluid");
        }
        return isTag() ? (BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, location)).isPresent()
                ? null : "gui.flowline.editor.error.unknown_tag")
                : (BuiltInRegistries.ITEM.containsKey(location) ? null : "gui.flowline.editor.error.unknown_item");
    }

    /** Parses SNBT typed by the player; empty text means "no NBT". */
    public static Optional<CompoundTag> parseNbt(String text) throws CommandSyntaxException {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        return Optional.of(TagParser.parseTag(trimmed));
    }

    // ---- building from stacks -----------------------------------------------------------------------------

    /** A rule matching {@code stack} exactly as it is (its type and, if it has any, its data components). */
    @Nullable
    public static FilterEntry fromStack(PipeType type, ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return null;
        if (type == PipeType.FLUID) {
            FluidStack fluid = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            if (fluid.isEmpty()) return null;
            return new FilterEntry(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString(),
                    encode(fluid.getComponentsPatch(), registries), false, false);
        }
        if (type != PipeType.ITEM) return null;
        return new FilterEntry(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                encode(stack.getComponentsPatch(), registries), false, false);
    }

    /** The component patch as NBT, or empty when the patch is empty. */
    public static Optional<CompoundTag> encode(DataComponentPatch patch, HolderLookup.Provider registries) {
        if (patch.isEmpty()) return Optional.empty();
        return DataComponentPatch.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), patch)
                .result()
                .filter(tag -> tag instanceof CompoundTag c && !c.isEmpty())
                .map(tag -> (CompoundTag) tag);
    }

    // ---- display ------------------------------------------------------------------------------------------

    /** An item that represents the rule in a slot: the item itself, the first tag member, or the fluid's bucket. */
    public ItemStack displayStack(PipeType type, HolderLookup.Provider registries) {
        ResourceLocation location = location();
        if (type == PipeType.FLUID) {
            Fluid fluid = Fluids.EMPTY;
            if (location != null && isTag()) {
                fluid = first(BuiltInRegistries.FLUID, TagKey.create(Registries.FLUID, location), Fluids.EMPTY);
            } else if (location != null) {
                fluid = BuiltInRegistries.FLUID.get(location);
            }
            Item bucket = fluid.getBucket();
            return new ItemStack(bucket == Items.AIR ? Items.BUCKET : bucket);
        }
        if (location == null) return new ItemStack(Items.NAME_TAG);
        Item item = isTag() ? first(BuiltInRegistries.ITEM, TagKey.create(Registries.ITEM, location), Items.BARRIER)
                : BuiltInRegistries.ITEM.get(location);
        ItemStack stack = new ItemStack(item == Items.AIR ? Items.BARRIER : item);
        if (!isTag() && nbt.isPresent()) {
            DataComponentPatch.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), nbt.get())
                    .result()
                    .ifPresent(stack::applyComponents);
        }
        return stack;
    }

    private static <T> T first(Registry<T> registry, TagKey<T> tag, T fallback) {
        return registry.getTag(tag)
                .flatMap(set -> set.stream().findFirst())
                .map(holder -> holder.value())
                .orElse(fallback);
    }
}
