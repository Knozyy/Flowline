package com.knozyy.flowline.compat;

import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeNetwork;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.transfer.ItemTransfer;
import com.knozyy.flowline.util.Stacks;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Curvy Pipes line sees when one of its ends sits on a Flowline pipe: a door into that pipe's network. Taking
 * from it pulls from the blocks the network's Extract sides face, under those sides' filters and "keep" amounts;
 * giving to it hands the resource to the network's Insert targets, under their filters, limits and the order of the
 * face's distribution. So a Curvy line between two Flowline networks is configured entirely in Flowline's screens
 * (one of its ends still has to be set to Extract in Curvy's own menu, since Curvy only moves from active ends).
 *
 * <p>Only Curvy gets these ports, through its inventory lookups; other mods still see a Flowline pipe as having no
 * inventory.
 */
public final class CurvyPort {
    private CurvyPort() {}

    /** Curvy's lookup of {@code cap} on {@code be}: a port for Flowline pipes, the block's own capability otherwise. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static LazyOptional capability(BlockEntity be, Capability cap, @Nullable Direction side) {
        if (be instanceof PipeBlockEntity pipe && side != null && pipe.getLevel() instanceof ServerLevel level) {
            Object port = port(level, pipe, side, cap);
            return port == null ? LazyOptional.empty() : LazyOptional.of(() -> port);
        }
        return be.getCapability(cap, side);
    }

    /** The port of one face of a pipe for {@code cap}, or null if the pipe does not move that kind. */
    @Nullable
    public static Object port(ServerLevel level, PipeBlockEntity pipe, Direction face, Capability<?> cap) {
        PipeType type = pipe.type();
        if (cap == ForgeCapabilities.ITEM_HANDLER && type.movesItems()) return new Items(level, pipe, face);
        if (cap == ForgeCapabilities.FLUID_HANDLER && type.movesFluids()) return new Fluids(level, pipe, face);
        if (cap == ForgeCapabilities.ENERGY && type.movesEnergy()) return new Energy(level, pipe, face);
        return null;
    }

    private abstract static class Port {
        final ServerLevel level;
        final PipeBlockEntity pipe;
        final Direction face;
        final PipeType type;

        Port(ServerLevel level, PipeBlockEntity pipe, Direction face) {
            this.level = level;
            this.pipe = pipe;
            this.face = face;
            this.type = pipe.type();
        }

        /** The face's own settings: its filter decides what may enter, its distribution the order of targets. */
        SideConfig faceCfg() {
            return pipe.side(face);
        }

        List<PipeNetwork.Target> targets() {
            return PipeNetwork.targets(level, pipe.getBlockPos(), face, type, faceCfg());
        }

        List<PipeNetwork.Source> sources(int channel) {
            List<PipeNetwork.Source> sources = new ArrayList<>();
            for (PipeNetwork.Source source : PipeNetwork.sources(level, pipe.getBlockPos(), type)) {
                if (source.cfg().channel(channel, type)) sources.add(source);
            }
            return sources;
        }
    }

    /** Items: the network's sources' slots one after another, then one always-empty inlet slot. */
    static final class Items extends Port implements IItemHandler {
        private record Slot(IItemHandler handler, int slot, SideConfig cfg) {}

        private final List<Slot> slots = new ArrayList<>();

        Items(ServerLevel level, PipeBlockEntity pipe, Direction face) {
            super(level, pipe, face);
            for (PipeNetwork.Source source : sources(PipeType.CH_ITEMS)) {
                IItemHandler handler = source.caps().itemHandler();
                if (handler == null) continue;
                for (int i = 0; i < handler.getSlots(); i++) slots.add(new Slot(handler, i, source.cfg()));
            }
        }

        @Override
        public int getSlots() {
            return slots.size() + 1;
        }

        @Nullable
        private Slot at(int index) {
            return index >= 0 && index < slots.size() ? slots.get(index) : null;
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            Slot slot = at(index);
            if (slot == null) return ItemStack.EMPTY;
            ItemStack stack = slot.handler().getStackInSlot(slot.slot());
            return slot.cfg().allowsItem(stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int index, int amount, boolean simulate) {
            Slot slot = at(index);
            if (slot == null || amount <= 0) return ItemStack.EMPTY;
            ItemStack offered = slot.handler().extractItem(slot.slot(), amount, true);
            if (offered.isEmpty() || !slot.cfg().allowsItem(offered)) return ItemStack.EMPTY;
            int keep = slot.cfg().limitFor(offered);
            if (keep > 0) {
                long spare = ItemTransfer.count(slot.handler(), offered) - keep;
                if (spare <= 0) return ItemStack.EMPTY;
                amount = (int) Math.min(amount, spare);
            }
            return slot.handler().extractItem(slot.slot(), Math.min(amount, offered.getCount()), simulate);
        }

        /** Whatever slot Curvy aims at, a stack given to the port goes into the network. */
        @Override
        public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !faceCfg().allowsItem(stack)) return stack;
            ItemStack rest = stack.copy();
            for (PipeNetwork.Target target : targets()) {
                IItemHandler handler = target.caps().itemHandler();
                SideConfig insert = target.insert();
                if (handler == null || !insert.channel(PipeType.CH_ITEMS, type) || !insert.allowsItem(rest)) continue;
                int want = rest.getCount();
                int max = insert.limitFor(rest);
                if (max > 0) want = (int) Math.max(0, Math.min(want, max - ItemTransfer.count(handler, rest)));
                if (want <= 0) continue;
                ItemStack left = ItemHandlerHelper.insertItemStacked(handler, Stacks.withCount(rest, want), simulate);
                rest.shrink(want - left.getCount());
                if (rest.isEmpty()) break;
            }
            return rest;
        }

        @Override
        public int getSlotLimit(int index) {
            Slot slot = at(index);
            return slot == null ? 64 : slot.handler().getSlotLimit(slot.slot());
        }

        @Override
        public boolean isItemValid(int index, ItemStack stack) {
            return true;
        }
    }

    /** Fluids: the network's sources' tanks one after another, then one always-empty inlet tank. */
    static final class Fluids extends Port implements IFluidHandler {
        private record Tank(IFluidHandler handler, int tank, SideConfig cfg) {}

        private final List<Tank> tanks = new ArrayList<>();

        Fluids(ServerLevel level, PipeBlockEntity pipe, Direction face) {
            super(level, pipe, face);
            for (PipeNetwork.Source source : sources(PipeType.CH_FLUIDS)) {
                IFluidHandler handler = source.caps().fluidHandler();
                if (handler == null) continue;
                for (int i = 0; i < handler.getTanks(); i++) tanks.add(new Tank(handler, i, source.cfg()));
            }
        }

        @Override
        public int getTanks() {
            return tanks.size() + 1;
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            if (index < 0 || index >= tanks.size()) return FluidStack.EMPTY;
            Tank tank = tanks.get(index);
            FluidStack fluid = tank.handler().getFluidInTank(tank.tank());
            return tank.cfg().allowsFluid(fluid) ? fluid : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int index) {
            if (index < 0 || index >= tanks.size()) return Integer.MAX_VALUE;
            return tanks.get(index).handler().getTankCapacity(tanks.get(index).tank());
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return true;
        }

        /** A fluid given to the port goes into the network. */
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !faceCfg().allowsFluid(resource)) return 0;
            int rest = resource.getAmount();
            for (PipeNetwork.Target target : targets()) {
                IFluidHandler handler = target.caps().fluidHandler();
                SideConfig insert = target.insert();
                if (handler == null || !insert.channel(PipeType.CH_FLUIDS, type) || !insert.allowsFluid(resource)) continue;
                int want = rest;
                int max = insert.limitFor(resource);
                if (max > 0) want = (int) Math.max(0, Math.min(want, max - amountOf(handler, resource)));
                if (want <= 0) continue;
                rest -= handler.fill(new FluidStack(resource, want), action);
                if (rest <= 0) break;
            }
            return resource.getAmount() - rest;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            int rest = resource.getAmount();
            List<IFluidHandler> seen = new ArrayList<>();
            for (Tank tank : tanks) {
                if (rest <= 0) break;
                if (seen.contains(tank.handler()) || !tank.cfg().allowsFluid(resource)) continue;
                seen.add(tank.handler());
                int can = rest;
                int keep = tank.cfg().limitFor(resource);
                if (keep > 0) can = (int) Math.max(0, Math.min(can, amountOf(tank.handler(), resource) - keep));
                if (can <= 0) continue;
                rest -= tank.handler().drain(new FluidStack(resource, can), action).getAmount();
            }
            int drained = resource.getAmount() - rest;
            return drained <= 0 ? FluidStack.EMPTY : new FluidStack(resource, drained);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < tanks.size(); i++) {
                FluidStack fluid = getFluidInTank(i);
                if (!fluid.isEmpty()) return drain(new FluidStack(fluid, maxDrain), action);
            }
            return FluidStack.EMPTY;
        }

        private static long amountOf(IFluidHandler handler, FluidStack like) {
            long amount = 0;
            for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack fluid = handler.getFluidInTank(i);
                if (fluid.isFluidEqual(like)) amount += fluid.getAmount();
            }
            return amount;
        }
    }

    /** Energy: takes from the network's sources (above their "keep" amount and rate), gives to its targets. */
    static final class Energy extends Port implements IEnergyStorage {
        Energy(ServerLevel level, PipeBlockEntity pipe, Direction face) {
            super(level, pipe, face);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int rest = maxReceive;
            for (PipeNetwork.Target target : targets()) {
                IEnergyStorage storage = target.caps().energyStorage();
                if (storage == null || !storage.canReceive() || !target.insert().channel(PipeType.CH_ENERGY, type)) continue;
                rest -= storage.receiveEnergy(rest, simulate);
                if (rest <= 0) break;
            }
            return maxReceive - rest;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int rest = maxExtract;
            for (PipeNetwork.Source source : sources(PipeType.CH_ENERGY)) {
                IEnergyStorage storage = source.caps().energyStorage();
                if (storage == null || !storage.canExtract()) continue;
                SideConfig cfg = source.cfg();
                int can = rest;
                if (cfg.limit > 0) can = Math.min(can, Math.max(0, storage.getEnergyStored() - cfg.limit));
                if (cfg.rate > 0) can = Math.min(can, cfg.rate);
                if (can <= 0) continue;
                rest -= storage.extractEnergy(can, simulate);
                if (rest <= 0) break;
            }
            return maxExtract - rest;
        }

        @Override
        public int getEnergyStored() {
            long stored = 0;
            for (PipeNetwork.Source source : sources(PipeType.CH_ENERGY)) {
                IEnergyStorage storage = source.caps().energyStorage();
                if (storage != null) stored += storage.getEnergyStored();
            }
            return (int) Math.min(Integer.MAX_VALUE, stored);
        }

        @Override
        public int getMaxEnergyStored() {
            long capacity = 0;
            for (PipeNetwork.Source source : sources(PipeType.CH_ENERGY)) {
                IEnergyStorage storage = source.caps().energyStorage();
                if (storage != null) capacity += storage.getMaxEnergyStored();
            }
            return (int) Math.max(1, Math.min(Integer.MAX_VALUE, capacity));
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
