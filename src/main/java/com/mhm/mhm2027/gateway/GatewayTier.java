package com.mhm.mhm2027.gateway;

/**
 * Hardware stats for each gateway level, from the "Gateway tiers" table in Design Draft v2.
 * Hook points come from the Cyber system tab (2 at LV0-2, 3 at LV3-5, 4 at LV6+).
 * All values are starting numbers for playtesting.
 */
public enum GatewayTier {
    NULL(-1, "Null", 0, 0, 0, 0, 0, 0),
    LV0(0, "Default", 128, 25, 1, 1, 0, 2),
    LV1(1, "Entry", 256, 45, 3, 2, 0, 2),
    LV2(2, "Entry", 384, 60, 5, 2, 0, 2),
    LV3(3, "Entry", 512, 85, 8, 2, 0, 3),
    LV4(4, "Mid", 768, 100, 10, 4, 1, 3),
    LV5(5, "Mid", 1024, 120, 12, 4, 1, 3),
    LV6(6, "Mid", 1536, 135, 15, 4, 1, 4),
    LV7(7, "Advanced", 2560, 155, 20, 4, 1, 4),
    LV8(8, "Advanced", 4096, 175, 30, 4, 1, 4),
    LV9(9, "Advanced", 7168, 195, 50, 4, 1, 4),
    LV10(10, "Apex", 12288, 300, 100, 8, 2, 4),
    LV11(11, "Creative", 1024 * 1024, 10_000, 10 * 1024, 16, 8, 4);

    public static final int MIN_LEVEL = -1;
    public static final int MAX_LEVEL = 11;

    private final int level;
    private final String band;
    private final int ramMb;
    private final int tops;
    private final int bufferGb;
    private final int standardSlots;
    private final int overloadSlots;
    private final int hookPoints;

    GatewayTier(int level, String band, int ramMb, int tops, int bufferGb, int standardSlots, int overloadSlots, int hookPoints) {
        this.level = level;
        this.band = band;
        this.ramMb = ramMb;
        this.tops = tops;
        this.bufferGb = bufferGb;
        this.standardSlots = standardSlots;
        this.overloadSlots = overloadSlots;
        this.hookPoints = hookPoints;
    }

    public static GatewayTier of(int level) {
        int clamped = Math.clamp(level, MIN_LEVEL, MAX_LEVEL);
        return values()[clamped - MIN_LEVEL];
    }

    public int level() {
        return level;
    }

    public String band() {
        return band;
    }

    public int ramMb() {
        return ramMb;
    }

    public int tops() {
        return tops;
    }

    /** Buffer is stored in MB (1 GB absorbs 1,024 MB of pressure). */
    public int bufferMb() {
        return bufferGb * 1024;
    }

    public int standardSlots() {
        return standardSlots;
    }

    public int overloadSlots() {
        return overloadSlots;
    }

    public int hookPoints() {
        return hookPoints;
    }

    /** LV-1 has no cyber layer at all: no programs, no gear, can't be hacked. */
    public boolean isNull() {
        return this == NULL;
    }

    public String label() {
        return "LV" + level;
    }
}
