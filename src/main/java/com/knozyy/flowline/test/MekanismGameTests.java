package com.knozyy.flowline.test;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.compat.ChemicalCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Chemical pipe tests. Without Mekanism every test passes at once; run them for real with
 * {@code ./gradlew runGameTestServer -PwithMekanism}. Mekanism types live in {@link MekanismTestSupport}.
 */
@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public final class MekanismGameTests {
    private static boolean skip(GameTestHelper h) {
        if (ChemicalCompat.available()) return false;
        h.succeed();
        return true;
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chemicalPipeMovesGasBetweenTanks(GameTestHelper h) {
        if (!skip(h)) MekanismTestSupport.chemicalPipeMovesGasBetweenTanks(h);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void extractFilterSkipsBlockedChemicalInEarlierTank(GameTestHelper h) {
        if (!skip(h)) MekanismTestSupport.extractFilterSkipsBlockedChemicalInEarlierTank(h);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void insertFilterBlocksChemicalFromTarget(GameTestHelper h) {
        if (!skip(h)) MekanismTestSupport.insertFilterBlocksChemicalFromTarget(h);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void blockRuleByModStopsEveryChemicalOfThatMod(GameTestHelper h) {
        if (!skip(h)) MekanismTestSupport.blockRuleByModStopsEveryChemicalOfThatMod(h);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void filteredSourceCountsAsWorkOnlyForAllowedChemicals(GameTestHelper h) {
        if (!skip(h)) MekanismTestSupport.filteredSourceCountsAsWorkOnlyForAllowedChemicals(h);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void chemicalRulesValidateIdsAndRejectUnsupportedParts(GameTestHelper h) {
        if (!skip(h)) MekanismTestSupport.chemicalRulesValidateIdsAndRejectUnsupportedParts(h);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void chemicalPipeHasALootTableAndDropsItself(GameTestHelper h) {
        if (!skip(h)) MekanismTestSupport.chemicalPipeHasALootTableAndDropsItself(h);
    }
}
