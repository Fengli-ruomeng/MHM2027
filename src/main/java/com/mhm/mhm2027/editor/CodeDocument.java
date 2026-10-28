package com.mhm.mhm2027.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The text model behind the in-game code editor: lines, a cursor, a selection, and undo.
 * Pure Java, so every editing rule is unit-tested without a game.
 *
 * Positions are (line, column) with columns counted in characters. Tabs are stored as two
 * spaces, which is also what the Tab key inserts.
 */
public final class CodeDocument {
    public static final String INDENT = "  ";
    private static final int MAX_UNDO = 200;
    private static final long COALESCE_MILLIS = 1000;

    public record Pos(int line, int col) implements Comparable<Pos> {
        @Override
        public int compareTo(Pos other) {
            return line != other.line ? Integer.compare(line, other.line) : Integer.compare(col, other.col);
        }
    }

    private record Snapshot(List<String> lines, Pos cursor, Pos anchor) {
    }

    /** Groups edits so that undo steps back by sensible amounts, not one key at a time. */
    private enum EditKind {
        TYPE, DELETE, OTHER
    }

    private final int maxLength;
    private final List<String> lines = new ArrayList<>(List.of(""));
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private Pos cursor = new Pos(0, 0);
    /** Where a selection started, or null when nothing is selected. */
    private Pos anchor;
    /** The column vertical movement tries to keep, like any editor. */
    private int preferredCol = -1;
    private EditKind lastEdit = EditKind.OTHER;
    private long lastEditAt;
    private int version;

    public CodeDocument(int maxLength) {
        this.maxLength = maxLength;
    }

    // region Text

    /** Replaces everything and clears undo, as when opening a file. */
    public void setText(String text) {
        lines.clear();
        lines.addAll(split(normalize(text)));
        cursor = new Pos(0, 0);
        anchor = null;
        undo.clear();
        redo.clear();
        lastEdit = EditKind.OTHER;
        version++;
    }

    public String text() {
        return String.join("\n", lines);
    }

    public int lineCount() {
        return lines.size();
    }

    public String line(int index) {
        return lines.get(index);
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }

    public int length() {
        int total = lines.size() - 1;
        for (String line : lines) {
            total += line.length();
        }
        return total;
    }

    /** Goes up on every change, so callers can tell when to re-check or re-highlight. */
    public int version() {
        return version;
    }

    // endregion

    // region Cursor and selection

    public Pos cursor() {
        return cursor;
    }

    public boolean hasSelection() {
        return anchor != null && !anchor.equals(cursor);
    }

    public Pos selectionStart() {
        return hasSelection() && anchor.compareTo(cursor) < 0 ? anchor : cursor;
    }

    public Pos selectionEnd() {
        return hasSelection() && anchor.compareTo(cursor) > 0 ? anchor : cursor;
    }

    public String selectedText() {
        return hasSelection() ? textBetween(selectionStart(), selectionEnd()) : "";
    }

    public void selectAll() {
        anchor = new Pos(0, 0);
        cursor = endOfDocument();
    }

    /** Moves the cursor; with {@code select} the selection grows from where it was. */
    public void moveTo(Pos target, boolean select) {
        if (select) {
            if (anchor == null) {
                anchor = cursor;
            }
        } else {
            anchor = null;
        }
        cursor = clamp(target);
        lastEdit = EditKind.OTHER;
    }

    public void moveLeft(boolean select, boolean word) {
        if (!select && hasSelection()) {
            moveTo(selectionStart(), false);
        } else {
            moveTo(word ? wordBoundary(cursor, -1) : offset(cursor, -1), select);
        }
        preferredCol = -1;
    }

    public void moveRight(boolean select, boolean word) {
        if (!select && hasSelection()) {
            moveTo(selectionEnd(), false);
        } else {
            moveTo(word ? wordBoundary(cursor, 1) : offset(cursor, 1), select);
        }
        preferredCol = -1;
    }

    public void moveVertical(int lines, boolean select) {
        if (preferredCol < 0) {
            preferredCol = cursor.col;
        }
        int line = Math.clamp(cursor.line + lines, 0, this.lines.size() - 1);
        moveTo(new Pos(line, Math.min(preferredCol, this.lines.get(line).length())), select);
    }

    /** Home goes to the first non-blank character, then to column 0, like most code editors. */
    public void home(boolean select) {
        String line = lines.get(cursor.line);
        int indent = indentOf(line).length();
        moveTo(new Pos(cursor.line, cursor.col == indent ? 0 : indent), select);
        preferredCol = -1;
    }

    public void end(boolean select) {
        moveTo(new Pos(cursor.line, lines.get(cursor.line).length()), select);
        preferredCol = -1;
    }

    public void documentStart(boolean select) {
        moveTo(new Pos(0, 0), select);
        preferredCol = -1;
    }

    public void documentEnd(boolean select) {
        moveTo(endOfDocument(), select);
        preferredCol = -1;
    }

    /** Selects the word (or run of other characters) around a position, as on a double click. */
    public void selectWord(Pos at) {
        Pos clamped = clamp(at);
        String line = lines.get(clamped.line);
        if (line.isEmpty()) {
            moveTo(clamped, false);
            return;
        }
        int col = Math.min(clamped.col, line.length() - 1);
        boolean word = isWordChar(line.charAt(col));
        int start = col;
        while (start > 0 && isWordChar(line.charAt(start - 1)) == word) {
            start--;
        }
        int end = col;
        while (end < line.length() && isWordChar(line.charAt(end)) == word) {
            end++;
        }
        anchor = new Pos(clamped.line, start);
        cursor = new Pos(clamped.line, end);
    }

    // endregion

    // region Editing

    /** Types or pastes text, replacing the selection. Stops at the length limit. */
    public void insert(String text) {
        String clean = normalize(text);
        if (clean.isEmpty() && !hasSelection()) {
            return;
        }
        int room = maxLength - (length() - selectedText().length());
        if (room <= 0) {
            return;
        }
        if (clean.length() > room) {
            clean = clean.substring(0, room);
        }
        checkpoint(clean.length() == 1 && !clean.equals("\n") ? EditKind.TYPE : EditKind.OTHER);
        deleteSelectionRaw();
        insertRaw(clean);
    }

    /** Enter: a new line that keeps the indent, one level deeper after a block opener. */
    public void newline() {
        String line = lines.get(cursor.line);
        String before = line.substring(0, Math.min(cursor.col, line.length()));
        String indent = indentOf(line);
        if (opensBlock(before)) {
            indent += INDENT;
        }
        insert("\n" + indent);
    }

    public void backspace(boolean word) {
        if (hasSelection()) {
            checkpoint(EditKind.OTHER);
            deleteSelectionRaw();
            return;
        }
        Pos from = word ? wordBoundary(cursor, -1) : offset(cursor, -1);
        // In leading whitespace, backspace removes a whole indent level.
        String line = lines.get(cursor.line);
        if (!word && cursor.col > 0 && line.substring(0, cursor.col).isBlank()) {
            from = new Pos(cursor.line, Math.max(0, (cursor.col - 1) / INDENT.length() * INDENT.length()));
        }
        if (!from.equals(cursor)) {
            checkpoint(EditKind.DELETE);
            deleteRaw(from, cursor);
        }
    }

    public void delete(boolean word) {
        if (hasSelection()) {
            checkpoint(EditKind.OTHER);
            deleteSelectionRaw();
            return;
        }
        Pos to = word ? wordBoundary(cursor, 1) : offset(cursor, 1);
        if (!to.equals(cursor)) {
            checkpoint(EditKind.DELETE);
            deleteRaw(cursor, to);
        }
    }

    /** Tab: indent the selected lines, or insert spaces up to the next indent stop. */
    public void indent() {
        if (hasSelection() && selectionStart().line != selectionEnd().line) {
            shiftLines(true);
            return;
        }
        int spaces = INDENT.length() - cursor.col % INDENT.length();
        insert(" ".repeat(spaces));
    }

    /** Shift+Tab: unindent the current or selected lines. */
    public void unindent() {
        shiftLines(false);
    }

    public void deleteSelection() {
        if (hasSelection()) {
            checkpoint(EditKind.OTHER);
            deleteSelectionRaw();
        }
    }

    // endregion

    // region Undo

    public boolean undo() {
        if (undo.isEmpty()) {
            return false;
        }
        redo.push(snapshot());
        restore(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) {
            return false;
        }
        undo.push(snapshot());
        restore(redo.pop());
        return true;
    }

    /** Saves an undo point unless this edit continues the previous one (typing a word, say). */
    private void checkpoint(EditKind kind) {
        long now = System.currentTimeMillis();
        boolean continues = kind != EditKind.OTHER && kind == lastEdit && now - lastEditAt < COALESCE_MILLIS && !hasSelection();
        if (!continues) {
            undo.push(snapshot());
            while (undo.size() > MAX_UNDO) {
                undo.removeLast();
            }
        }
        redo.clear();
        lastEdit = kind;
        lastEditAt = now;
    }

    private Snapshot snapshot() {
        return new Snapshot(List.copyOf(lines), cursor, anchor);
    }

    private void restore(Snapshot snapshot) {
        lines.clear();
        lines.addAll(snapshot.lines());
        cursor = clamp(snapshot.cursor());
        anchor = snapshot.anchor() == null ? null : clamp(snapshot.anchor());
        lastEdit = EditKind.OTHER;
        version++;
    }

    // endregion

    // region Internals

    private void insertRaw(String text) {
        String line = lines.get(cursor.line);
        String before = line.substring(0, cursor.col);
        String after = line.substring(cursor.col);
        List<String> pieces = split(text);
        if (pieces.size() == 1) {
            lines.set(cursor.line, before + text + after);
            cursor = new Pos(cursor.line, cursor.col + text.length());
        } else {
            lines.set(cursor.line, before + pieces.getFirst());
            for (int i = 1; i < pieces.size() - 1; i++) {
                lines.add(cursor.line + i, pieces.get(i));
            }
            int lastLine = cursor.line + pieces.size() - 1;
            lines.add(lastLine, pieces.getLast() + after);
            cursor = new Pos(lastLine, pieces.getLast().length());
        }
        anchor = null;
        preferredCol = -1;
        version++;
    }

    private void deleteSelectionRaw() {
        if (hasSelection()) {
            deleteRaw(selectionStart(), selectionEnd());
        }
        anchor = null;
    }

    private void deleteRaw(Pos from, Pos to) {
        Pos start = from.compareTo(to) <= 0 ? from : to;
        Pos end = from.compareTo(to) <= 0 ? to : from;
        String head = lines.get(start.line).substring(0, start.col);
        String tail = lines.get(end.line).substring(end.col);
        for (int i = end.line; i > start.line; i--) {
            lines.remove(i);
        }
        lines.set(start.line, head + tail);
        cursor = start;
        anchor = null;
        preferredCol = -1;
        version++;
    }

    private void shiftLines(boolean in) {
        int first = selectionStart().line;
        int last = selectionEnd().line;
        // A selection ending at column 0 doesn't include that last line.
        if (hasSelection() && last > first && selectionEnd().col == 0) {
            last--;
        }
        checkpoint(EditKind.OTHER);
        boolean changed = false;
        for (int i = first; i <= last; i++) {
            String line = lines.get(i);
            if (in) {
                lines.set(i, INDENT + line);
                changed = true;
            } else {
                int remove = Math.min(INDENT.length(), indentOf(line).length());
                if (remove > 0) {
                    lines.set(i, line.substring(remove));
                    changed = true;
                }
            }
        }
        if (!changed) {
            return;
        }
        int delta = in ? INDENT.length() : -INDENT.length();
        if (hasSelection()) {
            anchor = clamp(new Pos(anchor.line, anchor.col == 0 ? 0 : anchor.col + delta));
        }
        cursor = clamp(new Pos(cursor.line, cursor.col == 0 && hasSelection() ? 0 : Math.max(0, cursor.col + delta)));
        version++;
    }

    private String textBetween(Pos start, Pos end) {
        if (start.line == end.line) {
            return lines.get(start.line).substring(start.col, end.col);
        }
        StringBuilder text = new StringBuilder(lines.get(start.line).substring(start.col));
        for (int i = start.line + 1; i < end.line; i++) {
            text.append('\n').append(lines.get(i));
        }
        return text.append('\n').append(lines.get(end.line), 0, end.col).toString();
    }

    private Pos offset(Pos pos, int direction) {
        if (direction < 0) {
            if (pos.col > 0) {
                return new Pos(pos.line, pos.col - 1);
            }
            return pos.line > 0 ? new Pos(pos.line - 1, lines.get(pos.line - 1).length()) : pos;
        }
        if (pos.col < lines.get(pos.line).length()) {
            return new Pos(pos.line, pos.col + 1);
        }
        return pos.line < lines.size() - 1 ? new Pos(pos.line + 1, 0) : pos;
    }

    /** The next word edge, skipping spaces first, as Ctrl+arrow does in most editors. */
    private Pos wordBoundary(Pos pos, int direction) {
        String line = lines.get(pos.line);
        int col = pos.col;
        if (direction < 0) {
            if (col == 0) {
                return offset(pos, -1);
            }
            while (col > 0 && line.charAt(col - 1) == ' ') {
                col--;
            }
            if (col > 0) {
                boolean word = isWordChar(line.charAt(col - 1));
                while (col > 0 && line.charAt(col - 1) != ' ' && isWordChar(line.charAt(col - 1)) == word) {
                    col--;
                }
            }
        } else {
            if (col == line.length()) {
                return offset(pos, 1);
            }
            while (col < line.length() && line.charAt(col) == ' ') {
                col++;
            }
            if (col < line.length()) {
                boolean word = isWordChar(line.charAt(col));
                while (col < line.length() && line.charAt(col) != ' ' && isWordChar(line.charAt(col)) == word) {
                    col++;
                }
            }
        }
        return new Pos(pos.line, col);
    }

    private Pos clamp(Pos pos) {
        int line = Math.clamp(pos.line, 0, lines.size() - 1);
        return new Pos(line, Math.clamp(pos.col, 0, lines.get(line).length()));
    }

    private Pos endOfDocument() {
        int last = lines.size() - 1;
        return new Pos(last, lines.get(last).length());
    }

    static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    static String indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return line.substring(0, i);
    }

    /** Lines that open a Lua block, after which Enter indents one level deeper. */
    static boolean opensBlock(String code) {
        String trimmed = code.strip();
        int comment = trimmed.indexOf("--");
        if (comment >= 0) {
            trimmed = trimmed.substring(0, comment).strip();
        }
        return trimmed.endsWith(" then") || trimmed.equals("then") || trimmed.endsWith(" do") || trimmed.equals("do")
                || trimmed.equals("else") || trimmed.endsWith("{") || trimmed.equals("repeat")
                || (trimmed.matches(".*\\bfunction\\b.*\\)$") && !trimmed.endsWith("end"));
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", INDENT);
    }

    private static List<String> split(String text) {
        return new ArrayList<>(List.of(text.split("\n", -1)));
    }

    // endregion
}
