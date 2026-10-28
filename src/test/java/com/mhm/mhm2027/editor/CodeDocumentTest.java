package com.mhm.mhm2027.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mhm.mhm2027.editor.CodeDocument.Pos;

class CodeDocumentTest {
    private static CodeDocument doc(String text) {
        CodeDocument doc = new CodeDocument(64 * 1024);
        doc.setText(text);
        return doc;
    }

    @Test
    void typingAndNewlinesBuildLines() {
        CodeDocument doc = doc("");
        doc.insert("a");
        doc.insert("b");
        doc.newline();
        doc.insert("c");
        assertEquals("ab\nc", doc.text());
        assertEquals(new Pos(1, 1), doc.cursor());
    }

    @Test
    void enterKeepsIndentAndIndentsAfterBlockOpeners() {
        CodeDocument doc = doc("function on_start()");
        doc.end(false);
        doc.newline();
        assertEquals("  ", doc.line(1));
        doc.insert("if x then");
        doc.newline();
        assertEquals("    ", doc.line(2));
        doc.insert("y()");
        doc.newline();
        assertEquals("    ", doc.line(3), "plain lines keep the indent without adding more");
    }

    @Test
    void backspaceInLeadingSpaceRemovesAWholeIndentLevel() {
        CodeDocument doc = doc("    x");
        doc.moveTo(new Pos(0, 4), false);
        doc.backspace(false);
        assertEquals("  x", doc.text());
        doc.backspace(false);
        assertEquals("x", doc.text());
    }

    @Test
    void backspaceAtLineStartJoinsLines() {
        CodeDocument doc = doc("ab\ncd");
        doc.moveTo(new Pos(1, 0), false);
        doc.backspace(false);
        assertEquals("abcd", doc.text());
        assertEquals(new Pos(0, 2), doc.cursor());
    }

    @Test
    void selectionIsReplacedByTyping() {
        CodeDocument doc = doc("hello world");
        doc.moveTo(new Pos(0, 0), false);
        doc.moveTo(new Pos(0, 5), true);
        assertEquals("hello", doc.selectedText());
        doc.insert("bye");
        assertEquals("bye world", doc.text());
    }

    @Test
    void multiLineSelectionAndPaste() {
        CodeDocument doc = doc("one\ntwo\nthree");
        doc.moveTo(new Pos(0, 1), false);
        doc.moveTo(new Pos(2, 2), true);
        assertEquals("ne\ntwo\nth", doc.selectedText());
        doc.insert("X\nY");
        assertEquals("oX\nYree", doc.text());
        assertEquals(new Pos(1, 1), doc.cursor());
    }

    @Test
    void tabIndentsSelectedLinesAndShiftTabUndoesIt() {
        CodeDocument doc = doc("a\nb\nc");
        doc.moveTo(new Pos(0, 0), false);
        doc.moveTo(new Pos(1, 1), true);
        doc.indent();
        assertEquals("  a\n  b\nc", doc.text());
        doc.unindent();
        assertEquals("a\nb\nc", doc.text());
    }

    @Test
    void tabInsertsSpacesToTheNextIndentStop() {
        CodeDocument doc = doc("x");
        doc.end(false);
        doc.indent();
        assertEquals("x ", doc.text(), "column 1 to the stop at column 2");
    }

    @Test
    void undoAndRedoStepThroughEdits() {
        CodeDocument doc = doc("");
        doc.insert("abc");
        doc.newline();
        doc.insert("d");
        assertTrue(doc.undo());
        assertEquals("abc\n", doc.text());
        assertTrue(doc.undo());
        assertTrue(doc.undo());
        assertEquals("", doc.text());
        assertFalse(doc.undo());
        assertTrue(doc.redo());
        assertEquals("abc", doc.text());
    }

    @Test
    void typingAWordIsOneUndoStep() {
        CodeDocument doc = doc("");
        for (char c : "local".toCharArray()) {
            doc.insert(String.valueOf(c));
        }
        doc.undo();
        assertEquals("", doc.text());
    }

    @Test
    void wordMovementSkipsSpacesAndStopsAtWordEdges() {
        CodeDocument doc = doc("me.speed_boost(0.3, 2)");
        doc.moveRight(false, true);
        assertEquals(2, doc.cursor().col(), "stops after 'me'");
        doc.end(false);
        doc.moveLeft(false, true);
        assertEquals(21, doc.cursor().col(), "before ')'");
    }

    @Test
    void homeTogglesBetweenIndentAndColumnZero() {
        CodeDocument doc = doc("    code");
        doc.end(false);
        doc.home(false);
        assertEquals(4, doc.cursor().col());
        doc.home(false);
        assertEquals(0, doc.cursor().col());
    }

    @Test
    void verticalMovementRemembersTheColumn() {
        CodeDocument doc = doc("long line here\nx\nanother long line");
        doc.moveTo(new Pos(0, 10), false);
        doc.moveVertical(1, false);
        assertEquals(new Pos(1, 1), doc.cursor());
        doc.moveVertical(1, false);
        assertEquals(new Pos(2, 10), doc.cursor());
    }

    @Test
    void doubleClickSelectsAWord() {
        CodeDocument doc = doc("sys.notify(\"hi\")");
        doc.selectWord(new Pos(0, 6));
        assertEquals("notify", doc.selectedText());
    }

    @Test
    void lengthLimitCutsOffInsertedText() {
        CodeDocument doc = new CodeDocument(5);
        doc.setText("abc");
        doc.end(false);
        doc.insert("defgh");
        assertEquals("abcde", doc.text());
    }

    @Test
    void tabsAndWindowsLineEndingsAreNormalised() {
        CodeDocument doc = doc("a\r\n\tb");
        assertEquals("a\n  b", doc.text());
    }
}
