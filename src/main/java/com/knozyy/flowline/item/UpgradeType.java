package com.knozyy.flowline.item;

/** What an upgrade adds to the side it is installed on. Up to six upgrades per side, in any mix. */
public enum UpgradeType {
    /** Shorter starting interval. */
    SPEED(1, 0),
    /** More moved per operation. */
    STACK(0, 1),
    /** Counts as one Speed and one Stack. */
    KNOZY(1, 1);

    public final int speed;
    public final int stack;

    UpgradeType(int speed, int stack) {
        this.speed = speed;
        this.stack = stack;
    }
}
