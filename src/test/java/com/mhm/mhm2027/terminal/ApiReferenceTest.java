package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The wiki's API reference is generated from the API registry, so it can't drift from the code.
 * This fails when an API or event has no description, or when the page in docs/wiki is out of
 * date. To rewrite the page, run the tests with the environment variable UPDATE_WIKI=1.
 */
class ApiReferenceTest {
    private static final Path PAGE = Path.of("docs/wiki/API-Reference.md");

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
        APIS.put("sys.notify", "Show a short message above your hotbar.");
        APIS.put("sys.log", "Write a line to the terminal log (the panel's Log tab).");
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

    @Test
    void everyApiEventAndHookIsDescribed() {
        for (ApiSpec spec : ApiSpec.all()) {
            assertTrue(APIS.containsKey(spec.id()), "describe " + spec.id() + " in ApiReferenceTest");
        }
        for (String event : ScriptEvents.PROGRAM_EVENTS) {
            assertTrue(EVENTS.containsKey(event), "describe " + event + " in ApiReferenceTest");
        }
        for (String hook : ScriptEvents.KERNEL_HOOKS) {
            assertTrue(HOOKS.containsKey(hook), "describe " + hook + " in ApiReferenceTest");
        }
    }

    @Test
    void theWikiPageMatchesTheCode() throws Exception {
        String page = render();
        if (System.getenv("UPDATE_WIKI") != null) {
            Files.createDirectories(PAGE.getParent());
            Files.writeString(PAGE, page, StandardCharsets.UTF_8);
        }
        String saved = Files.exists(PAGE) ? Files.readString(PAGE, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";
        assertEquals(page, saved, "docs/wiki/API-Reference.md is out of date: run the tests with UPDATE_WIKI=1");
    }

    private static String render() {
        StringBuilder out = new StringBuilder();
        out.append("<!-- Generated from the mod's code by ApiReferenceTest. Don't edit by hand: run the tests with UPDATE_WIKI=1. -->\n\n");
        out.append("# API Reference\n\n");
        out.append("Every call, event and hook your scripts can use, taken from the mod's code.\n\n");
        out.append("- **Level**: the gateway level you need.\n");
        out.append("- **Kernel**: kernel hooks may call it too. Programs always can.\n");
        out.append("- **Socket**: it changes your body, so the program has to run from a socket (see [Sockets and Overload](Sockets-and-Overload)).\n");
        out.append("- **Cooldown**: seconds between uses, shared by all your programs.\n");
        out.append("- **RAM**: added to a program's code RAM when it uses the call.\n");
        section(out, "me", "`me`: your own body");
        section(out, "sys", "`sys`: your terminal");
        section(out, "remote", "`remote`: other gateways");

        out.append("\n## Program events\n\n");
        out.append("Define a function with the event's name and the program runs it when the event happens.\n\n");
        out.append("| Event | Fires when |\n| --- | --- |\n");
        EVENTS.forEach((event, text) -> out.append("| `").append(event).append("` | ").append(text).append(" |\n"));

        out.append("\n## Kernel hooks\n\n");
        out.append("A kernel may take over as many hooks as your gateway has hook points; the rest keep their default. "
                + "Hooks must answer at once: they can't sleep, and one that runs too long gets its default.\n\n");
        out.append("| Hook | When, and what it can do |\n| --- | --- |\n");
        HOOKS.forEach((hook, text) -> out.append("| `").append(hook).append("` | ").append(text).append(" |\n"));

        out.append("\n## Helpers\n\n");
        out.append("| Call | What it does |\n| --- | --- |\n");
        out.append("| `print(...)` | Same as `sys.log`. |\n");
        out.append("| `chance(percent)` | true that percent of the time. |\n");
        out.append("| `math`, `string`, `table` | Lua's standard libraries. |\n");
        return out.toString();
    }

    private static void section(StringBuilder out, String namespace, String title) {
        out.append("\n## ").append(title).append("\n\n");
        out.append("| Call | Level | Kernel | Socket | Cooldown | RAM | What it does |\n");
        out.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        List<ApiSpec> specs = ApiSpec.all().stream().filter(spec -> spec.namespace().equals(namespace)).toList();
        for (ApiSpec spec : specs) {
            out.append("| `").append(spec.id()).append('(').append(spec.params()).append(")` | LV").append(spec.minLevel())
                    .append(" | ").append(spec.kernel() ? "yes" : "")
                    .append(" | ").append(spec.needsSlot() ? "yes" : "")
                    .append(" | ").append(spec.cooldownSeconds() > 0 ? seconds(spec.cooldownSeconds()) + " s" : "")
                    .append(" | ").append(spec.ramMb()).append(" MB")
                    .append(" | ").append(APIS.get(spec.id())).append(" |\n");
        }
    }

    private static String seconds(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
