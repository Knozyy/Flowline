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
            if (entry == null) continue;
            ResourceLocation location = entry.location();
            if (!entry.target().isEmpty() && location == null) continue;   // malformed: ignore
            if (entry.target().isEmpty() && entry.nbt().isEmpty()) continue;
            Rule rule = new Rule(location, entry.isTag(), entry.nbt().orElse(null), entry.exactNbt());
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
        @Nullable private final ResourceLocation location;
        private final boolean tag;
        @Nullable private final CompoundTag nbt;
        private final boolean exact;
        @Nullable private TagKey<Item> itemTag;
        @Nullable private TagKey<Fluid> fluidTag;

        Rule(@Nullable ResourceLocation location, boolean tag, @Nullable CompoundTag nbt, boolean exact) {
            this.location = location;
            this.tag = tag;
            this.nbt = nbt;
            this.exact = exact;
        }

        boolean matchesItem(ItemStack stack, Lazy<Tag> data) {
            if (location != null) {
                if (tag) {
                    if (itemTag == null) itemTag = TagKey.create(Registries.ITEM, location);
                    if (!stack.is(itemTag)) return false;
                } else if (!location.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                    return false;
                }
            }
            return matchesNbt(data);
        }

        boolean matchesFluid(FluidStack fluid, Lazy<Tag> data) {
            if (location != null) {
                if (tag) {
                    if (fluidTag == null) fluidTag = TagKey.create(Registries.FLUID, location);
                    if (!fluid.is(fluidTag)) return false;
                } else if (!location.equals(BuiltInRegistries.FLUID.getKey(fluid.getFluid()))) {
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
