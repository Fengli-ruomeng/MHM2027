package com.mhm.mhm2027.terminal;

/**
 * The numbers behind the read-only world and body APIs (the clock, the weather, breath,
 * effect timers, how far world.block reaches), kept apart from the game so they can be
 * unit-tested.
 */
public final class WorldRules {
    /** One Overworld day, in ticks. */
    public static final int DAY_TICKS = 24000;
    /** Night, by the Overworld clock: from dusk at 13000 until dawn at 23000. */
    public static final int NIGHT_START = 13000;
    public static final int NIGHT_END = 23000;
    /** world.block reaches this many blocks from your feet along each axis. */
    public static final int BLOCK_REACH = 4;
    /** What world.block says about a block buried where nobody could see it. */
    public static final String HIDDEN = "hidden";

    private WorldRules() {
    }

    /** The time of day, 0 to 23999, from a clock's total ticks. */
    public static int timeOfDay(long clockTicks) {
        return (int) Math.floorMod(clockTicks, (long) DAY_TICKS);
    }

    /** Which day it is, counting from 0. */
    public static long day(long clockTicks) {
        return Math.floorDiv(clockTicks, (long) DAY_TICKS);
    }

    public static boolean isNight(int timeOfDay) {
        return timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
    }

    public static String weather(boolean raining, boolean thundering) {
        return thundering ? "thunder" : raining ? "rain" : "clear";
    }

    /** Breath in whole seconds, rounded up so it only reads 0 once you're out. Air below 0 means drowning. */
    public static int airSeconds(int airTicks) {
        return (Math.max(0, airTicks) + 19) / 20;
    }

    /** An effect's time left in whole seconds, rounded up; infinite for one that never runs out. */
    public static double effectSeconds(int ticks, boolean infinite) {
        return infinite ? Double.POSITIVE_INFINITY : (Math.max(0, ticks) + 19) / 20;
    }

    /** Whether an offset is one world.block may read: whole blocks, at most {@link #BLOCK_REACH} along each axis. */
    public static boolean inReach(double offset) {
        return offset == Math.rint(offset) && Math.abs(offset) <= BLOCK_REACH;
    }
}
