package com.knozyy.flowline.curve;

/** Which part of a curved pipe a mesh quad belongs to; it picks the texture and the render layer. */
public enum MeshPart {
    /** Metal corner rails, tinted in the pipe's colour. */
    RAIL,
    /** Glass panels, outside faces. */
    GLASS,
    /** Glass panels seen from inside, so the far panels show too. */
    GLASS_INNER,
    /** The collar where an inserting end meets its block. */
    COLLAR,
    /** Metal bands: an extracting end's ring and the bands along a duct. */
    RING,
    /** A joint's round metal body, like a valve where the pipes meet. */
    JOINT,
    /** The metal flange where a pipe goes into a joint. */
    FLANGE
}
