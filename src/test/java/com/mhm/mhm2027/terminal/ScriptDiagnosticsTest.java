package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void badHeaderFieldsPointAtTheHeader() {
        ScriptDiagnostics.Result result = ScriptDiagnostics.check("Typo", "-- comment\nprogram { name = \"T\", top = 3 }\n", 0);
        assertFalse(result.ok());
        assertEquals(1, result.line());
    }
}
