package com.mhm.mhm2027.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mhm.mhm2027.Mhm2027Client;
import com.mhm.mhm2027.editor.CodeDocument;
import com.mhm.mhm2027.editor.CodeDocument.Pos;
import com.mhm.mhm2027.editor.Completions;
import com.mhm.mhm2027.editor.LuaHighlighter;
import com.mhm.mhm2027.editor.LuaHighlighter.Span;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.network.EditorCheck;
import com.mhm.mhm2027.network.EditorFile;
import com.mhm.mhm2027.network.EditorRequest;
import com.mhm.mhm2027.network.PanelRequest;
import com.mhm.mhm2027.network.PanelSnapshot;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The in-game code editor for the player's own scripts. Files live on the server, in the
 * player's storage; the editor loads and saves them through it, and asks it to check the code
 * a moment after typing stops: the same analysis an install runs, so the errors and costs
 * shown here are exactly what the terminal will say.
 *
 * Editing rules live in {@link CodeDocument}; this class only draws it and routes input.
 */
public class CodeEditorScreen extends Screen {
    private enum Prompt {
        NEW_PROGRAM, NEW_KERNEL, RENAME
    }

    private static final int TOP_BAR = 20;
    private static final int SIDEBAR = 118;
    private static final int STATUS_HEIGHT = 40;
    private static final int LINE_HEIGHT = 10;
    private static final int GUTTER_PADDING = 5;
    private static final int TEXT_PADDING = 4;
    private static final int MAX_SUGGESTIONS = 8;
    private static final long CHECK_DELAY_MILLIS = 500;
    private static final long CHECK_RETRY_MILLIS = 1500;
    private static final int MAX_LENGTH = 64 * 1024;
    private static final Pattern KERNEL_HEADER = Pattern.compile("(?m)^\\s*kernel\\s*\\{");

    private static final int COLOR_BACKGROUND = 0xFF0B0E13;
    private static final int COLOR_PANEL = 0xFF111720;
    private static final int COLOR_BORDER = 0xFF1F7FA8;
    private static final int COLOR_TEXT = 0xFFD8DEE6;
    private static final int COLOR_DIM = 0xFF6E7884;
    private static final int COLOR_ACCENT = 0xFF55FFFF;
    private static final int COLOR_OK = 0xFF55FF7F;
    private static final int COLOR_WARN = 0xFFFFC040;
    private static final int COLOR_ERROR = 0xFFFF5555;
    private static final int COLOR_SELECTION = 0x803A6EA5;
    private static final int COLOR_CURRENT_LINE = 0x14FFFFFF;
    private static final int COLOR_ERROR_LINE = 0x40FF3030;
    private static final int COLOR_WARN_LINE = 0x30FFB000;
    private static final int COLOR_BUTTON = 0xFF1C2733;
    private static final int COLOR_BUTTON_HOVER = 0xFF2B3D52;
    private static final int COLOR_BUTTON_DISABLED = 0xFF151B22;
    private static final int COLOR_POPUP = 0xF0182230;

    private final CodeDocument document = new CodeDocument(MAX_LENGTH);
    private final List<Hit> hits = new ArrayList<>();
    private final @Nullable String initialFile;
    private boolean opened;

    private @Nullable String file;
    private boolean kernelFile;
    private int savedVersion;
    private int scrollLine;
    private int scrollX;
    private boolean dragging;

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
    private @Nullable EditBox nameBox;

    public CodeEditorScreen(@Nullable String file) {
        super(Component.translatable("mhm2027.editor.title"));
        this.initialFile = file;
    }

    // region Lifecycle

    @Override
    protected void init() {
        if (prompt != null) {
            showPrompt(prompt, nameBox == null ? "" : nameBox.getValue());
        }
    }

    @Override
    public void added() {
        super.added();
        ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.OPEN));
        // Coming back from a confirm dialog keeps the open file; only the first time loads one.
        if (!opened) {
            opened = true;
            EditorState.reset();
            if (initialFile != null) {
                ClientPacketDistributor.sendToServer(EditorRequest.of(EditorRequest.Action.OPEN, initialFile));
            }
        }
    }

    @Override
    public void removed() {
        ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.CLOSE));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        EditorFile incoming = EditorState.takeFile();
        if (incoming != null) {
            load(incoming);
        }
        if (file == null) {
            return;
        }
        long now = Util.getMillis();
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
    }

    private void sendCheck(int version, long now) {
        ClientPacketDistributor.sendToServer(new EditorRequest(EditorRequest.Action.CHECK, file, "", document.text(), version));
        lastCheckSent = version;
        lastCheckSentAt = now;
    }

    private boolean dirty() {
        return file != null && document.version() != savedVersion;
    }

    /** Runs an action now, or after the player agrees to drop unsaved changes. */
    private void afterDiscardCheck(Runnable action) {
        if (!dirty()) {
            action.run();
            return;
        }
        minecraft.setScreen(new ConfirmScreen(discard -> {
            minecraft.setScreen(this);
            if (discard) {
                savedVersion = document.version();
                action.run();
            }
        }, Component.translatable("mhm2027.editor.unsaved_title"), Component.translatable("mhm2027.editor.unsaved_message", file + ".lua")));
    }

    @Override
    public void onClose() {
        afterDiscardCheck(() -> minecraft.setScreen(new EdgePanelScreen(Mhm2027Client.OPEN_PANEL)));
    }

    // endregion

    // region Actions

    private void save() {
        if (file != null) {
            ClientPacketDistributor.sendToServer(new EditorRequest(EditorRequest.Action.SAVE, file, "", document.text(), 0));
            savedVersion = document.version();
        }
    }

    private void open(String name) {
        if (!name.equals(file)) {
            afterDiscardCheck(() -> ClientPacketDistributor.sendToServer(EditorRequest.of(EditorRequest.Action.OPEN, name)));
        }
    }

    private void delete() {
        if (file == null) {
            return;
        }
        String target = file;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            minecraft.setScreen(this);
            if (yes) {
                ClientPacketDistributor.sendToServer(EditorRequest.of(EditorRequest.Action.DELETE, target));
            }
        }, Component.translatable("mhm2027.editor.delete_title"), Component.translatable("mhm2027.editor.delete_message", target + ".lua")));
    }

    private void showPrompt(Prompt kind, String value) {
        prompt = kind;
        if (nameBox != null) {
            removeWidget(nameBox);
        }
        nameBox = new EditBox(font, width / 2 - 80, height / 2 - 4, 160, 16, Component.translatable("mhm2027.editor.name"));
        nameBox.setMaxLength(32);
        nameBox.setFilter(text -> text.matches("[A-Za-z0-9_\\-]*"));
        nameBox.setValue(value);
        addRenderableWidget(nameBox);
        setFocused(nameBox);
    }

    private void closePrompt() {
        if (nameBox != null) {
            removeWidget(nameBox);
        }
        nameBox = null;
        prompt = null;
    }

    private void confirmPrompt() {
        if (prompt == null || nameBox == null || nameBox.getValue().isEmpty()) {
            return;
        }
        String name = nameBox.getValue();
        Prompt kind = prompt;
        closePrompt();
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
        return SIDEBAR + 1;
    }

    private int codeTop() {
        return TOP_BAR + 1;
    }

    private int codeBottom() {
        return height - STATUS_HEIGHT;
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
            int left = font.width(text.substring(0, col));
            int right = font.width(text.substring(0, col + 1));
            if (target < (left + right) / 2.0) {
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
        int viewWidth = width - textLeft() - 8;
        if (cursorX < scrollX) {
            scrollX = Math.max(0, cursorX - 20);
        } else if (cursorX > scrollX + viewWidth) {
            scrollX = cursorX - viewWidth + 20;
        }
    }

    // endregion

    // region Drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        hits.clear();
        graphics.fill(0, 0, width, height, COLOR_BACKGROUND);
        drawTopBar(graphics, mouseX, mouseY);
        drawSidebar(graphics, mouseX, mouseY);
        drawCode(graphics);
        drawStatus(graphics);
        if (!suggestions.isEmpty() && prompt == null) {
            drawSuggestions(graphics, mouseX, mouseY);
        }
        if (prompt != null) {
            drawPrompt(graphics, mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void drawTopBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, TOP_BAR, COLOR_PANEL);
        graphics.fill(0, TOP_BAR, width, TOP_BAR + 1, COLOR_BORDER);
        String title = file == null ? "" : "  " + file + ".lua" + (dirty() ? " *" : "");
        text(graphics, Component.translatable("mhm2027.editor.title").append(title), 6, 6, COLOR_ACCENT);

        int x = width - 6;
        x = button(graphics, x, Component.translatable("mhm2027.editor.back"), true, mouseX, mouseY, this::onClose);
        x = button(graphics, x, Component.translatable("mhm2027.editor.delete"), file != null && prompt == null, mouseX, mouseY, this::delete);
        x = button(graphics, x, Component.translatable("mhm2027.editor.rename"), file != null && !dirty() && prompt == null, mouseX, mouseY,
                () -> showPrompt(Prompt.RENAME, file));
        button(graphics, x, Component.translatable("mhm2027.editor.save"), dirty() && prompt == null, mouseX, mouseY, this::save);
    }

    private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, TOP_BAR + 1, SIDEBAR, height, COLOR_PANEL);
        graphics.fill(SIDEBAR, TOP_BAR + 1, SIDEBAR + 1, height, COLOR_BORDER);
        text(graphics, Component.translatable("mhm2027.editor.files"), 6, TOP_BAR + 6, COLOR_DIM);

        int y = TOP_BAR + 18;
        int listBottom = height - 30;
        for (PanelSnapshot.ScriptRow script : PanelState.snapshot().scripts()) {
            if (script.preset() || y + LINE_HEIGHT > listBottom) {
                continue;
            }
            boolean current = script.id().equals(file);
            boolean hovered = inside(mouseX, mouseY, 2, y - 1, SIDEBAR - 4, LINE_HEIGHT + 1);
            if (current || hovered) {
                graphics.fill(2, y - 1, SIDEBAR - 2, y + LINE_HEIGHT - 1, current ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
            }
            String label = font.plainSubstrByWidth(script.name() + ".lua", SIDEBAR - 22, false);
            text(graphics, Component.literal(label), 8, y, current ? COLOR_ACCENT : COLOR_TEXT);
            if (script.kernel()) {
                text(graphics, Component.literal("K"), SIDEBAR - 12, y, COLOR_WARN);
            }
            if (prompt == null) {
                hits.add(new Hit(2, y - 1, SIDEBAR - 4, LINE_HEIGHT + 1, () -> open(script.id())));
            }
            y += LINE_HEIGHT + 2;
        }

        int buttonsY = height - 26;
        smallButton(graphics, 6, buttonsY, 52, Component.translatable("mhm2027.editor.new_program"), prompt == null, mouseX, mouseY,
                () -> showPrompt(Prompt.NEW_PROGRAM, ""));
        smallButton(graphics, 62, buttonsY, 52, Component.translatable("mhm2027.editor.new_kernel"), prompt == null, mouseX, mouseY,
                () -> showPrompt(Prompt.NEW_KERNEL, ""));
    }

    private void drawCode(GuiGraphicsExtractor graphics) {
        int left = codeLeft();
        int top = codeTop();
        int bottom = codeBottom();
        int gutter = gutterWidth();
        graphics.fill(left, top, left + gutter, bottom, COLOR_PANEL);

        if (file == null) {
            text(graphics, Component.translatable("mhm2027.editor.nothing_open"), textLeft(), top + 8, COLOR_DIM);
            return;
        }
        if (highlightVersion != document.version()) {
            highlight = LuaHighlighter.highlight(document.lines());
            highlightVersion = document.version();
        }

        EditorCheck check = currentCheck();
        int markedLine = check == null ? -1 : check.line();
        int markColor = check != null && !check.ok() ? COLOR_ERROR_LINE : COLOR_WARN_LINE;
        Pos cursor = document.cursor();
        Pos selStart = document.selectionStart();
        Pos selEnd = document.selectionEnd();
        boolean blink = (Util.getMillis() / 500) % 2 == 0;

        int last = Math.min(document.lineCount(), scrollLine + visibleLines());
        for (int line = scrollLine; line < last; line++) {
            int y = lineY(line);
            Component number = Component.literal(String.valueOf(line + 1));
            text(graphics, number, left + gutter - GUTTER_PADDING - font.width(number), y, line == markedLine ? COLOR_ERROR : COLOR_DIM);
        }

        graphics.enableScissor(left + gutter, top, width, bottom);
        for (int line = scrollLine; line < last; line++) {
            int y = lineY(line);
            String text = document.line(line);
            if (line == markedLine) {
                graphics.fill(left + gutter, y - 1, width, y + LINE_HEIGHT - 1, markColor);
            } else if (line == cursor.line() && !document.hasSelection()) {
                graphics.fill(left + gutter, y - 1, width, y + LINE_HEIGHT - 1, COLOR_CURRENT_LINE);
            }
            if (document.hasSelection() && line >= selStart.line() && line <= selEnd.line()) {
                int from = line == selStart.line() ? columnX(line, selStart.col()) : textLeft() - scrollX;
                int to = line == selEnd.line() ? columnX(line, selEnd.col()) : columnX(line, text.length()) + 4;
                graphics.fill(from, y - 1, to, y + LINE_HEIGHT - 1, COLOR_SELECTION);
            }
            for (Span span : highlight.get(line)) {
                String piece = text.substring(span.start(), span.end());
                if (!piece.isBlank()) {
                    graphics.text(font, piece, columnX(line, span.start()), y, colorOf(span.kind()), false);
                }
            }
            if (line == cursor.line() && blink && prompt == null) {
                int x = columnX(line, cursor.col());
                graphics.fill(x, y - 1, x + 1, y + LINE_HEIGHT - 1, COLOR_TEXT);
            }
        }
        graphics.disableScissor();
    }

    private void drawStatus(GuiGraphicsExtractor graphics) {
        int top = height - STATUS_HEIGHT;
        graphics.fill(SIDEBAR + 1, top, width, height, COLOR_PANEL);
        graphics.fill(SIDEBAR + 1, top, width, top + 1, COLOR_BORDER);
        int x = SIDEBAR + 8;
        int y = top + 5;

        EditorCheck check = currentCheck();
        if (file == null) {
            text(graphics, Component.translatable("mhm2027.editor.hint_open"), x, y, COLOR_DIM);
        } else if (check == null) {
            text(graphics, Component.translatable("mhm2027.editor.checking"), x, y, COLOR_DIM);
        } else if (!check.ok()) {
            Component message = check.line() >= 0
                    ? Component.translatable("mhm2027.editor.error_at", check.line() + 1, check.message())
                    : Component.translatable("mhm2027.editor.error", check.message());
            text(graphics, clip(message, width - x - 8), x, y, COLOR_ERROR);
        } else {
            MutableComponent summary = check.kind().equals("kernel")
                    ? Component.translatable("mhm2027.editor.ok_kernel", check.tops(), check.ramMb(), check.details().size())
                    : Component.translatable("mhm2027.editor.ok_program", check.kind(), check.tops(), check.ramMb(), check.level());
            if (check.overloadHeat() >= 0) {
                summary.append(" ").append(Component.translatable("mhm2027.editor.overload", check.overloadHeat()));
            }
            text(graphics, clip(summary, width - x - 8), x, y, COLOR_OK);
            if (!check.warnings().isEmpty()) {
                String more = check.warnings().size() > 1 ? " (+" + (check.warnings().size() - 1) + ")" : "";
                text(graphics, clip(Component.literal(check.warnings().getFirst() + more), width - x - 8), x, y + 11, COLOR_WARN);
            }
        }

        Component message = PanelState.message();
        text(graphics, clip(message, width - x - 120), x, y + 22, PanelState.messageSuccess() ? COLOR_OK : COLOR_ERROR);
        if (file != null) {
            Pos cursor = document.cursor();
            Component position = Component.translatable("mhm2027.editor.position", cursor.line() + 1, cursor.col() + 1);
            text(graphics, position, width - 8 - font.width(position), y + 22, COLOR_DIM);
        }
    }

    private void drawSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
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
        x = Math.min(x, width - widest - 4);
        graphics.fill(x, y, x + widest, y + count * LINE_HEIGHT + 4, COLOR_POPUP);
        outline(graphics, x, y, widest, count * LINE_HEIGHT + 4, COLOR_BORDER);

        int level = minecraft.player == null ? 0 : minecraft.player.getData(ModAttachments.GATEWAY).level();
        for (int i = 0; i < count; i++) {
            Completions.Suggestion each = suggestions.get(i);
            int rowY = y + 2 + i * LINE_HEIGHT;
            if (i == suggestion) {
                graphics.fill(x + 1, rowY - 1, x + widest - 1, rowY + LINE_HEIGHT - 1, COLOR_BUTTON_HOVER);
            }
            text(graphics, Component.literal(each.label()), x + 4, rowY, COLOR_TEXT);
            if (each.level() > 0) {
                Component tag = Component.literal("LV" + each.level());
                text(graphics, tag, x + widest - 4 - font.width(tag), rowY, each.level() > level ? COLOR_ERROR : COLOR_DIM);
            }
            int index = i;
            hits.add(new Hit(x, rowY - 1, widest, LINE_HEIGHT, () -> {
                suggestion = index;
                acceptSuggestion();
            }));
        }
    }

    private void drawPrompt(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0x90000000);
        int boxWidth = 200;
        int left = width / 2 - boxWidth / 2;
        int top = height / 2 - 30;
        graphics.fill(left, top, left + boxWidth, top + 64, COLOR_PANEL);
        outline(graphics, left, top, boxWidth, 64, COLOR_BORDER);
        String key = switch (prompt) {
            case NEW_PROGRAM -> "mhm2027.editor.prompt_new_program";
            case NEW_KERNEL -> "mhm2027.editor.prompt_new_kernel";
            case RENAME -> "mhm2027.editor.prompt_rename";
        };
        text(graphics, Component.translatable(key), left + 8, top + 8, COLOR_ACCENT);
        int buttonsY = top + 46;
        smallButton(graphics, left + boxWidth - 112, buttonsY, 50, Component.translatable("gui.ok"),
                nameBox != null && !nameBox.getValue().isEmpty(), mouseX, mouseY, this::confirmPrompt);
        smallButton(graphics, left + boxWidth - 58, buttonsY, 50, Component.translatable("gui.cancel"), true, mouseX, mouseY, this::closePrompt);
    }

    private @Nullable EditorCheck currentCheck() {
        EditorCheck check = EditorState.latestCheck();
        // A result for older text still helps while typing; one for another file doesn't.
        return check == null || lastCheckSent < 0 || check.requestId() < firstCheckForFile ? null : check;
    }

    private int colorOf(LuaHighlighter.Kind kind) {
        return switch (kind) {
            case KEYWORD -> 0xFFC678DD;
            case STRING -> 0xFF98C379;
            case COMMENT -> 0xFF5C6370;
            case NUMBER -> 0xFFD19A66;
            case API -> 0xFF61AFEF;
            case EVENT -> 0xFFE5C07B;
            case HEADER -> 0xFF56B6C2;
            case BUILTIN -> 0xFF7FC8D8;
            case PLAIN -> COLOR_TEXT;
        };
    }

    /** A top-bar button, laid out right to left. Returns the x where the next one ends. */
    private int button(GuiGraphicsExtractor graphics, int right, Component label, boolean enabled, int mouseX, int mouseY, Runnable action) {
        int width = font.width(label) + 12;
        smallButton(graphics, right - width, 4, width, label, enabled, mouseX, mouseY, action);
        return right - width - 4;
    }

    private void smallButton(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, boolean enabled, int mouseX, int mouseY, Runnable action) {
        int height = 12;
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height, !enabled ? COLOR_BUTTON_DISABLED : hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        outline(graphics, x, y, width, height, enabled ? COLOR_BORDER : COLOR_BUTTON);
        text(graphics, label, x + (width - font.width(label)) / 2, y + 2, enabled ? COLOR_TEXT : COLOR_DIM);
        if (enabled) {
            hits.add(new Hit(x, y, width, height, action));
        }
    }

    private void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void text(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, false);
    }

    private Component clip(Component text, int width) {
        String plain = text.getString();
        if (font.width(plain) <= width) {
            return text;
        }
        return Component.literal(font.plainSubstrByWidth(plain, Math.max(0, width - font.width("...")), false) + "...");
    }

    // endregion

    // region Input

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (prompt != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                confirmPrompt();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closePrompt();
                return true;
            }
            return super.keyPressed(event);
        }

        boolean shift = event.hasShiftDown();
        boolean ctrl = event.hasControlDown();
        if (!suggestions.isEmpty()) {
            switch (key) {
                case GLFW.GLFW_KEY_UP -> {
                    suggestion = (suggestion + Math.min(MAX_SUGGESTIONS, suggestions.size()) - 1) % Math.min(MAX_SUGGESTIONS, suggestions.size());
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    suggestion = (suggestion + 1) % Math.min(MAX_SUGGESTIONS, suggestions.size());
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
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (file == null) {
            return super.keyPressed(event);
        }

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
                return super.keyPressed(event);
            }
        }
        edited(false);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (prompt != null) {
            return super.charTyped(event);
        }
        if (file == null || event.codepoint() < 32 || event.codepoint() == 127) {
            return false;
        }
        document.insert(event.codepointAsString());
        edited(true);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (Hit hit : List.copyOf(hits)) {
                if (inside(event.x(), event.y(), hit.x(), hit.y(), hit.width(), hit.height())) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    hit.action().run();
                    return true;
                }
            }
        }
        if (prompt != null) {
            return super.mouseClicked(event, doubleClick);
        }
        if (file != null && event.button() == 0 && event.x() >= codeLeft() + gutterWidth() && event.y() >= codeTop() && event.y() < codeBottom()) {
            Pos at = positionAt(event.x(), event.y());
            if (doubleClick) {
                document.selectWord(at);
            } else {
                document.moveTo(at, event.hasShiftDown());
            }
            suggestions = List.of();
            dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging && file != null) {
            document.moveTo(positionAt(event.x(), event.y()), true);
            ensureCursorVisible();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (file == null || x < codeLeft()) {
            return false;
        }
        if (scrollX != 0 || minecraft.hasShiftDown()) {
            double amount = scrollX != 0 ? -scrollX : -scrollY;
            this.scrollX = Math.max(0, this.scrollX + (int) Math.signum(amount) * 24);
        } else {
            scrollLine = Math.clamp(scrollLine - (int) Math.signum(scrollY) * 3, 0, Math.max(0, document.lineCount() - visibleLines()));
        }
        return true;
    }

    private record Hit(int x, int y, int width, int height, Runnable action) {
    }

    private static boolean inside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    // endregion
}
