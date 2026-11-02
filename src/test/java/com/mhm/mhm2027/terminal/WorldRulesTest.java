package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldRulesTest {
    @Test
    void theClockWrapsIntoDaysAndATimeOfDay() {
        assertEquals(0, WorldRules.timeOfDay(0));
        assertEquals(0, WorldRules.day(0));
        assertEquals(6000, WorldRules.timeOfDay(3 * 24000L + 6000));
        assertEquals(3, WorldRules.day(3 * 24000L + 6000));
        assertEquals(23999, WorldRules.timeOfDay(24000L * 1_000_000 - 1), "a long-running world doesn't overflow");
        assertEquals(999_999, WorldRules.day(24000L * 1_000_000 - 1));
    }

    @Test
    void nightRunsFromDuskToDawn() {
        assertFalse(WorldRules.isNight(0));
        assertFalse(WorldRules.isNight(12999));
        assertTrue(WorldRules.isNight(13000));
        assertTrue(WorldRules.isNight(18000));
        assertTrue(WorldRules.isNight(22999));
        assertFalse(WorldRules.isNight(23000));
    }

    @Test
    void thunderOutranksRain() {
        assertEquals("clear", WorldRules.weather(false, false));
        assertEquals("rain", WorldRules.weather(true, false));
        assertEquals("thunder", WorldRules.weather(true, true));
    }

    @Test
    void breathIsWholeSecondsAndOnlyZeroWhenGone() {
        assertEquals(15, WorldRules.airSeconds(300));
        assertEquals(1, WorldRules.airSeconds(1));
        assertEquals(0, WorldRules.airSeconds(0));
        assertEquals(0, WorldRules.airSeconds(-20), "drowning");
    }

    @Test
    void effectsCountWholeSecondsOrForever() {
        assertEquals(30, WorldRules.effectSeconds(600, false));
        assertEquals(1, WorldRules.effectSeconds(1, false));
        assertEquals(Double.POSITIVE_INFINITY, WorldRules.effectSeconds(-1, true));
    }

    @Test
    void blockOffsetsAreWholeAndWithinReach() {
        assertTrue(WorldRules.inReach(0));
        assertTrue(WorldRules.inReach(-4));
        assertTrue(WorldRules.inReach(4));
        assertFalse(WorldRules.inReach(5));
        assertFalse(WorldRules.inReach(-5));
        assertFalse(WorldRules.inReach(0.5), "whole blocks only");
        assertFalse(WorldRules.inReach(Double.NaN));
    }
}
