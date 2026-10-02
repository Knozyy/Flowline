package com.knozyy.flowline.client;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.compat.CurvyPipesCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;

public final class BuildHint {
    private static long firstShownAt;
    private static final long DISPLAY_NANOS = 8_000_000_000L;
    private BuildHint() {}
    public static void render(GuiGraphics graphics) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui||!FlowlineConfig.isLoaded()
                ||!FlowlineConfig.Client.SPEC.isLoaded()||mc.player.isSpectator())return;
        if (firstShownAt == 0 && FlowlineConfig.Client.BUILD_HINT_SEEN.get()) return;
        long now = System.nanoTime();
        if (firstShownAt != 0 && now - firstShownAt >= DISPLAY_NANOS) return;
        boolean off=CurvyPipesCompat.pipe(mc.player.getOffhandItem());
        boolean main=CurvyPipesCompat.pipe(mc.player.getMainHandItem());
        if(!off&&!main)return;
        HitResult hit=mc.player.pick(FlowlineConfig.BUILD_RANGE.get(),1,false);
        if(hit.getType()!=HitResult.Type.BLOCK||hit.getLocation().distanceTo(mc.player.getEyePosition())<5)return;
        if (firstShownAt == 0) {
            firstShownAt = now;
            FlowlineConfig.Client.BUILD_HINT_SEEN.set(true);
            FlowlineConfig.Client.SPEC.save();
        }
        Component line=Component.translatable(off?"hud.flowline.build.ready":"hud.flowline.build.offhand",Keys.BUILD.getTranslatedKeyMessage());
        var lines=mc.font.split(line,Math.max(80,graphics.guiWidth()-32));
        int w=lines.stream().mapToInt(mc.font::width).max().orElse(0),x=(graphics.guiWidth()-w)/2;
        int y=graphics.guiHeight()/2-36-lines.size()*11;
        graphics.fill(x-7,y-4,x+w+7,y+lines.size()*11+3,0xB8202428);
        for(var text:lines){graphics.drawString(mc.font,text,(graphics.guiWidth()-mc.font.width(text))/2,y,0xE2E5E8,false);y+=11;}
    }
}
