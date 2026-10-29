package com.mhm.mhm2027.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mhm.mhm2027.terminal.KernelSettings;

class WiredRulesTest {
    @Test
    void appearFromDayTwoAndReachAQuarterByDayThirty() {
        assertEquals(0, WiredRules.spawnChance(1, 2, 30, 0.25), "none on the first day");
        assertTrue(WiredRules.spawnChance(2, 2, 30, 0.25) > 0, "some on day two");
        assertTrue(WiredRules.spawnChance(10, 2, 30, 0.25) < WiredRules.spawnChance(20, 2, 30, 0.25));
        assertEquals(0.25, WiredRules.spawnChance(30, 2, 30, 0.25), 1e-9);
        assertEquals(0.25, WiredRules.spawnChance(500, 2, 30, 0.25), 1e-9, "never above the cap");
        assertEquals(1.0, WiredRules.spawnChance(1, 1, 1, 1.0), 1e-9, "a test world can have them everywhere at once");
    }

    @Test
    void deeperMobsAreStronger() {
        assertEquals(1, WiredRules.level(1, 3, 80));
        assertEquals(2, WiredRules.level(1, 3, 25));
        assertEquals(3, WiredRules.level(1, 3, -20));
        assertEquals(4, WiredRules.level(3, 4, -1));
    }

    @Test
    void kernelsAreReproducibleAndValid() {
        assertEquals(WiredRules.rollKernel(12345), WiredRules.rollKernel(12345));
        Set<String> verifies = new HashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            KernelSettings kernel = WiredRules.rollKernel(seed);
            verifies.add(kernel.verify());
            assertTrue(KernelSettings.SCHEDULES.contains(kernel.schedule()));
            assertTrue(KernelSettings.GC_MODES.contains(kernel.gc()));
            assertTrue(kernel.rateLimit() >= KernelSettings.MIN_RATE_LIMIT && kernel.rateLimit() <= KernelSettings.MAX_RATE_LIMIT);
        }
        assertEquals(Set.copyOf(KernelSettings.VERIFY_MODES), verifies, "every verify mode turns up");
    }

    @Test
    void higherLevelsDropMoreShards() {
        assertEquals(1, WiredRules.shards(1, 0.9));
        assertEquals(2, WiredRules.shards(1, 0.1));
        assertEquals(5, WiredRules.shards(8, 0.9));
        assertEquals(1, WiredRules.shards(-1, 0.9), "even a broken gateway drops one");
    }
}
