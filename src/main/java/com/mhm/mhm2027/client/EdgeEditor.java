package com.mhm.mhm2027.client;

import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mhm.mhm2027.editor.CodeDocument;
import com.mhm.mhm2027.editor.CodeDocument.Pos;
import com.mhm.mhm2027.editor.Completions;
import com.mhm.mhm2027.editor.LuaHighlighter;
import com.mhm.mhm2027.editor.LuaHighlighter.Span;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.network.EditorCheck;
import com.mhm.mhm2027.network.EditorFile;
import com.mhm.mhm2027.network.EditorRequest;
import com.mhm.mhm2027.network.PanelSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The code editor, as a window inside Edge. Files live on the server, in the player's storage;
 * the editor loads and saves them through it, and asks it to check the code a moment after
 * typing stops: the same analysis an install runs, so the errors and costs shown here are
 * exactly what the terminal will say.
 *
 * Like the shell, it outlives the Edge screen for the session, so unsaved work survives closing
 * Edge. Name prompts and "discard changes?" questions are drawn inside the window, so nothing
 * here ever leaves Edge. Editing rules live in {@link CodeDocument}; this class draws it and
 * routes input.
 */
final class EdgeEditor {
    /** Where the desktop records its regions; checked topmost (last added) first. */
    interface Hits {
        void add(int x, int y, int width, int height, Runnable action);
    }

    /** The click being handled, for regions that care where exactly it landed. */
    record Click(double x, double y, boolean doubleClick, boolean shift) {
    }

    private enum Prompt {
        NEW_PROGRAM, NEW_KERNEL, RENAME
    }

    /** A yes/no question drawn over the editor. */
    private record Confirm(String title, String message, String yes, Runnable action) {
    }

    private static final int TOOLBAR = 15;
    private static final int STATUS_HEIGHT = 34;
    private static final int LINE_HEIGHT = 10;
    private static final int GUTTER_PADDING = 5;
    private static final int TEXT_PADDING = 4;
    private static final int MAX_SUGGESTIONS = 8;
    private static final int MAX_NAME = 32;
    private static final long CHECK_DELAY_MILLIS = 500;
    private static final long CHECK_RETRY_MILLIS = 1500;
    private static final int MAX_LENGTH = 64 * 1024;
    private static final Pattern KERNEL_HEADER = Pattern.compile("(?m)^\\s*kernel\\s*\\{");
    private static final Pattern NAME_CHAR = Pattern.compile("[A-Za-z0-9_\\-]");

    private static final int PANEL = 0xFF0E141E;
    private static final int RULE = 0xFF2C3550;
    private static final int CHIP = 0xFF121826;
    private static final int CHIP_HOVER = 0xFF1C2A3C;
    private static final int SELECTION = 0x803A6EA5;
    private static final int CURRENT_LINE = 0x14FFFFFF;
    private static final int ERROR_LINE = 0x40FF3030;
    private static final int WARN_LINE = 0x30FFB000;
    private static final int POPUP = 0xF0101826;
    private static final int SHADE = 0xA0000000;

    private final CodeDocument document = new CodeDocument(MAX_LENGTH);

    private @Nullable String file;
    private boolean kernelFile;
    private int savedVersion;
    private int scrollLine;
    private int scrollX;
    private boolean dragging;

    /** The file asked for, and the 1-based line to put the cursor on when it arrives (0 for none). */
    private @Nullable String opening;
    private int openingLine;

    private long lastEditAt;
    /** Check results below this request id belong to a file opened earlier. */
    private int firstCheckForFile;
    private int lastCheckSent = -1;
    private long lastCheckSentAt;

    private List<List<Span>> highlight = List.of();
    private int highlightVersion = -1;

    private List<Completions.Suggestion> suggestions = List.of();
    private int suggestion;

    private @Nullable Prompt prompt;
    private String promptText = "";
    private @Nullable Confirm confirm;

    // Layout of the last frame, for mouse and scrolling.
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int sidebar;
    private Font font = Minecraft.getInstance().font;

    // region State

    @Nullable String file() {
        return file;
    }

    boolean dirty() {
        return file != null && document.version() != savedVersion;
    }

    /** A prompt, question or suggestion list is open: Esc closes that instead of Edge. */
    boolean busy() {
        return prompt != null || confirm != null || !suggestions.isEmpty();
    }

    /** Opens a file (asking first if the open one has unsaved changes), with the cursor on {@code line} (1-based, 0 for the top). */
    void open(String name, int line) {
        if (name.equals(file)) {
            if (line > 0) {
                document.moveTo(new Pos(Math.min(line, document.lineCount()) - 1, 0), false);
                ensureCursorVisible();
            }
            return;
        }
        afterDiscardCheck(() -> {
            opening = name;
            openingLine = line;
            ClientPacketDistributor.sendToServer(EditorRequest.of(EditorRequest.Action.OPEN, name));
        });
    }

    /** Asks for a new program's name, with {@code name} filled in. */
    void create(String name) {
        showPrompt(Prompt.NEW_PROGRAM, name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name);
    }

    /** Runs {@code close} now, or once the player agrees to drop unsaved changes. */
    void requestClose(Runnable close) {
        afterDiscardCheck(close);
    }

    /** Called every client tick while Edge is open: picks up files and keeps the live check going. */
    void tick(long now) {
        EditorFile incoming = EditorState.takeFile();
        if (incoming != null) {
            load(incoming);
        }
        if (file == null) {
            return;
        }
        int version = document.version();
        EditorCheck check = EditorState.latestCheck();
        boolean answered = check != null && check.requestId() == lastCheckSent;
        if (version != lastCheckSent && now - lastEditAt >= CHECK_DELAY_MILLIS) {
            sendCheck(version, now);
        } else if (!answered && now - lastCheckSentAt >= CHECK_RETRY_MILLIS) {
            // The server limits how often it checks; ask again if an answer never came.
            sendCheck(version, now);
        }
    }

    private void load(EditorFile incoming) {
        if (incoming.name().isEmpty()) {
            file = null;
            document.setText("");
        } else {
            file = incoming.name();
            document.setText(incoming.content());
        }
        savedVersion = document.version();
        firstCheckForFile = document.version();
        scrollLine = 0;
        scrollX = 0;
        suggestions = List.of();
        lastEditAt = 0;
        lastCheckSent = -1;
        kernelFile = KERNEL_HEADER.matcher(document.text()).find();
        if (openingLine > 0 && file != null && file.equals(opening)) {
            document.moveTo(new Pos(Math.min(openingLine, document.lineCount()) - 1, 0), false);
            ensureCursorVisible();
        }
        opening = null;
        openingLine = 0;
    }

    private void sendCheck(int version, long now) {
        ClientPacketDistributor.sendToServer(new EditorRequest(EditorRequest.Action.CHECK, file, "", document.text(), version));
        lastCheckSent = version;
        lastCheckSentAt = now;
    }

    private void afterDiscardCheck(Runnable action) {
        if (!dirty()) {
            action.run();
            return;
        }
        // The question replaces any name prompt, so it's never hidden behind one.
        prompt = null;
        confirm = new Confirm(tr("mhm2027.editor.unsaved_title"), tr("mhm2027.editor.unsaved_message", file + ".lua"), "discard", () -> {
            savedVersion = document.version();
            action.run();
        });
    }

    // endregion

    // region Actions

    private void save() {
        if (file != null) {
            ClientPacketDistributor.sendToServer(new EditorRequest(EditorRequest.Action.SAVE, file, "", document.text(), 0));
            savedVersion = document.version();
        }
    }

    private void delete() {
        if (file == null) {
            return;
        }
        String target = file;
        confirm = new Confirm(tr("mhm2027.editor.delete_title"), tr("mhm2027.editor.delete_message", target + ".lua"), "delete",
                () -> ClientPacketDistributor.sendToServer(EditorRequest.of(EditorRequest.Action.DELETE, target)));
    }

    private void showPrompt(Prompt kind, String value) {
        prompt = kind;
        promptText = value;
        suggestions = List.of();
    }

    private void confirmPrompt() {
        if (prompt == null || promptText.isEmpty()) {
            return;
        }
        String name = promptText;
        Prompt kind = prompt;
        prompt = null;
        switch (kind) {
            case NEW_PROGRAM -> afterDiscardCheck(() -> ClientPacketDistributor.sendToServer(EditorRequest.of(EditorRequest.Action.CREATE_PROGRAM, name)));
            case NEW_KERNEL -> afterDiscardCheck(() -> ClientPacketDistributor.sendToServer(EditorRequest.of(EditorRequest.Action.CREATE_KERNEL, name)));
            case RENAME -> {
                if (file != null && !name.equals(file)) {
                    ClientPacketDistributor.sendToServer(new EditorRequest(EditorRequest.Action.RENAME, file, name, "", 0));
                }
            }
        }
    }

    private void answer(boolean yes) {
        Confirm question = confirm;
        confirm = null;
        if (yes && question != null) {
            question.action().run();
        }
    }

    private void edited(boolean typed) {
        lastEditAt = Util.getMillis();
        kernelFile = KERNEL_HEADER.matcher(document.text()).find();
        if (typed) {
            updateSuggestions();
        } else {
            suggestions = List.of();
        }
        ensureCursorVisible();
    }

    private void updateSuggestions() {
        Pos cursor = document.cursor();
        String before = document.line(cursor.line()).substring(0, cursor.col());
        suggestions = Completions.suggest(before, kernelFile);
        suggestion = 0;
    }

    private void acceptSuggestion() {
        if (!suggestions.isEmpty()) {
            document.insert(suggestions.get(suggestion).insert());
            edited(false);
        }
    }

    // endregion

    // region Layout

    private int codeLeft() {
        return left + sidebar + 1;
    }

    private int codeTop() {
        return top + TOOLBAR + 1;
    }

    private int codeBottom() {
        return bottom - STATUS_HEIGHT;
    }

    private int gutterWidth() {
        return font.width(String.valueOf(Math.max(999, document.lineCount()))) + 2 * GUTTER_PADDING;
    }

    private int textLeft() {
        return codeLeft() + gutterWidth() + TEXT_PADDING;
    }

    private int visibleLines() {
        return Math.max(1, (codeBottom() - codeTop() - 4) / LINE_HEIGHT);
    }

    private int lineY(int line) {
        return codeTop() + 2 + (line - scrollLine) * LINE_HEIGHT;
    }

    private int columnX(int line, int col) {
        String text = document.line(line);
        return textLeft() - scrollX + font.width(text.substring(0, Math.min(col, text.length())));
    }

    private Pos positionAt(double x, double y) {
        int line = Math.clamp(scrollLine + (int) Math.floor((y - codeTop() - 2) / LINE_HEIGHT), 0, document.lineCount() - 1);
        String text = document.line(line);
        double target = x - textLeft() + scrollX;
        int col = 0;
        while (col < text.length()) {
            int before = font.width(text.substring(0, col));
            int after = font.width(text.substring(0, col + 1));
            if (target < (before + after) / 2.0) {
                break;
            }
            col++;
        }
        return new Pos(line, col);
    }

    private void ensureCursorVisible() {
        Pos cursor = document.cursor();
        if (cursor.line() < scrollLine) {
            scrollLine = cursor.line();
        } else if (cursor.line() >= scrollLine + visibleLines()) {
            scrollLine = cursor.line() - visibleLines() + 1;
        }
        int cursorX = font.width(document.line(cursor.line()).substring(0, cursor.col()));
        int viewWidth = Math.max(20, right - textLeft() - 8);
        if (cursorX < scrollX) {
            scrollX = Math.max(0, cursorX - 20);
        } else if (cursorX > scrollX + viewWidth) {
            scrollX = cursorX - viewWidth + 20;
        }
    }

    // endregion

    // region Drawing

    /**
     * Draws the editor into a window's content area and records its click regions.
     *
     * @param click the click being handled when a region's action runs
     */
    void draw(GuiGraphicsExtractor graphics, Font font, EdgeWindows.Rect area, boolean focused, int mouseX, int mouseY, Hits hits,
              Supplier<Click> click, long now) {
        this.font = font;
        left = area.x();
        top = area.y();
        right = area.right();
        bottom = area.bottom();
        sidebar = Math.clamp(area.width() / 4, 70, 110);
        boolean modal = prompt != null || confirm != null;

        toolbar(graphics, mouseX, mouseY, hits, modal);
        files(graphics, mouseX, mouseY, hits, modal);
        // The code area takes clicks that miss everything drawn on top of it.
        if (file != null && !modal) {
            hits.add(codeLeft() + gutterWidth(), codeTop(), right - codeLeft() - gutterWidth(), codeBottom() - codeTop(), () -> {
                Click at = click.get();
                Pos pos = positionAt(at.x(), at.y());
                if (at.doubleClick()) {
                    document.selectWord(pos);
                } else {
                    document.moveTo(pos, at.shift());
                }
                suggestions = List.of();
                dragging = true;
            });
        }
        code(graphics, focused && !modal, now);
        status(graphics);
        if (!suggestions.isEmpty() && !modal) {
            suggestions(graphics, hits);
        }
        if (prompt != null) {
            prompt(graphics, mouseX, mouseY, hits, now);
        } else if (confirm != null) {
            confirm(graphics, mouseX, mouseY, hits);
        }
    }

    private void toolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Hits hits, boolean modal) {
        graphics.fill(left, top, right, top + TOOLBAR, PANEL);
        graphics.fill(left, top + TOOLBAR, right, top + TOOLBAR + 1, RULE);
        int x = right - 4;
        x = button(graphics, tr("mhm2027.editor.delete"), x, top + 2, EdgeShell.PINK, file != null && !modal, mouseX, mouseY, hits, this::delete);
        x = button(graphics, tr("mhm2027.editor.rename"), x, top + 2, EdgeShell.TEXT, file != null && !dirty() && !modal, mouseX, mouseY, hits,
                () -> showPrompt(Prompt.RENAME, file == null ? "" : file));
        x = button(graphics, tr("mhm2027.editor.save") + " ^S", x, top + 2, EdgeShell.CYAN, dirty() && !modal, mouseX, mouseY, hits, this::save);
        String title = file == null ? "no file open" : file + ".lua" + (dirty() ? " *" : "");
        graphics.enableScissor(left, top, x - 4, top + TOOLBAR);
        text(graphics, title, left + 6, top + 4, file == null ? EdgeShell.GREY : dirty() ? EdgeShell.GOLD : EdgeShell.TEXT);
        if (kernelFile && file != null) {
            text(graphics, "kernel", left + 12 + font.width(title), top + 4, EdgeShell.VIOLET);
        }
        graphics.disableScissor();
    }

    private void files(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Hits hits, boolean modal) {
        int listTop = top + TOOLBAR + 1;
        graphics.fill(left, listTop, left + sidebar, bottom, PANEL);
        graphics.fill(left + sidebar, listTop, left + sidebar + 1, bottom, RULE);
        text(graphics, tr("mhm2027.editor.files"), left + 5, listTop + 4, EdgeShell.GREY);

        int y = listTop + 16;
        int buttonsY = bottom - 14;
        for (PanelSnapshot.ScriptRow script : PanelState.snapshot().scripts()) {
            if (script.preset() || y + LINE_HEIGHT > buttonsY - 2) {
                continue;
            }
            boolean current = script.id().equals(file);
            boolean hovered = inside(mouseX, mouseY, left + 2, y - 1, sidebar - 4, LINE_HEIGHT + 1);
            if (current || hovered) {
                graphics.fill(left + 2, y - 1, left + sidebar - 2, y + LINE_HEIGHT - 1, current ? 0xFF10263A : CHIP_HOVER);
            }
            String label = font.plainSubstrByWidth(script.name() + ".lua", sidebar - 20, false);
            text(graphics, label, left + 6, y, current ? EdgeShell.CYAN : EdgeShell.TEXT);
            if (script.kernel()) {
                text(graphics, "K", left + sidebar - 10, y, EdgeShell.VIOLET);
            }
            if (!modal) {
                hits.add(left + 2, y - 1, sidebar - 4, LINE_HEIGHT + 1, () -> open(script.id(), 0));
            }
            y += LINE_HEIGHT + 1;
        }

        int half = (sidebar - 10) / 2;
        chip(graphics, tr("mhm2027.editor.new_program"), left + 4, buttonsY, half, EdgeShell.CYAN, !modal, mouseX, mouseY, hits,
                () -> showPrompt(Prompt.NEW_PROGRAM, ""));
        chip(graphics, tr("mhm2027.editor.new_kernel"), left + 6 + half, buttonsY, half, EdgeShell.VIOLET, !modal, mouseX, mouseY, hits,
                () -> showPrompt(Prompt.NEW_KERNEL, ""));
    }

    private void code(GuiGraphicsExtractor graphics, boolean focused, long now) {
        int codeLeft = codeLeft();
        int codeTop = codeTop();
        int codeBottom = codeBottom();
        int gutter = gutterWidth();
        graphics.fill(codeLeft, codeTop, codeLeft + gutter, codeBottom, PANEL);

        if (file == null) {
            graphics.enableScissor(codeLeft + gutter, codeTop, right, codeBottom);
            text(graphics, tr("mhm2027.editor.nothing_open"), textLeft(), codeTop + 8, EdgeShell.GREY);
            text(graphics, "or type edit <file> in the terminal", textLeft(), codeTop + 20, EdgeShell.GREY);
            graphics.disableScissor();
            return;
        }
        if (highlightVersion != document.version()) {
            highlight = LuaHighlighter.highlight(document.lines());
            highlightVersion = document.version();
        }

        EditorCheck check = currentCheck();
        int markedLine = check == null ? -1 : check.line();
        int markColor = check != null && !check.ok() ? ERROR_LINE : WARN_LINE;
        Pos cursor = document.cursor();
        Pos selStart = document.selectionStart();
        Pos selEnd = document.selectionEnd();
        boolean blink = focused && (now / 500) % 2 == 0;

        int last = Math.min(document.lineCount(), scrollLine + visibleLines());
        for (int line = scrollLine; line < last; line++) {
            String number = String.valueOf(line + 1);
            text(graphics, number, codeLeft + gutter - GUTTER_PADDING - font.width(number), lineY(line),
                    line == markedLine ? EdgeShell.PINK : EdgeShell.GREY);
        }

        graphics.enableScissor(codeLeft + gutter, codeTop, right, codeBottom);
        for (int line = scrollLine; line < last; line++) {
            int y = lineY(line);
            String text = document.line(line);
            if (line == markedLine) {
                graphics.fill(codeLeft + gutter, y - 1, right, y + LINE_HEIGHT - 1, markColor);
            } else if (line == cursor.line() && !document.hasSelection()) {
                graphics.fill(codeLeft + gutter, y - 1, right, y + LINE_HEIGHT - 1, CURRENT_LINE);
            }
            if (document.hasSelection() && line >= selStart.line() && line <= selEnd.line()) {
                int from = line == selStart.line() ? columnX(line, selStart.col()) : textLeft() - scrollX;
                int to = line == selEnd.line() ? columnX(line, selEnd.col()) : columnX(line, text.length()) + 4;
                graphics.fill(from, y - 1, to, y + LINE_HEIGHT - 1, SELECTION);
            }
            for (Span span : highlight.get(line)) {
                String piece = text.substring(span.start(), span.end());
                if (!piece.isBlank()) {
                    text(graphics, piece, columnX(line, span.start()), y, colorOf(span.kind()));
                }
            }
            if (line == cursor.line() && blink) {
                int x = columnX(line, cursor.col());
                graphics.fill(x, y - 1, x + 1, y + LINE_HEIGHT - 1, EdgeShell.TEXT);
            }
        }
        graphics.disableScissor();
    }

    private void status(GuiGraphicsExtractor graphics) {
        int statusTop = codeBottom();
        int x = codeLeft() + 6;
        int width = right - x - 6;
        graphics.fill(codeLeft(), statusTop, right, bottom, PANEL);
        graphics.fill(codeLeft(), statusTop, right, statusTop + 1, RULE);
        int y = statusTop + 4;

        EditorCheck check = currentCheck();
        if (file == null) {
            text(graphics, clip(tr("mhm2027.editor.hint_open"), width), x, y, EdgeShell.GREY);
        } else if (check == null) {
            text(graphics, tr("mhm2027.editor.checking"), x, y, EdgeShell.GREY);
        } else if (!check.ok()) {
            String message = check.line() >= 0 ? tr("mhm2027.editor.error_at", check.line() + 1, check.message())
                    : tr("mhm2027.editor.error", check.message());
            text(graphics, clip(message, width), x, y, EdgeShell.PINK);
        } else {
            String summary = check.kind().equals("kernel")
                    ? tr("mhm2027.editor.ok_kernel", check.tops(), check.ramMb(), check.details().size())
                    : tr("mhm2027.editor.ok_program", check.kind(), check.tops(), check.ramMb(), check.level());
            if (check.overloadHeat() >= 0) {
                summary += " " + tr("mhm2027.editor.overload", check.overloadHeat());
            }
            text(graphics, clip(summary, width), x, y, EdgeShell.GREEN);
            if (!check.warnings().isEmpty()) {
                String more = check.warnings().size() > 1 ? " (+" + (check.warnings().size() - 1) + ")" : "";
                text(graphics, clip(check.warnings().getFirst() + more, width), x, y + 10, EdgeShell.GOLD);
            }
        }

        String position = "";
        if (file != null) {
            Pos cursor = document.cursor();
            position = tr("mhm2027.editor.position", cursor.line() + 1, cursor.col() + 1);
            text(graphics, position, right - 6 - font.width(position), y + 20, EdgeShell.GREY);
        }
        String message = PanelState.message().getString();
        text(graphics, clip(message, width - font.width(position) - 8), x, y + 20, PanelState.messageSuccess() ? EdgeShell.CYAN : EdgeShell.PINK);
    }

    private void suggestions(GuiGraphicsExtractor graphics, Hits hits) {
        Pos cursor = document.cursor();
        int x = Math.max(textLeft(), columnX(cursor.line(), cursor.col()));
        int y = lineY(cursor.line()) + LINE_HEIGHT;
        int count = Math.min(MAX_SUGGESTIONS, suggestions.size());
        int widest = 0;
        for (Completions.Suggestion each : suggestions.subList(0, count)) {
            widest = Math.max(widest, font.width(each.label()) + 30);
        }
        if (y + count * LINE_HEIGHT + 4 > codeBottom()) {
            y = lineY(cursor.line()) - count * LINE_HEIGHT - 4;
        }
        x = Math.max(codeLeft(), Math.min(x, right - widest - 2));
        graphics.fill(x, y, x + widest, y + count * LINE_HEIGHT + 4, POPUP);
        outline(graphics, x, y, widest, count * LINE_HEIGHT + 4, EdgeShell.CYAN);

        Minecraft minecraft = Minecraft.getInstance();
        int level = minecraft.player == null ? 0 : minecraft.player.getData(ModAttachments.GATEWAY).level();
        for (int i = 0; i < count; i++) {
            Completions.Suggestion each = suggestions.get(i);
            int rowY = y + 2 + i * LINE_HEIGHT;
            if (i == suggestion) {
                graphics.fill(x + 1, rowY - 1, x + widest - 1, rowY + LINE_HEIGHT - 1, CHIP_HOVER);
            }
            text(graphics, each.label(), x + 4, rowY, EdgeShell.TEXT);
            if (each.level() > 0) {
                String tag = "LV" + each.level();
                text(graphics, tag, x + widest - 4 - font.width(tag), rowY, each.level() > level ? EdgeShell.PINK : EdgeShell.GREY);
            }
            int index = i;
            hits.add(x, rowY - 1, widest, LINE_HEIGHT, () -> {
                suggestion = index;
                acceptSuggestion();
            });
        }
    }

    /** The name prompt: a small box with its own text field, over a shaded editor. */
    private void prompt(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Hits hits, long now) {
        int[] box = modalBox(graphics, hits, 190, 58);
        int x = box[0];
        int y = box[1];
        String key = switch (prompt) {
            case NEW_PROGRAM -> "mhm2027.editor.prompt_new_program";
            case NEW_KERNEL -> "mhm2027.editor.prompt_new_kernel";
            case RENAME -> "mhm2027.editor.prompt_rename";
        };
        text(graphics, clip(tr(key), box[2] - 16), x + 8, y + 7, EdgeShell.CYAN);
        graphics.fill(x + 8, y + 20, x + box[2] - 8, y + 33, CHIP);
        outline(graphics, x + 8, y + 20, box[2] - 16, 13, RULE);
        int textEnd = text(graphics, promptText, x + 11, y + 23, EdgeShell.TEXT);
        if ((now / 450) % 2 == 0) {
            graphics.fill(textEnd + 1, y + 22, textEnd + 2, y + 31, EdgeShell.TEXT);
        }
        int buttonX = x + box[2] - 8;
        buttonX = button(graphics, "cancel", buttonX, y + 40, EdgeShell.DIM, true, mouseX, mouseY, hits, () -> prompt = null);
        button(graphics, "ok", buttonX, y + 40, EdgeShell.CYAN, !promptText.isEmpty(), mouseX, mouseY, hits, this::confirmPrompt);
    }

    private void confirm(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Hits hits) {
        int[] box = modalBox(graphics, hits, 210, 58);
        int x = box[0];
        int y = box[1];
        text(graphics, confirm.title(), x + 8, y + 7, EdgeShell.PINK);
        text(graphics, clip(confirm.message(), box[2] - 16), x + 8, y + 21, EdgeShell.TEXT);
        int buttonX = x + box[2] - 8;
        buttonX = button(graphics, "cancel", buttonX, y + 40, EdgeShell.DIM, true, mouseX, mouseY, hits, () -> answer(false));
        button(graphics, confirm.yes(), buttonX, y + 40, EdgeShell.PINK, true, mouseX, mouseY, hits, () -> answer(true));
    }

    /** Shades the editor, swallows clicks on it, and draws a centred box. Returns its x, y and width. */
    private int[] modalBox(GuiGraphicsExtractor graphics, Hits hits, int preferredWidth, int height) {
        graphics.fill(left, top, right, bottom, SHADE);
        hits.add(left, top, right - left, bottom - top, () -> {
        });
        int width = Math.min(preferredWidth, right - left - 8);
        int x = (left + right - width) / 2;
        int y = Math.max(top + 4, (top + bottom - height) / 2);
        graphics.fill(x, y, x + width, y + height, POPUP);
        outline(graphics, x, y, width, height, EdgeShell.CYAN);
        return new int[]{x, y, width};
    }

    private @Nullable EditorCheck currentCheck() {
        EditorCheck check = EditorState.latestCheck();
        // A result for older text still helps while typing; one for another file doesn't.
        return check == null || lastCheckSent < 0 || check.requestId() < firstCheckForFile ? null : check;
    }

    private static int colorOf(LuaHighlighter.Kind kind) {
        return switch (kind) {
            case KEYWORD -> 0xFFC678DD;
            case STRING -> 0xFF98C379;
            case COMMENT -> 0xFF5C6370;
            case NUMBER -> 0xFFD19A66;
            case API -> 0xFF61AFEF;
            case EVENT -> 0xFFE5C07B;
            case HEADER -> 0xFF56B6C2;
            case BUILTIN -> 0xFF7FC8D8;
            case PLAIN -> 0xFFD8DEE6;
        };
    }

    /** A toolbar button laid out right to left. Returns where the next one ends. */
    private int button(GuiGraphicsExtractor graphics, String label, int right, int y, int color, boolean enabled, int mouseX, int mouseY,
                       Hits hits, Runnable action) {
        int width = font.width(label) + 8;
        chip(graphics, label, right - width, y, width, color, enabled, mouseX, mouseY, hits, action);
        return right - width - 4;
    }

    private void chip(GuiGraphicsExtractor graphics, String label, int x, int y, int width, int color, boolean enabled, int mouseX, int mouseY,
                      Hits hits, Runnable action) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, width, 11);
        graphics.fill(x, y, x + width, y + 11, hovered ? CHIP_HOVER : CHIP);
        graphics.fill(x, y, x + width, y + 1, RULE);
        String shown = font.plainSubstrByWidth(label, width - 4, false);
        text(graphics, shown, x + (width - font.width(shown)) / 2, y + 2, enabled ? color : EdgeShell.GREY);
        if (enabled) {
            hits.add(x, y, width, 11, action);
        }
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private int text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, false);
        return x + font.width(text);
    }

    private String clip(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("...")), false) + "...";
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // endregion

    // region Input

    /** A key while the editor's window is focused. True if the editor used it. */
    boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (confirm != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                answer(true);
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                answer(false);
            }
            return true;
        }
        if (prompt != null) {
            switch (key) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> confirmPrompt();
                case GLFW.GLFW_KEY_ESCAPE -> prompt = null;
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!promptText.isEmpty()) {
                        promptText = promptText.substring(0, promptText.length() - 1);
                    }
                }
                default -> {
                    if (event.isPaste()) {
                        for (char c : Minecraft.getInstance().keyboardHandler.getClipboard().toCharArray()) {
                            typeName(c);
                        }
                    }
                }
            }
            return true;
        }

        boolean shift = event.hasShiftDown();
        boolean ctrl = event.hasControlDown();
        if (!suggestions.isEmpty()) {
            int shown = Math.min(MAX_SUGGESTIONS, suggestions.size());
            switch (key) {
                case GLFW.GLFW_KEY_UP -> {
                    suggestion = (suggestion + shown - 1) % shown;
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    suggestion = (suggestion + 1) % shown;
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
                    acceptSuggestion();
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    suggestions = List.of();
                    return true;
                }
                default -> {
                }
            }
        }
        if (file == null || key == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (ctrl && key == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        if (ctrl && (key == GLFW.GLFW_KEY_Y || (shift && key == GLFW.GLFW_KEY_Z))) {
            document.redo();
            edited(false);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            document.undo();
            edited(false);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_SPACE) {
            updateSuggestions();
            return true;
        }
        if (event.isSelectAll()) {
            document.selectAll();
            return true;
        }
        if (event.isCopy()) {
            if (document.hasSelection()) {
                minecraft.keyboardHandler.setClipboard(document.selectedText());
            }
            return true;
        }
        if (event.isCut()) {
            if (document.hasSelection()) {
                minecraft.keyboardHandler.setClipboard(document.selectedText());
                document.deleteSelection();
                edited(false);
            }
            return true;
        }
        if (event.isPaste()) {
            document.insert(minecraft.keyboardHandler.getClipboard());
            edited(false);
            return true;
        }

        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> document.moveLeft(shift, ctrl);
            case GLFW.GLFW_KEY_RIGHT -> document.moveRight(shift, ctrl);
            case GLFW.GLFW_KEY_UP -> document.moveVertical(-1, shift);
            case GLFW.GLFW_KEY_DOWN -> document.moveVertical(1, shift);
            case GLFW.GLFW_KEY_PAGE_UP -> document.moveVertical(-visibleLines(), shift);
            case GLFW.GLFW_KEY_PAGE_DOWN -> document.moveVertical(visibleLines(), shift);
            case GLFW.GLFW_KEY_HOME -> {
                if (ctrl) {
                    document.documentStart(shift);
                } else {
                    document.home(shift);
                }
            }
            case GLFW.GLFW_KEY_END -> {
                if (ctrl) {
                    document.documentEnd(shift);
                } else {
                    document.end(shift);
                }
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                document.backspace(ctrl);
                edited(true);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> document.delete(ctrl);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> document.newline();
            case GLFW.GLFW_KEY_TAB -> {
                if (shift) {
                    document.unindent();
                } else {
                    document.indent();
                }
            }
            default -> {
                return false;
            }
        }
        edited(false);
        return true;
    }

    /** A typed character while the editor's window is focused. True if the editor used it. */
    boolean charTyped(CharacterEvent event) {
        if (event.codepoint() < 32 || event.codepoint() == 127) {
            return false;
        }
        if (confirm != null) {
            return true;
        }
        if (prompt != null) {
            for (char c : event.codepointAsString().toCharArray()) {
                typeName(c);
            }
            return true;
        }
        if (file == null) {
            return false;
        }
        document.insert(event.codepointAsString());
        edited(true);
        return true;
    }

    private void typeName(char c) {
        if (promptText.length() < MAX_NAME && NAME_CHAR.matcher(String.valueOf(c)).matches()) {
            promptText += c;
        }
    }

    boolean dragging() {
        return dragging;
    }

    /** Extends the selection while the mouse is held after a click in the code. */
    void drag(double x, double y) {
        if (dragging && file != null) {
            document.moveTo(positionAt(x, y), true);
            ensureCursorVisible();
        }
    }

    void release() {
        dragging = false;
    }

    /** The wheel over the editor: lines, or sideways with Shift. */
    void scroll(double x, double scrollX, double scrollY, boolean shift) {
        if (file == null || x < codeLeft() || prompt != null || confirm != null) {
            return;
        }
        if (scrollX != 0 || shift) {
            double amount = scrollX != 0 ? -scrollX : -scrollY;
            this.scrollX = Math.max(0, this.scrollX + (int) Math.signum(amount) * 24);
        } else {
            scrollLine = Math.clamp(scrollLine - (int) Math.signum(scrollY) * 3, 0, Math.max(0, document.lineCount() - visibleLines()));
        }
    }

    private static boolean inside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    // endregion
}
