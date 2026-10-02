package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.menu.PipeMenu;
import com.knozyy.flowline.network.FilterSyncPayload;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.RedstoneMode;
import com.knozyy.flowline.pipe.SideConfig;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.pipe.SideStatus;
import com.knozyy.flowline.registry.ModBlocks;
import com.knozyy.flowline.registry.ModItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Server side of the pipe screen: filter sync, rule placement and limits, side tabs and the status badge. */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public class GuiGameTests {
    private static final BlockPos SOURCE = new BlockPos(0, 1, 1);
    private static final BlockPos PIPE = new BlockPos(1, 1, 1);
    private static final BlockPos DESTINATION = new BlockPos(2, 1, 1);

    private static PipeBlockEntity pipe(GameTestHelper h) {
        h.setBlock(PIPE, ModBlocks.ITEM_PIPE.get());
        return (PipeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(PIPE));
    }

    private static PipeMenu menu(GameTestHelper h, PipeBlockEntity pipe, Direction side) {
        return new PipeMenu(0, h.makeMockPlayer().getInventory(), pipe, side);
    }

    /** A rule carrying about {@code bytes} of data, split over strings below NBT's 64 KiB string limit. */
    private static FilterEntry heavyRule(String id, int bytes) {
        CompoundTag data = new CompoundTag();
        for (int i = 0; bytes > 0; i++, bytes -= 20_000) data.putString("lore" + i, "x".repeat(Math.min(bytes, 20_000)));
        return new FilterEntry(Optional.of(id), List.of(), false, Optional.of(data), false, false);
    }

    /** Round trip through the wire format, then apply like the client does. */
    private static void apply(List<FilterEntry> client, FilterSyncPayload payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            payload.encode(buf);
            FilterSyncPayload decoded = FilterSyncPayload.decode(buf);
            if (decoded.reset()) client.clear();
            for (FilterSyncPayload.Change change : decoded.changes()) {
                while (client.size() <= change.index()) client.add(null);
                client.set(change.index(), change.entry().orElse(null));
            }
        } finally {
            buf.release();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void filterSyncSendsOnlyChanges(GameTestHelper h) {
        SideConfig cfg = new SideConfig(PipeType.ITEM);
        int rules = 54 + 6 * 54;
        for (int i = 0; i < rules; i++) cfg.setEntry(i, FilterEntry.ofName("rule" + i));
        List<FilterEntry> sent = new ArrayList<>();
        List<FilterSyncPayload.Change> changes = PipeMenu.diffFilter(sent, cfg);
        h.assertTrue(changes.size() == rules, "the first sync carries every rule, got " + changes.size());
        List<List<FilterSyncPayload.Change>> packets = PipeMenu.packets(changes, PipeMenu.MAX_SYNC_BYTES);
        h.assertTrue(packets.size() == (rules + FilterSyncPayload.MAX_CHANGES - 1) / FilterSyncPayload.MAX_CHANGES,
                "the largest filter splits into packets of at most " + FilterSyncPayload.MAX_CHANGES + " rules");
        List<FilterEntry> client = new ArrayList<>();
        for (int i = 0; i < packets.size(); i++) apply(client, new FilterSyncPayload(1, i == 0, packets.get(i)));
        for (int i = 0; i < rules; i++) {
            h.assertTrue(cfg.getEntry(i).equals(client.get(i)), "rule " + i + " must arrive unchanged");
        }
        h.assertTrue(PipeMenu.diffFilter(sent, cfg).isEmpty(), "an unchanged filter is not sent again");
        cfg.setEntry(7, null);
        List<FilterSyncPayload.Change> one = PipeMenu.diffFilter(sent, cfg);
        h.assertTrue(one.size() == 1 && one.get(0).index() == 7 && one.get(0).entry().isEmpty(),
                "removing one rule sends just that position");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heavyRulesAreSplitAndRefused(GameTestHelper h) {
        FilterEntry heavy = heavyRule("minecraft:diamond", 100_000);
        h.assertTrue(PipeMenu.encodedSize(heavy) > PipeMenu.MAX_RULE_BYTES, "the test rule must be over the limit");
        List<FilterSyncPayload.Change> changes = new ArrayList<>();
        for (int i = 0; i < 5; i++) changes.add(new FilterSyncPayload.Change(i, Optional.of(heavy)));
        for (List<FilterSyncPayload.Change> packet : PipeMenu.packets(changes, PipeMenu.MAX_SYNC_BYTES)) {
            int bytes = packet.stream().mapToInt(c -> PipeMenu.encodedSize(c.entry().orElseThrow())).sum();
            h.assertTrue(packet.size() == 1 || bytes <= PipeMenu.MAX_SYNC_BYTES,
                    "a sync packet stays within its byte budget, got " + bytes);
        }
        PipeBlockEntity pipe = pipe(h);
        PipeMenu menu = menu(h, pipe, Direction.EAST);
        menu.setEntry(0, heavy);
        h.assertTrue(pipe.side(Direction.EAST).getEntry(0) == null, "a rule over the size limit is refused");
        menu.setEntry(0, heavyRule("minecraft:diamond", 1_000));
        h.assertTrue(pipe.side(Direction.EAST).getEntry(0) != null, "a rule with a little data is kept");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rulesGoToTheFirstFreePosition(GameTestHelper h) {
        PipeBlockEntity pipe = pipe(h);
        SideConfig cfg = pipe.side(Direction.EAST);
        PipeMenu menu = menu(h, pipe, Direction.EAST);
        menu.setEntry(-1, FilterEntry.ofName("first"));
        menu.setEntry(-1, FilterEntry.ofName("second"));
        h.assertTrue(cfg.getEntry(0) != null && cfg.getEntry(1) != null, "-1 fills positions in order");
        menu.setEntry(-1, FilterEntry.ofName("first"));
        h.assertTrue(cfg.getEntry(2) == null, "a duplicate is refused even without a position");
        menu.setEntry(0, null);
        menu.setEntry(-1, FilterEntry.ofName("third"));
        h.assertTrue(cfg.getEntry(0) != null && cfg.getEntry(0).name().orElse("").equals("third"),
                "a freed position is used again first");

        int capacity = cfg.filterCapacity();
        menu.setEntry(capacity, FilterEntry.ofName("past"));
        h.assertTrue(cfg.getEntry(capacity) == null, "positions past the capacity are refused");
        pipe.installUpgrade(Direction.EAST, new ItemStack(ModItems.FILTER_UPGRADE.get()));
        h.assertTrue(cfg.filterCapacity() > capacity, "a Filter upgrade raises the capacity");
        menu.setEntry(capacity, FilterEntry.ofName("past"));
        h.assertTrue(cfg.getEntry(capacity) != null, "with the upgrade the position is usable");

        for (int i = 0; i < cfg.filterCapacity(); i++) {
            if (cfg.getEntry(i) == null) cfg.setEntry(i, FilterEntry.ofName("fill" + i));
        }
        int version = cfg.filterVersion();
        menu.setEntry(-1, FilterEntry.ofName("overflow"));
        h.assertTrue(cfg.filterVersion() == version, "a full filter takes no new rule");
        h.assertTrue(PipeMenu.firstFree(cfg) == -1, "a full filter has no free position");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void shiftClickAddsAFilterRule(GameTestHelper h) {
        PipeBlockEntity pipe = pipe(h);
        net.minecraft.world.entity.player.Player player = h.makeMockPlayer();
        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 5));
        PipeMenu menu = new PipeMenu(0, player.getInventory(), pipe, Direction.EAST);
        int firstInventorySlot = 6;   // after the six upgrade slots; holds player inventory slot 9
        menu.quickMoveStack(player, firstInventorySlot);
        FilterEntry rule = pipe.side(Direction.EAST).getEntry(0);
        h.assertTrue(rule != null && rule.item().orElse("").equals("minecraft:diamond"),
                "shift-clicking a stack in the inventory adds it as a rule");
        h.assertTrue(player.getInventory().getItem(9).getCount() == 5, "the stack itself stays in the inventory");
        menu.quickMoveStack(player, firstInventorySlot);
        h.assertTrue(pipe.side(Direction.EAST).getEntry(1) == null, "shift-clicking it again adds no duplicate");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sideTabsOnlyOpenAttachedSides(GameTestHelper h) {
        h.setBlock(SOURCE, Blocks.CHEST);
        PipeBlockEntity pipe = pipe(h);
        PipeBlock.updateConnections(h.getLevel(), pipe.getBlockPos());
        h.succeedWhen(() -> {
            BlockState state = h.getBlockState(PIPE);
            h.assertTrue(PipeMenu.canOpenSide(state, Direction.WEST), "the side facing the chest has a screen");
            h.assertTrue(!PipeMenu.canOpenSide(state, Direction.UP), "a side with nothing attached has no screen");
            PipeMenu menu = menu(h, pipe, Direction.WEST);
            h.assertTrue(!menu.clickMenuButton(h.makeMockPlayer(), PipeMenu.BTN_SIDE + Direction.UP.get3DDataValue()),
                    "the side button refuses an empty side");
            h.assertTrue(!menu.clickMenuButton(h.makeMockPlayer(), PipeMenu.BTN_SIDE + Direction.WEST.get3DDataValue()),
                    "the side button refuses the side that is already open");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void statusFollowsTheLastOperation(GameTestHelper h) {
        SideConfig cfg = new SideConfig(PipeType.ITEM);
        cfg.mode = SideMode.EXTRACT;
        h.assertTrue(SideStatus.of(cfg, () -> true) == SideStatus.STARTING, "before the first run");
        cfg.lastMoved = 4;
        h.assertTrue(SideStatus.of(cfg, () -> false) == SideStatus.WORKING, "something moved");
        cfg.lastMoved = 0;
        h.assertTrue(SideStatus.of(cfg, () -> true) == SideStatus.STUCK, "work left but nothing moved");
        h.assertTrue(SideStatus.of(cfg, () -> false) == SideStatus.IDLE, "nothing to move");
        cfg.lastBlocked = true;
        h.assertTrue(SideStatus.of(cfg, () -> true) == SideStatus.WAITING, "redstone holds the side back");
        cfg.lastBlocked = false;
        cfg.redstone = RedstoneMode.PULSE;
        h.assertTrue(SideStatus.of(cfg, () -> true) == SideStatus.WAITING, "pulse mode between pulses");
        cfg.pulsePending = true;
        h.assertTrue(SideStatus.of(cfg, () -> true) == SideStatus.WORKING, "a pulse is due");
        cfg.redstone = RedstoneMode.IGNORED;
        cfg.sleeping = true;
        h.assertTrue(SideStatus.of(cfg, () -> true) == SideStatus.SLEEPING, "no target");
        cfg.resetRuntime();
        h.assertTrue(cfg.lastMoved == SideConfig.NOT_RUN && !cfg.lastBlocked, "a reset forgets the last run");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void curvyPortBridgesTwoNetworks(GameTestHelper h) {
        BlockPos chestA = new BlockPos(0, 1, 1), pipeA = new BlockPos(1, 1, 1);
        BlockPos chestB = new BlockPos(0, 1, 3), pipeB = new BlockPos(1, 1, 3);
        h.setBlock(chestA, Blocks.CHEST);
        h.setBlock(chestB, Blocks.CHEST);
        h.setBlock(pipeA, ModBlocks.ITEM_PIPE.get());
        h.setBlock(pipeB, ModBlocks.ITEM_PIPE.get());
        PipeBlockEntity a = (PipeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pipeA));
        PipeBlockEntity b = (PipeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pipeB));
        PipeBlock.updateConnections(h.getLevel(), a.getBlockPos());
        PipeBlock.updateConnections(h.getLevel(), b.getBlockPos());
        PipeBlock.setMode(a, Direction.WEST, SideMode.EXTRACT, null);
        a.side(Direction.WEST).setEntry(0, FilterEntry.ofItem("minecraft:diamond"));
        a.side(Direction.WEST).limit = 3;
        ChestBlockEntity from = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chestA));
        ChestBlockEntity to = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chestB));
        from.setItem(0, new ItemStack(Items.DIAMOND, 10));
        from.setItem(1, new ItemStack(Items.DIRT, 5));
        h.succeedWhen(() -> {
            var out = (net.minecraftforge.items.IItemHandler) com.knozyy.flowline.compat.CurvyPort.port(h.getLevel(), a,
                    Direction.UP, net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER);
            var in = (net.minecraftforge.items.IItemHandler) com.knozyy.flowline.compat.CurvyPort.port(h.getLevel(), b,
                    Direction.UP, net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER);
            h.assertTrue(out != null && in != null, "item pipes offer a Curvy port");
            // what a Curvy line with an Extract end on pipe A does: take from A's port, give to B's port
            for (int slot = 0; slot < out.getSlots(); slot++) {
                ItemStack taken = out.extractItem(slot, 64, false);
                if (taken.isEmpty()) continue;
                ItemStack left = in.insertItem(0, taken, false);
                h.assertTrue(left.isEmpty(), "pipe B's network takes everything the bridge carries");
            }
            h.assertTrue(to.countItem(Items.DIAMOND) == 7, "diamonds cross the bridge, keeping 3 at the source; got "
                    + to.countItem(Items.DIAMOND));
            h.assertTrue(from.countItem(Items.DIAMOND) == 3, "the source keeps its 3 diamonds");
            h.assertTrue(from.countItem(Items.DIRT) == 5 && to.countItem(Items.DIRT) == 0,
                    "the Extract side's filter decides what the port offers");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void movingSideReportsWorking(GameTestHelper h) {
        h.setBlock(SOURCE, Blocks.CHEST);
        h.setBlock(DESTINATION, Blocks.CHEST);
        PipeBlockEntity pipe = pipe(h);
        PipeBlock.updateConnections(h.getLevel(), pipe.getBlockPos());
        ChestBlockEntity source = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(SOURCE));
        source.setItem(0, new ItemStack(Items.DIAMOND, 64));
        PipeBlock.setMode(pipe, Direction.WEST, SideMode.EXTRACT, null);
        SideConfig cfg = pipe.side(Direction.WEST);
        h.succeedWhen(() -> h.assertTrue(SideStatus.of(cfg, () -> false) == SideStatus.WORKING,
                "an extracting side that moves items shows as working, last moved " + cfg.lastMoved));
    }
}
