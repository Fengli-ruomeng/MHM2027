package com.mhm.mhm2027.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FirmwareTest {
    @Test
    void costsFollowTheProgressionTable() {
        int[][] table = {{4, 5}, {8, 8}, {12, 10}, {16, 15}, {24, 20}, {32, 25}, {48, 30}, {64, 35}, {96, 40}, {128, 50}};
        for (int level = 1; level <= 10; level++) {
            Firmware firmware = Firmware.valueOf("LV" + level);
            assertEquals(level, firmware.target());
            assertEquals(table[level - 1][0], firmware.shards(), "shards for LV" + level);
            assertEquals(table[level - 1][1], firmware.xpLevels(), "levels for LV" + level);
        }
    }

    @Test
    void firmwareGoesUpOneLevelAtATime() {
        assertNull(Firmware.LV1.refusal(0));
        assertNull(Firmware.LV4.refusal(3));
        assertEquals(new Firmware.Refusal("wrong_level", 3), Firmware.LV4.refusal(1));
        assertEquals(new Firmware.Refusal("already_level", 2), Firmware.LV2.refusal(2));
        assertEquals(new Firmware.Refusal("already_level", 10), Firmware.LV10.refusal(11), "creative gateways are above every firmware");
    }

    @Test
    void nullIsLeftOnlyThroughRestore() {
        assertNull(Firmware.NULL.refusal(0));
        assertNull(Firmware.NULL.refusal(7));
        assertEquals("already_null", Firmware.NULL.refusal(-1).key());
        assertEquals("null_gateway", Firmware.LV1.refusal(-1).key());
        assertNull(Firmware.RESTORE.refusal(-1));
        assertEquals(0, Firmware.RESTORE.target());
        assertEquals("not_null", Firmware.RESTORE.refusal(3).key());
    }

    @Test
    void itemNames() {
        assertEquals("firmware_lv10", Firmware.LV10.itemName());
        assertEquals("null_firmware", Firmware.NULL.itemName());
        assertEquals("restore_firmware", Firmware.RESTORE.itemName());
    }
}
