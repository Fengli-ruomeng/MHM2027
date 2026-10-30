package com.mhm.mhm2027.terminal;

/** Small rules behind program events, kept apart so they can be unit-tested. */
public final class EventRules {
    /** on_low_health fires below this share of max health until a program sets its own threshold. */
    public static final double DEFAULT_LOW_HEALTH_SHARE = 0.3;

    private EventRules() {
    }

    /** The health on_low_health fires below: the program's own threshold, or 30% of max health. */
    public static double lowHealthThreshold(double set, double maxHealth) {
        return Double.isNaN(set) ? maxHealth * DEFAULT_LOW_HEALTH_SHARE : set;
    }
}
