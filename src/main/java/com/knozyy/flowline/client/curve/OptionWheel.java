package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.Flowline;
import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.client.Keys;
import com.knozyy.flowline.client.ui.Theme;
import com.knozyy.flowline.curve.CurveNode;
import com.knozyy.flowline.network.ModNetwork;
import com.knozyy.flowline.network.curve.CurveBreakPayload;
import com.knozyy.flowline.network.curve.CurveEndPayload;
import com.knozyy.flowline.network.curve.CurveForceJointPayload;
import com.knozyy.flowline.network.curve.CurveInsertPayload;
import com.knozyy.flowline.network.curve.CurveRemovePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Curvy's option wheel: hold the options key (left Alt) with curved placement in hand, scroll to pick, let go to apply
 * it to the node or edge under the crosshair.
 */
@Mod.EventBusSubscriber(modid = Flowline.MODID, value = Dist.CLIENT)
public final class OptionWheel {
    public enum Option {
        REMOVE, BRANCH, EXTEND, MOVE, FORCE_JOINT, SETTINGS;

        public String key() {
            return "hud.flowline.wheel." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static int selected;
    private static boolean open;

    private OptionWheel() {}

    public static boolean open() {
        return open;
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!open || event.getScrollDelta() == 0) return;
        int n = Option.values().length;
        selected = Math.floorMod(selected + (event.getScrollDelta() > 0 ? -1 : 1), n);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.screen == null && CurveInput.active(mc) && CurveInput.held(mc, Keys.OPTIONS);
        if (open && !down && mc.screen == null) apply(mc, Option.values()[selected]);
        open = down;
    }

    private static void apply(Minecraft mc, Option option) {
        CurveTarget target = CurveHighlight.current();
        if (target == null || mc.player == null) return;
        boolean node = target.kind == CurveTarget.Kind.NODE, edge = target.kind == CurveTarget.Kind.EDGE;
        switch (option) {
            case REMOVE -> {
                if (node) ModNetwork.sendToServer(new CurveRemovePayload(CurvePlacer.nextRequest(), target.nodeId));
                if (edge) ModNetwork.sendToServer(new CurveBreakPayload(CurvePlacer.nextRequest(), target.edgeId));
            }
            case BRANCH, EXTEND -> {
                // on an edge, branching first puts a node where it is pointed at; draw from that node next
                if (edge && option == Option.BRANCH) {
                    ModNetwork.sendToServer(new CurveInsertPayload(CurvePlacer.nextRequest(), target.edgeId, target.segment,
                            target.along));
                }
                if (node && CurveInput.offhandType(mc) != null) CurvePlacer.begin(target, CurveInput.offhandType(mc));
            }
            case MOVE -> {
                if (node) CurvePlacer.startMoving(target.nodeId);
            }
            case FORCE_JOINT -> {
                if (!node) return;
                boolean forced = ClientCurves.INSTANCE.graph().node(target.nodeId).map(CurveNode::forceJoint).orElse(false);
                ModNetwork.sendToServer(new CurveForceJointPayload(CurvePlacer.nextRequest(), target.nodeId, !forced));
            }
            case SETTINGS -> {
                if (node) ModNetwork.sendToServer(new CurveEndPayload(CurvePlacer.nextRequest(), target.nodeId,
                        CurveEndPayload.Action.OPEN));
            }
        }
    }

    /** The six options to the right of the crosshair, the picked one filled. */
    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!open || mc.options.hideGui) return;
        Option[] options = Option.values();
        int x = g.guiWidth() / 2 + 16, y = g.guiHeight() / 2 - options.length * 6;
        int width = 0;
        for (Option o : options) width = Math.max(width, mc.font.width(Component.translatable(o.key())));
        g.fill(x - 4, y - 4, x + width + 8, y + options.length * 12 + 2, 0xC8000000 | (Theme.BG & 0xFFFFFF));
        int accent = 0xFF000000 | Theme.accent(CurvePlacer.drawingTypeOr(CurveInput.offhandType(mc)));
        for (int i = 0; i < options.length; i++) {
            int ty = y + i * 12;
            boolean on = i == selected;
            if (on) g.fill(x - 2, ty - 2, x + width + 6, ty + 10, accent);
            g.drawString(mc.font, Component.translatable(options[i].key()), x + 2, ty, on ? Theme.ON_ACCENT : Theme.TEXT, false);
        }
    }
}
