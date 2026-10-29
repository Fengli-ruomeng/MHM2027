package com.mhm.mhm2027.terminal;

import java.util.List;
import java.util.Set;

import org.squiddev.cobalt.LuaTable;

/**
 * The settings half of a kernel, from the {@code kernel { ... }} header. gc changes how fast
 * Leak clears and what that costs. schedule, verify and rate_limit are the static flaw
 * trade-offs: {@link FlawRules} reads them, and nothing else in the terminal does yet.
 */
public record KernelSettings(String schedule, String gc, String verify, int rateLimit) {
    public static final KernelSettings DEFAULT = new KernelSettings("fair", "normal", "full", 4);

    static final Set<String> HEADER_KEYS = Set.of("name", "schedule", "gc", "verify", "rate_limit");

    /** TOPS always held for hook work, so a busy fight never starves the kernel. */
    private static final int HOOK_RESERVE_TOPS = 2;
    private static final int BASE_RAM_MB = 8;
    private static final int RAM_PER_HOOK_MB = 2;

    /** Kernel RAM: a base plus a little per hook. */
    static int ramFor(int hooks) {
        return BASE_RAM_MB + RAM_PER_HOOK_MB * hooks;
    }

    /** TOPS the kernel holds: the hook reserve plus whatever garbage collection costs. */
    public int topsReserve() {
        return HOOK_RESERVE_TOPS + gcTops();
    }
    public static final List<String> SCHEDULES = List.of("fair", "priority");
    public static final List<String> GC_MODES = List.of("lazy", "normal", "aggressive");
    public static final List<String> VERIFY_MODES = List.of("full", "fast", "skip");
    public static final int MIN_RATE_LIMIT = 1;
    public static final int MAX_RATE_LIMIT = 20;

    /** Settings from outside a script (a mob's native kernel). Refuses values a header would refuse. */
    public static KernelSettings of(String schedule, String gc, String verify, int rateLimit) throws InstallException {
        if (!SCHEDULES.contains(schedule) || !GC_MODES.contains(gc) || !VERIFY_MODES.contains(verify)
                || rateLimit < MIN_RATE_LIMIT || rateLimit > MAX_RATE_LIMIT) {
            throw new InstallException("Kernel settings must be schedule " + SCHEDULES + ", gc " + GC_MODES
                    + ", verify " + VERIFY_MODES + ", rate_limit " + MIN_RATE_LIMIT + "-" + MAX_RATE_LIMIT);
        }
        return new KernelSettings(schedule, gc, verify, rateLimit);
    }

    static KernelSettings fromHeader(LuaTable header) throws InstallException {
        return new KernelSettings(
                choice(header, "schedule", SCHEDULES, DEFAULT.schedule),
                choice(header, "gc", GC_MODES, DEFAULT.gc),
                choice(header, "verify", VERIFY_MODES, DEFAULT.verify),
                ScriptCompiler.intField(header, "rate_limit", DEFAULT.rateLimit, MIN_RATE_LIMIT, MAX_RATE_LIMIT));
    }

    /** Multiplier on the garbage-collection rate. */
    public double gcMultiplier() {
        return switch (gc) {
            case "lazy" -> 0.5;
            case "aggressive" -> 2.0;
            default -> 1.0;
        };
    }

    /** TOPS the kernel holds for garbage collection, on top of its hook reserve. */
    public int gcTops() {
        return switch (gc) {
            case "lazy" -> 0;
            case "aggressive" -> 3;
            default -> 1;
        };
    }

    private static String choice(LuaTable header, String key, List<String> options, String fallback) throws InstallException {
        String value = ScriptCompiler.stringField(header, key, fallback);
        if (!options.contains(value)) {
            throw new InstallException("Kernel setting " + key + " must be one of " + String.join(", ", options));
        }
        return value;
    }
}
