package com.knozyy.flowline.pipe;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.compat.ChemicalCompat;
import com.knozyy.flowline.pipe.transfer.EnergyTransfer;
import com.knozyy.flowline.pipe.transfer.FluidTransfer;
import com.knozyy.flowline.pipe.transfer.ItemTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

public enum PipeType implements StringRepresentable {
    ITEM("item", true, false, false, false),
    FLUID("fluid", false, true, false, false),
    ENERGY("energy", false, false, true, false),
    /** Moves items, fluids and energy at once. Rules are item rules unless marked as fluid rules. */
    UNIVERSAL("universal", true, true, true, false),
    /** Mekanism chemicals (gases, infuse types, pigments, slurries). Only registered when Mekanism is loaded. */
    CHEMICAL("chemical", false, false, false, true);

    /** Channel bits of {@link SideConfig#channels}. */
    public static final int CH_ITEMS = 1, CH_FLUIDS = 2, CH_ENERGY = 4;
    public static final int ALL_CHANNELS = CH_ITEMS | CH_FLUIDS | CH_ENERGY;

    private final String name;
    private final boolean items, fluids, energy, chemicals;

    PipeType(String name, boolean items, boolean fluids, boolean energy, boolean chemicals) {
        this.name = name;
        this.items = items;
        this.fluids = fluids;
        this.energy = energy;
        this.chemicals = chemicals;
    }

    public boolean movesItems() {
        return items;
    }

    public boolean movesFluids() {
        return fluids;
    }

    public boolean movesEnergy() {
        return energy;
    }

    public boolean movesChemicals() {
        return chemicals;
    }

    /** Whether rule filters exist on this pipe (energy and chemicals have nothing to filter). */
    public boolean hasFilter() {
        return items || fluids;
    }

    /** Whether filter rules name fluids (fluid pipes) rather than items. */
    public boolean filtersFluids() {
        return this == FLUID;
    }

    /** Whether sides of this pipe can switch its kinds on and off one by one. */
    public boolean hasChannels() {
        return this == UNIVERSAL;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Whether the block at {@code pos} exposes any capability this pipe moves on {@code access}. */
    public boolean hasEndpoint(Level level, BlockPos pos, Direction access) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        return items && be.getCapability(ForgeCapabilities.ITEM_HANDLER, access).isPresent()
                || fluids && be.getCapability(ForgeCapabilities.FLUID_HANDLER, access).isPresent()
                || energy && be.getCapability(ForgeCapabilities.ENERGY, access).isPresent()
                || chemicals && ChemicalCompat.hasHandler(be, access);
    }

    /**
     * Runs one operation for every kind this pipe moves.
     *
     * @param elapsed ticks since the previous operation, for rate limits
     * @param onItem  told about the first item stack moved to each target (for the travel animation); may be null
     * @return how much was moved in total (items + mB + FE); 0 means the operation found no work
     */
    public long transfer(Level level, BlockPos sourcePos, Caps source, SideConfig cfg, List<PipeNetwork.Target> targets,
                         int elapsed, @Nullable BiConsumer<PipeNetwork.Target, ItemStack> onItem) {
        int multiplier = Pacing.stackMultiplier(cfg.stackCount);
        boolean balanced = cfg.distribution == Distribution.BALANCED;
        long moved = 0;
        if (items && cfg.channel(CH_ITEMS, this)) {
            moved += ItemTransfer.run(level, sourcePos, source, cfg, targets,
                    Pacing.itemsPerOperation(cfg.stackCount), balanced, this, onItem);
        }
        if (fluids && cfg.channel(CH_FLUIDS, this)) {
            moved += FluidTransfer.run(level, source, cfg, targets,
                    Pacing.fluidPerOperation(cfg.stackCount), balanced, this);
        }
        if (energy && cfg.channel(CH_ENERGY, this)) {
            long budget = (long) FlowlineConfig.ENERGY_PER_OPERATION.get() * multiplier;
            if (cfg.rate > 0) budget = Math.min(budget, (long) cfg.rate * Math.max(1, elapsed));
            moved += EnergyTransfer.run(source, cfg, targets, (int) Math.min(Integer.MAX_VALUE, budget), balanced, this);
        }
        if (chemicals) {
            moved += ChemicalCompat.transfer(source, targets,
                    Pacing.chemicalPerOperation(cfg.stackCount), balanced);
        }
        return moved;
    }
}
