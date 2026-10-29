package com.mhm.mhm2027.mob;

import java.util.Random;

import com.mhm.mhm2027.terminal.KernelSettings;

/**
 * The numbers behind gateway ("wired") mobs, kept apart so they can be unit-tested: how often
 * they appear, what level they spawn at, what kernel they roll, and what they drop.
 */
public final class WiredRules {
    private WiredRules() {
    }

    /**
     * The chance a fresh hostile mob carries a gateway on a given day (day 1 is the first day).
     * None before {@code startDay}, then rising evenly to {@code maxChance} on {@code fullDay}.
     */
    public static double spawnChance(long day, int startDay, int fullDay, double maxChance) {
        if (day < startDay) {
            return 0;
        }
        if (fullDay <= startDay || day >= fullDay) {
            return maxChance;
        }
        // Even on the first day there is a small chance, so the ramp starts above zero.
        return maxChance * (day - startDay + 1) / (fullDay - startDay + 1);
    }

    /** Deeper means stronger: the low end of the range at the surface, the high end below y=0. */
    public static int level(int minLevel, int maxLevel, int y) {
        if (y >= 50) {
            return minLevel;
        }
        if (y < 0) {
            return maxLevel;
        }
        double depth = (50 - y) / 50.0;
        return minLevel + (int) Math.round(depth * (maxLevel - minLevel));
    }

    /**
     * A mob's random kernel. Seeded, so the same world seed and mob UUID always give the same
     * kernel and any odd behaviour can be reproduced.
     */
    public static KernelSettings rollKernel(long seed) {
        Random random = new Random(seed);
        String schedule = random.nextDouble() < 0.3 ? "priority" : "fair";
        double gcRoll = random.nextDouble();
        String gc = gcRoll < 0.25 ? "lazy" : gcRoll < 0.75 ? "normal" : "aggressive";
        double verifyRoll = random.nextDouble();
        String verify = verifyRoll < 0.5 ? "full" : verifyRoll < 0.85 ? "fast" : "skip";
        int rateLimit = 2 + random.nextInt(11);
        return new KernelSettings(schedule, gc, verify, rateLimit);
    }

    /** Data Shards a gateway mob drops: more for higher levels, and one extra half the time ({@code roll} in [0, 1)). */
    public static int shards(int level, double roll) {
        return 1 + Math.max(0, level) / 2 + (roll < 0.5 ? 1 : 0);
    }

    /** TOPS a mob commits to an access attempt. */
    public static int attackTops(int level) {
        return 2 + Math.max(0, level);
    }
}
