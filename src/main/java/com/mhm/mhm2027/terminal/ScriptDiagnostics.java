package com.mhm.mhm2027.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.mhm.mhm2027.gateway.GatewayTier;

/**
 * The editor's live check. It runs the same analysis an install does, finds the line each
 * problem belongs to, and adds warnings for code that is valid but won't install on this
 * gateway yet (APIs above its level, too many hooks, more TOPS than it has).
 */
public final class ScriptDiagnostics {
    private static final Pattern LINE_NUMBER = Pattern.compile(":(\\d+):");
    private static final Pattern API_NAME = Pattern.compile("\\b((?:me|sys|remote)\\.[A-Za-z_]+)");
    private static final Pattern NAMED_HANDLER = Pattern.compile("^(on_[a-z_]+) ");
    private static final Pattern HEADER = Pattern.compile("^\\s*(program|kernel)\\s*\\{");

    /**
     * @param line    0-based line of the error, or of the first warning; -1 if none applies
     * @param summary what the script is and costs; null when there is an error
     */
    public record Result(boolean ok, String message, int line, @Nullable ScriptSummary summary, List<String> warnings) {
    }

    private ScriptDiagnostics() {
    }

    public static Result check(String file, String source, int gatewayLevel) {
        return check(file, source, gatewayLevel, List.of());
    }

    /**
     * @param flaws the loaded flaw classes. The editor passes the datapack registry; tests pass
     *              the file they are checking. An empty list previews nothing.
     */
    public static Result check(String file, String source, int gatewayLevel, List<FlawRules.ClassDef> flaws) {
        ScriptAnalyzer.Analysis analysis;
        try {
            analysis = ScriptAnalyzer.analyze(file, source, false);
        } catch (InstallException e) {
            return new Result(false, e.getMessage(), locate(source, e.getMessage()), null, List.of());
        }
        ScriptSummary summary = analysis.summary();

        GatewayTier tier = GatewayTier.of(gatewayLevel);
        List<String> warnings = new ArrayList<>();
        int line = -1;
        if (summary.isKernel()) {
            int hooks = summary.details().size();
            if (hooks > tier.hookPoints()) {
                warnings.add("Defines " + hooks + " hooks; your gateway has " + tier.hookPoints() + " hook points");
                // Point at the hook defined last in the file: the one that went over.
                String[] lines = source.split("\n", -1);
                for (String hook : summary.details()) {
                    line = Math.max(line, firstCodeLineContaining(lines, "function " + hook));
                }
            }
            // No fight is running in the editor, so heat, enhancements and program count stay zero.
            // What remains is the price of the settings, plus core when every hook point is used.
            if (analysis.kernelSettings() != null && !flaws.isEmpty()) {
                FlawRules.Profile profile = FlawRules.evaluate(flaws, FlawRules.Reading.of(
                        analysis.kernelSettings(), 0, 0, 0, hooks, tier.hookPoints()));
                for (FlawRules.Hit hit : profile.active()) {
                    warnings.add(FlawRules.warning(hit));
                    if (line < 0) {
                        line = headerLine(source);
                    }
                }
            }
        } else {
            if (summary.level() > gatewayLevel) {
                String api = summary.details().stream()
                        .filter(id -> ApiSpec.byId(id).map(spec -> spec.minLevel() > gatewayLevel).orElse(false))
                        .findFirst().orElse("");
                warnings.add(api + " needs gateway LV" + summary.level() + "; yours is LV" + gatewayLevel + ", so it won't run yet");
                line = locate(source, api);
            }
            if (summary.tops() > tier.tops()) {
                warnings.add("Reserves " + summary.tops() + " TOPS; your gateway has " + tier.tops());
                if (line < 0) {
                    line = headerLine(source);
                }
            }
        }
        return new Result(true, "", line, summary, warnings);
    }

    /** The 0-based line a message refers to, or -1. */
    static int locate(String source, String message) {
        Matcher number = LINE_NUMBER.matcher(message);
        if (number.find()) {
            return Integer.parseInt(number.group(1)) - 1;
        }
        String[] lines = source.split("\n", -1);
        Matcher api = API_NAME.matcher(message);
        if (api.find()) {
            return firstCodeLineContaining(lines, api.group(1));
        }
        Matcher handler = NAMED_HANDLER.matcher(message);
        if (handler.find()) {
            return firstCodeLineContaining(lines, handler.group(1));
        }
        if (message.contains("header") || message.contains("Header") || message.startsWith("Kernel setting")
                || message.startsWith("type must") || message.contains("hooks")) {
            return headerLine(source);
        }
        return -1;
    }

    private static int headerLine(String source) {
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (HEADER.matcher(lines[i]).find()) {
                return i;
            }
        }
        return -1;
    }

    private static int firstCodeLineContaining(String[] lines, String text) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int comment = line.indexOf("--");
            if ((comment < 0 ? line : line.substring(0, comment)).contains(text)) {
                return i;
            }
        }
        return -1;
    }
}
