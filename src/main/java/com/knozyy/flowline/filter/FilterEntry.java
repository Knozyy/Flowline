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
import net.neoforged.fml.ModList;
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * One filter rule. Every part that is set must match:
 *
 * @param item          an item id (or fluid id for fluid rules)
 * @param tags          tag ids without '#'; the stack must be in any of them, or in all of them when {@code allTags}
 * @param allTags       false: any listed tag is enough (OR); true: every listed tag is required (AND)
 * @param nbt           data components (the stack's component patch in NBT form) that must be present
 * @param exactNbt      true: the stack's components must equal {@code nbt}; false: {@code nbt} must be contained
 * @param invert        true: stacks matching this rule are blocked
 * @param mod           a mod id ("@create"): the stack's registry namespace must be this
 * @param name          a case-insensitive regular expression searched in the stack's display name
 * @param minDurability lowest remaining durability in percent (0..100) an item may have
 * @param maxDurability highest remaining durability in percent (0..100); a range other than 0..100 needs a
 *                      damageable item
 * @param fluid         on universal pipes: the rule is about fluids instead of items (fluid pipes always are)
 */
public record FilterEntry(Optional<String> item, List<String> tags, boolean allTags, Optional<CompoundTag> nbt,
                          boolean exactNbt, boolean invert, Optional<String> mod, Optional<String> name,
                          int minDurability, int maxDurability, boolean fluid) {
    public static final int MAX_TAGS = 32;
    public static final int MAX_TEXT = 128;

    public static final Codec<FilterEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("item").forGetter(FilterEntry::item),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(FilterEntry::tags),
            Codec.BOOL.optionalFieldOf("all_tags", false).forGetter(FilterEntry::allTags),
            CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(FilterEntry::nbt),
            Codec.BOOL.optionalFieldOf("exact_nbt", false).forGetter(FilterEntry::exactNbt),
            Codec.BOOL.optionalFieldOf("invert", false).forGetter(FilterEntry::invert),
            Codec.STRING.optionalFieldOf("mod").forGetter(FilterEntry::mod),
            Codec.STRING.optionalFieldOf("name").forGetter(FilterEntry::name),
            Codec.INT.optionalFieldOf("min_durability", 0).forGetter(FilterEntry::minDurability),
            Codec.INT.optionalFieldOf("max_durability", 100).forGetter(FilterEntry::maxDurability),
            Codec.BOOL.optionalFieldOf("fluid", false).forGetter(FilterEntry::fluid),
            // Older saves stored a single "target": an id, or a tag starting with '#'. Read only.
            Codec.STRING.optionalFieldOf("target").forGetter(entry -> Optional.empty())
    ).apply(i, (item, tags, allTags, nbt, exact, invert, mod, name, min, max, fluid, legacy) -> {
        if (legacy.isPresent() && item.isEmpty() && tags.isEmpty() && !legacy.get().isEmpty()) {
            String target = legacy.get();
            return target.startsWith("#")
                    ? new FilterEntry(Optional.empty(), List.of(target.substring(1)), false, nbt, exact, invert)
                    : new FilterEntry(Optional.of(target), List.of(), false, nbt, exact, invert);
        }
        return new FilterEntry(item, tags, allTags, nbt, exact, invert, mod, name, min, max, fluid);
    }));

    public static final StreamCodec<ByteBuf, FilterEntry> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    /** An item/tag/NBT rule without the newer parts. */
    public FilterEntry(Optional<String> item, List<String> tags, boolean allTags, Optional<CompoundTag> nbt,
                       boolean exactNbt, boolean invert) {
        this(item, tags, allTags, nbt, exactNbt, invert, Optional.empty(), Optional.empty(), 0, 100, false);
    }

    public FilterEntry {
        tags = List.copyOf(tags);
        minDurability = Math.max(0, Math.min(100, minDurability));
        maxDurability = Math.max(0, Math.min(100, maxDurability));
        mod = mod.map(String::trim).filter(m -> !m.isEmpty());
        name = name.filter(n -> !n.isEmpty());
    }

    public static FilterEntry ofMod(String mod) {
        return new FilterEntry(Optional.empty(), List.of(), false, Optional.empty(), false, false, Optional.of(mod),
                Optional.empty(), 0, 100, false);
    }

    public static FilterEntry ofName(String regex) {
        return new FilterEntry(Optional.empty(), List.of(), false, Optional.empty(), false, false, Optional.empty(),
                Optional.of(regex), 0, 100, false);
    }

    public static FilterEntry ofDurability(int min, int max) {
        return new FilterEntry(Optional.empty(), List.of(), false, Optional.empty(), false, false, Optional.empty(),
                Optional.empty(), min, max, false);
    }

    public FilterEntry withFluid(boolean value) {
        return new FilterEntry(item, tags, allTags, nbt, exactNbt, invert, mod, name, minDurability, maxDurability,
                value);
    }

    public boolean hasDurability() {
        return minDurability > 0 || maxDurability < 100;
    }

    /** Whether this rule is about fluids on a pipe of {@code pipe}'s type. */
    public boolean isFluidRule(PipeType pipe) {
        return pipe == PipeType.FLUID || pipe == PipeType.UNIVERSAL && fluid;
    }

    /** The type whose registries this rule uses: {@link PipeType#FLUID} for fluid rules, else {@link PipeType#ITEM}. */
    public PipeType ruleType(PipeType pipe) {
        return isFluidRule(pipe) ? PipeType.FLUID : PipeType.ITEM;
    }

    public static FilterEntry ofItem(String id) {
        return new FilterEntry(Optional.of(id), List.of(), false, Optional.empty(), false, false);
    }

    public static FilterEntry ofTags(boolean all, String... tags) {
        return new FilterEntry(Optional.empty(), List.of(tags), all, Optional.empty(), false, false);
    }

    public FilterEntry withInvert(boolean value) {
        return new FilterEntry(item, tags, allTags, nbt, exactNbt, value, mod, name, minDurability, maxDurability,
                fluid);
    }

    public FilterEntry withNbt(Optional<CompoundTag> value) {
        return new FilterEntry(item, tags, allTags, value, exactNbt, invert, mod, name, minDurability, maxDurability,
                fluid);
    }

    /** Whether both rules select the same stacks (Allow/Block aside); used to reject duplicates. */
    public boolean sameMatch(FilterEntry other) {
        return item.equals(other.item) && java.util.Set.copyOf(tags).equals(java.util.Set.copyOf(other.tags))
                && (tags.size() < 2 || allTags == other.allTags) && nbt.equals(other.nbt)
                && (nbt.isEmpty() || exactNbt == other.exactNbt)
                && mod.equals(other.mod) && name.equals(other.name) && fluid == other.fluid
                && (hasDurability() ? minDurability == other.minDurability && maxDurability == other.maxDurability
                : !other.hasDurability());
    }

    public boolean isEmpty() {
        return item.isEmpty() && tags.isEmpty() && nbt.isEmpty() && mod.isEmpty() && name.isEmpty()
                && !hasDurability();
    }

    // ---- validation ---------------------------------------------------------------------------------------

    /** @return a translation key describing what is wrong, or null if the entry can be used on {@code type}. */
    @Nullable
    public String problem(PipeType pipe) {
        PipeType type = ruleType(pipe);
        if (isEmpty()) return "gui.flowline.editor.error.empty";
        if (mod.isPresent() && !ModList.get().isLoaded(mod.get())) return "gui.flowline.editor.error.unknown_mod";
        if (name.isPresent()) {
            if (name.get().length() > MAX_TEXT) return "gui.flowline.editor.error.regex";
            try {
                Pattern.compile(name.get());
            } catch (PatternSyntaxException e) {
                return "gui.flowline.editor.error.regex";
            }
        }
        if (minDurability > maxDurability) return "gui.flowline.editor.error.durability";
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
        if (!type.movesItems()) return null;
        return new FilterEntry(Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()), List.of(),
                false, encode(stack.getComponentsPatch(), registries), false, false);
    }

    /** A fluid rule matching {@code fluid} exactly (its fluid and its data components). */
    @Nullable
    public static FilterEntry fromFluid(FluidStack fluid, HolderLookup.Provider registries) {
        if (fluid.isEmpty()) return null;
        return new FilterEntry(Optional.of(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()), List.of(),
                false, encode(fluid.getComponentsPatch(), registries), false, false, Optional.empty(), Optional.empty(),
                0, 100, true);
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

    /** The fluid that represents a fluid-pipe rule: its fluid, or a member of its first tag. */
    public Fluid displayFluid() {
        ResourceLocation itemId = item.map(ResourceLocation::tryParse).orElse(null);
        if (itemId != null) return BuiltInRegistries.FLUID.get(itemId);
        ResourceLocation firstTag = tags.isEmpty() ? null : ResourceLocation.tryParse(tags.get(0));
        if (firstTag == null) return Fluids.EMPTY;
        return first(BuiltInRegistries.FLUID, TagKey.create(Registries.FLUID, firstTag), Fluids.EMPTY);
    }

    /** The fluid inside a sample container, or empty. */
    public static Fluid fluidOf(ItemStack sample) {
        return fluidIn(sample).getFluid();
    }

    /** Up to {@code limit} still (source) fluids of a fluid tag. */
    public static List<Fluid> tagFluids(ResourceLocation id, int limit) {
        List<Fluid> fluids = new ArrayList<>();
        BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id)).ifPresent(set -> set.stream()
                .map(Holder::value).filter(fluid -> fluid.isSource(fluid.defaultFluidState()))
                .limit(limit).forEach(fluids::add));
        return fluids;
    }

    /** An item that represents the rule in a slot: the item itself, a member of its first tag, or a bucket. */
    public ItemStack displayStack(PipeType pipe, HolderLookup.Provider registries) {
        PipeType type = ruleType(pipe);
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
        } else if (mod.isPresent()) {
            shown = BuiltInRegistries.ITEM.entrySet().stream()
                    .filter(e -> e.getKey().location().getNamespace().equals(mod.get()))
                    .map(java.util.Map.Entry::getValue).findFirst().orElse(Items.BARRIER);
        } else if (name.isPresent()) {
            shown = Items.NAME_TAG;
        } else if (hasDurability()) {
            shown = Items.IRON_PICKAXE;
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
