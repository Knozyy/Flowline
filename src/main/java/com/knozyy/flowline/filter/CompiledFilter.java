package com.knozyy.flowline.filter;

import com.knozyy.flowline.pipe.PipeType;
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
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Pre-resolved filter rules of one side. Semantics follow Pipez: a stack matching any inverted rule is blocked;
 * otherwise, if there are normal rules, it must match at least one of them; with no normal rules everything passes.
 * Item rules and fluid rules are kept apart, so on universal pipes each only affects its own kind.
 */
public final class CompiledFilter {
    public static final CompiledFilter ALLOW_ALL = new CompiledFilter(List.of(), List.of(), List.of(), List.of());

    private final List<Rule> itemAllow;
    private final List<Rule> itemDeny;
    private final List<Rule> fluidAllow;
    private final List<Rule> fluidDeny;

    private CompiledFilter(List<Rule> itemAllow, List<Rule> itemDeny, List<Rule> fluidAllow, List<Rule> fluidDeny) {
        this.itemAllow = itemAllow;
        this.itemDeny = itemDeny;
        this.fluidAllow = fluidAllow;
        this.fluidDeny = fluidDeny;
    }

    public static CompiledFilter compile(List<FilterEntry> entries, PipeType pipe) {
        List<Rule> itemAllow = new ArrayList<>();
        List<Rule> itemDeny = new ArrayList<>();
        List<Rule> fluidAllow = new ArrayList<>();
        List<Rule> fluidDeny = new ArrayList<>();
        for (FilterEntry entry : entries) {
            if (entry == null || entry.isEmpty()) continue;
            Rule rule = Rule.of(entry);
            if (rule == null) continue;   // malformed id or pattern: ignore the rule
            boolean fluid = entry.isFluidRule(pipe);
            if (entry.invert()) {
                (fluid ? fluidDeny : itemDeny).add(rule);
            } else {
                (fluid ? fluidAllow : itemAllow).add(rule);
            }
        }
        return entries.stream().allMatch(e -> e == null || e.isEmpty()) ? ALLOW_ALL
                : new CompiledFilter(itemAllow, itemDeny, fluidAllow, fluidDeny);
    }

    public boolean isEmpty() {
        return itemAllow.isEmpty() && itemDeny.isEmpty() && fluidAllow.isEmpty() && fluidDeny.isEmpty();
    }

    public boolean allowsItem(ItemStack stack) {
        if (itemAllow.isEmpty() && itemDeny.isEmpty()) return true;
        Lazy data = new Lazy(() -> FilterEntry.encode(stack.getTag()));
        for (Rule rule : itemDeny) {
            if (rule.matchesItem(stack, data)) return false;
        }
        if (itemAllow.isEmpty()) return true;
        for (Rule rule : itemAllow) {
            if (rule.matchesItem(stack, data)) return true;
        }
        return false;
    }

    public boolean allowsFluid(FluidStack fluid) {
        if (fluidAllow.isEmpty() && fluidDeny.isEmpty()) return true;
        Lazy data = new Lazy(() -> FilterEntry.encode(fluid.getTag()));
        for (Rule rule : fluidDeny) {
            if (rule.matchesFluid(fluid, data)) return false;
        }
        if (fluidAllow.isEmpty()) return true;
        for (Rule rule : fluidAllow) {
            if (rule.matchesFluid(fluid, data)) return true;
        }
        return false;
    }

    /** Encodes a stack's components at most once per check, and only if some rule needs them. */
    private static final class Lazy {
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
        @Nullable private final String mod;
        @Nullable private final Pattern name;
        private final int minDurability;
        private final int maxDurability;
        private final boolean durability;
        @Nullable private List<TagKey<Item>> itemTags;
        @Nullable private List<TagKey<Fluid>> fluidTags;

        private Rule(@Nullable ResourceLocation id, List<ResourceLocation> tags, boolean allTags,
                     @Nullable CompoundTag nbt, boolean exact, @Nullable String mod, @Nullable Pattern name,
                     int minDurability, int maxDurability, boolean durability) {
            this.id = id;
            this.tags = tags;
            this.allTags = allTags;
            this.nbt = nbt;
            this.exact = exact;
            this.mod = mod;
            this.name = name;
            this.minDurability = minDurability;
            this.maxDurability = maxDurability;
            this.durability = durability;
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
            Pattern name = null;
            if (entry.name().isPresent()) {
                try {
                    name = Pattern.compile(entry.name().get(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                } catch (PatternSyntaxException e) {
                    return null;
                }
            }
            return new Rule(id, List.copyOf(tags), entry.allTags(), entry.nbt().orElse(null), entry.exactNbt(),
                    entry.mod().orElse(null), name, entry.minDurability(), entry.maxDurability(),
                    entry.hasDurability());
        }

        boolean matchesItem(ItemStack stack, Lazy data) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && !id.equals(key)) return false;
            if (mod != null && !mod.equals(key.getNamespace())) return false;
            if (!tags.isEmpty()) {
                if (itemTags == null) itemTags = tags.stream().map(t -> TagKey.create(Registries.ITEM, t)).toList();
                if (allTags ? !itemTags.stream().allMatch(stack::is) : itemTags.stream().noneMatch(stack::is)) {
                    return false;
                }
            }
            if (durability) {
                if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return false;
                int percent = (stack.getMaxDamage() - stack.getDamageValue()) * 100 / stack.getMaxDamage();
                if (percent < minDurability || percent > maxDurability) return false;
            }
            if (name != null && !name.matcher(stack.getHoverName().getString()).find()) return false;
            return matchesNbt(data);
        }

        boolean matchesFluid(FluidStack fluid, Lazy data) {
            ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            if (id != null && !id.equals(key)) return false;
            if (mod != null && !mod.equals(key.getNamespace())) return false;
            if (!tags.isEmpty()) {
                if (fluidTags == null) fluidTags = tags.stream().map(t -> TagKey.create(Registries.FLUID, t)).toList();
                Fluid f = fluid.getFluid();
                if (allTags ? !fluidTags.stream().allMatch(f::is) : fluidTags.stream().noneMatch(f::is)) {
                    return false;
                }
            }
            if (name != null && !name.matcher(fluid.getDisplayName().getString()).find()) return false;
            return matchesNbt(data);
        }

        private boolean matchesNbt(Lazy data) {
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
