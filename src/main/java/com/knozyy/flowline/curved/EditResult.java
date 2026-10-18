package com.knozyy.flowline.curved;

import com.knozyy.flowline.curve.PlaceError;
import org.jetbrains.annotations.Nullable;

/**
 * How a curved pipe edit ended: done, refused for one of Curvy's reasons ({@code missing} pipes when too costly), or
 * refused by protection.
 */
public record EditResult(@Nullable PlaceError error, boolean denied, int missing) {
    public static final EditResult OK = new EditResult(null, false, 0);
    public static final EditResult DENIED = new EditResult(null, true, 0);

    public static EditResult error(PlaceError error) {
        return new EditResult(error, false, 0);
    }

    public static EditResult missing(int pipes) {
        return new EditResult(PlaceError.TOO_COSTLY, false, pipes);
    }

    public boolean ok() {
        return error == null && !denied;
    }
}
