package com.knozyy.flowline.filter;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pre-resolved filter rules of one side. Semantics follow Pipez: a stack matching any inverted rule is blocked;
 * otherwise, if there are normal rules, it must match at least one of them; with no normal rules everything passes.
 */
public final class CompiledFilter {
    public static final CompiledFilter ALLOW_ALL = new CompiledFilter(List.of(), List.of());

    private final List<Rule> allow;
    private final List<Rule> deny;

    private CompiledFilter(List<Rule> allow, List<Rule> deny) {
        this.allow = allow;
        this.deny = deny;
    }

    public static CompiledFilter compile(List<FilterEntry> entries) {
        List<Rule> allow = new ArrayList<>();
        List<Rule> deny = new ArrayList<>();
        for (FilterEntry entry : entries) {
            if (entry == null || entry.isEmpty()) continue;
            Rule rule = Rule.of(entry);
            if (rule == null) continue;   // malformed id: ignore the rule
            (entry.invert() ? deny : allow).add(rule);
        }
        return allow.isEmpty() && deny.isEmpty() ? ALLOW_ALL : new CompiledFilter(allow, deny);
    }

    public boolean allowsItem(ItemStack stack, HolderLookup.Provider registries) {
        if (this == ALLOW_ALL) return true;
        Lazy<Tag> data = new Lazy<>(() -> FilterEntry.encode(stack.getComponentsPatch(), registries));
        for (Rule rule : deny) {
            if (rule.matchesItem(stack, data)) return false;
        }
        if (allow.isEmpty()) return true;
        for (Rule rule : allow) {
            if (rule.matchesItem(stack, data)) return true;
        }
        return false;
    }

    public boolean allowsFluid(FluidStack fluid, HolderLookup.Provider registries) {
        if (this == ALLOW_ALL) return true;
        Lazy<Tag> data = new Lazy<>(() -> FilterEntry.encode(fluid.getComponentsPatch(), registries));
        for (Rule rule : deny) {
            if (rule.matchesFluid(fluid, data)) return false;
        }
        if (allow.isEmpty()) return true;
        for (Rule rule : allow) {
            if (rule.matchesFluid(fluid, data)) return true;
        }
        return false;
    }

    /** Encodes a stack's components at most once per check, and only if some rule needs them. */
    private static final class Lazy<T> {
        private final java.util.function.Supplier<Optional<CompoundTag>> supplier;
        private boolean done;
        private CompoundTag value;

        Lazy(java.util.function.Supplier<Optional<CompoundTag>> supplier) {
            this.supplier = supplier;
        }

        CompoundTag get() {
            if (!done) {
                value = supplier.get().orElseGet(CompoundTag::new);
                done = true;
            }
            return value;
        }
    }

    private static final class Rule {
        @Nullable private final ResourceLocation id;
        private final List<ResourceLocation> tags;
        private final boolean allTags;
        @Nullable private final CompoundTag nbt;
        private final boolean exact;
        @Nullable private List<TagKey<Item>> itemTags;
        @Nullable private List<TagKey<Fluid>> fluidTags;

        private Rule(@Nullable ResourceLocation id, List<ResourceLocation> tags, boolean allTags,
                     @Nullable CompoundTag nbt, boolean exact) {
            this.id = id;
            this.tags = tags;
            this.allTags = allTags;
            this.nbt = nbt;
            this.exact = exact;
        }

        @Nullable
        static Rule of(FilterEntry entry) {
            ResourceLocation id = null;
            if (entry.item().isPresent()) {
                id = ResourceLocation.tryParse(entry.item().get());
                if (id == null) return null;
            }
            List<ResourceLocation> tags = new ArrayList<>();
            for (String tag : entry.tags()) {
                ResourceLocation location = ResourceLocation.tryParse(tag);
                if (location == null) return null;
                tags.add(location);
            }
            return new Rule(id, List.copyOf(tags), entry.allTags(), entry.nbt().orElse(null), entry.exactNbt());
        }

        boolean matchesItem(ItemStack stack, Lazy<Tag> data) {
            if (id != null && !id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return false;
            if (!tags.isEmpty()) {
                if (itemTags == null) itemTags = tags.stream().map(t -> TagKey.create(Registries.ITEM, t)).toList();
                if (allTags ? !itemTags.stream().allMatch(stack::is) : itemTags.stream().noneMatch(stack::is)) {
                    return false;
                }
            }
            return matchesNbt(data);
        }

        boolean matchesFluid(FluidStack fluid, Lazy<Tag> data) {
            if (id != null && !id.equals(BuiltInRegistries.FLUID.getKey(fluid.getFluid()))) return false;
            if (!tags.isEmpty()) {
                if (fluidTags == null) fluidTags = tags.stream().map(t -> TagKey.create(Registries.FLUID, t)).toList();
                if (allTags ? !fluidTags.stream().allMatch(fluid::is) : fluidTags.stream().noneMatch(fluid::is)) {
                    return false;
                }
            }
            return matchesNbt(data);
        }

        private boolean matchesNbt(Lazy<Tag> data) {
            if (nbt == null) return true;
            CompoundTag actual = data.get();
            return exact ? nbt.equals(actual) : contains(nbt, actual);
        }
    }

    /** True if every key/value in {@code expected} is present in {@code actual}; lists match element-wise "any". */
    static boolean contains(Tag expected, Tag actual) {
        if (expected instanceof CompoundTag e) {
            if (!(actual instanceof CompoundTag a)) return false;
            for (String key : e.getAllKeys()) {
                Tag value = a.get(key);
                if (value == null || !contains(e.get(key), value)) return false;
            }
            return true;
        }
        if (expected instanceof ListTag e) {
            if (!(actual instanceof ListTag a)) return false;
            for (Tag element : e) {
                boolean found = false;
                for (Tag candidate : a) {
                    if (contains(element, candidate)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }
        return expected.equals(actual);
    }
}
