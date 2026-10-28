package com.mhm.mhm2027.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mhm.mhm2027.terminal.ApiSpec;
import com.mhm.mhm2027.terminal.ScriptEvents;

/**
 * Autocomplete for the editor, from the same registry the runtime checks against, so the
 * editor can never suggest something the terminal would refuse for a different reason.
 */
public final class Completions {
    private static final Pattern API_PREFIX = Pattern.compile("\\b(me|sys)\\.([A-Za-z_]*)$");
    private static final Pattern EVENT_PREFIX = Pattern.compile("^\\s*function\\s+(on_[A-Za-z_]*)$");

    /**
     * One suggestion.
     *
     * @param label  what the list shows, e.g. {@code me.speed_boost(amount, seconds)}
     * @param insert what gets typed when it's accepted (the rest of the word, plus "(" for APIs)
     * @param level  gateway level the API needs; -1 for events
     */
    public record Suggestion(String label, String insert, int level, boolean kernelOnly) {
    }

    private Completions() {
    }

    /**
     * Suggestions for the text before the cursor on the current line.
     *
     * @param kernel whether the file is a kernel, which changes what fits
     */
    public static List<Suggestion> suggest(String beforeCursor, boolean kernel) {
        List<Suggestion> suggestions = new ArrayList<>();
        Matcher api = API_PREFIX.matcher(beforeCursor);
        if (api.find()) {
            String prefix = api.group(1) + "." + api.group(2);
            for (ApiSpec spec : ApiSpec.all()) {
                if (spec.id().startsWith(prefix) && (!kernel || spec.kernel())) {
                    suggestions.add(new Suggestion(spec.id() + "(" + spec.params() + ")",
                            spec.id().substring(prefix.length()) + "(", spec.minLevel(), false));
                }
            }
            return suggestions;
        }
        Matcher event = EVENT_PREFIX.matcher(beforeCursor);
        if (event.find()) {
            String prefix = event.group(1);
            for (String name : kernel ? ScriptEvents.KERNEL_HOOKS : ScriptEvents.PROGRAM_EVENTS) {
                if (name.startsWith(prefix)) {
                    suggestions.add(new Suggestion(name + "()", name.substring(prefix.length()) + "(", -1, kernel));
                }
            }
        }
        return suggestions;
    }
}
