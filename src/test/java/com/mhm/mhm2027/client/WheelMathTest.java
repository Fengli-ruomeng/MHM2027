package com.mhm.mhm2027.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WheelMathTest {
    @Test
    void slotsGoClockwiseFromTheTop() {
        assertEquals(0, WheelMath.slotAt(0, -20, 8));
        assertEquals(1, WheelMath.slotAt(14, -14, 8));
        assertEquals(2, WheelMath.slotAt(20, 0, 8));
        assertEquals(4, WheelMath.slotAt(0, 20, 8));
        assertEquals(6, WheelMath.slotAt(-20, 0, 8));
        assertEquals(7, WheelMath.slotAt(-14, -14, 8));
        // Just left of straight up is still the top slot.
        assertEquals(0, WheelMath.slotAt(-3, -20, 8));
    }

    @Test
    void theDeadZonePicksNothing() {
        assertEquals(-1, WheelMath.slotAt(0, 0, 8));
        assertEquals(-1, WheelMath.slotAt(5, -5, 8));
    }

    @Test
    void positionsAndSlotsAgree() {
        for (int slot = 0; slot < WheelMath.SLOTS; slot++) {
            double[] at = WheelMath.position(slot, 60);
            assertEquals(slot, WheelMath.slotAt(at[0], at[1], 8));
        }
    }

    @Test
    void labelsNameTheProgram() {
        assertEquals("BREACH", WheelMath.label("run Breach"));
        assertEquals("ADRENALINE SHOT", WheelMath.label("run Adrenaline Shot.lua"));
        assertEquals("STOP WATCHER", WheelMath.label("stop Watcher"));
        assertEquals("EMPTY", WheelMath.label("  "));
    }
}
