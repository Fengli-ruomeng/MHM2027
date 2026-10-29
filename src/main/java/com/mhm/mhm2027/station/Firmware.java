package com.mhm.mhm2027.station;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Every firmware the Flash Station can write, with its costs from the design's progression
 * table. Firmware goes up one level at a time. Null Firmware can be flashed from any level and
 * Restore Firmware is the only way back from Null, to LV0.
 */
public enum Firmware {
    LV1(1, 4, 5),
    LV2(2, 8, 8),
    LV3(3, 12, 10),
    LV4(4, 16, 15),
    LV5(5, 24, 20),
    LV6(6, 32, 25),
    LV7(7, 48, 30),
    LV8(8, 64, 35),
    LV9(9, 96, 40),
    LV10(10, 128, 50),
    NULL(-1, 0, 1),
    RESTORE(0, 0, 5);

    /** Why a firmware can't be flashed: a lang key suffix and the level it names, if any. */
    public record Refusal(String key, int level) {
    }

    private final int target;
    private final int shards;
    private final int xpLevels;

    Firmware(int target, int shards, int xpLevels) {
        this.target = target;
        this.shards = shards;
        this.xpLevels = xpLevels;
    }

    /** The gateway level after flashing. */
    public int target() {
        return target;
    }

    /** Data Shards the station takes when the flash completes. */
    public int shards() {
        return shards;
    }

    /** XP levels the station takes when the flash completes. */
    public int xpLevels() {
        return xpLevels;
    }

    /** The item's registry path: firmware_lv1 to firmware_lv10, null_firmware, restore_firmware. */
    public String itemName() {
        return switch (this) {
            case NULL -> "null_firmware";
            case RESTORE -> "restore_firmware";
            default -> "firmware_" + name().toLowerCase(Locale.ROOT);
        };
    }

    /** Why this firmware can't go onto a gateway at {@code currentLevel}, or null if it can. */
    public @Nullable Refusal refusal(int currentLevel) {
        boolean isNull = currentLevel < 0;
        return switch (this) {
            case NULL -> isNull ? new Refusal("already_null", 0) : null;
            case RESTORE -> isNull ? null : new Refusal("not_null", 0);
            default -> {
                if (isNull) {
                    yield new Refusal("null_gateway", 0);
                }
                if (currentLevel >= target) {
                    yield new Refusal("already_level", target);
                }
                yield currentLevel == target - 1 ? null : new Refusal("wrong_level", target - 1);
            }
        };
    }
}
