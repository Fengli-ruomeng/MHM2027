package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Every kernel setting that opens a flaw also buys something. */
class KernelTradeoffTest {
    private static KernelSettings kernel(String schedule, String verify, int rateLimit) {
        return new KernelSettings(schedule, "normal", verify, rateLimit);
    }

    @Test
    void weakerVerifyMakesYourOwnAccessFaster() {
        assertEquals(1.0, kernel("fair", "full", 4).accessSpeed());
        assertTrue(kernel("fair", "fast", 4).accessSpeed() > 1.0);
        assertTrue(kernel("fair", "skip", 4).accessSpeed() > kernel("fair", "fast", 4).accessSpeed());

        int full = AccessRules.durationTicks(AccessRules.FORCE_TICKS, 4, 4, 0, false, 1.0);
        int skip = AccessRules.durationTicks(AccessRules.FORCE_TICKS, 4, 4, 0, false, 1.5);
        assertEquals(AccessRules.FORCE_TICKS, full);
        assertEquals(40, skip, "force at 1.5x speed: 3 s becomes 2 s");
        assertEquals(full, AccessRules.durationTicks(AccessRules.FORCE_TICKS, 4, 4, 0, false), "the old call is verify full");
    }

    @Test
    void aHigherRateLimitInspectsFaster() {
        assertEquals(20, kernel("fair", "full", 4).inspectTicks(20), "the default takes the base second");
        assertEquals(10, kernel("fair", "full", 8).inspectTicks(20));
        assertEquals(KernelSettings.MIN_INSPECT_TICKS, kernel("fair", "full", 20).inspectTicks(20));
        assertEquals(KernelSettings.MAX_INSPECT_TICKS, kernel("fair", "full", 1).inspectTicks(20));
    }

    @Test
    void onlyPriorityKernelsRankPrograms() {
        assertTrue(kernel("priority", "full", 4).ranksPrograms());
        assertFalse(kernel("fair", "full", 4).ranksPrograms());
    }

    @Test
    void lowHealthDefaultsToThirtyPercent() {
        assertEquals(6.0, EventRules.lowHealthThreshold(Double.NaN, 20), 1e-9);
        assertEquals(8.0, EventRules.lowHealthThreshold(8, 20), 1e-9);
    }

    @Test
    void priorityIsAHeaderFieldFromOneToNine() {
        String program = """
                program { name = "Guard", priority = 9 }
                function on_low_health(hp) end
                function on_program_crash(name) end
                """;
        assertTrue(ScriptDiagnostics.check("Guard", program, 0).ok());
        assertFalse(ScriptDiagnostics.check("Guard", program.replace("priority = 9", "priority = 10"), 0).ok());
    }
}
