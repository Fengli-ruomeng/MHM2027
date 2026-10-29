package com.mhm.mhm2027.terminal;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One entry of the API registry, following the "API schema" in the Scripting and API tab.
 * Install checks, RAM cost, runtime checks and the editor's autocomplete all read the same
 * entry. These live in Java for now; moving the numbers into datapack files is a later step.
 *
 * @param id        namespace and name, such as {@code me.speed_boost}
 * @param params    parameter names, as the editor shows them
 * @param kernel    whether kernel hooks may call it (programs always may)
 * @param minLevel  gateway level needed
 * @param steps     charged against the TOPS budget per call, in units of {@link #STEP_INSTRUCTIONS}
 * @param ramMb     added to code RAM when a script uses it
 * @param cooldownSeconds seconds between uses, shared by every program on the same body
 * @param needsSlot changes your body, so the program needs a standard slot
 */
public record ApiSpec(String id, String params, boolean kernel, int minLevel, int steps, int ramMb, double cooldownSeconds, boolean needsSlot) {
    /** One step costs this many instructions of budget. */
    public static final int STEP_INSTRUCTIONS = 10;

    private static final Map<String, ApiSpec> REGISTRY = new LinkedHashMap<>();

    // me: your own body. Program-only.
    public static final ApiSpec ME_HEALTH = read("me.health");
    public static final ApiSpec ME_MAX_HEALTH = read("me.max_health");
    public static final ApiSpec ME_HUNGER = read("me.hunger");
    public static final ApiSpec ME_POS = read("me.pos");
    public static final ApiSpec ME_SPEED_BOOST = register(new ApiSpec("me.speed_boost", "amount, seconds", false, 1, 2, 4, 8, true));
    public static final ApiSpec ME_JUMP_BOOST = register(new ApiSpec("me.jump_boost", "amount, seconds", false, 2, 2, 4, 8, true));
    public static final ApiSpec ME_FALL_GUARD = register(new ApiSpec("me.fall_guard", "seconds", false, 2, 2, 4, 10, true));
    public static final ApiSpec ME_REGEN_BURST = register(new ApiSpec("me.regen_burst", "amount", false, 3, 3, 6, 10, true));
    public static final ApiSpec ME_MAX_HEALTH_BOOST = register(new ApiSpec("me.max_health_boost", "amount, seconds", false, 5, 3, 8, 30, true));
    public static final ApiSpec ME_DAMAGE_BOOST = register(new ApiSpec("me.damage_boost", "amount, seconds", false, 6, 3, 8, 12, true));
    public static final ApiSpec ME_KNOCKBACK_RESIST = register(new ApiSpec("me.knockback_resist", "seconds", false, 6, 2, 6, 12, true));

    // sys: your terminal. Usable from programs and kernel hooks.
    public static final ApiSpec SYS_RAM = sysRead("sys.ram");
    public static final ApiSpec SYS_TOPS = sysRead("sys.tops");
    public static final ApiSpec SYS_HEAT = sysRead("sys.heat");
    public static final ApiSpec SYS_LEAK = sysRead("sys.leak");
    public static final ApiSpec SYS_BUFFER = sysRead("sys.buffer");
    public static final ApiSpec SYS_PROGRAMS = register(new ApiSpec("sys.programs", "", true, 0, 2, 1, 0, false));
    public static final ApiSpec SYS_START = register(new ApiSpec("sys.start", "name", true, 0, 10, 1, 0, false));
    public static final ApiSpec SYS_STOP = register(new ApiSpec("sys.stop", "name", true, 0, 2, 1, 0, false));
    public static final ApiSpec SYS_SLEEP = register(new ApiSpec("sys.sleep", "seconds", false, 0, 1, 0, 0, false));
    public static final ApiSpec SYS_EVERY = register(new ApiSpec("sys.every", "seconds", false, 0, 1, 1, 0, false));
    public static final ApiSpec SYS_NOTIFY = register(new ApiSpec("sys.notify", "text", true, 0, 2, 1, 0, false));
    public static final ApiSpec SYS_LOG = register(new ApiSpec("sys.log", "text", true, 0, 1, 0, 0, false));
    public static final ApiSpec SYS_FLAWS = sysRead("sys.flaws");

    private static ApiSpec read(String id) {
        return register(new ApiSpec(id, "", false, 0, 1, 1, 0, false));
    }

    private static ApiSpec sysRead(String id) {
        return register(new ApiSpec(id, "", true, 0, 1, 0, 0, false));
    }

    private static ApiSpec register(ApiSpec spec) {
        REGISTRY.put(spec.id(), spec);
        return spec;
    }

    public static Optional<ApiSpec> byId(String id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    /** Every API, in registry order. */
    public static Collection<ApiSpec> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** The part after the namespace, which is the Lua field name. */
    public String member() {
        return id.substring(id.indexOf('.') + 1);
    }

    public String namespace() {
        return id.substring(0, id.indexOf('.'));
    }

    public int cooldownTicks() {
        return (int) Math.round(cooldownSeconds * 20);
    }
}
