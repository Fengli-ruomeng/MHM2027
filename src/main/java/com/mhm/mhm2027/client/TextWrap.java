package com.mhm.mhm2027.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Word wrap for Edge's output text: lays a line of coloured runs out over rows no wider than
 * the window, breaking at spaces where it can and inside a word only when the word alone is too
 * long. Rows after the first hang under the line's first run when that run is a short label (a
 * prompt, a line number, a timestamp, a "programs" heading), so lists stay readable.
 *
 * Only scrolling output goes through here. Title bars, the prompt, buttons, progress bars and
 * tables drawn as columns are laid out by their own code and never wrap. Pure, so it can be
 * tested; the screen passes the font's width function.
 */
public final class TextWrap {
    /**
     * A piece of one run on one row: characters {@code start} to {@code end} of run {@code run},
     * drawn {@code x} pixels from the row's left edge.
     */
    public record Piece(int run, int start, int end, int x) {
    }

    private TextWrap() {
    }

    /** The rows a line takes at {@code width} pixels: always at least one, maybe empty. */
    public static List<List<Piece>> layout(List<String> runs, int width, ToIntFunction<String> measure) {
        int indent = indent(runs, width, measure);
        List<List<Piece>> rows = new ArrayList<>();
        List<Piece> row = new ArrayList<>();
        int x = 0;
        int rowStart = 0;
        // Spaces wait until the next word is known to fit beside them: a row never ends or
        // (once wrapped) starts with spaces. The line's own leading spaces are kept.
        List<int[]> pending = List.of();
        int pendingWidth = 0;
        for (List<int[]> token : tokens(runs)) {
            boolean spaces = runs.get(token.getFirst()[0]).charAt(token.getFirst()[1]) == ' ';
            int tokenWidth = 0;
            for (int[] at : token) {
                tokenWidth += measure.applyAsInt(String.valueOf(runs.get(at[0]).charAt(at[1])));
            }
            if (spaces && !(rows.isEmpty() && row.isEmpty())) {
                pending = token;
                pendingWidth = tokenWidth;
                continue;
            }
            if (!spaces && x + pendingWidth + tokenWidth > width && x > rowStart) {
                rows.add(row);
                row = new ArrayList<>();
                x = indent;
                rowStart = indent;
            } else {
                for (int[] at : pending) {
                    add(row, at[0], at[1], x);
                    x += measure.applyAsInt(String.valueOf(runs.get(at[0]).charAt(at[1])));
                }
            }
            pending = List.of();
            pendingWidth = 0;
            for (int[] at : token) {
                int charWidth = measure.applyAsInt(String.valueOf(runs.get(at[0]).charAt(at[1])));
                // A word longer than a whole row breaks where it must.
                if (x + charWidth > width && x > rowStart) {
                    rows.add(row);
                    row = new ArrayList<>();
                    x = indent;
                    rowStart = indent;
                }
                add(row, at[0], at[1], x);
                x += charWidth;
            }
        }
        rows.add(row);
        return rows;
    }

    /**
     * How far rows after the first are indented: under the second run when the first is a short
     * label, otherwise under the line's own leading spaces. Never more than half the width.
     */
    static int indent(List<String> runs, int width, ToIntFunction<String> measure) {
        int limit = width / 2;
        if (runs.size() > 1) {
            int label = measure.applyAsInt(runs.getFirst());
            if (label <= limit) {
                return label;
            }
        }
        String first = runs.isEmpty() ? "" : runs.getFirst();
        int spaces = 0;
        while (spaces < first.length() && first.charAt(spaces) == ' ') {
            spaces++;
        }
        return Math.min(limit, measure.applyAsInt(first.substring(0, spaces)));
    }

    /** The line's characters as words and runs of spaces, each a list of {run, index}. Words may cross runs. */
    private static List<List<int[]>> tokens(List<String> runs) {
        List<List<int[]>> tokens = new ArrayList<>();
        List<int[]> token = new ArrayList<>();
        boolean inSpaces = false;
        for (int run = 0; run < runs.size(); run++) {
            String text = runs.get(run);
            for (int i = 0; i < text.length(); i++) {
                boolean space = text.charAt(i) == ' ';
                if (!token.isEmpty() && space != inSpaces) {
                    tokens.add(token);
                    token = new ArrayList<>();
                }
                inSpaces = space;
                token.add(new int[]{run, i});
            }
        }
        if (!token.isEmpty()) {
            tokens.add(token);
        }
        return tokens;
    }

    /** Adds one character to a row, growing the last piece when it's the same run and follows on. */
    private static void add(List<Piece> row, int run, int index, int x) {
        if (!row.isEmpty()) {
            Piece last = row.getLast();
            if (last.run() == run && last.end() == index) {
                row.set(row.size() - 1, new Piece(run, last.start(), index + 1, last.x()));
                return;
            }
        }
        row.add(new Piece(run, index, index + 1, x));
    }
}
