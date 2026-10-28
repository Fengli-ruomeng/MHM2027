package com.mhm.mhm2027.terminal;

import java.util.List;
import java.util.Set;

import org.squiddev.cobalt.LuaTable;

/**
 * The settings half of a kernel, from the {@code kernel { ... }} header. These are where the
 * flaw trade-offs will come from once cyber combat exists. For now gc changes how fast Leak
 * clears and what that costs; the others are stored and shown but have no effect yet.
 */
public record KernelSettings(String schedule, String gc, String verify, int rateLimit) {
    public static final KernelSettings DEFAULT = new KernelSettings("fair", "normal", "full", 4);

    static final Set<String> HEADER_KEYS = Set.of("name", "schedule", "gc", "verify", "rate_limit");
    private static final List<String> SCHEDULES = List.of("fair", "priority");
    private static final List<String> GC_MODES = List.of("lazy", "normal", "aggressive");
    private static final List<String> VERIFY_MODES = List.of("full", "fast", "skip");

    static KernelSettings fromHeader(LuaTable header) throws InstallException {
        return new KernelSettings(
                choice(header, "schedule", SCHEDULES, DEFAULT.schedule),
                choice(header, "gc", GC_MODES, DEFAULT.gc),
                choice(header, "verify", VERIFY_MODES, DEFAULT.verify),
                ScriptCompiler.intField(header, "rate_limit", DEFAULT.rateLimit, 1, 20));
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
