package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mhm.mhm2027.terminal.FlawRules.Approach;
import com.mhm.mhm2027.terminal.FlawRules.ClassDef;
import com.mhm.mhm2027.terminal.FlawRules.Hit;
import com.mhm.mhm2027.terminal.FlawRules.Profile;

class FlawRulesTest {
    private static final Path FLAWS = Path.of("src/main/resources/data/mhm2027/edge_flaws");

    private static List<ClassDef> shipped() throws Exception {
        List<ClassDef> classes = new ArrayList<>();
        for (String name : List.of("memory", "intake", "scheduler", "thermal", "motor", "core")) {
            classes.add(FlawRules.parse(Files.readString(FLAWS.resolve(name + ".json"), StandardCharsets.UTF_8)));
        }
        return classes;
    }

    private static Profile at(List<ClassDef> classes, String schedule, String verify, int rateLimit,
                              int programs, double heat, int enhancements, int hooks, int hookPoints) {
        return FlawRules.evaluate(classes, FlawRules.Reading.of(
                new KernelSettings(schedule, "normal", verify, rateLimit), programs, heat, enhancements, hooks, hookPoints));
    }

    private static Hit hit(Profile profile, String className) {
        return profile.active().stream().filter(flaw -> flaw.className().equals(className)).findFirst().orElseThrow();
    }

    private static Approach approach(Profile profile, String className) {
        return profile.approaching().stream().filter(flaw -> flaw.className().equals(className)).findFirst().orElseThrow();
    }

    private static boolean has(Profile profile, String className) {
        return profile.active().stream().anyMatch(flaw -> flaw.className().equals(className));
    }

    @Test
    void memoryFollowsVerifyAndKeepsTheHigherSeverity() throws Exception {
        List<ClassDef> classes = shipped();
        assertFalse(classes.getFirst().dynamic());

        Profile full = at(classes, "fair", "full", 4, 0, 0, 0, 0, 2);
        assertFalse(has(full, "memory"));

        Hit fast = hit(at(classes, "fair", "fast", 4, 0, 0, 0, 0, 2), "memory");
        assertEquals(FlawSeverity.MINOR, fast.severity());
        assertEquals("verify = fast", fast.cause());
        assertFalse(fast.dynamic());

        Hit skip = hit(at(classes, "fair", "skip", 4, 0, 0, 0, 0, 2), "memory");
        assertEquals(FlawSeverity.CRITICAL, skip.severity());
        assertEquals("verify = skip", skip.cause());
    }

    @Test
    void intakeRisesWithRateLimitAndWarnsJustBelow() throws Exception {
        List<ClassDef> classes = shipped();
        assertFalse(has(at(classes, "fair", "full", 5, 0, 0, 0, 0, 2), "intake"));
        assertFalse(at(classes, "fair", "full", 5, 0, 0, 0, 0, 2).approaching().stream().anyMatch(flaw -> flaw.className().equals("intake")));

        Approach close = approach(at(classes, "fair", "full", 6, 0, 0, 0, 0, 2), "intake");
        assertEquals(FlawSeverity.MINOR, close.severity());

        Hit minor = hit(at(classes, "fair", "full", 8, 0, 0, 0, 0, 2), "intake");
        assertEquals(FlawSeverity.MINOR, minor.severity());
        assertEquals("rate_limit 8", minor.cause());

        Profile almostMajor = at(classes, "fair", "full", 10, 0, 0, 0, 0, 2);
        assertEquals(FlawSeverity.MINOR, hit(almostMajor, "intake").severity());
        assertEquals(FlawSeverity.MAJOR, approach(almostMajor, "intake").severity());

        Hit major = hit(at(classes, "fair", "full", 12, 0, 0, 0, 0, 2), "intake");
        assertEquals(FlawSeverity.MAJOR, major.severity());
        assertEquals("rate_limit 12", major.cause());
        assertTrue(at(classes, "fair", "full", 12, 0, 0, 0, 0, 2).approaching().stream().noneMatch(flaw -> flaw.className().equals("intake")));
    }

    @Test
    void schedulerIsMinorUntilManyProgramsAreRunning() throws Exception {
        List<ClassDef> classes = shipped();
        ClassDef scheduler = classes.stream().filter(flaw -> flaw.className().equals("scheduler")).findFirst().orElseThrow();
        assertFalse(scheduler.dynamic(), "running programs raise the severity, but the class stays a setting flaw");

        assertFalse(has(at(classes, "fair", "full", 4, 5, 0, 0, 0, 2), "scheduler"));

        Hit idle = hit(at(classes, "priority", "full", 4, 0, 0, 0, 0, 2), "scheduler");
        assertEquals(FlawSeverity.MINOR, idle.severity());
        assertEquals("schedule = priority", idle.cause());

        Profile busy = at(classes, "priority", "full", 4, 2, 0, 0, 0, 2);
        assertEquals(FlawSeverity.MINOR, hit(busy, "scheduler").severity());
        assertEquals("programs 2, needs at least 3", approach(busy, "scheduler").cause());

        Hit loaded = hit(at(classes, "priority", "full", 4, 3, 0, 0, 0, 2), "scheduler");
        assertEquals(FlawSeverity.MAJOR, loaded.severity());
        assertEquals("schedule = priority, 3 programs running", loaded.cause());
    }

    @Test
    void dynamicFlawsFollowTheFightAndClearWhenItEnds() throws Exception {
        List<ClassDef> classes = shipped();

        assertFalse(has(at(classes, "fair", "full", 4, 0, 49, 0, 0, 2), "thermal"));
        Approach warming = approach(at(classes, "fair", "full", 4, 0, 50, 0, 0, 2), "thermal");
        assertEquals(FlawSeverity.MAJOR, warming.severity());
        assertTrue(warming.dynamic());
        Hit hot = hit(at(classes, "fair", "full", 4, 0, 70, 0, 0, 2), "thermal");
        assertEquals(FlawSeverity.MAJOR, hot.severity());
        assertEquals("heat 70", hot.cause());
        assertEquals("heat 70.5", hit(at(classes, "fair", "full", 4, 0, 70.5, 0, 0, 2), "thermal").cause());

        assertFalse(has(at(classes, "fair", "full", 4, 0, 0, 0, 0, 2), "motor"));
        assertEquals(FlawSeverity.MINOR, hit(at(classes, "fair", "full", 4, 0, 0, 1, 0, 2), "motor").severity());
        Profile two = at(classes, "fair", "full", 4, 0, 0, 2, 0, 2);
        assertEquals(FlawSeverity.MINOR, hit(two, "motor").severity());
        assertEquals(FlawSeverity.MAJOR, approach(two, "motor").severity());
        assertEquals(FlawSeverity.MAJOR, hit(at(classes, "fair", "full", 4, 0, 0, 3, 0, 2), "motor").severity());
        assertEquals("body enhancements: 3", hit(at(classes, "fair", "full", 4, 0, 0, 3, 0, 2), "motor").cause());

        assertFalse(has(at(classes, "fair", "full", 4, 0, 0, 0, 0, 2), "core"));
        Approach oneShort = approach(at(classes, "fair", "full", 4, 0, 0, 0, 1, 2), "core");
        assertEquals("1 of 2 hook points", oneShort.cause());
        Hit full = hit(at(classes, "fair", "full", 4, 0, 0, 0, 2, 2), "core");
        assertEquals(FlawSeverity.MINOR, full.severity());
        assertEquals("using all 2 hook points", full.cause());
        assertTrue(full.dynamic());
        assertEquals(FlawSeverity.MINOR, hit(at(classes, "fair", "full", 4, 0, 0, 0, 3, 2), "core").severity());
    }

    @Test
    void installLogListsStaticFlawsAndChangesReportNoneWhenOneClears() throws Exception {
        List<ClassDef> classes = shipped();
        Profile exposed = at(classes, "priority", "skip", 12, 4, 80, 3, 2, 2);
        assertEquals(List.of(
                "flaw memory critical: verify = skip",
                "flaw intake major: rate_limit 12",
                "flaw scheduler major: schedule = priority, 4 programs running"),
                FlawRules.installLog(exposed));

        Profile calm = at(classes, "fair", "full", 4, 0, 0, 0, 0, 2);
        assertEquals(List.of("no static flaws"), FlawRules.installLog(calm));

        assertEquals(List.of(
                new FlawRules.Change("memory", "critical"),
                new FlawRules.Change("intake", "major"),
                new FlawRules.Change("scheduler", "major"),
                new FlawRules.Change("thermal", "major"),
                new FlawRules.Change("motor", "major"),
                new FlawRules.Change("core", "minor")),
                FlawRules.changes(List.of(), exposed.active()));

        Profile cooled = at(classes, "priority", "skip", 12, 4, 0, 3, 2, 2);
        assertEquals(List.of(new FlawRules.Change("thermal", "none")),
                FlawRules.changes(exposed.active(), cooled.active()));
        assertEquals(List.of(), FlawRules.changes(cooled.active(), cooled.active()));
    }

    @Test
    void aStateRuleIsDynamicUnlessTheFileSaysOtherwise() throws Exception {
        ClassDef inferred = FlawRules.parse("""
                { "id": "edge:custom", "rules": [
                  { "when": { "state": "heat", "at_least": 10 }, "severity": "minor" }
                ]}
                """);
        assertTrue(inferred.dynamic());
        assertEquals("custom", inferred.className());

        FlawRules.FlawFormatException bad = assertThrows(FlawRules.FlawFormatException.class, () -> FlawRules.parse("""
                { "id": "edge:memory", "rules": [
                  { "when": { "setting": "verify", "equal": "skip" }, "severity": "critical" }
                ]}
                """));
        assertTrue(bad.getMessage().contains("unknown field"), bad.getMessage());
    }

    @Test
    void theDesignsMemoryExampleParses() throws Exception {
        ClassDef memory = FlawRules.parse("""
                {
                  "id": "edge:memory",
                  "rules": [
                    { "when": { "setting": "verify", "equals": "fast" }, "severity": "minor" },
                    { "when": { "setting": "verify", "equals": "skip" }, "severity": "critical" }
                  ]
                }
                """);
        assertEquals("edge:memory", memory.id());
        assertEquals("memory", memory.className());
        Profile skip = FlawRules.evaluate(List.of(memory), FlawRules.Reading.of(new KernelSettings("fair", "normal", "skip", 4), 0, 0, 0, 0, 2));
        assertEquals("memory critical: verify = skip", FlawRules.warning(skip.active().getFirst()));
    }
}
