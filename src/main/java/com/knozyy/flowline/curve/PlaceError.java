package com.knozyy.flowline.curve;

import java.util.Locale;
import java.util.Optional;

/** Why a curved placement or edit was refused, by code. */
public enum PlaceError {
    INVALID_SHAPE(1),
    INVALID_OBJECT(2),
    TOO_COSTLY(3),
    TOO_FAR(4),
    INTERSECT_BLOCK(5),
    INTERSECT_PIPE(6),
    ANGLE_TOO_SMALL(7),
    NODE_IN_JOINT(8);

    private final int code;

    PlaceError(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Optional<PlaceError> byCode(int code) {
        for (PlaceError error : values()) if (error.code == code) return Optional.of(error);
        return Optional.empty();
    }

    /** The translation key of the message shown to the player. */
    public String key() {
        return "message.flowline.curve." + name().toLowerCase(Locale.ROOT);
    }
}
