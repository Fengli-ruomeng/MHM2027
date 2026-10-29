package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ScriptDiagnosticsTest {
    @Test
    void syntaxErrorsPointAtTheirLine() {
        ScriptDiagnostics.Result result = ScriptDiagnostics.check("Broken", """
                program { name = "Broken" }

                function on_start()
                  if x then
                end
                """, 0);
        assertFalse(result.ok());
        assertTrue(result.line() >= 3, "points at or after the unclosed if: " + result.line() + " " + result.message());
    }

    @Test
    void unknownApisPointAtTheLineThatUsesThem() {
        ScriptDiagnostics.Result result = ScriptDiagnostics.check("Typo", """
                program { name = "Typo" }
                -- me.fly() in a comment doesn't count
                function on_start()
                  me.fly()
                end
                """, 0);
        assertFalse(result.ok());
        assertEquals(3, result.line());
    }

    @Test
    void validCodeAboveYourLevelIsAWarningNotAnError() {
        String source = """
                program { name = "Adrenaline", tops = 4 }
                function on_hit_taken(a, amount)
                  me.speed_boost(0.3, 2)
                end
                """;
        ScriptDiagnostics.Result atZero = ScriptDiagnostics.check("Adrenaline", source, 0);
        assertTrue(atZero.ok());
        assertEquals(1, atZero.warnings().size());
        assertEquals(2, atZero.line());
        assertEquals(6, atZero.summary().ramMb());

        assertTrue(ScriptDiagnostics.check("Adrenaline", source, 1).warnings().isEmpty());
    }

    @Test
    void tooManyHooksForTheGatewayIsAWarning() {
        ScriptDiagnostics.Result result = ScriptDiagnostics.check("Big", """
                kernel { name = "Big" }
                function on_panic(r) end
                function on_low_ram(f) end
                function on_session_opened(w) end
                """, 0);
        assertTrue(result.ok());
        assertEquals(1, result.warnings().size());
        assertEquals(3, result.line());
    }

    @Test
    void aKernelPreviewsTheFlawsItsSettingsCreate() throws Exception {
        Path folder = Path.of("src/main/resources/data/mhm2027/edge_flaws");
        List<FlawRules.ClassDef> flaws = new ArrayList<>();
        for (String name : List.of("memory", "intake", "scheduler", "thermal", "motor", "core")) {
            flaws.add(FlawRules.parse(Files.readString(folder.resolve(name + ".json"), StandardCharsets.UTF_8)));
        }
        ScriptDiagnostics.Result result = ScriptDiagnostics.check("Skip", """
                -- note
                kernel { name = "Skip", verify = "skip", schedule = "priority" }
                function on_panic(reason) end
                function on_low_ram(free) end
                """, 0, flaws);
        assertTrue(result.ok());
        assertEquals(List.of(
                "memory critical: verify = skip",
                "scheduler minor: schedule = priority",
                "core minor: using all 2 hook points"), result.warnings());
        assertEquals(1, result.line());

        ScriptDiagnostics.Result program = ScriptDiagnostics.check("Plain", "program { name = \"Plain\" }\n", 0, flaws);
        assertTrue(program.ok());
        assertTrue(program.warnings().isEmpty());
    }

    @Test
    void badHeaderFieldsPointAtTheHeader() {
        ScriptDiagnostics.Result result = ScriptDiagnostics.check("Typo", "-- comment\nprogram { name = \"T\", top = 3 }\n", 0);
        assertFalse(result.ok());
        assertEquals(1, result.line());
    }
}
