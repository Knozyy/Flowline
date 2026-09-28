package com.knozyy.flowline.filter;

import com.knozyy.flowline.pipe.PipeType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * One filter rule. Every part that is set must match:
 *
 * @param item     an item id (or fluid id on fluid pipes)
 * @param tags     tag ids without '#'; the stack must be in any of them, or in all of them when {@code allTags}
 * @param allTags  false: any listed tag is enough (OR); true: every listed tag is required (AND)
 * @param nbt      data components (the stack's component patch in NBT form) that must be present
 * @param exactNbt true: the stack's components must equal {@code nbt}; false: {@code nbt} must be contained in them
 * @param invert   true: stacks matching this rule are blocked
 */
public record FilterEntry(Optional<String> item, List<String> tags, boolean allTags, Optional<CompoundTag> nbt,
                          boolean exactNbt, boolean invert) {
    public static final int MAX_TAGS = 32;

    public static final Codec<FilterEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("item").forGetter(FilterEntry::item),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(FilterEntry::tags),
            Codec.BOOL.optionalFieldOf("all_tags", false).forGetter(FilterEntry::allTags),
            CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(FilterEntry::nbt),
            Codec.BOOL.optionalFieldOf("exact_nbt", false).forGetter(FilterEntry::exactNbt),
            Codec.BOOL.optionalFieldOf("invert", false).forGetter(FilterEntry::invert),
            // Older saves stored a single "target": an id, or a tag starting with '#'. Read only.
            Codec.STRING.optionalFieldOf("target").forGetter(entry -> Optional.empty())
    ).apply(i, (item, tags, allTags, nbt, exact, invert, legacy) -> {
        if (legacy.isPresent() && item.isEmpty() && tags.isEmpty() && !legacy.get().isEmpty()) {
            String target = legacy.get();
            return target.startsWith("#")
                    ? new FilterEntry(Optional.empty(), List.of(target.substring(1)), false, nbt, exact, invert)
                    : new FilterEntry(Optional.of(target), List.of(), false, nbt, exact, invert);
        }
        return new FilterEntry(item, tags, allTags, nbt, exact, invert);
    }));

    public static final StreamCodec<ByteBuf, FilterEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(256)), FilterEntry::item,
            ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(MAX_TAGS)), FilterEntry::tags,
            ByteBufCodecs.BOOL, FilterEntry::allTags,
            ByteBufCodecs.optional(ByteBufCodecs.COMPOUND_TAG), FilterEntry::nbt,
            ByteBufCodecs.BOOL, FilterEntry::exactNbt,
            ByteBufCodecs.BOOL, FilterEntry::invert,
            FilterEntry::new);

    public FilterEntry {
        tags = List.copyOf(tags);
    }

    public static FilterEntry ofItem(String id) {
        return new FilterEntry(Optional.of(id), List.of(), false, Optional.empty(), false, false);
    }

    public static FilterEntry ofTags(boolean all, String... tags) {
        return new FilterEntry(Optional.empty(), List.of(tags), all, Optional.empty(), false, false);
    }

    public FilterEntry withInvert(boolean value) {
        return new FilterEntry(item, tags, allTags, nbt, exactNbt, value);
    }

    public FilterEntry withNbt(Optional<CompoundTag> value) {
        return new FilterEntry(item, tags, allTags, value, exactNbt, invert);
    }

    public boolean isEmpty() {
        return item.isEmpty() && tags.isEmpty() && nbt.isEmpty();
    }

    // ---- validation ---------------------------------------------------------------------------------------

    /** @return a translation key describing what is wrong, or null if the entry can be used on {@code type}. */
    @Nullable
    public String problem(PipeType type) {
        if (isEmpty()) return "gui.flowline.editor.error.empty";
        if (tags.size() > MAX_TAGS) return "gui.flowline.editor.error.too_many_tags";
        if (item.isPresent()) {
            ResourceLocation id = ResourceLocation.tryParse(item.get());
            if (id == null) return "gui.flowline.editor.error.syntax";
            boolean known = type == PipeType.FLUID ? BuiltInRegistries.FLUID.containsKey(id)
                    : BuiltInRegistries.ITEM.containsKey(id);
            if (!known) {
                return type == PipeType.FLUID ? "gui.flowline.editor.error.unknown_fluid"
                        : "gui.flowline.editor.error.unknown_item";
            }
        }
        for (String tag : tags) {
            ResourceLocation id = ResourceLocation.tryParse(tag);
            if (id == null) return "gui.flowline.editor.error.syntax";
            if (!tagExists(type, id)) return "gui.flowline.editor.error.unknown_tag";
        }
        return null;
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
            FluidStack fluid = fluidIn(stack);
            if (fluid.isEmpty()) return null;
            return new FilterEntry(Optional.of(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()), List.of(),
                    false, encode(fluid.getComponentsPatch(), registries), false, false);
        }
        if (type != PipeType.ITEM) return null;
        return new FilterEntry(Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()), List.of(),
                false, encode(stack.getComponentsPatch(), registries), false, false);
    }

    /** The component patch as NBT, or empty when the patch is empty. */
    public static Optional<CompoundTag> encode(DataComponentPatch patch, HolderLookup.Provider registries) {
        if (patch.isEmpty()) return Optional.empty();
        return DataComponentPatch.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), patch)
                .result()
                .filter(tag -> tag instanceof CompoundTag c && !c.isEmpty())
                .map(tag -> (CompoundTag) tag);
    }

    private static FluidStack fluidIn(ItemStack stack) {
        return FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
    }

    // ---- library helpers (tags and components of a sample, all known tags) ------------------------------

    /** The id of the sample (item, or the fluid inside it on fluid pipes), or null if it has none. */
    @Nullable
    public static String idOf(PipeType type, ItemStack sample) {
        if (sample.isEmpty()) return null;
        if (type == PipeType.FLUID) {
            FluidStack fluid = fluidIn(sample);
            return fluid.isEmpty() ? null : BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
        }
        return BuiltInRegistries.ITEM.getKey(sample.getItem()).toString();
    }

    /** Tags the sample (or the fluid inside it) belongs to, sorted. */
    public static List<ResourceLocation> tagsOf(PipeType type, ItemStack sample) {
        Stream<ResourceLocation> tags;
        if (type == PipeType.FLUID) {
            FluidStack fluid = fluidIn(sample);
            if (fluid.isEmpty()) return List.of();
            tags = fluid.getFluidHolder().tags().map(TagKey::location);
        } else {
            tags = sample.getTags().map(TagKey::location);
        }
        return tags.sorted().toList();
    }

    /** Data components of the sample (or the fluid inside it) as NBT; empty compound if it has none. */
    public static CompoundTag componentsOf(PipeType type, ItemStack sample, HolderLookup.Provider registries) {
        if (sample.isEmpty()) return new CompoundTag();
        DataComponentPatch patch = type == PipeType.FLUID ? fluidIn(sample).getComponentsPatch()
                : sample.getComponentsPatch();
        return encode(patch, registries).orElseGet(CompoundTag::new);
    }

    /** Every tag known to the item (or fluid) registry. */
    public static Stream<ResourceLocation> allTags(PipeType type) {
        return type == PipeType.FLUID ? BuiltInRegistries.FLUID.getTagNames().map(TagKey::location)
                : BuiltInRegistries.ITEM.getTagNames().map(TagKey::location);
    }

    public static boolean tagExists(PipeType type, ResourceLocation id) {
        return type == PipeType.FLUID ? BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id)).isPresent()
                : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).isPresent();
    }

    /** Up to {@code limit} display stacks of a tag's members; fluids show as their buckets. */
    public static List<ItemStack> tagMembers(PipeType type, ResourceLocation id, int limit) {
        List<ItemStack> members = new ArrayList<>();
        if (type == PipeType.FLUID) {
            BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id)).ifPresent(set -> set.stream()
                    .map(Holder::value).map(Fluid::getBucket).filter(bucket -> bucket != Items.AIR).distinct()
                    .limit(limit).forEach(bucket -> members.add(new ItemStack(bucket))));
        } else {
            BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(set -> set.stream()
                    .limit(limit).forEach(holder -> members.add(new ItemStack(holder.value()))));
        }
        return members;
    }

    public static int tagSize(PipeType type, ResourceLocation id) {
        return type == PipeType.FLUID
                ? BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id)).map(set -> set.size()).orElse(0)
                : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).map(set -> set.size()).orElse(0);
    }

    // ---- display ------------------------------------------------------------------------------------------

    /** An item that represents the rule in a slot: the item itself, a member of its first tag, or a bucket. */
    public ItemStack displayStack(PipeType type, HolderLookup.Provider registries) {
        ResourceLocation itemId = item.map(ResourceLocation::tryParse).orElse(null);
        ResourceLocation firstTag = tags.isEmpty() ? null : ResourceLocation.tryParse(tags.get(0));
        if (type == PipeType.FLUID) {
            Fluid fluid = Fluids.EMPTY;
            if (itemId != null) {
                fluid = BuiltInRegistries.FLUID.get(itemId);
            } else if (firstTag != null) {
                fluid = first(BuiltInRegistries.FLUID, TagKey.create(Registries.FLUID, firstTag), Fluids.EMPTY);
            }
            Item bucket = fluid.getBucket();
            return new ItemStack(bucket == Items.AIR ? Items.BUCKET : bucket);
        }
        Item shown;
        if (itemId != null) {
            shown = BuiltInRegistries.ITEM.get(itemId);
        } else if (firstTag != null) {
            shown = first(BuiltInRegistries.ITEM, TagKey.create(Registries.ITEM, firstTag), Items.BARRIER);
        } else {
            shown = Items.NAME_TAG;
        }
        ItemStack stack = new ItemStack(shown == Items.AIR ? Items.BARRIER : shown);
        if (itemId != null && nbt.isPresent()) {
            DataComponentPatch.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), nbt.get())
                    .result()
                    .ifPresent(stack::applyComponents);
        }
        return stack;
    }

    private static <T> T first(Registry<T> registry, TagKey<T> tag, T fallback) {
        return registry.getTag(tag)
                .flatMap(set -> set.stream().findFirst())
                .map(Holder::value)
                .orElse(fallback);
    }
}
