package com.knozyy.flowline.test;

import com.google.gson.JsonParser;
import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.compat.CurvyPipesCompat;
import com.knozyy.flowline.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import java.util.function.Consumer;
import com.knozyy.flowline.pipe.PipeBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Flowline.MODID)
@PrefixGameTestTemplate(false)
public final class CurvyIntegrationGameTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void curvedFeaturesFollowOptionalModPresence(GameTestHelper helper) {
        boolean enabled = CurvyPipesCompat.available();
        for (String type : CurvyPipesCompat.TYPES) {
            helper.assertTrue(!ForgeRegistries.ITEMS.containsKey(CurvyPipesCompat.id(type)),
                    "There must be no second Flowline " + type + " item");
            if (enabled) {
                try {
                    var method = Class.forName("cyb0124.curvy_pipes.common.CommonHandler")
                            .getDeclaredMethod("useItem", int.class, boolean.class, boolean.class);
                    method.setAccessible(true);
                    int status = (int) method.invoke(null, net.minecraft.world.item.Item.getId(CurvyPipesCompat.item(type)), false, false);
                    helper.assertTrue(status != 0, "Native Curvy must recognize the original " + type + " item");
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            }
            for (String suffix : new String[] {"curve", "uncurve"}) {
                var recipe = helper.getLevel().getRecipeManager().byKey(
                        new ResourceLocation(Flowline.MODID, type + "_pipe_" + suffix));
                helper.assertTrue(recipe.isEmpty(), "Conversion recipes must not exist");
            }
        }
        helper.assertTrue(CurvyPipesCompat.block(ModItems.ITEM_PIPE.get()) != null,
                "The same item must support normal block placement");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sameMaterialPlacesNormalBlocksAndPaysForThem(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        int index = 0;
        for (String type : CurvyPipesCompat.TYPES) {
            var item = CurvyPipesCompat.item(type);
            PipeBlock block = CurvyPipesCompat.block(item);
            var relative = new BlockPos(index++, 2, 1);
            var pos = helper.absolutePos(relative);
            boolean wet = type.equals("fluid");
            helper.setBlock(relative, wet ? Blocks.WATER : Blocks.AIR);
            ItemStack stack = new ItemStack(item, 3);
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
            UseOnContext context = new UseOnContext(player, InteractionHand.OFF_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            helper.assertTrue(stack.useOn(context).consumesAction(), "Original " + type + " item must place normally");
            helper.assertTrue(helper.getBlockState(relative).is(block), "The normal Flowline block must be placed");
            helper.assertTrue(helper.getBlockState(relative).getValue(PipeBlock.WATERLOGGED) == wet,
                    "Normal placement must preserve waterlogging");
            helper.assertTrue(stack.getCount() == 2, "Normal placement must consume one original pipe");
            helper.assertTrue(block.asItem() == item, "Pick block and drops must resolve to the same item");
            var drops = Block.getDrops(helper.getBlockState(relative), helper.getLevel(), pos,
                    helper.getLevel().getBlockEntity(pos));
            helper.assertTrue(drops.stream().anyMatch(drop -> drop.is(item)), "Breaking a block must return the same pipe");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void normalPlacementStillHonorsProtection(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        var relative = new BlockPos(1, 2, 1);
        var pos = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.AIR);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        ItemStack stack = new ItemStack(ModItems.ITEM_PIPE.get(), 3);
        player.setItemInHand(InteractionHand.OFF_HAND, stack);
        Consumer<BlockEvent.EntityPlaceEvent> deny = event -> {
            if (event.getEntity() == player && event.getPos().equals(pos)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, deny);
        try {
            var result = stack.useOn(new UseOnContext(player, InteractionHand.OFF_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
            helper.assertTrue(!result.consumesAction(), "Protection must refuse placement");
            helper.assertTrue(helper.getBlockState(relative).isAir(), "Protection must restore the world");
            helper.assertTrue(stack.getCount() == 3, "Refused placement must not consume material");
        } finally { MinecraftForge.EVENT_BUS.unregister(deny); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativePlacementUsesActualInventoryStacks(GameTestHelper helper) {
        if (!CurvyPipesCompat.available()) { helper.succeed(); return; }
        var player = helper.makeMockPlayer();
        for (String type : CurvyPipesCompat.TYPES) {
            var item = CurvyPipesCompat.item(type);
            var selected = new ItemStack(item, 3);
            var reserve = new ItemStack(item, 4);
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, selected);
            player.setItemInHand(InteractionHand.OFF_HAND, reserve);
            try {
                Class<?> bridge = Class.forName("cyb0124.curvy_pipes.common.CommonHandler");
                var gather = bridge.getDeclaredMethod("gatherStacks", net.minecraft.world.entity.player.Player.class,
                        net.minecraft.world.item.Item.class);
                var gathered = (java.util.Stack<?>) gather.invoke(null, player, item);
                helper.assertTrue(gathered.size() == 2 && gathered.contains(selected) && gathered.contains(reserve),
                        "Native placement must gather the original, mutable " + type + " stacks");
                var consume = bridge.getDeclaredMethod("consumeStacks", net.minecraft.world.entity.player.Player.class,
                        java.util.Stack.class, int.class);
                consume.setAccessible(true);
                consume.invoke(null, player, gathered, 5);
                helper.assertTrue(selected.getCount() + reserve.getCount() == 2,
                        "Native placement must pay from the original inventory");
                var refund = bridge.getDeclaredMethod("giveStack", net.minecraft.world.entity.player.Player.class,
                        net.minecraft.world.item.Item.class, int.class);
                refund.setAccessible(true);
                refund.invoke(null, player, item, 5);
                helper.assertTrue(player.getInventory().countItem(item) == 7, "Native refund must return original pipes");
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeInterceptionOnlyAppliesToMainHand(GameTestHelper helper) {
        if (!CurvyPipesCompat.available()) { helper.succeed(); return; }
        var player = helper.makeMockPlayer();
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        for (String type : CurvyPipesCompat.TYPES) {
            var item = CurvyPipesCompat.item(type);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 3));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item, 3));
            try {
                var method = Class.forName("cyb0124.curvy_pipes.common.CommonHandler").getDeclaredMethod("onRightClickBlock",
                        net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class);
                for (InteractionHand hand : InteractionHand.values()) {
                    var event = new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, hand, pos, hit);
                    method.invoke(null, event);
                    helper.assertTrue((event.getUseItem() == net.minecraftforge.eventbus.api.Event.Result.DENY)
                                    == (hand == InteractionHand.MAIN_HAND),
                            "Curvy must intercept only the main hand; offhand must place ordinary blocks");
                }
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeConfigPreservesUserPipesAndIntegrations(GameTestHelper helper) {
        String yaml = "ignore_unknown_pipes: false\nae2: {cables: OffHand}\npipe_types:\n"
                + "  - {id: custom, name: Custom, texture: curvy_pipes:block/item_pipe, diameter: 0.1, variant: {Item: {rate: 1E3}}}\n";
        String merged = CurvyPipesCompat.withFlowlinePipes(yaml);
        var config = JsonParser.parseString(merged).getAsJsonObject();
        helper.assertTrue(config.getAsJsonObject("ae2").get("cables").getAsString().equals("OffHand"),
                "Curvy integration settings must survive");
        var pipes = config.getAsJsonArray("pipe_types");
        helper.assertTrue(pipes.get(0).getAsJsonObject().getAsJsonObject("variant").getAsJsonObject("Item")
                .get("rate").getAsJsonPrimitive().isNumber(), "Curvy scientific notation must remain numeric");
        helper.assertTrue(pipes.size() == 4 && pipes.get(0).getAsJsonObject().get("id").getAsString().equals("custom"),
                "User pipe definitions must survive");
        helper.assertTrue(JsonParser.parseString(CurvyPipesCompat.withFlowlinePipes(merged)).getAsJsonObject()
                .getAsJsonArray("pipe_types").size() == 4, "Repeated loading must not duplicate pipe IDs");
        helper.assertTrue(JsonParser.parseString(CurvyPipesCompat.withFlowlinePipes("ae2: {cables: Disable}\n"))
                .getAsJsonObject().getAsJsonArray("pipe_types").size() == 3, "Integration-only configs must work");
        helper.succeed();
    }
}
