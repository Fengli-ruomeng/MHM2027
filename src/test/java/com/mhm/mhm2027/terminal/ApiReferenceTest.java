package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The wiki's API reference is generated from the API registry and {@link ApiDocs}, so it can't
 * drift from the code. This fails when an API or event has no description, or when the page in docs/wiki is out of
 * date. To rewrite the page, run the tests with the environment variable UPDATE_WIKI=1.
 */
class ApiReferenceTest {
    private static final Path PAGE = Path.of("docs/wiki/API-Reference.md");

    private static final Map<String, String> APIS = ApiDocs.apis();
    private static final Map<String, String> EVENTS = ApiDocs.events();
    private static final Map<String, String> HOOKS = ApiDocs.hooks();

    @Test
    void everyApiEventAndHookIsDescribed() {
        for (ApiSpec spec : ApiSpec.all()) {
            assertTrue(APIS.containsKey(spec.id()), "describe " + spec.id() + " in ApiDocs");
        }
        for (String event : ScriptEvents.PROGRAM_EVENTS) {
            assertTrue(EVENTS.containsKey(event), "describe " + event + " in ApiDocs");
        }
        for (String hook : ScriptEvents.KERNEL_HOOKS) {
            assertTrue(HOOKS.containsKey(hook), "describe " + hook + " in ApiDocs");
        }
    }

    @Test
    void everyApiIsOnThePage() {
        String page = render();
        for (ApiSpec spec : ApiSpec.all()) {
            assertTrue(ApiSpec.NAMESPACES.contains(spec.namespace()), "add " + spec.namespace() + " to ApiSpec.NAMESPACES");
            assertTrue(page.contains("| `" + spec.id() + "("), "give the " + spec.namespace() + " namespace a section on the page");
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
        section(out, "world", "`world`: what's around you");

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
