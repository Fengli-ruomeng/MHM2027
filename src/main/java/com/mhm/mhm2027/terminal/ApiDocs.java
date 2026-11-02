package com.mhm.mhm2027.terminal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One line on what every API, program event and kernel hook does. Edge's {@code man} prints
 * these, and the wiki's API Reference page is generated from them (see ApiReferenceTest), so
 * the game and the wiki can't say different things. Text may use Markdown backticks and links;
 * {@link #plain(String)} strips them for the terminal.
 */
public final class ApiDocs {
    private static final Map<String, String> APIS = new LinkedHashMap<>();
    private static final Map<String, String> EVENTS = new LinkedHashMap<>();
    private static final Map<String, String> HOOKS = new LinkedHashMap<>();

    static {
        APIS.put("me.health", "Your current health.");
        APIS.put("me.max_health", "Your maximum health.");
        APIS.put("me.hunger", "Your food level, 0 to 20.");
        APIS.put("me.pos", "Your position: x, y, z.");
        APIS.put("me.looking_at", "What's under your crosshair within reach: `{type = \"block\", id, x, y, z}` or `{type = \"entity\", id, entity}`, or nil.");
        APIS.put("me.speed_boost", "Faster movement: amount 0.05 to 0.5, for 1 to 10 seconds.");
        APIS.put("me.jump_boost", "Higher jumps: amount 0.05 to 0.5, for 1 to 10 seconds.");
        APIS.put("me.fall_guard", "No fall damage for 1 to 10 seconds.");
        APIS.put("me.regen_burst", "Heal 1 to 6 health now, paid in the same amount of hunger.");
        APIS.put("me.max_health_boost", "1 to 10 extra max health for 1 to 30 seconds.");
        APIS.put("me.damage_boost", "Hit harder: amount 0.05 to 0.5, for 1 to 10 seconds.");
        APIS.put("me.knockback_resist", "No knockback for 1 to 10 seconds.");
        APIS.put("sys.ram", "Free and total RAM, in MB.");
        APIS.put("sys.tops", "Free and total TOPS.");
        APIS.put("sys.heat", "Your Heat, 0 to 100.");
        APIS.put("sys.leak", "Your Leak, in MB.");
        APIS.put("sys.buffer", "Your Buffer and its maximum, in MB.");
        APIS.put("sys.programs", "Your running programs: a list of `{name, type, ram, tops, state}`.");
        APIS.put("sys.start", "Start a program from storage. Returns true, or false and the reason.");
        APIS.put("sys.stop", "Stop a running program by name.");
        APIS.put("sys.sleep", "Pause this program for 0.05 to 60 seconds. Not in kernel hooks.");
        APIS.put("sys.every", "Fire `on_timer` every so many seconds (at least 0.25). 0 turns it off.");
        APIS.put("sys.notify", "Show a card on your HUD, tagged with the program's name.");
        APIS.put("sys.log", "Print a line in the program's Edge window; from a kernel hook, in kernel.log.");
        APIS.put("sys.flaws", "Your active flaws: a list of `{class, severity, cause}`.");
        APIS.put("sys.low_health", "Set the health below which `on_low_health` fires. nil goes back to 30% of max.");
        APIS.put("remote.target", "The gateway under your crosshair, within your scan range, as a handle; or nil.");
        APIS.put("remote.scan", "Gateways around you, nearest first, up to 8 handles. The radius is capped at your scan range.");
        APIS.put("remote.inspect", "Waits about 1 s (faster with a high rate_limit), then returns the target's address, name, level and flaws. They hear `on_scanned`.");
        APIS.put("remote.access", "Waits for an access attempt, then returns a session, or nil and a reason. See [Cyber Combat](Cyber-Combat).");
        APIS.put("sys.end_session", "Close a session someone holds on you. Costs Buffer.");
        APIS.put("sys.reboot", "Reboot: every program stops and every session on you ends, with a short debuff.");

        EVENTS.put("on_start", "A resident program starts.");
        EVENTS.put("on_stop", "The program is stopped. It gets one short slice to tidy up.");
        EVENTS.put("on_run", "A transient or query program is run.");
        EVENTS.put("on_timer", "The interval set with `sys.every` passes.");
        EVENTS.put("on_hit_taken", "You take damage. Gets `(attacker, amount)`.");
        EVENTS.put("on_hit_dealt", "You deal damage. Gets `(target, amount)`.");
        EVENTS.put("on_kill", "You kill something. Gets `(target)`.");
        EVENTS.put("on_flaw_changed", "A flaw appears, changes or goes away. Gets `(class, severity)`; severity is \"none\" when it goes.");
        EVENTS.put("on_scanned", "Someone finishes inspecting you. Gets `(by)`.");
        EVENTS.put("on_access_attempt", "Someone starts an access attempt on you. Gets `(req)`; programs can't decide it, the kernel does.");
        EVENTS.put("on_session_opened", "Someone got in. Gets `(who)`.");
        EVENTS.put("on_low_health", "Your health drops below the `sys.low_health` threshold. Gets `(hp)`. Fires once per drop.");
        EVENTS.put("on_program_crash", "Another of your programs crashed. Gets `(name)`.");

        HOOKS.put("on_access_request", "Something tries to open a session on you. Gets `(req)` with `req.from`, `req.method`, `req.tops`, `req.escalate`. "
                + "Return `allow()`, `deny()` (costs Buffer), `delay(seconds)` (up to 2) or `resist(tops)` (costs TOPS and Heat). Default: allow.");
        HOOKS.put("on_session_opened", "Someone got in. Gets `(who)`. Raise the alarm, reboot, start a defence. Default: nothing.");
        HOOKS.put("on_low_ram", "Free RAM ran out. Gets `(free)`. Stop programs in the order you choose. Default: the newest program stops.");
        HOOKS.put("on_panic", "The kernel panicked and rebooted. Gets `(reason)`. Choose what to start again. Default: nothing restarts.");
    }

    private ApiDocs() {
    }

    /** API descriptions by id, such as {@code me.speed_boost}, in registry order. */
    public static Map<String, String> apis() {
        return Collections.unmodifiableMap(APIS);
    }

    /** Program event descriptions by name. */
    public static Map<String, String> events() {
        return Collections.unmodifiableMap(EVENTS);
    }

    /** Kernel hook descriptions by name. */
    public static Map<String, String> hooks() {
        return Collections.unmodifiableMap(HOOKS);
    }

    public static Optional<String> api(String id) {
        return Optional.ofNullable(APIS.get(id));
    }

    /** Markdown for the terminal: no backticks, and links become their text. */
    public static String plain(String markdown) {
        return markdown.replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1").replace("`", "");
    }
}
