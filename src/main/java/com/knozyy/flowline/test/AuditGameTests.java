package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.item.ConfigCardItem;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.network.FilterSyncPayload;
import com.knozyy.flowline.network.TravelPayload;
import com.knozyy.flowline.pipe.*;
import com.knozyy.flowline.pipe.transfer.*;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModItems;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class AuditGameTests {
    private static final BlockPos SOURCE = new BlockPos(0, 1, 1);
    private static final BlockPos PIPE = new BlockPos(1, 1, 1);
    private static final BlockPos DESTINATION = new BlockPos(2, 1, 1);

    private static final class ResourceChest extends ChestBlockEntity {
        private final LazyOptional<IItemHandler> items;
        private final LazyOptional<IFluidHandler> fluids;
        private final LazyOptional<IEnergyStorage> energy;

        ResourceChest(BlockPos pos, IItemHandler items, IFluidHandler fluids, IEnergyStorage energy) {
            super(pos, Blocks.CHEST.defaultBlockState());
            this.items = items == null ? LazyOptional.empty() : LazyOptional.of(() -> items);
            this.fluids = fluids == null ? LazyOptional.empty() : LazyOptional.of(() -> fluids);
            this.energy = energy == null ? LazyOptional.empty() : LazyOptional.of(() -> energy);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            if (cap == ForgeCapabilities.ITEM_HANDLER) return items.cast();
            if (cap == ForgeCapabilities.FLUID_HANDLER) return fluids.cast();
            if (cap == ForgeCapabilities.ENERGY) return energy.cast();
            return super.getCapability(cap, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            items.invalidate();
            fluids.invalidate();
            energy.invalidate();
        }
    }

    private static Caps resources(GameTestHelper h, BlockPos pos, PipeType type,
                                  IItemHandler items, IFluidHandler fluids, IEnergyStorage energy) {
        h.setBlock(pos, Blocks.CHEST);
        BlockPos absolute = h.absolutePos(pos);
        h.getLevel().removeBlockEntity(absolute);
        h.getLevel().setBlockEntity(new ResourceChest(absolute, items, fluids, energy));
        return Caps.create(type, h.getLevel(), absolute, Direction.UP);
    }

    private static PipeBlockEntity pipe(GameTestHelper h) {
        return pipe(h, PipeType.ITEM);
    }

    private static PipeBlockEntity pipe(GameTestHelper h, PipeType type) {
        h.setBlock(PIPE, type == PipeType.FLUID ? ModBlocks.FLUID_PIPE.get() : ModBlocks.ITEM_PIPE.get());
        return (PipeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(PIPE));
    }

    private static PipeNetwork.Target target(PipeBlockEntity pipe, Caps caps) {
        return new PipeNetwork.Target(pipe.getBlockPos(), Direction.EAST, 0, caps, pipe, List.of());
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void returnedItemsAreNotReportedAsTransferred(GameTestHelper h) {
        ItemStackHandler source = new ItemStackHandler(1);
        ItemStackHandler destination = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return simulate ? super.insertItem(slot, stack, true) : stack;
            }
        };
        source.setStackInSlot(0, new ItemStack(Items.DIAMOND, 8));
        Caps from = resources(h, SOURCE, PipeType.ITEM, source, null, null);
        Caps to = resources(h, DESTINATION, PipeType.ITEM, destination, null, null);
        int[] animations = {0};
        int moved = ItemTransfer.run(h.getLevel(), h.absolutePos(SOURCE), from, new SideConfig(PipeType.ITEM),
                List.of(target(pipe(h), to)), 8, false, PipeType.ITEM, (t, stack) -> animations[0]++);
        h.assertTrue(moved == 0 && animations[0] == 0, "rolled-back items must not consume the budget or animate");
        h.assertTrue(source.getStackInSlot(0).getCount() == 8 && destination.getStackInSlot(0).isEmpty(),
                "all rejected items must return to the source");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void partialItemAcceptancePreservesBudgetForOtherTargets(GameTestHelper h) {
        ItemStackHandler source = new ItemStackHandler(1);
        ItemStackHandler partial = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (simulate) return super.insertItem(slot, stack, true);
                int accepted = Math.min(2, stack.getCount());
                ItemStack offered = stack.copy(); offered.setCount(accepted);
                ItemStack rest = super.insertItem(slot, offered, false);
                ItemStack result = stack.copy(); result.shrink(accepted - rest.getCount()); return result;
            }
        };
        ItemStackHandler finalTarget = new ItemStackHandler(1);
        source.setStackInSlot(0, new ItemStack(Items.DIAMOND, 8));
        Caps from = resources(h, SOURCE, PipeType.ITEM, source, null, null);
        Caps first = resources(h, DESTINATION, PipeType.ITEM, partial, null, null);
        Caps second = resources(h, new BlockPos(3, 1, 1), PipeType.ITEM, finalTarget, null, null);
        PipeBlockEntity pipe = pipe(h);
        int moved = ItemTransfer.run(h.getLevel(), h.absolutePos(SOURCE), from, new SideConfig(PipeType.ITEM),
                List.of(target(pipe, first), target(pipe, second)), 8, false, PipeType.ITEM, null);
        h.assertTrue(moved == 8 && partial.getStackInSlot(0).getCount() == 2
                && finalTarget.getStackInSlot(0).getCount() == 6, "only actual acceptance spends the shared budget");
        h.assertTrue(source.getStackInSlot(0).isEmpty(), "all eight items must arrive, without duplication");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void regulatorsCountLargeInventoriesWithoutOverflow(GameTestHelper h) {
        ItemStackHandler source = new ItemStackHandler(2), destination = new ItemStackHandler(1);
        source.setStackInSlot(0, new ItemStack(Items.DIAMOND, 1_500_000_000));
        source.setStackInSlot(1, new ItemStack(Items.DIAMOND, 1_500_000_000));
        Caps from = resources(h, SOURCE, PipeType.ITEM, source, null, null);
        Caps to = resources(h, DESTINATION, PipeType.ITEM, destination, null, null);
        SideConfig cfg = new SideConfig(PipeType.ITEM); cfg.limit = 1_000_000_000;
        int moved = ItemTransfer.run(h.getLevel(), h.absolutePos(SOURCE), from, cfg,
                List.of(target(pipe(h), to)), 16, false, PipeType.ITEM, null);
        h.assertTrue(moved == 16 && destination.getStackInSlot(0).getCount() == 16,
                "a large virtual inventory still has surplus above its reserve");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void balancedItemsShareOnlyTheSurplusAboveReserve(GameTestHelper h) {
        ItemStackHandler source = new ItemStackHandler(2), first = new ItemStackHandler(1), second = new ItemStackHandler(1);
        source.setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
        source.setStackInSlot(1, new ItemStack(Items.DIAMOND, 10));
        Caps from = resources(h, SOURCE, PipeType.ITEM, source, null, null);
        Caps one = resources(h, DESTINATION, PipeType.ITEM, first, null, null);
        Caps two = resources(h, new BlockPos(3, 1, 1), PipeType.ITEM, second, null, null);
        SideConfig cfg = new SideConfig(PipeType.ITEM); cfg.limit = 14;
        PipeBlockEntity pipe = pipe(h);
        int moved = ItemTransfer.run(h.getLevel(), h.absolutePos(SOURCE), from, cfg,
                List.of(target(pipe, one), target(pipe, two)), 20, true, PipeType.ITEM, null);
        h.assertTrue(moved == 6 && first.getStackInSlot(0).getCount() == 3 && second.getStackInSlot(0).getCount() == 3,
                "balanced distribution must divide the six surplus items, counting the reserve once");
        h.assertTrue(!PipeType.ITEM.hasWork(from, cfg), "the retained reserve is not stuck work");
        h.succeed();
    }

    private static final class TwoTanks implements IFluidHandler {
        final FluidTank first = new FluidTank(2000), second = new FluidTank(2000);
        @Override public int getTanks() { return 2; }
        private FluidTank tank(int index) { return index == 0 ? first : second; }
        @Override public FluidStack getFluidInTank(int index) { return tank(index).getFluid(); }
        @Override public int getTankCapacity(int index) { return tank(index).getCapacity(); }
        @Override public boolean isFluidValid(int index, FluidStack stack) { return true; }
        @Override public int fill(FluidStack stack, FluidAction action) {
            int filled = first.fill(stack, action);
            FluidStack rest = stack.copy(); rest.shrink(filled);
            return filled + second.fill(rest, action);
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            FluidStack drained = first.drain(stack, action);
            FluidStack remaining = stack.copy(); remaining.shrink(drained.getAmount());
            FluidStack extra = second.drain(remaining, action);
            if (drained.isEmpty()) return extra;
            drained.grow(extra.getAmount()); return drained;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            FluidStack drained = first.drain(amount, action);
            return drained.isEmpty() ? second.drain(amount, action) : drained;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fluidFiltersDoNotHideLaterTanks(GameTestHelper h) {
        TwoTanks source = new TwoTanks(); FluidTank destination = new FluidTank(2000);
        source.first.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        source.second.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        Caps from = resources(h, SOURCE, PipeType.FLUID, null, source, null);
        Caps to = resources(h, DESTINATION, PipeType.FLUID, null, destination, null);
        SideConfig cfg = new SideConfig(PipeType.FLUID); cfg.setEntry(0, FilterEntry.ofItem("minecraft:water"));
        int moved = FluidTransfer.run(h.getLevel(), from, cfg, List.of(target(pipe(h), to)), 1000, false, PipeType.FLUID);
        h.assertTrue(moved == 1000 && destination.getFluid().getFluid() == Fluids.WATER,
                "water in a later tank must flow despite the blocked first lava tank");
        h.assertTrue(source.first.getFluidAmount() == 1000 && source.second.isEmpty(), "blocked fluid stays untouched");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stuckSignalUsesEligibleFluidAndEnergySurplus(GameTestHelper h) {
        TwoTanks source = new TwoTanks();
        source.first.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        source.second.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        Caps from = resources(h, SOURCE, PipeType.FLUID, null, source, null);
        SideConfig cfg = new SideConfig(PipeType.FLUID); cfg.setEntry(0, FilterEntry.ofItem("minecraft:water"));
        h.assertTrue(PipeType.FLUID.hasWork(from, cfg), "eligible water in a later tank must count as work");
        cfg.limit = 1000;
        h.assertTrue(!PipeType.FLUID.hasWork(from, cfg), "reserved fluid must not trigger stuck output");
        EnergyStorage energy = new EnergyStorage(2000); energy.receiveEnergy(1000, false);
        Caps power = resources(h, DESTINATION, PipeType.ENERGY, null, null, energy);
        SideConfig energyCfg = new SideConfig(PipeType.ENERGY); energyCfg.limit = 1000;
        h.assertTrue(!PipeType.ENERGY.hasWork(power, energyCfg), "reserved energy must not trigger stuck output");
        energy.receiveEnergy(1, false);
        h.assertTrue(PipeType.ENERGY.hasWork(power, energyCfg), "extractable energy above reserve counts as work");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void destinationFiltersDoNotHideLaterTanks(GameTestHelper h) {
        TwoTanks source = new TwoTanks(); FluidTank destination = new FluidTank(2000);
        source.first.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        source.second.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        Caps from = resources(h, SOURCE, PipeType.FLUID, null, source, null);
        Caps to = resources(h, DESTINATION, PipeType.FLUID, null, destination, null);
        PipeBlockEntity pipe = pipe(h, PipeType.FLUID);
        pipe.side(Direction.EAST).setEntry(0, FilterEntry.ofItem("minecraft:water"));
        int moved = FluidTransfer.run(h.getLevel(), from, new SideConfig(PipeType.FLUID),
                List.of(target(pipe, to)), 1000, false, PipeType.FLUID);
        h.assertTrue(moved == 1000 && destination.getFluid().getFluid() == Fluids.WATER,
                "an insertion filter must allow eligible later tanks");h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void balancedEnergyHandlesMaximumIntBudget(GameTestHelper h) {
        EnergyStorage source = new EnergyStorage(Integer.MAX_VALUE), first = new EnergyStorage(Integer.MAX_VALUE),
                second = new EnergyStorage(Integer.MAX_VALUE);
        source.receiveEnergy(Integer.MAX_VALUE, false);
        Caps from = resources(h, SOURCE, PipeType.ENERGY, null, null, source);
        Caps one = resources(h, DESTINATION, PipeType.ENERGY, null, null, first);
        Caps two = resources(h, new BlockPos(3, 1, 1), PipeType.ENERGY, null, null, second);
        PipeBlockEntity pipe = pipe(h);
        int moved = EnergyTransfer.run(from, new SideConfig(PipeType.ENERGY), List.of(target(pipe, one), target(pipe, two)),
                Integer.MAX_VALUE, true, PipeType.ENERGY);
        h.assertTrue(moved == Integer.MAX_VALUE && first.getEnergyStored() == 1_073_741_824
                && second.getEnergyStored() == 1_073_741_823, "maximum energy budget must split evenly without overflow");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void largeLegalEnergyConfigCannotOverflowTransferBudget(GameTestHelper h) {
        EnergyStorage source = new EnergyStorage(Integer.MAX_VALUE), destination = new EnergyStorage(Integer.MAX_VALUE);
        source.receiveEnergy(Integer.MAX_VALUE, false);
        Caps from = resources(h, SOURCE, PipeType.ENERGY, null, null, source);
        Caps to = resources(h, DESTINATION, PipeType.ENERGY, null, null, destination);
        PipeBlockEntity pipe = pipe(h);
        int base = FlowlineConfig.ENERGY_PER_TICK.get();
        List<? extends Integer> multipliers = FlowlineConfig.STACK_MULTIPLIERS.get();
        try {
            FlowlineConfig.ENERGY_PER_TICK.set(100_000_000);
            FlowlineConfig.STACK_MULTIPLIERS.set(List.of(Integer.MAX_VALUE));
            long moved = PipeType.ENERGY.transfer(h.getLevel(), h.absolutePos(SOURCE), from,
                    new SideConfig(PipeType.ENERGY), List.of(target(pipe, to)), 12000, null);
            h.assertTrue(moved == Integer.MAX_VALUE && source.getEnergyStored() == 0,
                    "legal high multipliers must clamp the budget, never wrap it");
        } finally {
            FlowlineConfig.ENERGY_PER_TICK.set(base);FlowlineConfig.STACK_MULTIPLIERS.set(multipliers);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oldVisualPacketsRejectInvalidListLengths(GameTestHelper h) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int size : new int[]{-1, TravelPayload.MAX_PATH + 1}) {
                buf.clear();buf.writeItem(new ItemStack(Items.DIAMOND));buf.writeVarInt(size);
                boolean rejected = false;
                try { TravelPayload.decode(buf); } catch (DecoderException expected) { rejected = true; }
                h.assertTrue(rejected, "invalid item animation list length must be rejected before allocation");
            }
            for (int size : new int[]{-1, FilterSyncPayload.MAX_CHANGES + 1}) {
                buf.clear();buf.writeVarInt(1);buf.writeBoolean(false);buf.writeVarInt(size);
                boolean rejected = false;
                try { FilterSyncPayload.decode(buf); } catch (DecoderException expected) { rejected = true; }
                h.assertTrue(rejected, "invalid filter sync length must be rejected before allocation");
            }
            for (int index : new int[]{-1, FilterSyncPayload.MAX_INDEX + 1}) {
                buf.clear();buf.writeVarInt(1);buf.writeBoolean(false);buf.writeVarInt(1);buf.writeVarInt(index);
                boolean rejected = false;
                try { FilterSyncPayload.decode(buf); } catch (DecoderException expected) { rejected = true; }
                h.assertTrue(rejected, "filter sync positions out of bounds must be rejected");
            }
            buf.clear();FilterSyncPayload valid = new FilterSyncPayload(1, true,
                    List.of(new FilterSyncPayload.Change(3, Optional.of(FilterEntry.ofName("diamond"))),
                            new FilterSyncPayload.Change(5, Optional.empty())));
            valid.encode(buf);h.assertTrue(FilterSyncPayload.decode(buf).equals(valid), "valid filter syncs must still round-trip");
        } finally { buf.release(); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void corruptCardSlotsCannotCrashOrGrowFilter(GameTestHelper h) {
        PipeBlockEntity pipe = pipe(h);var player = h.makeMockPlayer();
        ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());ListTag entries = new ListTag();
        for (int slot : new int[]{-1, Integer.MAX_VALUE}) {
            CompoundTag entry = new CompoundTag();entry.putInt("slot", slot);
            entry.put("rule", FilterEntry.CODEC.encodeStart(NbtOps.INSTANCE, FilterEntry.ofItem("minecraft:diamond"))
                    .result().orElseThrow());entries.add(entry);
        }
        CompoundTag data = new CompoundTag();data.put("filter", entries);card.getOrCreateTag().put("flowline_card", data);
        ((ConfigCardItem) card.getItem()).useOnPipe(pipe, Direction.EAST, Conn.ENDPOINT, player, InteractionHand.MAIN_HAND, card);
        h.assertTrue(pipe.side(Direction.EAST).filterSize() == 0, "corrupt card indices must be skipped without allocation");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wrenchScrollRequiresBuildPermissionAndSneaking(GameTestHelper h) {
        h.setBlock(DESTINATION, Blocks.CHEST);PipeBlockEntity pipe = pipe(h);
        PipeBlock.updateConnections(h.getLevel(), pipe.getBlockPos());var player = h.makeMockPlayer();
        player.setPos(pipe.getBlockPos().getX(), pipe.getBlockPos().getY(), pipe.getBlockPos().getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));player.setShiftKeyDown(true);
        player.getAbilities().mayBuild = false;WrenchItem.scroll(player, pipe.getBlockPos(), Direction.EAST, true, false);
        h.assertTrue(pipe.side(Direction.EAST).priority == 0, "no building permission: settings remain unchanged");
        player.getAbilities().mayBuild = true;player.setShiftKeyDown(false);
        WrenchItem.scroll(player, pipe.getBlockPos(), Direction.EAST, true, false);
        h.assertTrue(pipe.side(Direction.EAST).priority == 0, "non-sneaking scroll must not edit the pipe");
        player.setShiftKeyDown(true);WrenchItem.scroll(player, pipe.getBlockPos(), Direction.EAST, true, false);
        h.assertTrue(pipe.side(Direction.EAST).priority == 1, "authorized shortcut still changes priority");h.succeed();
    }
}
