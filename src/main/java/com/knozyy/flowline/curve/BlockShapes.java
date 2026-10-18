package com.knozyy.flowline.curve;

import java.util.List;

/** The world as the validator sees it: each block cell's collision and outline boxes, in world coordinates. */
public interface BlockShapes {
    List<Aabb> collision(int x, int y, int z);

    List<Aabb> outline(int x, int y, int z);
}
