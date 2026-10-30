package com.mhm.mhm2027.terminal;

/**
 * The numbers behind access attempts and sessions, from the Cyber system tab, kept apart so
 * they can be unit-tested and tuned in one place. All times are in ticks (20 per second).
 *
 * An attempt's speed is the attacker's committed TOPS against the defender's, capped between
 * half and double normal speed. The defender always commits its kernel reserve; resist() adds
 * to it.
 */
public final class AccessRules {
    /** force works on anyone in about 3 s. */
    public static final int FORCE_TICKS = 60;
    /** A method matching a flaw: about 2 s for minor, 1 s for major, 0.5 s for critical. */
    public static final int MINOR_TICKS = 40;
    public static final int MAJOR_TICKS = 20;
    public static final int CRITICAL_TICKS = 10;
    /** No attempt finishes in under 0.5 s. */
    public static final int MIN_TICKS = 10;
    public static final double MIN_SPEED = 0.5;
    public static final double MAX_SPEED = 2.0;
    /** delay() buys at most this much time per attempt. */
    public static final double MAX_DELAY_SECONDS = 2.0;
    /** escalate() is the same contest, shorter. */
    public static final double ESCALATE_SHARE = 0.5;

    /** Sessions last about 6 s at user level. */
    public static final int SESSION_TICKS = 120;
    /** After a deny, the attacker can't retry on that target for about 2 s. */
    public static final int DENY_LOCKOUT_TICKS = 40;
    /** After a session ends, the same attacker can't access that target again for about 10 s. */
    public static final int SESSION_LOCKOUT_TICKS = 200;

    /** Session action caps, the same at every level and on every target. */
    public static final double NEUTRALIZE_MAX_SECONDS = 4;
    public static final double LEAK_MAX_SHARE = 0.5;
    public static final double HEAT_MAX_PER_CALL = 25;
    /** Resisting raises your own Heat by this much per TOPS committed. */
    public static final double HEAT_PER_RESISTED_TOPS = 1.5;

    private AccessRules() {
    }

    /** Base time for a method: force, or a method that matched a flaw of this severity. */
    public static int baseTicks(FlawSeverity matched) {
        return switch (matched) {
            case MINOR -> MINOR_TICKS;
            case MAJOR -> MAJOR_TICKS;
            case CRITICAL -> CRITICAL_TICKS;
            case NONE -> FORCE_TICKS;
        };
    }

    /** Committed TOPS against committed TOPS, capped between half and double speed. */
    public static double speed(int attackerTops, int defenderTops) {
        return Math.clamp((double) attackerTops / Math.max(1, defenderTops), MIN_SPEED, MAX_SPEED);
    }

    /**
     * How long an attempt takes: the base time divided by speed, never under 0.5 s, plus any
     * delay the defender's kernel asked for.
     */
    public static int durationTicks(int baseTicks, int attackerTops, int defenderTops, double delaySeconds, boolean escalate) {
        return durationTicks(baseTicks, attackerTops, defenderTops, delaySeconds, escalate, 1.0);
    }

    /**
     * @param attackerSpeed the attacker's kernel verify setting: 1 at full, more at fast or skip
     */
    public static int durationTicks(int baseTicks, int attackerTops, int defenderTops, double delaySeconds, boolean escalate,
                                    double attackerSpeed) {
        double base = (escalate ? baseTicks * ESCALATE_SHARE : baseTicks) / Math.max(1.0, attackerSpeed);
        int contest = Math.max(MIN_TICKS, (int) Math.round(base / speed(attackerTops, defenderTops)));
        return contest + (int) Math.round(Math.clamp(delaySeconds, 0, MAX_DELAY_SECONDS) * 20);
    }
}
