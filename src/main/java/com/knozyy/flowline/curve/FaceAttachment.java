package com.knozyy.flowline.curve;

/** A node fixed to face {@code face} (Minecraft's Direction order: 0 down … 5 east) of the block at x, y, z. */
public record FaceAttachment(int x, int y, int z, int face) {}
