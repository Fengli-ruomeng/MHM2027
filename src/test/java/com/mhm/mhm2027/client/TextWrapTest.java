package com.mhm.mhm2027.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

class TextWrapTest {
    /** Every character 6 pixels wide, like the game font's digits. */
    private static final ToIntFunction<String> MONO = text -> text.length() * 6;

    /** The text of each row, with its indent written as spaces. */
    private static List<String> rows(List<String> runs, int width) {
        return TextWrap.layout(runs, width, MONO).stream().map(row -> {
            StringBuilder out = new StringBuilder();
            for (TextWrap.Piece piece : row) {
                while (out.length() * 6 < piece.x()) {
                    out.append(' ');
                }
                out.append(runs.get(piece.run()), piece.start(), piece.end());
            }
            return out.toString();
        }).toList();
    }

    @Test
    void aShortLineStaysOnOneRow() {
        assertEquals(List.of("hello world"), rows(List.of("hello world"), 600));
    }

    @Test
    void aLongLineBreaksAtSpaces() {
        assertEquals(List.of("run: no program", "called Breach"), rows(List.of("run: no program called Breach"), 6 * 16));
    }

    @Test
    void rowsHangUnderAShortLabel() {
        List<String> runs = List.of("programs  ", "Adrenaline.lua  Aim.lua  Breach.lua");
        assertEquals(List.of("programs  Adrenaline.lua", "          Aim.lua", "          Breach.lua"), rows(runs, 6 * 26));
    }

    @Test
    void aWordTooLongForARowIsSplit() {
        assertEquals(List.of("abcdef", "ghij"), rows(List.of("abcdefghij"), 6 * 6));
    }

    @Test
    void aRunCutByTheWrapKeepsItsIndexOnBothRows() {
        List<String> runs = List.of("> ", "stop Watcher now");
        List<List<TextWrap.Piece>> layout = TextWrap.layout(runs, 6 * 14, MONO);
        assertEquals(2, layout.size());
        assertEquals(1, layout.get(0).getLast().run());
        assertEquals(1, layout.get(1).getFirst().run());
        assertEquals(12, layout.get(1).getFirst().x(), "continues under the second run");
    }

    @Test
    void wordsCanCrossRunsWithoutBreakingInside() {
        // "LV" and "3" are separate runs but one word: it moves to the next row whole.
        List<String> runs = List.of("level ", "LV", "3");
        assertEquals(List.of("level", "LV3"), rows(runs, 6 * 8));
    }

    @Test
    void aLongFirstRunMeansNoHangingIndent() {
        int indent = TextWrap.indent(List.of("a sentence that is quite long ", "[help]"), 6 * 20, MONO);
        assertEquals(0, indent);
        assertTrue(TextWrap.indent(List.of("  1  ", "code"), 600, MONO) > 0);
    }

    @Test
    void anEmptyLineIsOneEmptyRow() {
        assertEquals(1, TextWrap.layout(List.of(""), 100, MONO).size());
        assertTrue(TextWrap.layout(List.of(""), 100, MONO).getFirst().isEmpty());
    }
}
