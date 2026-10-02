package com.knozyy.flowline.client;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.FlowlineConfig;
import com.knozyy.flowline.curve.*;
import com.knozyy.flowline.item.CurvePipeItem;
import com.knozyy.flowline.item.WrenchItem;
import com.knozyy.flowline.network.*;
import com.knozyy.flowline.pipe.PipeBlock;
import com.knozyy.flowline.pipe.PipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static com.knozyy.flowline.network.CurveActionPayload.Action;

@Mod.EventBusSubscriber(modid=Flowline.MODID,value=Dist.CLIENT)
public final class CurveClient {
    public static final Map<Integer,CurveViewPayload.Node> NODES=new LinkedHashMap<>();
    public static final Map<Integer,CurveViewPayload.Edge> EDGES=new LinkedHashMap<>();
    private static ClientLevel level;
    public static int cursor, moving;
    private static int gridIndex;
    private static double distance=4;
    private static long requested;
    private static boolean waiting, limited;
    private static boolean useHeld;
    private static PipeType held;
    private CurveClient() {}
    public static PipeType type(){
        var player=Minecraft.getInstance().player;
        return player!=null&&player.getMainHandItem().getItem() instanceof CurvePipeItem item?((PipeBlock)item.getBlock()).type():null;
    }
    public static CurveGeometry.Shape shape(CurveViewPayload.Edge e){
        var a=NODES.get(e.a());var b=NODES.get(e.b());return a==null||b==null?null:new CurveGeometry.Shape(a.point(),b.point(),e.ta(),e.tb());
    }
    public static void clear(){NODES.clear();EDGES.clear();CurveRenderer.clear();level=null;cursor=moving=0;waiting=useHeld=false;requested=0;held=null;}
    public static void receive(CurveViewPayload payload){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(payload.dimension()))return;
        NODES.clear();for(var n:payload.nodes())NODES.put(n.id(),n);EDGES.clear();for(var e:payload.edges())EDGES.put(e.id(),e);
        cursor=payload.cursor();limited=payload.limited();waiting=false;CurveRenderer.update();
    }
    public static void flow(CurveFlowPayload payload){
        var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.dimension().location().equals(payload.dimension()))CurveRenderer.flow(payload);
    }
    public static void tick(){
        var mc=Minecraft.getInstance();if(mc.level!=level){clear();level=mc.level;}if(mc.level==null||mc.player==null)return;
        if(!mc.options.keyUse.isDown())useHeld=false;
        if(type()!=held){if(cursor!=0)send(CurveActionPayload.simple(Action.CANCEL,0));moving=0;held=type();}
        if(mc.level.getGameTime()<requested||mc.level.getGameTime()-requested>=40){requested=mc.level.getGameTime();ModNetwork.sendToServer(CurveActionPayload.simple(Action.REQUEST,0));}
        while(Keys.CURVE_CANCEL.consumeClick())if(mc.screen==null&&(cursor!=0||moving!=0)){moving=0;send(CurveActionPayload.simple(Action.CANCEL,0));}
        while(Keys.CURVE_FINISH.consumeClick())if(mc.screen==null&&cursor!=0)send(CurveActionPayload.simple(Action.CANCEL,0));
        CurveRenderer.tick();
    }
    public static void send(CurveActionPayload action){waiting=true;ModNetwork.sendToServer(action);}
    public static CurvePicking.Hit pick(){
        var mc=Minecraft.getInstance();if(mc.player==null)return null;
        HitResult block=mc.player.pick(CurveGeometry.REACH,1,false);
        double limit=block.getType()==HitResult.Type.BLOCK?mc.player.getEyePosition().distanceTo(block.getLocation()):CurveGeometry.REACH;
        return CurvePicking.pick(mc.player.getEyePosition(),mc.player.getEyePosition().add(mc.player.getLookAngle().scale(CurveGeometry.REACH)),limit,NODES.values(),EDGES.values(),CurveClient::shape);
    }
    public static CurveActionPayload location(Action action,int id){
        var mc=Minecraft.getInstance();HitResult hit=mc.player.pick(distance,1,false);
        if(hit instanceof BlockHitResult bh&&hit.getType()==HitResult.Type.BLOCK){
            Vec3 point=bh.getLocation().add(Vec3.atLowerCornerOf(bh.getDirection().getNormal()).scale(0.10));
            return new CurveActionPayload(action,id,point,bh.getBlockPos(),bh.getDirection(),true);
        }
        double grid=new double[]{0,0.125,0.25,0.5}[gridIndex];
        Vec3 point=CurveGeometry.snap(mc.player.getEyePosition().add(mc.player.getLookAngle().scale(distance)),grid);
        return new CurveActionPayload(action,id,point,null,Direction.UP,false);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void input(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||mc.player.isSpectator())return;
        PipeType type=type();var hit=pick();boolean tool=WrenchItem.isWrench(mc.player.getMainHandItem())||mc.player.getMainHandItem().isEmpty();
        if(event.isPickBlock()&&type!=null){gridIndex=(gridIndex+(mc.player.isShiftKeyDown()?3:1))%4;event.setCanceled(true);event.setSwingHand(false);return;}
        if(event.isAttack()&&hit!=null&&(type!=null||tool)){
            if(!waiting)send(CurveActionPayload.simple(hit.node()!=0?Action.REMOVE_NODE:Action.REMOVE_EDGE,hit.node()!=0?hit.node():hit.edge()));
            event.setCanceled(true);event.setSwingHand(false);return;
        }
        if(!event.isUseItem()||event.getHand()!=InteractionHand.MAIN_HAND)return;
        if(useHeld&&(type!=null||hit!=null&&tool)){event.setCanceled(true);event.setSwingHand(false);return;}
        if(type!=null&&!mc.player.isShiftKeyDown()||hit!=null&&tool)useHeld=true;
        if(type==null){
            if(hit!=null&&tool){
                if(hit.node()!=0&&NODES.get(hit.node()).block()!=null&&!Screen.hasControlDown())send(CurveActionPayload.simple(mc.player.isShiftKeyDown()?Action.MODE:Action.CONFIG,hit.node()));
                else mc.setScreen(new CurveEditScreen(hit));event.setCanceled(true);event.setSwingHand(false);
            }return;
        }
        if(mc.player.isShiftKeyDown())return;
        event.setCanceled(true);event.setSwingHand(false);if(waiting)return;
        if(moving!=0){int id=moving;moving=0;send(location(Action.MOVE,id));return;}
        if(hit!=null&&Screen.hasControlDown()){mc.setScreen(new CurveEditScreen(hit));return;}
        if(hit!=null&&hit.node()!=0&&NODES.get(hit.node()).type()==type){send(new CurveActionPayload(cursor==0?Action.SELECT:Action.CONNECT,hit.node(),Vec3.ZERO,null,Direction.UP,cursor!=0));return;}
        send(location(Action.ADD,0));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void scroll(InputEvent.MouseScrollingEvent event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null||type()==null||mc.player.isShiftKeyDown())return;
        if(event.getScrollDelta()==0)return;distance=Math.max(1,Math.min(7.5,distance+Math.signum(event.getScrollDelta())*0.5));event.setCanceled(true);
    }
    public static void hud(GuiGraphics g){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui||type()==null)return;
        var key=mc.options.keyUse.getTranslatedKeyMessage();
        Component line=Component.translatable(moving!=0?"hud.flowline.curve.move":cursor==0?"hud.flowline.curve.start":"hud.flowline.curve.continue",key);
        Component detail=Component.translatable("hud.flowline.curve.tools",Keys.CURVE_FINISH.getTranslatedKeyMessage(),Keys.CURVE_CANCEL.getTranslatedKeyMessage(),new double[]{0,0.125,0.25,0.5}[gridIndex]);
        int maxWidth=Math.max(80,g.guiWidth()-32);
        var header=mc.font.split(line,maxWidth);var help=mc.font.split(detail,maxWidth);
        int w=java.util.stream.Stream.concat(header.stream(),help.stream()).mapToInt(mc.font::width).max().orElse(0);
        int x=(g.guiWidth()-w)/2,y=g.guiHeight()/2+24;
        g.fill(x-7,y-4,x+w+7,y+(header.size()+help.size())*11+3,0xB8202428);
        for(var text:header){g.drawString(mc.font,text,(g.guiWidth()-mc.font.width(text))/2,y,0xE2E5E8,false);y+=11;}
        for(var text:help){g.drawString(mc.font,text,(g.guiWidth()-mc.font.width(text))/2,y,0xA6AFB8,false);y+=11;}
        if(limited)g.drawString(mc.font,Component.translatable("hud.flowline.curve.limited"),8,8,0xD4B779,false);
    }
}
