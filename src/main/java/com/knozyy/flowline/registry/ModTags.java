package com.knozyy.flowline.registry;

import com.knozyy.flowline.Flowline;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Tags that data packs (and KubeJS tag events) can fill. All start empty. */
public final class ModTags {
    /** Blocks no pipe connects to, whatever capabilities they have. */
    public static final TagKey<Block> NO_CONNECT = block("no_connect");

    private ModTags() {}

    private static TagKey<Block> block(String name) {
        return TagKey.create(Registries.BLOCK, new ResourceLocation(Flowline.MODID, name));
    }
}
