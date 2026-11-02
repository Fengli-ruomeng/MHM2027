package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every complete program in the wiki installs, at exactly the level it says it needs. A
 * complete example starts with a line such as "-- Needs LV2 and a socket."; anything else
 * in a code block is a fragment and isn't checked.
 */
class WikiExamplesTest {
    private static final Path WIKI = Path.of("docs/wiki");
    private static final Pattern LUA_BLOCK = Pattern.compile("```lua\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern NEEDS = Pattern.compile("\\A-- Needs LV(\\d+)( and a socket)?\\.");

    @Test
    void everyCompleteExampleInstallsAtTheLevelItStates() throws Exception {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(WIKI)) {
            for (Path page : files.filter(file -> file.toString().endsWith(".md")).sorted().toList()) {
                String text = Files.readString(page, StandardCharsets.UTF_8).replace("\r\n", "\n");
                Matcher block = LUA_BLOCK.matcher(text);
                while (block.find()) {
                    String source = block.group(1);
                    Matcher needs = NEEDS.matcher(source);
                    if (!needs.find()) {
                        continue;
                    }
                    checked++;
                    int stated = Integer.parseInt(needs.group(1));
                    boolean statesSocket = needs.group(2) != null;
                    String where = page.getFileName() + ", " + source.lines().skip(1).findFirst().orElse("");
                    try {
                        ScriptSummary summary = ScriptAnalyzer.analyze("Example", source, false).summary();
                        if (summary.level() != stated) {
                            problems.add(where + ": says LV" + stated + " but needs LV" + summary.level());
                        }
                        boolean needsSocket = summary.details().stream()
                                .anyMatch(id -> ApiSpec.byId(id).map(ApiSpec::needsSlot).orElse(false));
                        if (needsSocket != statesSocket) {
                            problems.add(where + (needsSocket ? ": needs a socket but doesn't say so" : ": says it needs a socket but doesn't"));
                        }
                    } catch (InstallException e) {
                        problems.add(where + ": " + e.getMessage());
                    }
                }
            }
        }
        assertTrue(checked >= 30, "only " + checked + " complete examples found");
        assertTrue(problems.isEmpty(), "\n" + String.join("\n", problems));
    }
}
