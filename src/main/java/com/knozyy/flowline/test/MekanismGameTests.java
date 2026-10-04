package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.compat.ChemicalCompat;
import com.knozyy.flowline.filter.FilterEntry;
import com.knozyy.flowline.pipe.PipeBlockEntity;
import com.knozyy.flowline.pipe.PipeType;
import com.knozyy.flowline.pipe.SideMode;
import com.knozyy.flowline.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Chemical pipe tests. Without Mekanism every test passes at once; run them for real with
 * {@code ./gradlew runGameTestServer -PwithMekanism}. Mekanism types live in {@link MekanismTestSupport}.
 */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public final class MekanismGameTests {
    private static final BlockPos SOURCE = new BlockPos(0, 1, 1);
    private static final BlockPos PIPE = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(2, 1, 1);

    /** Source and target holding gas, one chemical pipe between them with its west side set to Extract. */
    private record Line(MekanismTestSupport.Tanks source, MekanismTestSupport.Tanks target, PipeBlockEntity pipe) {}

    private static Line line(GameTestHelper h, int sourceTanks) {
        MekanismTestSupport.Tanks source = new MekanismTestSupport.Tanks(sourceTanks, 100_000);
        MekanismTestSupport.Tanks target = new MekanismTestSupport.Tanks(1, 100_000);
        place(h, SOURCE, new MekanismTestSupport.GasChest(h.absolutePos(SOURCE), source));
        place(h, TARGET, new MekanismTestSupport.GasChest(h.absolutePos(TARGET), target));
        h.setBlock(PIPE, ModBlocks.CHEMICAL_PIPE.get());
        PipeBlockEntity pipe = (PipeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(PIPE));
        pipe.side(Direction.WEST).mode = SideMode.EXTRACT;
        return new Line(source, target, pipe);
    }

    private static void place(GameTestHelper h, BlockPos pos, MekanismTestSupport.GasChest chest) {
        h.setBlock(pos, Blocks.CHEST);
        h.getLevel().removeBlockEntity(chest.getBlockPos());
        h.getLevel().setBlockEntity(chest);
    }

    private static boolean skip(GameTestHelper h) {
        if (ChemicalCompat.available()) return false;
        h.succeed();
        return true;
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chemicalPipeMovesGasBetweenTanks(GameTestHelper h) {
        if (skip(h)) return;
        Line line = line(h, 1);
        var hydrogen = MekanismTestSupport.gas("hydrogen");
        line.source().setChemicalInTank(0, new mekanism.api.chemical.gas.GasStack(hydrogen, 1000));
        h.succeedWhen(() -> {
            h.assertTrue(line.target().amountOf(hydrogen) == 1000, "the target tank should receive all 1000 mB");
            h.assertTrue(line.source().amountOf(hydrogen) == 0, "the source tank should be empty");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void extractFilterSkipsBlockedChemicalInEarlierTank(GameTestHelper h) {
        if (skip(h)) return;
        Line line = line(h, 2);
        var oxygen = MekanismTestSupport.gas("oxygen");
        var hydrogen = MekanismTestSupport.gas("hydrogen");
        line.source().setChemicalInTank(0, new mekanism.api.chemical.gas.GasStack(oxygen, 1000));
        line.source().setChemicalInTank(1, new mekanism.api.chemical.gas.GasStack(hydrogen, 1000));
        line.pipe().side(Direction.WEST).setEntry(0, FilterEntry.ofItem("mekanism:hydrogen"));
        h.succeedWhen(() -> {
            h.assertTrue(line.target().amountOf(hydrogen) == 1000, "allowed hydrogen in the second tank must flow");
            h.assertTrue(line.source().amountOf(oxygen) == 1000 && line.target().amountOf(oxygen) == 0,
                    "oxygen is not on the Allow list and must stay in the source");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void insertFilterBlocksChemicalFromTarget(GameTestHelper h) {
        if (skip(h)) return;
        Line line = line(h, 1);
        var hydrogen = MekanismTestSupport.gas("hydrogen");
        line.source().setChemicalInTank(0, new mekanism.api.chemical.gas.GasStack(hydrogen, 1000));
        line.pipe().side(Direction.EAST).setEntry(0, FilterEntry.ofItem("mekanism:hydrogen").withInvert(true));
        h.runAfterDelay(120, () -> {
            h.assertTrue(line.target().amountOf(hydrogen) == 0, "a Block rule on the insert side must refuse hydrogen");
            h.assertTrue(line.source().amountOf(hydrogen) == 1000, "nothing may leave the source");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void blockRuleByModStopsEveryChemicalOfThatMod(GameTestHelper h) {
        if (skip(h)) return;
        Line line = line(h, 1);
        var oxygen = MekanismTestSupport.gas("oxygen");
        line.source().setChemicalInTank(0, new mekanism.api.chemical.gas.GasStack(oxygen, 1000));
        line.pipe().side(Direction.WEST).setEntry(0, FilterEntry.ofMod("mekanism").withInvert(true));
        h.runAfterDelay(120, () -> {
            h.assertTrue(line.target().amountOf(oxygen) == 0 && line.source().amountOf(oxygen) == 1000,
                    "a Block rule for the whole mod must stop its chemicals");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void filteredSourceCountsAsWorkOnlyForAllowedChemicals(GameTestHelper h) {
        if (skip(h)) return;
        Line line = line(h, 1);
        var oxygen = MekanismTestSupport.gas("oxygen");
        line.source().setChemicalInTank(0, new mekanism.api.chemical.gas.GasStack(oxygen, 1000));
        var cfg = line.pipe().side(Direction.WEST);
        var caps = com.knozyy.flowline.pipe.Caps.create(PipeType.CHEMICAL, h.getLevel(), h.absolutePos(SOURCE),
                Direction.UP);
        h.assertTrue(PipeType.CHEMICAL.hasWork(caps, cfg), "oxygen with no rules is work");
        cfg.setEntry(0, FilterEntry.ofItem("mekanism:hydrogen"));
        h.assertTrue(!PipeType.CHEMICAL.hasWork(caps, cfg), "oxygen is not allowed, so there is nothing to do");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void chemicalRulesValidateIdsAndRejectUnsupportedParts(GameTestHelper h) {
        if (skip(h)) return;
        h.assertTrue(FilterEntry.ofItem("mekanism:hydrogen").problem(PipeType.CHEMICAL) == null, "a real gas is valid");
        h.assertTrue("gui.flowline.editor.error.unknown_chemical".equals(
                FilterEntry.ofItem("mekanism:nonsense").problem(PipeType.CHEMICAL)), "an unknown id is refused");
        h.assertTrue("gui.flowline.editor.error.chemical_parts".equals(
                FilterEntry.ofTags(false, "forge:ores").problem(PipeType.CHEMICAL)), "tags do not apply to chemicals");
        h.assertTrue(FilterEntry.ofName("^hydro").problem(PipeType.CHEMICAL) == null, "name patterns are valid");
        h.assertTrue(FilterEntry.ofMod("mekanism").problem(PipeType.CHEMICAL) == null, "a loaded mod is valid");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void chemicalPipeHasALootTableAndDropsItself(GameTestHelper h) {
        if (skip(h)) return;
        ResourceLocation table = new ResourceLocation(Flowline.MODID, "blocks/chemical_pipe");
        h.assertTrue(h.getLevel().getServer().getLootData().getLootTable(table) != LootTable.EMPTY,
                "flowline:blocks/chemical_pipe must be a loaded loot table");
        h.setBlock(PIPE, ModBlocks.CHEMICAL_PIPE.get());
        BlockPos pos = h.absolutePos(PIPE);
        List<net.minecraft.world.item.ItemStack> drops = Block.getDrops(h.getLevel().getBlockState(pos), h.getLevel(),
                pos, h.getLevel().getBlockEntity(pos));
        h.assertTrue(drops.size() == 1 && drops.get(0).is(ModBlocks.CHEMICAL_PIPE.get().asItem()),
                "breaking a chemical pipe drops one chemical pipe");
        h.succeed();
    }
}
