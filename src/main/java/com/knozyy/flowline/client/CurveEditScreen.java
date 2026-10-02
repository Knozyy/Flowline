package com.knozyy.flowline.client;

import com.knozyy.flowline.curve.CurvePicking;
import com.knozyy.flowline.network.CurveActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import static com.knozyy.flowline.network.CurveActionPayload.Action;

/** Small contextual editor; the world stays visible while choosing an operation. */
public final class CurveEditScreen extends Screen {
    private final CurvePicking.Hit hit;
    public CurveEditScreen(CurvePicking.Hit hit){super(Component.translatable("gui.flowline.curve.title"));this.hit=hit;}
    private record Choice(String label,Runnable action){}
    @Override protected void init(){
        List<Choice> choices=new ArrayList<>();boolean node=hit.node()!=0;
        if(node&&!CurveClient.NODES.containsKey(hit.node())||!node&&!CurveClient.EDGES.containsKey(hit.edge())){
            onClose();return;
        }
        if(node){
            if(CurveClient.type()!=null){
                choices.add(new Choice("branch",()->CurveClient.send(CurveActionPayload.simple(Action.SELECT,hit.node()))));
                choices.add(new Choice("move",()->CurveClient.moving=hit.node()));
            }
            choices.add(new Choice("joint",()->CurveClient.send(CurveActionPayload.simple(Action.JOINT,hit.node()))));
            if(CurveClient.NODES.get(hit.node()).block()!=null){
                choices.add(new Choice("config",()->CurveClient.send(CurveActionPayload.simple(Action.CONFIG,hit.node()))));
                choices.add(new Choice("mode",()->CurveClient.send(CurveActionPayload.simple(Action.MODE,hit.node()))));
            }
        }else if(CurveClient.type()!=null)choices.add(new Choice("insert",()->CurveClient.send(CurveActionPayload.simple(Action.INSERT,hit.edge()))));
        choices.add(new Choice("remove",()->CurveClient.send(CurveActionPayload.simple(node?Action.REMOVE_NODE:Action.REMOVE_EDGE,node?hit.node():hit.edge()))));
        choices.add(new Choice("close",()->{}));
        int y=height/2-choices.size()*12;
        for(Choice c:choices){addRenderableWidget(Button.builder(Component.translatable("gui.flowline.curve."+c.label),b->{onClose();c.action.run();}).bounds(width/2-86,y,172,20).build());y+=24;}
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        g.fill(width/2-96,height/2-116,width/2+96,height/2+116,0xE0202428);
        super.render(g,x,y,partial);
    }
    @Override public boolean isPauseScreen(){return false;}
}
