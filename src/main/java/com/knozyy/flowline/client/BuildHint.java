package com.knozyy.flowline.client;

import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.item.CurvePipeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;

public final class BuildHint {
    private BuildHint() {}
    public static void render(GuiGraphics graphics) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui||!FlowlineConfig.isLoaded()||mc.player.isSpectator())return;
        boolean off=mc.player.getOffhandItem().getItem() instanceof CurvePipeItem;
        boolean main=mc.player.getMainHandItem().getItem() instanceof CurvePipeItem;
        if(!off&&!main)return;
        HitResult hit=mc.player.pick(FlowlineConfig.BUILD_RANGE.get(),1,false);
        if(hit.getType()!=HitResult.Type.BLOCK||hit.getLocation().distanceTo(mc.player.getEyePosition())<5)return;
        Component line=Component.translatable(off?"hud.flowline.build.ready":"hud.flowline.build.offhand",Keys.BUILD.getTranslatedKeyMessage());
        var lines=mc.font.split(line,Math.max(80,graphics.guiWidth()-32));
        int w=lines.stream().mapToInt(mc.font::width).max().orElse(0),x=(graphics.guiWidth()-w)/2;
        int y=graphics.guiHeight()/2-36-lines.size()*11;
        graphics.fill(x-7,y-4,x+w+7,y+lines.size()*11+3,0xB8202428);
        for(var text:lines){graphics.drawString(mc.font,text,(graphics.guiWidth()-mc.font.width(text))/2,y,0xE2E5E8,false);y+=11;}
    }
}
