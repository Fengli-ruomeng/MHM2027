package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ScriptAnalyzerTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static String read(String path) throws Exception {
        return Files.readString(RESOURCES.resolve(path), StandardCharsets.UTF_8);
    }

    @Test
    void summarisesOwnProgram() throws Exception {
        ScriptSummary summary = ScriptAnalyzer.analyze("Adrenaline", read("mhm2027/example_scripts/Adrenaline.lua"), false).summary();
        assertEquals("Adrenaline", summary.name());
        assertEquals("resident", summary.kind());
        assertEquals(4, summary.tops());
        assertEquals(2 + 4, summary.ramMb(), "resident base 2 MB + me.speed_boost 4 MB");
        assertEquals(1, summary.level(), "highest API used is LV1");
        assertEquals(List.of("me.speed_boost"), summary.details());
    }

    @Test
    void summarisesOwnKernel() throws Exception {
        ScriptSummary summary = ScriptAnalyzer.analyze("Bulwark", read("mhm2027/example_scripts/Bulwark.lua"), false).summary();
        assertTrue(summary.isKernel());
        assertEquals(2 + 3, summary.tops(), "hook reserve 2 + aggressive gc 3");
        assertEquals(8 + 2 * 2, summary.ramMb());
        assertEquals(0, summary.level(), "two hooks fit an LV0 gateway");
    }

    @Test
    void everyBuiltInPresetLoads() throws Exception {
        try (Stream<Path> files = Files.list(RESOURCES.resolve("data/mhm2027/edge_presets"))) {
            List<Path> presets = files.filter(file -> file.toString().endsWith(".lua")).toList();
            assertTrue(presets.size() >= 4);
            for (Path file : presets) {
                String name = file.getFileName().toString().replace(".lua", "");
                ScriptAnalyzer.Analysis analysis = ScriptAnalyzer.analyze(name, Files.readString(file), true);
                assertTrue(!analysis.description().isEmpty(), name + " should have a description");
            }
        }
    }

    @Test
    void everyExampleScriptInstalls() throws Exception {
        try (Stream<Path> files = Files.list(RESOURCES.resolve("mhm2027/example_scripts"))) {
            List<Path> examples = files.filter(file -> file.toString().endsWith(".lua")).toList();
            assertTrue(examples.size() >= 6);
            for (Path file : examples) {
                String name = file.getFileName().toString().replace(".lua", "");
                ScriptAnalyzer.analyze(name, Files.readString(file), false);
            }
        }
        assertEquals(0, ScriptAnalyzer.analyze("Scanner", read("mhm2027/example_scripts/Scanner.lua"), false).summary().level(),
                "Scanner should work at LV0");
    }

    @Test
    void presetsMayReachExactlyOneLevelAboveTheirRequirement() throws Exception {
        ScriptAnalyzer.Analysis shot = ScriptAnalyzer.analyze("adrenaline_shot", read("data/mhm2027/edge_presets/adrenaline_shot.lua"), true);
        assertEquals(0, shot.presetLevel());

        InstallException tooFar = assertThrows(InstallException.class, () -> ScriptAnalyzer.analyze("cheat", """
                program { name = "Cheat", level = 0 }
                function on_start() me.regen_burst(6) end
                """, true));
        assertTrue(tooFar.getMessage().contains("LV3"), tooFar.getMessage());
    }

    @Test
    void presetKernelsGetTheHookPointsOfTheLevelAbove() throws Exception {
        ScriptAnalyzer.Analysis aegis = ScriptAnalyzer.analyze("aegis", read("data/mhm2027/edge_presets/aegis.lua"), true);
        assertEquals(3, aegis.summary().details().size());

        assertThrows(InstallException.class, () -> ScriptAnalyzer.analyze("greedy", """
                kernel { name = "Greedy", level = 0 }
                function on_panic() end
                function on_low_ram() end
                function on_session_opened() end
                """, true));
    }

    @Test
    void onlyPresetsMaySetPresetFields() {
        assertThrows(InstallException.class, () -> ScriptAnalyzer.analyze("mine", "program { name = \"Mine\", level = 3 }", false));
    }

    @Test
    void refusesScriptsWithoutAHeader() {
        assertThrows(InstallException.class, () -> ScriptAnalyzer.analyze("nothing", "print('hi')", false));
    }
}
