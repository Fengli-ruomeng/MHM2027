package com.mhm.mhm2027.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EdgeShellTest {
    private static EdgeShell.Data data() {
        return new EdgeShell.Data("steve", "10.47.203.9", true,
                new EdgeShell.Status("LV4", "Mid", 412, 768, 36, 64, 100, 8600, 10240, 38),
                new EdgeShell.Kernel("QuickHands", "QuickHands", "priority", "normal", "skip", 8, List.of("on_panic"), 10, 3),
                List.of(new EdgeShell.Script("Breach", "Breach", false, false),
                        new EdgeShell.Script("Watcher", "Watcher", false, false),
                        new EdgeShell.Script("QuickHands", "QuickHands", true, false),
                        new EdgeShell.Script("preset:mhm2027:adrenaline_shot", "Adrenaline Shot", false, true)),
                List.of(new EdgeShell.Proc("Watcher", "Watcher", "resident", "idle", 4, 1)),
                List.of(new EdgeShell.Flaw("memory", "critical", "verify = skip", false)),
                List.of(new EdgeShell.Socket(false, "preset:mhm2027:adrenaline_shot", "Adrenaline Shot"), new EdgeShell.Socket(true, "", "")),
                List.of("Watcher", "Breach"),
                List.of("run Breach", "", "", "", "", "", "", ""));
    }

    @Test
    void wheelSetsAndClearsSlots() {
        EdgeShell shell = new EdgeShell();
        shell.submit("wheel set 3 run Breach", data(), 0);
        shell.submit("wheel rm 1", data(), 0);
        assertEquals(List.of(new EdgeShell.Request("WHEEL_SET", "3 run Breach"), new EdgeShell.Request("WHEEL_SET", "1")), shell.drain());

        shell.submit("wheel set 9 run Breach", data(), 0);
        shell.submit("wheel set 2 wheel ls", data(), 0);
        shell.submit("wheel set 2", data(), 0);
        assertTrue(shell.drain().isEmpty(), "bad slots, nested wheels and empty shortcuts are refused");
    }

    @Test
    void wheelListsItsSlots() {
        EdgeShell shell = new EdgeShell();
        List<EdgeShell.Line> printed = shell.submit("wheel", data(), 0);
        assertTrue(printed.getFirst().text().endsWith("wheel"), "the echoed prompt comes first");
        assertTrue(printed.stream().anyMatch(line -> line.text().contains("1  run Breach")));
        assertTrue(printed.stream().anyMatch(line -> line.text().contains("8  -")));
    }

    @Test
    void submitReturnsOnlyItsOwnLines() {
        EdgeShell shell = new EdgeShell();
        shell.submit("help", data(), 0);
        List<EdgeShell.Line> printed = shell.submit("run Nothing", data(), 0);
        assertEquals(2, printed.size());
        assertTrue(printed.get(1).text().contains("no program called Nothing"));
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
    void logOpensWindows() {
        EdgeShell shell = new EdgeShell();
        shell.submit("log", data(), 0);
        shell.submit("log watcher", data(), 0);
        shell.submit("log Nobody", data(), 0);
        assertEquals(List.of(new EdgeShell.Request("WINDOW", EdgeWindows.KERNEL), new EdgeShell.Request("WINDOW", EdgeWindows.program("Watcher"))),
                shell.drain());
        assertTrue(shell.lines().getLast().text().contains("nothing from Nobody"));
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

    private static String text(EdgeShell shell) {
        return String.join("\n", shell.lines().stream().map(EdgeShell.Line::text).toList());
    }

    @Test
    void topOpensItsWindowAndEditWorksWithoutAFile() {
        EdgeShell shell = new EdgeShell();
        shell.submit("top", data(), 0);
        shell.submit("edit", data(), 0);
        shell.submit("edit watcher", data(), 0);
        assertEquals(List.of(new EdgeShell.Request("WINDOW", EdgeWindows.TOP), new EdgeShell.Request("EDIT", ""),
                new EdgeShell.Request("EDIT", "Watcher")), shell.drain());
    }

    @Test
    void manShowsAnApisLevelCostAndDescription() {
        EdgeShell shell = new EdgeShell();
        shell.submit("man me.damage_boost", data(), 0);
        String page = text(shell);
        assertTrue(page.contains("me.damage_boost(amount, seconds)"));
        assertTrue(page.contains("Hit harder"));
        assertTrue(page.contains("LV6 (you're LV4)"), page);
        assertTrue(page.contains("runs from a socket"));
        assertTrue(page.contains("12 s cooldown"));
    }

    @Test
    void manStripsMarkdownAndFindsMembersEventsHooksAndCommands() {
        EdgeShell shell = new EdgeShell();
        shell.setColumns(200);
        shell.submit("man access", data(), 0);
        assertTrue(text(shell).contains("See Cyber Combat."), "links become their text");
        assertFalse(text(shell).contains("`"));
        shell.submit("clear", data(), 0);
        shell.submit("man on_session_opened", data(), 0);
        assertTrue(text(shell).contains("program event") && text(shell).contains("kernel hook"), "it is both");
        shell.submit("clear", data(), 0);
        shell.submit("man scan", data(), 0);
        assertTrue(text(shell).contains("shell command"));
        shell.submit("clear", data(), 0);
        shell.submit("man nonsense", data(), 0);
        assertTrue(text(shell).contains("man: nothing on nonsense"));
    }

    @Test
    void manWrapsLongTextToTheTerminal() {
        EdgeShell shell = new EdgeShell();
        shell.setColumns(30);
        shell.submit("man on_access_request", data(), 0);
        assertTrue(shell.lines().size() > 4);
        shell.lines().stream().skip(2).forEach(line -> assertTrue(line.text().length() <= 30, line.text()));
    }

    @Test
    void manNamespaceListsItsApis() {
        EdgeShell shell = new EdgeShell();
        shell.submit("man remote", data(), 0);
        EdgeShell.Seg access = shell.lines().stream().flatMap(line -> line.segs().stream())
                .filter(seg -> seg.text().startsWith("remote.access(")).findFirst().orElseThrow();
        assertEquals("man remote.access", access.command());
        assertEquals("man remote.access", shell.complete("man remote.acc", data(), 0));
    }

    @Test
    void catAsksForOwnFilesAndRefusesPresets() {
        EdgeShell shell = new EdgeShell();
        shell.submit("cat breach.lua", data(), 0);
        shell.submit("cat Adrenaline Shot", data(), 0);
        assertEquals(List.of(new EdgeShell.Request("CAT", "Breach")), shell.drain());
        assertTrue(shell.lines().getLast().text().contains("locked"));
    }

    @Test
    void aCatFilePrintsNumberedWithItsIndentation() {
        EdgeShell shell = new EdgeShell();
        shell.printFile("Breach", "program { name = \"Breach\" }\n\nfunction on_start()\n\tsys.log(\"hi\") -- go\nend\n", 0);
        List<String> lines = shell.lines().stream().map(EdgeShell.Line::text).toList();
        assertTrue(lines.getFirst().contains("Breach.lua · 5 lines"));
        assertEquals("4    sys.log(\"hi\") -- go", lines.get(4));
        assertEquals("5  end", lines.get(5));
    }

    @Test
    void scanAsksTheServerAndPrintsWhatItFound() {
        EdgeShell shell = new EdgeShell();
        shell.submit("scan", data(), 0);
        shell.submit("scan 8", data(), 0);
        shell.submit("scan far", data(), 0);
        assertEquals(List.of(new EdgeShell.Request("SCAN", ""), new EdgeShell.Request("SCAN", "8")), shell.drain());
        assertTrue(shell.lines().getLast().text().contains("number of blocks"));
        shell.scanned(20, List.of(new EdgeShell.Gateway("10.1.2.3", "Wired Zombie", false, 2, 6.25)), 0);
        String out = text(shell);
        assertTrue(out.contains("10.1.2.3") && out.contains("Wired Zombie") && out.contains("LV2") && out.contains("6.3m"), out);
        assertTrue(out.contains("1 gateway within 20 blocks"));
        shell.scanned(20, List.of(), 0);
        assertTrue(shell.lines().getLast().text().contains("no gateways within 20 blocks"));
    }

    @Test
    void editingANewNameOffersToCreateIt() {
        EdgeShell shell = new EdgeShell();
        shell.submit("edit Hello.lua", data(), 0);
        shell.submit("edit Adrenaline Shot", data(), 0);
        shell.submit("burn Hello", data(), 0);
        shell.submit("edit not a name!", data(), 0);
        assertEquals(List.of(new EdgeShell.Request("EDIT_NEW", "Hello")), shell.drain());
        List<String> lines = shell.lines().stream().map(EdgeShell.Line::text).toList();
        assertTrue(lines.stream().anyMatch(line -> line.contains("Adrenaline Shot is a preset")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("burn: no file called Hello")));
        assertTrue(lines.getLast().contains("edit: no file called not a name!"));
    }
}
