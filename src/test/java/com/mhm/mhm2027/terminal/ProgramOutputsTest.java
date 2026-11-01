package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProgramOutputsTest {
    @Test
    void eachProgramKeepsItsOwnLines() {
        ProgramOutputs outputs = new ProgramOutputs();
        outputs.started("Watcher", "Watcher");
        outputs.started("Breach", "Breach");
        outputs.line("Watcher", 600, "Low health: 8 HP");
        outputs.line("Breach", 601, "access 10.238.207.168");
        outputs.line("Nobody", 602, "dropped");

        ProgramOutputs.Output watcher = outputs.outputs().getFirst();
        assertEquals("Watcher", watcher.name());
        assertEquals(List.of(new ProgramOutputs.Line(600, "Low health: 8 HP")), watcher.lines());
        assertEquals(1, outputs.outputs().get(1).lines().size());
    }

    @Test
    void kernelAndSessionLinesGoToKernelLog() {
        ProgramOutputs outputs = new ProgramOutputs();
        outputs.line("kernel", 10, "denied 10.71.4.200");
        outputs.line("session", 11, "closed on 10.71.4.200 (reboot)");
        assertEquals(List.of("denied 10.71.4.200", "session closed on 10.71.4.200 (reboot)"),
                outputs.kernel().stream().map(ProgramOutputs.Line::text).toList());
    }

    @Test
    void aCrashKeepsTheLineItPointsAt() {
        ProgramOutputs outputs = new ProgramOutputs();
        outputs.started("Watcher", "Watcher");
        outputs.ended("Watcher", "crashed", "Watcher.lua:12: attempt to index a nil value");
        ProgramOutputs.Output watcher = outputs.outputs().getFirst();
        assertEquals("crashed", watcher.ended());
        assertEquals(12, watcher.errorLine());
        assertEquals("attempt to index a nil value", watcher.reason());

        outputs.started("Hot", "Hot");
        outputs.ended("Hot", "crashed", "overheated");
        assertEquals(0, outputs.outputs().get(1).errorLine());
        assertEquals("overheated", outputs.outputs().get(1).reason());
    }

    @Test
    void aNewRunStartsAFreshWindow() {
        ProgramOutputs outputs = new ProgramOutputs();
        outputs.started("Watcher", "Watcher");
        outputs.line("Watcher", 0, "old");
        outputs.ended("Watcher", "stopped", "reboot");
        int first = outputs.outputs().getFirst().serial();

        outputs.started("Watcher", "Watcher");
        ProgramOutputs.Output again = outputs.outputs().getFirst();
        assertTrue(again.serial() > first);
        assertTrue(again.lines().isEmpty());
        assertEquals("", again.ended());
        assertEquals(0, again.total());
    }

    @Test
    void linesAndEndedProgramsAreCapped() {
        ProgramOutputs outputs = new ProgramOutputs();
        outputs.started("Chatty", "Chatty");
        for (int i = 0; i < ProgramOutputs.LINES + 5; i++) {
            outputs.line("Chatty", 0, "line " + i);
        }
        ProgramOutputs.Output chatty = outputs.outputs().getFirst();
        assertEquals(ProgramOutputs.LINES, chatty.lines().size());
        assertEquals(ProgramOutputs.LINES + 5, chatty.total());
        assertEquals("line 5", chatty.lines().getFirst().text());

        for (int i = 0; i < ProgramOutputs.ENDED_KEPT + 2; i++) {
            outputs.started("P" + i, "P" + i);
            outputs.ended("P" + i, "finished", "");
        }
        List<String> names = outputs.outputs().stream().map(ProgramOutputs.Output::name).toList();
        // Chatty is still running, so only the oldest ended ones go.
        assertTrue(names.contains("Chatty"));
        assertEquals(ProgramOutputs.ENDED_KEPT + 1, names.size());
        assertTrue(!names.contains("P0") && !names.contains("P1"));
    }
}
