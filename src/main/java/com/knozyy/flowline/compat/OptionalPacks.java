package com.knozyy.flowline.compat;

import com.knozyy.flowline.Flowline;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;

/**
 * Data that only makes sense with another mod, shipped as always-on server data packs inside the jar
 * ({@code optional/<mod>/}, made by tools/gen_resources.py) and added only when that mod is loaded. Forge 1.20.1 has no
 * load conditions for loot tables, so the Chemical Pipe's table cannot sit in the main data.
 */
public final class OptionalPacks {
    private OptionalPacks() {}

    public static void add(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        if (ModList.get().isLoaded(ChemicalCompat.MEKANISM)) addPack(event, ChemicalCompat.MEKANISM);
    }

    private static void addPack(AddPackFindersEvent event, String mod) {
        Path path = ModList.get().getModFileById(Flowline.MODID).getFile().findResource("optional/" + mod);
        Pack pack = Pack.readMetaAndCreate(Flowline.MODID + ":" + mod, Component.literal("Flowline: " + mod + " data"),
                true, id -> new PathPackResources(id, path, true), PackType.SERVER_DATA, Pack.Position.TOP,
                PackSource.BUILT_IN);
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
