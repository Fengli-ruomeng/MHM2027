package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Overload programs: the header flag, how far their code may reach, and what the editor says. */
class OverloadAnalysisTest {
    private static final String OWN_OVERLOAD = """
            program { name = "Surge", tops = 4, overload = true }
            function on_start()
              me.damage_boost(0.3, 5)
            end
            """;

    @Test
    void theShippedOverloadPresetLoads() throws Exception {
        String source = Files.readString(Path.of("src/main/resources/data/mhm2027/edge_presets/overclock.lua"), StandardCharsets.UTF_8);
        ScriptAnalyzer.Analysis analysis = ScriptAnalyzer.analyze("overclock", source, true);
        assertTrue(analysis.overload());
        assertEquals(8, analysis.presetLevel(), "an Overload preset's level is the level its code runs at");
    }

    @Test
    void anOverloadPresetsCodeStaysAtItsOwnLevel() {
        // A normal LV5 preset runs at LV6 and could use damage_boost; an Overload one runs at exactly LV5.
        String preset = """
                program { name = "Hot", level = 5, overload = true }
                function on_start() me.damage_boost(0.3, 5) end
                """;
        InstallException error = assertThrows(InstallException.class, () -> ScriptAnalyzer.analyze("hot", preset, true));
        assertTrue(error.getMessage().contains("me.damage_boost"), error.getMessage());
    }

    @Test
    void ownOverloadScriptsReachTwoLevelsUp() throws Exception {
        assertTrue(ScriptAnalyzer.analyze("Surge", OWN_OVERLOAD, false).overload());

        ScriptDiagnostics.Result atFour = ScriptDiagnostics.check("Surge", OWN_OVERLOAD, 4);
        assertTrue(atFour.ok());
        assertTrue(atFour.warnings().isEmpty(), "LV6 code is within reach of LV4: " + atFour.warnings());
        assertEquals(4, atFour.overloadHeat(), "2 a second, plus a gap of 2 levels");

        ScriptDiagnostics.Result atThree = ScriptDiagnostics.check("Surge", OWN_OVERLOAD, 3);
        assertTrue(atThree.warnings().stream().anyMatch(warning -> warning.contains("won't run yet")), atThree.warnings().toString());
        assertTrue(atThree.warnings().stream().anyMatch(warning -> warning.contains("start at LV4")), atThree.warnings().toString());
    }

    @Test
    void withoutTheFlagThereIsNoReach() {
        String plain = OWN_OVERLOAD.replace(", overload = true", "");
        ScriptDiagnostics.Result result = ScriptDiagnostics.check("Surge", plain, 4);
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("won't run yet")), result.warnings().toString());
        assertEquals(-1, result.overloadHeat());
    }

    @Test
    void oneShotProgramsCantOverload() {
        // A transient or query program would apply its boosts and end before paying any Heat.
        for (String type : new String[]{"transient", "query"}) {
            String source = OWN_OVERLOAD.replace("tops = 4,", "type = \"" + type + "\", tops = 4,").replace("on_start", "on_run");
            ScriptDiagnostics.Result result = ScriptDiagnostics.check("Surge", source, 4);
            assertFalse(result.ok(), type);
            assertTrue(result.message().contains("resident"), result.message());
        }
    }

    @Test
    void theFlagMustBeABoolean() {
        ScriptDiagnostics.Result result = ScriptDiagnostics.check("Surge", OWN_OVERLOAD.replace("overload = true", "overload = 1"), 4);
        assertFalse(result.ok());
        assertTrue(result.message().contains("overload"), result.message());
    }
}
