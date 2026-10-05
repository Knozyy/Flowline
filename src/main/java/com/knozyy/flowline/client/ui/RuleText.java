package com.knozyy.flowline.client.ui;

import com.knozyy.flowline.compat.ChemicalCompat;
import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Plain-words descriptions of filter rules, for the rule list and the rule editor's summary. */
public final class RuleText {
    private RuleText() {}

    /** The rule's main line: its item or fluid, else its first tag, mod, name pattern or durability range. */
    public static Component title(FilterEntry rule, PipeType pipe, HolderLookup.Provider registries) {
        if (rule.item().isPresent() && rule.isChemicalRule(pipe)) {
            ResourceLocation id = ResourceLocation.tryParse(rule.item().get());
            return id == null ? Component.literal(rule.item().get()) : ChemicalCompat.name(id);
        }
        if (rule.item().isPresent()) {
            return rule.isFluidRule(pipe) ? rule.displayFluid().getFluidType().getDescription()
                    : rule.displayStack(pipe, registries).getHoverName();
        }
        if (!rule.tags().isEmpty()) {
            String more = rule.tags().size() > 1 ? " +" + (rule.tags().size() - 1) : "";
            return Component.literal(tag(rule.tags().get(0)) + more);
        }
        if (rule.mod().isPresent()) return Component.literal("@" + rule.mod().get());
        if (rule.name().isPresent()) return Component.literal("“" + rule.name().get() + "”");
        if (rule.hasDurability()) {
            return Component.translatable("gui.flowline.rule.durability", rule.minDurability(), rule.maxDurability());
        }
        return Component.translatable("gui.flowline.text.data_only");
    }

    /** The second line: what kind of rule it is, then the conditions the title does not show. */
    public static Component detail(FilterEntry rule, PipeType pipe, boolean extracting) {
        List<Component> parts = new ArrayList<>();
        boolean titleIsTag = rule.item().isEmpty() && !rule.tags().isEmpty();
        boolean titleIsMod = rule.item().isEmpty() && rule.tags().isEmpty() && rule.mod().isPresent();
        boolean titleIsName = rule.item().isEmpty() && rule.tags().isEmpty() && rule.mod().isEmpty()
                && rule.name().isPresent();
        if (rule.item().isPresent()) {
            parts.add(Component.translatable(rule.isChemicalRule(pipe) ? "gui.flowline.text.kind_chemical"
                    : rule.isFluidRule(pipe) ? "gui.flowline.text.kind_fluid" : "gui.flowline.text.kind_item"));
        } else if (titleIsTag) {
            parts.add(Component.translatable(rule.allTags() ? "gui.flowline.text.tags_all" : "gui.flowline.text.tags_any"));
        } else if (titleIsMod) {
            parts.add(Component.translatable("gui.flowline.text.mod_all"));
        } else if (titleIsName) {
            parts.add(Component.translatable("gui.flowline.text.name"));
        }
        if (rule.item().isPresent() && !rule.tags().isEmpty()) parts.add(Component.literal(tag(rule.tags().get(0))));
        if (rule.mod().isPresent() && !titleIsMod) parts.add(Component.literal("@" + rule.mod().get()));
        if (rule.name().isPresent() && !titleIsName) {
            parts.add(Component.translatable("gui.flowline.text.name_value", rule.name().get()));
        }
        boolean titleIsDurability = rule.item().isEmpty() && rule.tags().isEmpty() && rule.mod().isEmpty()
                && rule.name().isEmpty();
        if (rule.hasDurability() && !titleIsDurability) {
            parts.add(Component.translatable("gui.flowline.text.durability", rule.minDurability(), rule.maxDurability()));
        }
        if (rule.nbt().isPresent()) {
            parts.add(Component.translatable(rule.exactNbt() ? "gui.flowline.text.data_exact"
                    : "gui.flowline.text.data_contains"));
        }
        if (rule.amount() > 0 && !rule.invert()) {
            parts.add(Component.translatable(extracting ? "gui.flowline.text.keep" : "gui.flowline.text.max",
                    rule.amount()));
        }
        return join(parts, Component.literal(" · "));
    }

    /** Every condition of the rule in one line, for the editor's summary. Empty when it has none. */
    public static Component conditions(FilterEntry rule, PipeType pipe, HolderLookup.Provider registries) {
        List<Component> parts = new ArrayList<>();
        if (rule.item().isPresent()) parts.add(title(rule.withNbt(java.util.Optional.empty()), pipe, registries));
        if (!rule.tags().isEmpty()) {
            List<Component> tags = rule.tags().stream().map(t -> (Component) Component.literal(tag(t))).toList();
            parts.add(join(tags, Component.translatable(rule.allTags() ? "gui.flowline.text.and"
                    : "gui.flowline.text.or")));
        }
        rule.mod().ifPresent(mod -> parts.add(Component.literal("@" + mod)));
        rule.name().ifPresent(name -> parts.add(Component.translatable("gui.flowline.text.name_value", name)));
        if (rule.hasDurability() && !rule.isFluidRule(pipe)) {
            parts.add(Component.translatable("gui.flowline.text.durability", rule.minDurability(), rule.maxDurability()));
        }
        if (rule.nbt().isPresent()) {
            parts.add(Component.translatable(rule.exactNbt() ? "gui.flowline.text.data_exact"
                    : "gui.flowline.text.data_contains"));
        }
        return join(parts, Component.literal(" · "));
    }

    /** "#logs" for vanilla tags, "#create:crushed_ores" for others. */
    public static String tag(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return "#" + id;
        return "#" + (location.getNamespace().equals("minecraft") ? location.getPath() : location.toString());
    }

    private static Component join(List<Component> parts, Component separator) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(separator);
            out.append(parts.get(i));
        }
        return out;
    }
}
