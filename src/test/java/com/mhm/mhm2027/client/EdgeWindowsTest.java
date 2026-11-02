package com.mhm.mhm2027.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EdgeWindowsTest {
    private static EdgeWindows desktop() {
        EdgeWindows windows = new EdgeWindows();
        windows.setArea(new EdgeWindows.Rect(6, 20, 628, 318));
        windows.open(EdgeWindows.TERMINAL, 0);
        return windows;
    }

    @Test
    void programsStackDownTheRightOfTheTerminal() {
        EdgeWindows windows = desktop();
        EdgeWindows.Window first = windows.open(EdgeWindows.program("Breach"), 0);
        EdgeWindows.Window second = windows.open(EdgeWindows.program("Watcher"), 0);
        EdgeWindows.Rect terminal = windows.bounds(windows.get(EdgeWindows.TERMINAL).orElseThrow());
        EdgeWindows.Rect a = windows.bounds(first);
        EdgeWindows.Rect b = windows.bounds(second);
        assertTrue(a.x() > terminal.right());
        assertEquals(windows.area().y(), a.y());
        assertTrue(b.y() >= a.bottom(), "the second goes below the first");
        assertEquals(windows.area().right(), a.right());
    }

    @Test
    void theNewestWindowIsOnTopAndFocusMovesIt() {
        EdgeWindows windows = desktop();
        windows.open(EdgeWindows.program("Breach"), 0);
        assertEquals(EdgeWindows.program("Breach"), windows.focused().orElseThrow().id());
        windows.focus(EdgeWindows.TERMINAL);
        assertEquals(EdgeWindows.TERMINAL, windows.focused().orElseThrow().id());
    }

    @Test
    void thePillOpensFocusesAndMinimises() {
        EdgeWindows windows = desktop();
        String breach = EdgeWindows.program("Breach");
        windows.pill(breach, 0);
        assertTrue(windows.get(breach).orElseThrow().shown());
        windows.pill(breach, 0);
        assertTrue(windows.get(breach).orElseThrow().minimised(), "on top, so a click minimises it");
        assertEquals(EdgeWindows.TERMINAL, windows.focused().orElseThrow().id());
        windows.pill(breach, 0);
        assertTrue(windows.get(breach).orElseThrow().shown());
        assertEquals(breach, windows.focused().orElseThrow().id());
    }

    @Test
    void aClosingWindowIsGoneOnceItsAnimationEnds() {
        EdgeWindows windows = desktop();
        String breach = EdgeWindows.program("Breach");
        windows.open(breach, 0);
        windows.close(breach, 1000);
        assertTrue(windows.get(breach).isEmpty());
        assertEquals(2, windows.windows().size(), "still drawn while it folds away");
        windows.prune(1000 + EdgeWindows.ANIMATION_MILLIS);
        assertEquals(1, windows.windows().size());
    }

    @Test
    void windowsStayOnTheDesktop() {
        EdgeWindows windows = desktop();
        String breach = EdgeWindows.program("Breach");
        windows.open(breach, 0);
        windows.move(breach, -500, 9999);
        EdgeWindows.Rect rect = windows.bounds(windows.get(breach).orElseThrow());
        assertEquals(windows.area().x(), rect.x());
        assertEquals(windows.area().bottom(), rect.bottom());

        windows.resize(breach, 10, 10);
        rect = windows.bounds(windows.get(breach).orElseThrow());
        assertEquals(EdgeWindows.MIN_WIDTH, rect.width());
        assertEquals(EdgeWindows.MIN_HEIGHT, rect.height());
    }

    @Test
    void maximisingFillsTheDesktopAndDraggingRestoresIt() {
        EdgeWindows windows = desktop();
        String breach = EdgeWindows.program("Breach");
        EdgeWindows.Window window = windows.open(breach, 0);
        EdgeWindows.Rect before = windows.bounds(window);
        windows.toggleMaximise(breach);
        assertEquals(windows.area(), windows.bounds(window));
        windows.move(breach, before.x(), before.y());
        assertFalse(window.maximised());
        assertEquals(before, windows.bounds(window));
    }

    @Test
    void theTopWindowAtAPointWins() {
        EdgeWindows windows = desktop();
        EdgeWindows.Rect terminal = windows.bounds(windows.get(EdgeWindows.TERMINAL).orElseThrow());
        String breach = EdgeWindows.program("Breach");
        windows.open(breach, 0);
        windows.move(breach, terminal.x(), terminal.y());
        assertEquals(breach, windows.at(terminal.x() + 5, terminal.y() + 5).orElseThrow().id());
        windows.minimise(breach, 0);
        assertEquals(EdgeWindows.TERMINAL, windows.at(terminal.x() + 5, terminal.y() + 5).orElseThrow().id());
    }

    @Test
    void aSmallerScreenPullsWindowsBackIn() {
        EdgeWindows windows = desktop();
        String breach = EdgeWindows.program("Breach");
        windows.open(breach, 0);
        windows.setArea(new EdgeWindows.Rect(6, 20, 300, 200));
        EdgeWindows.Rect rect = windows.bounds(windows.get(breach).orElseThrow());
        assertTrue(rect.right() <= 306 && rect.bottom() <= 220);
    }

    @Test
    void theEditorCoversMostOfTheDesktopAndDoesntPushProgramsAside() {
        EdgeWindows windows = desktop();
        EdgeWindows.Window editor = windows.open(EdgeWindows.EDITOR, 0);
        EdgeWindows.Rect rect = windows.bounds(editor);
        assertEquals(windows.area().right(), rect.right());
        assertEquals(windows.area().height(), rect.height());
        assertTrue(rect.x() > windows.area().x(), "a strip of the terminal stays visible");
        EdgeWindows.Window top = windows.open(EdgeWindows.TOP, 0);
        assertEquals(windows.area().y(), windows.bounds(top).y(), "top still goes in the first gap of the right column");
    }
}
