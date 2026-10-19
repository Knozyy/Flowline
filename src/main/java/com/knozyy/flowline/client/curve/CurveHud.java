package com.knozyy.flowline.client.curve;

import com.knozyy.flowline.curve.PlaceError;
import com.knozyy.flowline.network.curve.CurveResultPayload;
import org.jetbrains.annotations.Nullable;

/** What the HUD says about the server's answer to an edit; pure, so a GameTest can check it. */
public final class CurveHud {
    private CurveHud() {}

    /** The translation key for a server result, or null when the edit went through. */
    @Nullable
    public static String resultKey(CurveResultPayload result) {
        if (result.denied()) return "message.flowline.curve.denied";
        if (result.code() == 0) return null;
        return PlaceError.byCode(result.code()).map(PlaceError::key).orElse(PlaceError.INVALID_OBJECT.key());
    }
}
