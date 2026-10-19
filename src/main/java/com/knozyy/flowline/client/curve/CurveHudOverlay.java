package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.client.ClientCurves;
import com.knozyy.flowline.client.Keys;
import com.knozyy.flowline.client.ui.Theme;
import com.knozyy.flowline.curve.ChainPreview;
import com.knozyy.flowline.network.curve.CurveResultPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The lines under the crosshair while curved placement is in hand: the mode, what a right-click does, the grid, the
 * chain's cost, and for three seconds what the server said about the last edit ({@link CurveHud#resultKey}). The
 * client's own preview only colours the ghost tube; refusals shown as text are always the server's.
 */
public final class CurveHudOverlay {
    private static final long RESULT_MILLIS = 3000;
    private static final int BAD = 0xFFE05A5A;
    @Nullable
    private static CurveResultPayload shown;
    private static long shownAt;

    private CurveHudOverlay() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
        CurveResultPayload result = ClientCurves.INSTANCE.lastResult;
        if (result != shown) {
            shown = result;
            shownAt = Util.getMillis();
        }
        String key = shown == null || Util.getMillis() - shownAt > RESULT_MILLIS ? null : CurveHud.resultKey(shown);
        Component refusal = key == null ? null : Component.translatable(key, shown.missing());

        int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 14;
        if (!CurveInput.active(mc)) {
            if (refusal != null) centred(g, mc, refusal, cx, y, BAD);
            return;
        }
        Component name = Component.translatable("hud.flowline.mode.curvy");
        Component hint = Component.translatable(hintKey(), Keys.OPTIONS.getTranslatedKeyMessage());
        Component grid = Component.translatable("hud.flowline.grid", Component.translatable(CurvePlacer.grid().key()));
        ChainPreview preview = CurvePreview.current();
        Component cost = null;
        boolean short_ = false;
        if (preview != null && !preview.spans().isEmpty()) {
            int have = mc.player.getAbilities().instabuild ? Integer.MAX_VALUE : mc.player.getOffhandItem().getCount();
            short_ = preview.cost() > have;
            cost = Component.translatable("hud.flowline.curve.cost", preview.cost());
        }
        Component keys = Component.translatable("hud.flowline.curve.keys", Keys.BUILD.getTranslatedKeyMessage(),
                Keys.OPTIONS.getTranslatedKeyMessage());
        Component second = cost == null ? grid : grid.copy().append("  ·  ").append(cost);

        int nameW = mc.font.width(name) + 8;
        int textW = Math.max(Math.max(mc.font.width(hint), mc.font.width(second)), mc.font.width(keys));
        if (refusal != null) textW = Math.max(textW, mc.font.width(refusal));
        int width = nameW + 6 + textW, x = cx - width / 2;
        int rows = refusal == null ? 3 : 4;
        g.fill(x - 5, y - 4, x + width + 5, y + rows * 11, 0xC8000000 | (Theme.BG & 0xFFFFFF));
        int accent = 0xFF000000 | Theme.accent(CurvePlacer.drawingTypeOr(CurveInput.offhandType(mc)));
        g.fill(x, y - 1, x + nameW, y + 10, accent);
        g.drawString(mc.font, name, x + 4, y + 1, Theme.ON_ACCENT, false);
        int tx = x + nameW + 6;
        g.drawString(mc.font, hint, tx, y + 1, Theme.TEXT, false);
        g.drawString(mc.font, grid, tx, y + 12, Theme.MUTED, false);
        if (cost != null) {
            g.drawString(mc.font, cost, tx + mc.font.width(grid.copy().append("  ·  ")), y + 12, short_ ? BAD : Theme.MUTED, false);
        }
        g.drawString(mc.font, keys, tx, y + 23, Theme.MUTED, false);
        if (refusal != null) g.drawString(mc.font, refusal, tx, y + 34, BAD, false);
    }

    private static String hintKey() {
        if (CurvePlacer.moving() >= 0) return "hud.flowline.curve.moving";
        if (!CurvePlacer.drawing()) return "hud.flowline.curve.start";
        CurveTarget target = CurveHighlight.current();
        return target != null && target.kind == CurveTarget.Kind.AIR ? "hud.flowline.curve.add" : "hud.flowline.curve.finish";
    }

    private static void centred(GuiGraphics g, Minecraft mc, Component text, int cx, int y, int colour) {
        int w = mc.font.width(text);
        g.fill(cx - w / 2 - 5, y - 4, cx + w / 2 + 5, y + 11, 0xC8000000 | (Theme.BG & 0xFFFFFF));
        g.drawString(mc.font, text, cx - w / 2, y, colour, false);
    }
}
