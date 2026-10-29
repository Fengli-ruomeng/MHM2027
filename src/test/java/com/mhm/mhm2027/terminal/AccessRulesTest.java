package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AccessRulesTest {
    @Test
    void methodsTakeTheDesignTimesAtEvenStrength() {
        assertEquals(60, AccessRules.durationTicks(AccessRules.baseTicks(FlawSeverity.NONE), 3, 3, 0, false), "force ~3 s");
        assertEquals(40, AccessRules.durationTicks(AccessRules.baseTicks(FlawSeverity.MINOR), 3, 3, 0, false), "minor ~2 s");
        assertEquals(20, AccessRules.durationTicks(AccessRules.baseTicks(FlawSeverity.MAJOR), 3, 3, 0, false), "major ~1 s");
        assertEquals(10, AccessRules.durationTicks(AccessRules.baseTicks(FlawSeverity.CRITICAL), 3, 3, 0, false), "critical ~0.5 s");
    }

    @Test
    void speedIsCommittedTopsAgainstCommittedTopsCapped() {
        assertEquals(4.0 / 3, AccessRules.speed(4, 3), 1e-9);
        assertEquals(2.0, AccessRules.speed(100, 3), "capped at double");
        assertEquals(0.5, AccessRules.speed(1, 30), "capped at half");
        assertEquals(45, AccessRules.durationTicks(60, 4, 3, 0, false), "a 4-TOPS program against Baseline: ~2.25 s");
        assertEquals(120, AccessRules.durationTicks(60, 2, 10, 0, false), "a strong resist halves the speed");
    }

    @Test
    void neverUnderHalfASecondButDelayAddsOnTop() {
        assertEquals(10, AccessRules.durationTicks(AccessRules.CRITICAL_TICKS, 100, 1, 0, false));
        assertEquals(10 + 40, AccessRules.durationTicks(AccessRules.CRITICAL_TICKS, 100, 1, 5, false), "delay is capped at 2 s");
    }

    @Test
    void escalateIsTheSameContestShorter() {
        assertEquals(30, AccessRules.durationTicks(60, 3, 3, 0, true));
    }
}
