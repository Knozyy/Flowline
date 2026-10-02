package com.knozyy.flowline.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.MissingMappingsEvent;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.CoreSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import com.knozyy.flowline.pipe.PipeBlock;

/** Supplies native Curvy pipe definitions; Curvy owns placement, meshes, physics, menus and transport. */
@Mod.EventBusSubscriber(modid = "flowline")
public final class CurvyPipesCompat {
    public static final List<String> TYPES = List.of("item", "fluid", "energy");
    private static final Map<String, Item> MATERIAL_ITEMS = new LinkedHashMap<>();
    private static final Map<Item, PipeBlock> BLOCKS = new java.util.IdentityHashMap<>();
    private static final Gson CONFIG_JSON = new GsonBuilder().registerTypeAdapter(Double.class, new TypeAdapter<Double>() {
        @Override public void write(JsonWriter out, Double value) throws IOException {
            // Curvy's YAML parser rejects some JSON exponent spellings; emit plain decimal numbers.
            out.jsonValue(BigDecimal.valueOf(value).toPlainString());
        }
        @Override public Double read(JsonReader in) throws IOException { return in.nextDouble(); }
    }).create();
    private CurvyPipesCompat() {}

    public static boolean available() { return ModList.get().isLoaded("curvy_pipes"); }

    public static ResourceLocation id(String type) {
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unsupported Curvy pipe: " + type);
        return new ResourceLocation("curvy_pipes", "flowline_" + type + "_pipe");
    }

    public static Item item(String type) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation("flowline", type + "_pipe"));
    }

    /** Native palette IDs remain stable, but their material is the ordinary Flowline item. */
    public static Item resolveMaterial(String nativeId) {
        for (String type : TYPES) if (id(type).toString().equals(nativeId)) return item(type);
        return null;
    }

    public static Item pipeItem(PipeBlock block, String path) {
        String type = path.replace("_pipe", "");
        if (!available() || !TYPES.contains(type)) return new BlockItem(block, new Item.Properties());
        Item item = MATERIAL_ITEMS.get(type);
        if (item == null) throw new IllegalStateException("Curvy must register before Flowline: " + path);
        BLOCKS.put(item, block);
        Item.BY_BLOCK.put(block, item);
        return item;
    }

    public static PipeBlock block(Item item) {
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof PipeBlock pipe) return pipe;
        return BLOCKS.get(item);
    }

    public static boolean supported(ItemStack stack) { return BLOCKS.containsKey(stack.getItem()); }

    public static boolean pipe(ItemStack stack) {
        return block(stack.getItem()) != null;
    }

    /** Forge rebuilds this map from BlockItems during registry synchronization. Restore native pipe entries. */
    public static void bindBlocks() { BLOCKS.forEach((item, block) -> Item.BY_BLOCK.put(block, item)); }

    @SubscribeEvent
    public static void remapPreviousItems(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, "curvy_pipes")) {
            Item material = resolveMaterial(mapping.getKey().toString());
            if (material != null) mapping.remap(material);
        }
    }

    /** Curvy creates its other items normally; Flowline's palette entries reuse existing items. */
    @SuppressWarnings("unchecked")
    public static IForgeRegistry<Item> materialRegistry(IForgeRegistry<Item> registry) {
        return (IForgeRegistry<Item>) Proxy.newProxyInstance(IForgeRegistry.class.getClassLoader(),
                new Class<?>[] {IForgeRegistry.class}, (proxy, method, args) -> {
                    if (method.getName().equals("register") && args != null && args.length == 2) {
                        String path = args[0] instanceof ResourceLocation id ? id.getPath() : args[0].toString();
                        for (String type : TYPES) if (id(type).getPath().equals(path)) {
                            Item item = (Item) args[1];
                            MATERIAL_ITEMS.put(type, item);
                            registry.register(new ResourceLocation("flowline", type + "_pipe"), item);
                            return null;
                        }
                    }
                    try { return method.invoke(registry, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
    }

    /** Transform only the in-memory config. The user's Curvy YAML is never rewritten. */
    public static String withFlowlinePipes(String yaml) {
        Object parsed = new Load(LoadSettings.builder().setSchema(new CoreSchema())
                .setLabel("Curvy Pipes config").build()).loadFromString(yaml);
        if (!(parsed instanceof Map<?, ?> source)) throw new IllegalArgumentException("Curvy config must be a mapping");
        Map<String, Object> config = new LinkedHashMap<>();
        source.forEach((key, value) -> config.put((String) key, value));
        Object existing = config.get("pipe_types");
        if (existing != null && !(existing instanceof List<?>))
            throw new IllegalArgumentException("Curvy pipe_types must be a list");
        List<Object> pipes = new ArrayList<>(existing == null ? List.of() : (List<?>) existing);
        for (String type : TYPES) {
            String name = id(type).getPath();
            // Reserve these IDs so every client/server gets the same native pipe palette.
            pipes.removeIf(value -> value instanceof Map<?, ?> pipe && name.equals(pipe.get("id")));
            String variant = switch (type) { case "item" -> "Item"; case "fluid" -> "Fluid"; default -> "Energy"; };
            double rate = switch (type) { case "item" -> 16.0 / 30; case "fluid" -> 1000.0 / 30; default -> 8000; };
            pipes.add(Map.of("id", name, "name", "Flowline " + variant + " Pipe",
                    "texture", "flowline:block/curvy_" + type + "_pipe", "diameter", 0.2,
                    "variant", Map.of(variant, Map.of("rate", rate))));
        }
        config.put("pipe_types", pipes);
        // JSON is a YAML subset accepted by Curvy's serde_yaml parser.
        return CONFIG_JSON.toJson(config);
    }
}
