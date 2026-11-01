package com.mhm.mhm2027.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EdgeShellTest {
    private static EdgeShell.Data data(boolean isNull) {
        return new EdgeShell.Data("steve", "10.47.203.9", isNull, true,
                new EdgeShell.Status("LV4", "Mid", 412, 768, 36, 64, 100, 8600, 10240, 38),
                new EdgeShell.Kernel("QuickHands", "QuickHands", "priority", "normal", "skip", 8, List.of("on_panic"), 10, 3),
                List.of(new EdgeShell.Script("Breach", "Breach", false, false),
                        new EdgeShell.Script("Watcher", "Watcher", false, false),
                        new EdgeShell.Script("QuickHands", "QuickHands", true, false),
                        new EdgeShell.Script("preset:mhm2027:adrenaline_shot", "Adrenaline Shot", false, true)),
                List.of(new EdgeShell.Proc("Watcher", "Watcher", "resident", "idle", 4, 1)),
                List.of(new EdgeShell.Flaw("memory", "critical", "verify = skip", false)),
                List.of(new EdgeShell.Socket(false, "preset:mhm2027:adrenaline_shot", "Adrenaline Shot"), new EdgeShell.Socket(true, "", "")),
                List.of("[Watcher] started", "[kernel] denied 10.71.4.200", "[Watcher] Low health: 8 HP"));
    }

    private static EdgeShell.Data data() {
        return data(false);
    }

    @Test
    void runFindsProgramsByNameIdOrFileName() {
        EdgeShell shell = new EdgeShell();
        shell.submit("run breach.lua", data(), 0);
        shell.submit("run Adrenaline Shot", data(), 0);
        assertEquals(List.of(new EdgeShell.Request("START", "Breach"), new EdgeShell.Request("START", "preset:mhm2027:adrenaline_shot")),
                shell.drain());
        shell.submit("run QuickHands", data(), 0);
        assertTrue(shell.drain().isEmpty(), "a kernel isn't a program");
    }

    @Test
    void unknownCommandsSaySo() {
        EdgeShell shell = new EdgeShell();
        shell.submit("hack", data(), 0);
        assertTrue(shell.lines().getLast().text().contains("command not found"));
    }

    @Test
    void psHasStopButtonsThatRun() {
        EdgeShell shell = new EdgeShell();
        shell.submit("ps", data(), 0);
        EdgeShell.Seg stop = shell.lines().getLast().segs().stream().filter(seg -> seg.text().equals("[stop]")).findFirst().orElseThrow();
        assertTrue(stop.runs());
        assertEquals("stop Watcher", stop.command());
    }

    @Test
    void lsFillsThePromptInsteadOfRunning() {
        EdgeShell shell = new EdgeShell();
        shell.submit("ls", data(), 0);
        EdgeShell.Seg file = shell.lines().stream().flatMap(line -> line.segs().stream())
                .filter(seg -> seg.text().equals("Breach.lua")).findFirst().orElseThrow();
        assertFalse(file.runs());
        assertEquals("run Breach", file.command());
    }

    @Test
    void logFiltersByProgram() {
        EdgeShell shell = new EdgeShell();
        shell.submit("log watcher", data(), 0);
        List<String> printed = shell.lines().stream().skip(1).map(EdgeShell.Line::text).toList();
        assertEquals(List.of("[Watcher] started", "[Watcher] Low health: 8 HP"), printed);
    }

    @Test
    void socketsAndKernelsQueueTheirRequests() {
        EdgeShell shell = new EdgeShell();
        shell.submit("socket add watcher", data(), 0);
        shell.submit("socket rm adrenaline shot", data(), 0);
        shell.submit("kernel install quickhands", data(), 0);
        shell.submit("kernel install Baseline", data(), 0);
        assertEquals(List.of(new EdgeShell.Request("SOCKET", "Watcher"), new EdgeShell.Request("UNSOCKET", "preset:mhm2027:adrenaline_shot"),
                new EdgeShell.Request("INSTALL_KERNEL", "QuickHands"), new EdgeShell.Request("INSTALL_KERNEL", "Baseline")), shell.drain());
    }

    @Test
    void tabCompletesCommandsAndNames() {
        EdgeShell shell = new EdgeShell();
        assertEquals("status ", shell.complete("sta", data(), 0));
        assertEquals("run Adrenaline Shot", shell.complete("run adr", data(), 0));
        assertEquals("socket add ", shell.complete("socket a", data(), 0));
        assertEquals("kernel install QuickHands", shell.complete("kernel install q", data(), 0));
        // Two matches: nothing to add, but both are listed.
        int before = shell.lines().size();
        assertEquals("s", shell.complete("s", data(), 0));
        assertEquals(before + 1, shell.lines().size());
    }

    @Test
    void historyGoesBackAndForth() {
        EdgeShell shell = new EdgeShell();
        shell.submit("ps", data(), 0);
        shell.submit("status", data(), 0);
        assertEquals("status", shell.historyUp(""));
        assertEquals("ps", shell.historyUp(""));
        assertEquals("ps", shell.historyUp(""), "stays at the oldest");
        assertEquals("status", shell.historyDown());
        assertEquals("", shell.historyDown());
    }

    @Test
    void aNullGatewayHasNothingToRun() {
        EdgeShell shell = new EdgeShell();
        shell.submit("run Breach", data(true), 0);
        assertTrue(shell.drain().isEmpty());
        assertTrue(shell.lines().getLast().text().contains("Null"));
    }

    @Test
    void clearEmptiesTheScreen() {
        EdgeShell shell = new EdgeShell();
        shell.submit("help", data(), 0);
        shell.submit("clear", data(), 0);
        assertTrue(shell.lines().isEmpty());
    }

    @Test
    void linesPrintedTogetherTypeInOneAfterAnother() {
        EdgeShell shell = new EdgeShell();
        shell.submit("help", data(), 1000);
        assertTrue(shell.lines().getLast().at() > shell.lines().getFirst().at());
    }
}
