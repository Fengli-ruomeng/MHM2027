package com.mhm.mhm2027.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.GatewayTier;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.hud.HudState;
import com.mhm.mhm2027.network.EditorFile;
import com.mhm.mhm2027.network.EditorRequest;
import com.mhm.mhm2027.network.PanelRequest;
import com.mhm.mhm2027.network.PanelSnapshot;
import com.mhm.mhm2027.network.ScanResult;
import com.mhm.mhm2027.terminal.Addresses;
import com.mhm.mhm2027.terminal.ProgramOutputs;
import com.mhm.mhm2027.terminal.ScriptStore;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Edge, the gateway's operating system: a top bar, windows and a taskbar. The terminal takes
 * commands; every program gets a window with its own output, kernel.log one for the kernel,
 * top a live process table, and the code editor one of its own. Windows move by their title
 * bar, resize from the corner and minimise to the taskbar; the focused window gets the
 * keyboard. The shell, the editor and the windows outlive the screen for the session, so
 * reopening Edge picks up where you left off, unsaved code included.
 *
 * Drawn immediate-mode, like the rest of the HUD: clicks are matched against the regions
 * drawn in the last frame, topmost first.
 */
public class EdgeDesktopScreen extends Screen {
    private static final int BACKGROUND = 0xF0090D14;
    private static final int BAR = 0xFF0B0F17;
    private static final int RULE = 0xFF2C3550;
    private static final int TITLE_BAR = 0xFF10263A;
    private static final int TITLE_BAR_DIM = 0xFF121826;
    private static final int WINDOW_FILL = 0xF20B0F17;
    private static final int UNFOCUSED = 0xFF3A4254;
    private static final int HOVER = 0x26FFFFFF;
    private static final int LINE_HEIGHT = 10;
    /** Output types itself in at this many milliseconds per character. */
    private static final long TYPE_MILLIS = 3;
    /** How long a program started from Edge has to show up before its window stops waiting. */
    private static final long PENDING_MILLIS = 5000;
    private static final List<String> CHIPS = List.of("help", "ls", "ps", "status", "socket ls", "flaws");

    /** The shell and the windows outlive the screen for the session. */
    private static EdgeShell shell = new EdgeShell();
    private static EdgeWindows windows = new EdgeWindows();
    private static EdgeEditor editor = new EdgeEditor();
    /** Programs just started from Edge, by id: each window opens once the server shows the new run. */
    private static final Map<String, Pending> pending = new HashMap<>();
    /** How far each program window has typed its output, by program name. */
    private static final Map<String, Typing> typing = new HashMap<>();

    private final List<Hit> hits = new ArrayList<>();
    private List<Pill> pills = List.of();
    private String input = "";
    private int cursor;
    private int lastMessage;
    private int firstSnapshot;
    private @Nullable Drag drag;
    private double clickX;
    private double clickY;
    private boolean doubleClick;
    private boolean clickShift;

    public EdgeDesktopScreen() {
        super(Component.translatable("mhm2027.desktop.title"));
    }

    /** A new world, a new session. */
    static void reset() {
        shell = new EdgeShell();
        windows = new EdgeWindows();
        editor = new EdgeEditor();
        pending.clear();
        typing.clear();
    }

    @Override
    public void added() {
        super.added();
        ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.OPEN));
        lastMessage = PanelState.messageSerial();
        firstSnapshot = PanelState.snapshotSerial();
        if (shell.lines().isEmpty()) {
            shell.welcome(data(), Util.getMillis());
        }
    }

    @Override
    protected void init() {
        windows.setArea(desktop());
        if (windows.get(EdgeWindows.TERMINAL).isEmpty()) {
            windows.open(EdgeWindows.TERMINAL, Util.getMillis());
        }
    }

    @Override
    public void removed() {
        ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.CLOSE));
        super.removed();
    }

    /** Programs keep running while Edge is open, so the game must not pause. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft.player == null || minecraft.player.getData(ModAttachments.GATEWAY).tier().isNull()) {
            // Gone Null with Edge open (only an admin can do that): Edge goes with the gateway.
            onClose();
            return;
        }
        long now = Util.getMillis();
        if (PanelState.messageSerial() != lastMessage) {
            lastMessage = PanelState.messageSerial();
            shell.message(PanelState.lastMessage().getString(), PanelState.messageSuccess(), now);
            scrollTerminalToEnd();
        }
        for (EdgeShell.Request request : shell.drain()) {
            handle(request, now);
        }
        for (ScanResult result : PanelState.takeScans()) {
            shell.scanned(result.radius(), result.rows().stream().map(row -> new EdgeShell.Gateway(row.address(), row.name(), row.player(),
                    row.level(), row.distance())).toList(), now);
            scrollTerminalToEnd();
        }
        for (EditorFile file : EditorState.takePrints()) {
            shell.printFile(file.name(), file.content(), now);
            scrollTerminalToEnd();
        }
        editor.tick(now);
        openStarted(now);
        closeVanished(now);
    }

    private void handle(EdgeShell.Request request, long now) {
        switch (request.action()) {
            case "EDIT" -> openEditor(request.argument(), 0);
            case "EDIT_NEW" -> {
                openEditor("", 0);
                editor.create(request.argument());
            }
            case "CAT" -> ClientPacketDistributor.sendToServer(EditorRequest.of(EditorRequest.Action.READ, request.argument()));
            case "EXIT" -> onClose();
            case "WINDOW" -> windows.open(switch (request.argument()) {
                case EdgeWindows.KERNEL, EdgeWindows.TOP -> request.argument();
                default -> EdgeWindows.program(request.argument());
            }, now);
            case "START" -> start(request.argument(), now);
            case "FOLDER" -> {
                Path folder = localFolder();
                if (folder == null) {
                    shell.message("folder: your storage is on the server; open it in singleplayer", false, now);
                } else {
                    Util.getPlatform().openPath(folder);
                }
            }
            default -> ClientPacketDistributor.sendToServer(new PanelRequest(PanelRequest.Action.valueOf(request.action()), request.argument()));
        }
    }

    /** Opens the editor's window, and a file in it unless {@code file} is empty. */
    private void openEditor(String file, int line) {
        windows.open(EdgeWindows.EDITOR, Util.getMillis());
        if (!file.isEmpty()) {
            editor.open(file, line);
        }
    }

    /** Starts a program; its window opens when the server shows the new run. */
    private void start(String id, long now) {
        int seen = outputs().stream().filter(output -> output.id().equals(id)).mapToInt(ProgramOutputs.Output::serial).max().orElse(0);
        pending.put(id, new Pending(seen, now));
        ClientPacketDistributor.sendToServer(new PanelRequest(PanelRequest.Action.START, id));
    }

    private void openStarted(long now) {
        pending.entrySet().removeIf(entry -> {
            for (ProgramOutputs.Output output : outputs()) {
                if (output.id().equals(entry.getKey()) && output.serial() > entry.getValue().serial()) {
                    windows.open(EdgeWindows.program(output.name()), now);
                    return true;
                }
            }
            return now - entry.getValue().at() > PENDING_MILLIS;
        });
    }

    /** A program window whose output the server no longer keeps closes itself. */
    private void closeVanished(long now) {
        if (PanelState.snapshotSerial() == firstSnapshot) {
            // Nothing from the server since Edge opened: what it shows may be stale.
            return;
        }
        Set<String> names = outputs().stream().map(ProgramOutputs.Output::name).collect(Collectors.toSet());
        for (EdgeWindows.Window window : windows.windows()) {
            if (EdgeWindows.isProgram(window.id()) && !window.closing() && !names.contains(EdgeWindows.programName(window.id()))) {
                windows.close(window.id(), now);
            }
        }
    }

    // region Drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        hits.clear();
        long now = Util.getMillis();
        graphics.fill(0, 0, width, height, BACKGROUND);
        windows.setArea(desktop());
        windows.prune(now);
        EdgeShell.Data data = data();
        topBar(graphics, data);
        pills = pills(data);
        emptyHint(graphics);
        for (EdgeWindows.Window window : windows.windows()) {
            window(graphics, window, data, mouseX, mouseY, now);
        }
        taskbar(graphics, mouseX, mouseY, now);
    }

    /** Between the top bar and the taskbar. */
    private EdgeWindows.Rect desktop() {
        return new EdgeWindows.Rect(6, 20, width - 12, height - 42);
    }

    private void topBar(GuiGraphicsExtractor graphics, EdgeShell.Data data) {
        graphics.fill(0, 0, width, 14, BAR);
        graphics.fill(0, 14, width, 15, RULE);
        int x = 6;
        x = text(graphics, "EDGE", x, 3, EdgeShell.TEXT) + 10;
        x = text(graphics, data.user() + "@" + data.address(), x, 3, EdgeShell.CYAN) + 10;
        text(graphics, data.status().level() + " · " + data.kernel().name(), x, 3, EdgeShell.DIM);
        // Right side: the four resources as small cell meters, then the clock.
        String clock = clock();
        int right = width - 6 - font.width(clock);
        text(graphics, clock, right, 3, EdgeShell.DIM);
        EdgeShell.Status status = data.status();
        right = meter(graphics, "HEAT", status.heat() / 100.0, status.heat() >= 70 ? EdgeShell.ORANGE : EdgeShell.TEXT, right - 12);
        right = meter(graphics, "TOPS", 1 - (double) status.topsFree() / Math.max(1, status.tops()), EdgeShell.TEXT, right - 10);
        right = meter(graphics, "RAM", status.ramUsed() / Math.max(1, status.ram()), status.ramUsed() > status.ram() ? EdgeShell.MAGENTA : EdgeShell.TEXT, right - 10);
        meter(graphics, "BUF", status.buffer() / Math.max(1, status.bufferMax()), EdgeShell.CYAN, right - 10);
    }

    /** A label and eight cells, drawn so they end at {@code right}. Returns where they start. */
    private int meter(GuiGraphicsExtractor graphics, String label, double share, int color, int right) {
        int cells = 8;
        int cellsX = right - cells * 4;
        int lit = (int) Math.round(Math.clamp(share, 0, 1) * cells);
        for (int i = 0; i < cells; i++) {
            graphics.fill(cellsX + i * 4, 4, cellsX + i * 4 + 3, 10, i < lit ? color : 0x40F2F6FF);
        }
        int labelX = cellsX - 4 - font.width(label);
        text(graphics, label, labelX, 3, EdgeShell.DIM);
        return labelX;
    }

    /** With only the terminal open, a word on where the other windows go. */
    private void emptyHint(GuiGraphicsExtractor graphics) {
        if (windows.windows().stream().anyMatch(window -> !window.id().equals(EdgeWindows.TERMINAL) && window.shown())) {
            return;
        }
        EdgeWindows.Rect area = windows.area();
        int left = windows.get(EdgeWindows.TERMINAL).filter(EdgeWindows.Window::shown).map(window -> windows.bounds(window).right()).orElse(area.x());
        if (area.right() - left < 120) {
            return;
        }
        int middle = (left + area.right()) / 2;
        int y = area.y() + area.height() / 2 - 10;
        centered(graphics, "programs you run open here", middle, y, EdgeShell.GREY);
        centered(graphics, "log opens kernel.log, top the process table", middle, y + 12, EdgeShell.GREY);
        centered(graphics, "edit opens the code editor", middle, y + 24, EdgeShell.GREY);
    }

    private void window(GuiGraphicsExtractor graphics, EdgeWindows.Window window, EdgeShell.Data data, int mouseX, int mouseY, long now) {
        EdgeWindows.Rect bounds = windows.bounds(window);
        int accent = accent(window.id(), data);
        float progress = EdgeWindows.progress(window, now);
        if (!window.shown()) {
            if (progress < 1) {
                fold(graphics, bounds, pillRect(window.id()), progress, accent);
            }
            return;
        }
        if (progress < 1) {
            grow(graphics, pillRect(window.id()), bounds, progress, accent, now);
            return;
        }
        boolean focused = windows.focused().map(top -> top == window).orElse(false);
        // The window itself catches any click that misses what's on it, so nothing beneath gets it.
        hits.add(new Hit(bounds.x(), bounds.y(), bounds.width(), bounds.height(), () -> windows.focus(window.id())));
        LensHud.panel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), focused ? accent : UNFOCUSED, WINDOW_FILL);
        if (window.id().equals(EdgeWindows.TERMINAL)) {
            titleBar(graphics, window, bounds, focused, accent, "terminal", "~", "", 0, mouseX, mouseY);
            terminal(graphics, window, bounds, data, mouseX, mouseY, now);
        } else if (window.id().equals(EdgeWindows.KERNEL)) {
            titleBar(graphics, window, bounds, focused, accent, EdgeWindows.KERNEL, data.kernel().name(), "live", EdgeShell.VIOLET, mouseX, mouseY);
            kernelLog(graphics, window, bounds);
        } else if (window.id().equals(EdgeWindows.TOP)) {
            titleBar(graphics, window, bounds, focused, accent, EdgeWindows.TOP, data.programs().size() + " running", "live", EdgeShell.CYAN,
                    mouseX, mouseY);
            top(graphics, window, bounds, data, mouseX, mouseY);
        } else if (window.id().equals(EdgeWindows.EDITOR)) {
            String file = editor.file();
            titleBar(graphics, window, bounds, focused, accent, "editor", file == null ? "" : file + ".lua",
                    editor.dirty() ? "unsaved" : file == null ? "" : "saved", editor.dirty() ? EdgeShell.GOLD : EdgeShell.GREY, mouseX, mouseY);
            EdgeWindows.Rect content = new EdgeWindows.Rect(bounds.x() + 1, bounds.y() + EdgeWindows.TITLE_HEIGHT + 1, bounds.width() - 2,
                    bounds.height() - EdgeWindows.TITLE_HEIGHT - 2);
            editor.draw(graphics, font, content, focused, mouseX, mouseY,
                    (x, y, width, height, action) -> hits.add(new Hit(x, y, width, height, action)),
                    () -> new EdgeEditor.Click(clickX, clickY, doubleClick, clickShift), now);
        } else {
            program(graphics, window, bounds, focused, accent, data, mouseX, mouseY, now);
        }
        // Resize from the corner.
        int gripX = bounds.right() - 7;
        int gripY = bounds.bottom() - 7;
        graphics.fill(gripX + 4, gripY, gripX + 5, gripY + 5, UNFOCUSED);
        graphics.fill(gripX, gripY + 4, gripX + 5, gripY + 5, UNFOCUSED);
        hits.add(new Hit(gripX - 2, gripY - 2, 9, 9, () -> startDrag(window, false)));
    }

    /** Opening: the window grows out of its taskbar pill, with a short glitch. */
    private void grow(GuiGraphicsExtractor graphics, EdgeWindows.Rect from, EdgeWindows.Rect to, float t, int accent, long now) {
        float eased = 1 - (1 - t) * (1 - t);
        EdgeWindows.Rect rect = from.toward(to, eased);
        LensHud.panel(graphics, rect.x(), rect.y(), Math.max(10, rect.width()), Math.max(8, rect.height()), accent, WINDOW_FILL);
        Random random = new Random(now / 40);
        for (int i = 0; i < 3; i++) {
            int barY = rect.y() + random.nextInt(Math.max(1, rect.height()));
            int shift = random.nextInt(9) - 4;
            graphics.fill(rect.x() + shift, barY, rect.right() + shift, barY + 1, LensHud.fade(accent, 0.55f));
        }
    }

    /** Closing or minimising: it folds to its title bar, then slides into the taskbar and fades. */
    private void fold(GuiGraphicsExtractor graphics, EdgeWindows.Rect from, EdgeWindows.Rect to, float t, int accent) {
        EdgeWindows.Rect title = new EdgeWindows.Rect(from.x(), from.y(), from.width(), EdgeWindows.TITLE_HEIGHT);
        EdgeWindows.Rect rect = t < 0.5f ? from.toward(title, t * 2) : title.toward(to, (t - 0.5f) * 2);
        float alpha = t < 0.5f ? 1 : 1 - (t - 0.5f) * 2;
        LensHud.panel(graphics, rect.x(), rect.y(), Math.max(10, rect.width()), Math.max(8, rect.height()),
                LensHud.fade(accent, alpha), LensHud.fade(WINDOW_FILL, alpha));
    }

    private void titleBar(GuiGraphicsExtractor graphics, EdgeWindows.Window window, EdgeWindows.Rect bounds, boolean focused, int accent,
                          String title, String sub, String state, int stateColor, int mouseX, int mouseY) {
        graphics.fill(bounds.x() + 1, bounds.y() + 4, bounds.right() - 1, bounds.y() + 14, focused ? TITLE_BAR : TITLE_BAR_DIM);
        // Drag by the title bar; a double click maximises.
        hits.add(new Hit(bounds.x(), bounds.y(), bounds.width(), EdgeWindows.TITLE_HEIGHT + 1, () -> titleClicked(window)));
        int buttonsX = bounds.right() - 38;
        graphics.enableScissor(bounds.x() + 2, bounds.y() + 4, buttonsX - 4, bounds.y() + 14);
        int x = text(graphics, "◆ " + title, bounds.x() + 8, bounds.y() + 5, focused ? accent : EdgeShell.DIM);
        int subEnd = text(graphics, sub, x + 6, bounds.y() + 5, EdgeShell.GREY);
        int stateX = buttonsX - 6 - font.width(state);
        if (!state.isEmpty() && stateX > subEnd + 8) {
            text(graphics, state, stateX, bounds.y() + 5, stateColor);
        }
        graphics.disableScissor();
        button(graphics, "_", buttonsX, bounds.y() + 4, EdgeShell.DIM, mouseX, mouseY, () -> windows.minimise(window.id(), Util.getMillis()));
        button(graphics, "□", buttonsX + 12, bounds.y() + 4, EdgeShell.DIM, mouseX, mouseY, () -> windows.toggleMaximise(window.id()));
        button(graphics, "×", buttonsX + 24, bounds.y() + 4, EdgeShell.PINK, mouseX, mouseY, () -> close(window));
    }

    private void terminal(GuiGraphicsExtractor graphics, EdgeWindows.Window window, EdgeWindows.Rect bounds, EdgeShell.Data data,
                          int mouseX, int mouseY, long now) {
        int left = bounds.x() + 8;
        int right = bounds.right() - 8;
        int chipsY = bounds.bottom() - 15;
        int promptY = chipsY - 13;
        int top = bounds.y() + 19;
        int visible = Math.max(1, (promptY - 3 - top) / LINE_HEIGHT);
        shell.setColumns((right - left) / Math.max(1, font.width("0")));

        // Output, newest at the bottom; the wheel scrolls back.
        List<EdgeShell.Line> lines = shell.lines().stream().filter(line -> line.at() <= now).toList();
        window.setScroll(Math.min(window.scroll(), Math.max(0, lines.size() - visible)));
        int first = Math.max(0, lines.size() - visible - window.scroll());
        graphics.enableScissor(left, top, right, promptY - 2);
        int y = top;
        for (EdgeShell.Line line : lines.subList(first, Math.min(lines.size(), first + visible))) {
            drawLine(graphics, line, left, y, right, mouseX, mouseY, now);
            y += LINE_HEIGHT;
        }
        graphics.disableScissor();
        if (window.scroll() > 0) {
            String more = "▼ " + window.scroll() + " newer";
            text(graphics, more, right - font.width(more), promptY - 12, EdgeShell.GREY);
        }

        // The prompt, with a blinking block cursor.
        graphics.fill(left - 2, promptY - 2, right + 2, promptY - 1, RULE);
        String prompt = EdgeShell.prompt(data);
        int inputX = text(graphics, prompt, left, promptY + 1, EdgeShell.CYAN);
        graphics.enableScissor(inputX, promptY, right, promptY + 10);
        text(graphics, input, inputX, promptY + 1, EdgeShell.TEXT);
        if ((now / 450) % 2 == 0) {
            int cursorX = inputX + font.width(input.substring(0, cursor));
            graphics.fill(cursorX, promptY, cursorX + 5, promptY + 9, 0xB0F2F6FF);
        }
        graphics.disableScissor();

        // Quick commands, one click each, as many as fit.
        int chipX = left;
        for (String chip : CHIPS) {
            if (chipX + font.width(chip) + 8 > right) {
                break;
            }
            chipX = chip(graphics, chip, chipX, chipsY, EdgeShell.DIM, mouseX, mouseY, () -> runCommand(chip));
        }
    }

    /** One output line, typing itself in; clickable runs get a hover box. */
    private void drawLine(GuiGraphicsExtractor graphics, EdgeShell.Line line, int x, int y, int right, int mouseX, int mouseY, long now) {
        int budget = (int) ((now - line.at()) / TYPE_MILLIS);
        for (EdgeShell.Seg seg : line.segs()) {
            if (budget <= 0 || x >= right) {
                return;
            }
            String text = seg.text().length() > budget ? seg.text().substring(0, budget) : seg.text();
            budget -= seg.text().length();
            int segWidth = font.width(text);
            if (seg.clickable()) {
                boolean hovered = inside(mouseX, mouseY, x - 1, y - 1, segWidth + 2, LINE_HEIGHT);
                if (hovered) {
                    graphics.fill(x - 1, y - 1, x + segWidth + 1, y + LINE_HEIGHT - 1, HOVER);
                }
                graphics.fill(x, y + 8, x + segWidth, y + 9, (seg.color() & 0x00FFFFFF) | 0x80000000);
                hits.add(new Hit(x - 1, y - 1, segWidth + 2, LINE_HEIGHT, () -> click(seg)));
            }
            text(graphics, text, x, y, seg.color());
            x += segWidth;
        }
    }

    /**
     * A program's window: what it printed, newest at the bottom. Once it ends, a footer says how,
     * with restart and close (and, for a crash in your own script, the line to fix).
     */
    private void program(GuiGraphicsExtractor graphics, EdgeWindows.Window window, EdgeWindows.Rect bounds, boolean focused, int accent,
                         EdgeShell.Data data, int mouseX, int mouseY, long now) {
        String name = EdgeWindows.programName(window.id());
        ProgramOutputs.Output output = output(name);
        PanelSnapshot.ProgramRow running = running(name);
        String socket = output == null ? "" : socketLabel(data, output.id());
        String sub = !socket.isEmpty() ? socket : running != null ? running.type() : "";
        boolean ended = running == null && output != null && !output.ended().isEmpty();
        String state = running != null ? running.state() : ended ? output.ended() : "";
        int stateColor = running != null ? stateColor(running.state()) : output != null && output.ended().equals("crashed") ? EdgeShell.PINK : EdgeShell.GREY;
        titleBar(graphics, window, bounds, focused, accent, name, sub, state, stateColor, mouseX, mouseY);

        int left = bounds.x() + 8;
        int right = bounds.right() - 8;
        boolean overload = socket.startsWith("O") && running != null;
        boolean waiting = running != null && running.progress().active();
        int footer = ended ? 24 : (overload ? 13 : 0) + (waiting ? 13 : 0);
        int top = bounds.y() + 18;
        int bottom = bounds.bottom() - 5 - footer;
        int visible = Math.max(1, (bottom - top) / LINE_HEIGHT);

        List<ProgramOutputs.Line> lines = output == null ? List.of() : output.lines();
        Typing typed = typing.computeIfAbsent(name, key -> new Typing());
        if (output != null) {
            typed.update(output, now);
        }
        window.setScroll(Math.min(window.scroll(), Math.max(0, lines.size() - visible)));
        int first = Math.max(0, lines.size() - visible - window.scroll());
        int base = output == null ? 0 : output.total() - lines.size();
        long budget = (now - typed.start) / TYPE_MILLIS;
        graphics.enableScissor(left, top, right, bottom);
        if (lines.isEmpty()) {
            text(graphics, output == null ? "waiting for output..." : "nothing printed yet", left, top, EdgeShell.GREY);
        }
        int y = top;
        for (int i = first; i < Math.min(lines.size(), first + visible); i++) {
            ProgramOutputs.Line line = lines.get(i);
            String text = line.text();
            if (base + i >= typed.from) {
                // New lines type in one after another.
                long before = 0;
                for (int j = Math.max(0, typed.from - base); j < i; j++) {
                    before += lines.get(j).text().length();
                }
                long typedSoFar = budget - before;
                if (typedSoFar <= 0) {
                    break;
                }
                text = text.substring(0, (int) Math.min(typedSoFar, text.length()));
            }
            int x = text(graphics, time(line.minute()) + " ", left, y, EdgeShell.GREY);
            text(graphics, text, x, y, lineColor(line.text()));
            y += LINE_HEIGHT;
        }
        graphics.disableScissor();

        if (ended) {
            footer(graphics, window, output, left, right, bounds.bottom() - 5 - footer + 2, mouseX, mouseY);
        } else {
            int barY = bounds.bottom() - 5 - footer + 3;
            if (waiting) {
                progressBar(graphics, running, left, right, barY, now);
                barY += 13;
            }
            if (overload) {
                heatBar(graphics, data, left, right, barY);
            }
        }
    }

    /**
     * An access attempt or inspection under way: where it's aimed, a bar and the time left. The
     * server sends the ticks left twice a second; the bar counts down in between.
     */
    private void progressBar(GuiGraphicsExtractor graphics, PanelSnapshot.ProgramRow running, int left, int right, int y, long now) {
        PanelSnapshot.Progress progress = running.progress();
        double ticksLeft = Math.max(0, progress.ticksLeft() - (now - PanelState.snapshotAt()) / 50.0);
        double done = Math.clamp(1 - ticksLeft / progress.totalTicks(), 0, 1);
        int color = running.state().equals("scanning") ? EdgeShell.CYAN : EdgeShell.MAGENTA;
        graphics.fill(left - 2, y - 3, right + 2, y - 2, RULE);
        String label = running.state() + " " + progress.detail();
        String time = String.format(Locale.ROOT, "%d%% · %.1fs", Math.round(done * 100), ticksLeft / 20);
        int timeX = right - font.width(time);
        text(graphics, time, timeX, y, EdgeShell.DIM);
        int barRight = timeX - 6;
        int labelWidth = Math.min(font.width(label), Math.max(0, (barRight - left) / 2));
        graphics.enableScissor(left, y - 1, left + labelWidth, y + 9);
        text(graphics, label, left, y, color);
        graphics.disableScissor();
        int barLeft = left + labelWidth + 6;
        if (barRight - barLeft < 12) {
            return;
        }
        graphics.fill(barLeft, y + 1, barRight, y + 7, 0x40F2F6FF);
        graphics.fill(barLeft, y + 1, barLeft + (int) Math.round((barRight - barLeft) * done), y + 7, color);
        // A bright edge that sweeps with the bar, so a slow attempt still looks alive.
        int edge = barLeft + (int) Math.round((barRight - barLeft) * done);
        if (done < 1 && (now / 120) % 2 == 0) {
            graphics.fill(Math.max(barLeft, edge - 1), y, edge + 1, y + 8, EdgeShell.TEXT);
        }
    }

    /**
     * top: every running program, busiest first, with what it reserves and what it uses, and the
     * kernel's own share. Click a name to open its window; ✕ stops it.
     */
    private void top(GuiGraphicsExtractor graphics, EdgeWindows.Window window, EdgeWindows.Rect bounds, EdgeShell.Data data,
                     int mouseX, int mouseY) {
        int left = bounds.x() + 8;
        int right = bounds.right() - 8;
        int y = bounds.y() + 18;
        EdgeShell.Status status = data.status();
        int reserved = status.tops() - status.topsFree();
        graphics.enableScissor(left, y - 1, right, bounds.bottom() - 4);
        int x = text(graphics, "TOPS ", left, y, EdgeShell.GREY);
        x = text(graphics, reserved + "/" + status.tops(), x, y, status.topsFree() <= 2 ? EdgeShell.PINK : EdgeShell.TEXT);
        x = text(graphics, "  RAM ", x, y, EdgeShell.GREY);
        x = text(graphics, EdgeShell.size(status.ramUsed(), status.ram()), x, y, status.ramUsed() > status.ram() ? EdgeShell.PINK : EdgeShell.TEXT);
        x = text(graphics, "  HEAT ", x, y, EdgeShell.GREY);
        text(graphics, String.valueOf(status.heat()), x, y, status.heat() >= 70 ? EdgeShell.ORANGE : EdgeShell.TEXT);
        y += LINE_HEIGHT + 2;

        int[] columns = {0, 74, 124, 196, 222, 248, 274};
        String[] heads = {"NAME", "TYPE", "STATE", "RES", "USE", "PEAK", "RAM"};
        for (int i = 0; i < heads.length; i++) {
            text(graphics, heads[i], left + columns[i], y, EdgeShell.GREY);
        }
        graphics.fill(left, y + 9, right, y + 10, RULE);
        y += LINE_HEIGHT + 1;

        List<PanelSnapshot.ProgramRow> rows = PanelState.snapshot().programs().stream()
                .sorted(Comparator.comparingDouble(PanelSnapshot.ProgramRow::averageTops).reversed()).toList();
        int visible = Math.max(1, (bounds.bottom() - 5 - LINE_HEIGHT - y) / LINE_HEIGHT);
        window.setScroll(Math.min(window.scroll(), Math.max(0, rows.size() - visible)));
        for (PanelSnapshot.ProgramRow row : rows.subList(window.scroll(), Math.min(rows.size(), window.scroll() + visible))) {
            String name = font.plainSubstrByWidth(row.name(), columns[1] - 6, false);
            boolean hovered = inside(mouseX, mouseY, left - 2, y - 1, columns[1] - 4, LINE_HEIGHT);
            if (hovered) {
                graphics.fill(left - 2, y - 1, left + columns[1] - 4, y + LINE_HEIGHT - 1, HOVER);
            }
            text(graphics, name, left, y, EdgeShell.TEXT);
            hits.add(new Hit(left - 2, y - 1, columns[1] - 4, LINE_HEIGHT, () -> windows.open(EdgeWindows.program(row.name()), Util.getMillis())));
            text(graphics, row.type(), left + columns[1], y, EdgeShell.DIM);
            text(graphics, font.plainSubstrByWidth(row.state(), columns[3] - columns[2] - 4, false), left + columns[2], y, stateColor(row.state()));
            text(graphics, String.valueOf(row.tops()), left + columns[3], y, EdgeShell.DIM);
            boolean hot = row.averageTops() > row.tops() * 0.9f;
            text(graphics, String.format(Locale.ROOT, "%.1f", row.averageTops()), left + columns[4], y, hot ? EdgeShell.ORANGE : EdgeShell.TEXT);
            text(graphics, String.format(Locale.ROOT, "%.1f", row.peakTops()), left + columns[5], y, EdgeShell.DIM);
            int ramEnd = text(graphics, row.ramMb() + "M", left + columns[6], y, EdgeShell.DIM);
            String stop = "×";
            int stopX = Math.max(ramEnd + 6, right - font.width(stop) - 2);
            boolean stopHovered = inside(mouseX, mouseY, stopX - 2, y - 1, font.width(stop) + 4, LINE_HEIGHT);
            text(graphics, stop, stopX, y, stopHovered ? EdgeShell.TEXT : EdgeShell.PINK);
            hits.add(new Hit(stopX - 2, y - 1, font.width(stop) + 4, LINE_HEIGHT,
                    () -> ClientPacketDistributor.sendToServer(new PanelRequest(PanelRequest.Action.STOP, row.name()))));
            y += LINE_HEIGHT;
        }
        if (rows.isEmpty()) {
            text(graphics, "nothing running", left, y, EdgeShell.GREY);
            y += LINE_HEIGHT;
        }
        // The kernel's share comes off the top, always.
        EdgeShell.Kernel kernel = data.kernel();
        int kernelY = Math.max(y, bounds.bottom() - 5 - LINE_HEIGHT);
        x = text(graphics, "kernel ", left, kernelY, EdgeShell.GREY);
        x = text(graphics, kernel.name(), x, kernelY, EdgeShell.VIOLET);
        text(graphics, "  " + kernel.tops() + " TOPS · " + kernel.ramMb() + "M reserved", x, kernelY, EdgeShell.GREY);
        graphics.disableScissor();
    }

    private void footer(GuiGraphicsExtractor graphics, EdgeWindows.Window window, ProgramOutputs.Output output, int left, int right, int y,
                        int mouseX, int mouseY) {
        graphics.fill(left - 2, y - 2, right + 2, y - 1, RULE);
        boolean crashed = output.ended().equals("crashed");
        String reason = output.errorLine() > 0 ? "line " + output.errorLine() + ": " + output.reason()
                : output.reason().isEmpty() ? output.ended() : output.ended() + " · " + output.reason();
        graphics.enableScissor(left, y, right, y + 10);
        text(graphics, reason, left, y, crashed ? EdgeShell.PINK : EdgeShell.GREY);
        graphics.disableScissor();
        int x = chip(graphics, "restart", left, y + 11, EdgeShell.CYAN, mouseX, mouseY, () -> start(output.id(), Util.getMillis()));
        if (crashed && !output.id().startsWith("preset:")) {
            String edit = output.errorLine() > 0 ? "edit line " + output.errorLine() : "edit";
            x = chip(graphics, edit, x, y + 11, EdgeShell.TEXT, mouseX, mouseY,
                    () -> openEditor(output.id(), output.errorLine()));
        }
        chip(graphics, "close", x, y + 11, EdgeShell.DIM, mouseX, mouseY, () -> windows.close(window.id(), Util.getMillis()));
    }

    /** An Overload program's window shows the Heat it's building. */
    private void heatBar(GuiGraphicsExtractor graphics, EdgeShell.Data data, int left, int right, int y) {
        graphics.fill(left - 2, y - 3, right + 2, y - 2, RULE);
        int heat = data.status().heat();
        String label = "Heat " + heat + "/100";
        int x = text(graphics, label, left, y, heat >= 70 ? EdgeShell.ORANGE : EdgeShell.DIM) + 6;
        int cells = Math.clamp((right - x) / 4, 0, 20);
        int lit = Math.round(heat / 100f * cells);
        for (int i = 0; i < cells; i++) {
            graphics.fill(x + i * 4, y + 1, x + i * 4 + 3, y + 7, i < lit ? EdgeShell.ORANGE : 0x40F2F6FF);
        }
    }

    private void kernelLog(GuiGraphicsExtractor graphics, EdgeWindows.Window window, EdgeWindows.Rect bounds) {
        int left = bounds.x() + 8;
        int right = bounds.right() - 8;
        int top = bounds.y() + 18;
        int bottom = bounds.bottom() - 5;
        int visible = Math.max(1, (bottom - top) / LINE_HEIGHT);
        List<ProgramOutputs.Line> lines = PanelState.snapshot().kernelLog();
        window.setScroll(Math.min(window.scroll(), Math.max(0, lines.size() - visible)));
        int first = Math.max(0, lines.size() - visible - window.scroll());
        graphics.enableScissor(left, top, right, bottom);
        if (lines.isEmpty()) {
            text(graphics, "nothing yet", left, top, EdgeShell.GREY);
        }
        int y = top;
        for (ProgramOutputs.Line line : lines.subList(first, Math.min(lines.size(), first + visible))) {
            int x = text(graphics, time(line.minute()) + " ", left, y, EdgeShell.GREY);
            text(graphics, line.text(), x, y, kernelColor(line.text()));
            y += LINE_HEIGHT;
        }
        graphics.disableScissor();
    }

    private void taskbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, long now) {
        int y = height - 17;
        graphics.fill(0, y - 1, width, y, RULE);
        graphics.fill(0, y, width, height, BAR);
        // The EDGE button: help.
        int edgeWidth = font.width("◆ EDGE") + 10;
        graphics.fill(6, y + 3, 6 + edgeWidth, y + 14, EdgeShell.MAGENTA);
        text(graphics, "◆ EDGE", 11, y + 5, 0xFF0B0F17);
        hits.add(new Hit(6, y + 3, edgeWidth, 11, () -> runCommand("help")));

        String focused = windows.focused().map(EdgeWindows.Window::id).orElse("");
        for (Pill pill : pills) {
            boolean active = pill.id().equals(focused);
            boolean open = windows.get(pill.id()).map(EdgeWindows.Window::shown).orElse(false);
            boolean hovered = inside(mouseX, mouseY, pill.x(), y + 3, pill.width(), 11);
            graphics.fill(pill.x(), y + 3, pill.x() + pill.width(), y + 14, active ? EdgeShell.CYAN : RULE);
            graphics.fill(pill.x() + 1, y + 4, pill.x() + pill.width() - 1, y + 13, active ? TITLE_BAR : hovered ? 0xFF1C2A3C : 0xFF121826);
            graphics.fill(pill.x() + 4, y + 7, pill.x() + 7, y + 10, pill.dot());
            text(graphics, pill.label(), pill.x() + 10, y + 5, open ? EdgeShell.TEXT : EdgeShell.DIM);
            hits.add(new Hit(pill.x(), y + 3, pill.width(), 11, () -> windows.pill(pill.id(), Util.getMillis())));
        }

        // Right side: the socket slots, as on the HUD.
        int slotX = width - 6;
        List<HudState.Slot> slots = HudFeed.state().slots();
        for (int i = slots.size() - 1; i >= 0; i--) {
            HudState.Slot slot = slots.get(i);
            String label = slotLabel(slot);
            int slotWidth = slotWidth(label);
            slotX -= slotWidth;
            int border = slot.empty() ? RULE : slot.overload() ? EdgeShell.ORANGE : slot.running() ? EdgeShell.CYAN : EdgeShell.GREY;
            graphics.fill(slotX, y + 3, slotX + slotWidth, y + 14, border);
            graphics.fill(slotX + 1, y + 4, slotX + slotWidth - 1, y + 13, BAR);
            text(graphics, label, slotX + (slotWidth - font.width(label)) / 2 + 1, y + 5, slot.empty() ? EdgeShell.GREY : EdgeShell.TEXT);
            slotX -= 3;
        }
    }

    /**
     * The taskbar's pills: the terminal, kernel.log when it's open, every running program, and
     * ended programs whose window is still open. As many as fit before the socket slots.
     */
    private List<Pill> pills(EdgeShell.Data data) {
        Set<String> ids = new LinkedHashSet<>(List.of(EdgeWindows.TERMINAL));
        for (String fixed : List.of(EdgeWindows.EDITOR, EdgeWindows.TOP, EdgeWindows.KERNEL)) {
            if (windows.get(fixed).isPresent()) {
                ids.add(fixed);
            }
        }
        data.programs().forEach(program -> ids.add(EdgeWindows.program(program.name())));
        for (ProgramOutputs.Output output : outputs()) {
            if (windows.get(EdgeWindows.program(output.name())).isPresent()) {
                ids.add(EdgeWindows.program(output.name()));
            }
        }
        int slotsWidth = HudFeed.state().slots().stream().mapToInt(slot -> slotWidth(slotLabel(slot)) + 3).sum();
        int limit = width - 6 - slotsWidth - 8;
        List<Pill> result = new ArrayList<>();
        int x = 6 + font.width("◆ EDGE") + 16;
        for (String id : ids) {
            String label = EdgeWindows.isProgram(id) ? EdgeWindows.programName(id)
                    : id.equals(EdgeWindows.EDITOR) && editor.dirty() ? "editor *" : id;
            int pillWidth = font.width(label) + 16;
            if (x + pillWidth > limit) {
                break;
            }
            result.add(new Pill(id, label, dot(id), x, pillWidth));
            x += pillWidth + 4;
        }
        return result;
    }

    /** Where a window opens from and folds into: its pill, or the EDGE button if it has none. */
    private EdgeWindows.Rect pillRect(String id) {
        for (Pill pill : pills) {
            if (pill.id().equals(id)) {
                return new EdgeWindows.Rect(pill.x(), height - 14, pill.width(), 11);
            }
        }
        return new EdgeWindows.Rect(6, height - 14, font.width("◆ EDGE") + 10, 11);
    }

    private int dot(String id) {
        if (id.equals(EdgeWindows.TERMINAL)) {
            return EdgeShell.CYAN;
        }
        if (id.equals(EdgeWindows.KERNEL)) {
            return EdgeShell.VIOLET;
        }
        if (id.equals(EdgeWindows.TOP)) {
            return EdgeShell.CYAN;
        }
        if (id.equals(EdgeWindows.EDITOR)) {
            return editor.dirty() ? EdgeShell.GOLD : EdgeShell.TEXT;
        }
        String name = EdgeWindows.programName(id);
        PanelSnapshot.ProgramRow running = running(name);
        if (running != null) {
            return stateColor(running.state());
        }
        ProgramOutputs.Output output = output(name);
        return output != null && output.ended().equals("crashed") ? EdgeShell.PINK : EdgeShell.GREY;
    }

    private int accent(String id, EdgeShell.Data data) {
        if (id.equals(EdgeWindows.TERMINAL)) {
            return EdgeShell.CYAN;
        }
        if (id.equals(EdgeWindows.KERNEL)) {
            return EdgeShell.VIOLET;
        }
        if (id.equals(EdgeWindows.TOP)) {
            return EdgeShell.CYAN;
        }
        if (id.equals(EdgeWindows.EDITOR)) {
            return EdgeShell.GOLD;
        }
        String name = EdgeWindows.programName(id);
        ProgramOutputs.Output output = output(name);
        if (output == null) {
            return EdgeShell.CYAN;
        }
        if (running(name) == null && output.ended().equals("crashed")) {
            return EdgeShell.PINK;
        }
        return socketLabel(data, output.id()).startsWith("O") ? EdgeShell.ORANGE : EdgeShell.CYAN;
    }

    private int chip(GuiGraphicsExtractor graphics, String label, int x, int y, int color, int mouseX, int mouseY, Runnable action) {
        int chipWidth = font.width(label) + 8;
        boolean hovered = inside(mouseX, mouseY, x, y, chipWidth, 11);
        graphics.fill(x, y, x + chipWidth, y + 11, hovered ? 0xFF1C2A3C : 0xFF121826);
        graphics.fill(x, y, x + chipWidth, y + 1, RULE);
        text(graphics, label, x + 4, y + 2, color);
        hits.add(new Hit(x, y, chipWidth, 11, action));
        return x + chipWidth + 4;
    }

    private void button(GuiGraphicsExtractor graphics, String label, int x, int y, int color, int mouseX, int mouseY, Runnable action) {
        boolean hovered = inside(mouseX, mouseY, x, y, 10, 10);
        graphics.fill(x, y, x + 10, y + 10, color);
        graphics.fill(x + 1, y + 1, x + 9, y + 9, hovered ? 0xFF2A1220 : TITLE_BAR);
        text(graphics, label, x + 5 - font.width(label) / 2, y + 1, color);
        hits.add(new Hit(x, y, 10, 10, action));
    }

    /** Draws text and returns where it ends. */
    private int text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, false);
        return x + font.width(text);
    }

    private void centered(GuiGraphicsExtractor graphics, String text, int middle, int y, int color) {
        text(graphics, text, middle - font.width(text) / 2, y, color);
    }

    /** "day 12 · 18:40", from the Overworld's clock. */
    private String clock() {
        if (minecraft.level == null) {
            return "";
        }
        long time = minecraft.level.getOverworldClockTime();
        long ofDay = (time + 6000) % 24000;
        return String.format(Locale.ROOT, "day %d · %02d:%02d", time / 24000 + 1, ofDay / 1000, ofDay % 1000 * 60 / 1000);
    }

    private static String time(int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    private static String slotLabel(HudState.Slot slot) {
        return slot.empty() ? "-" : slot.label();
    }

    private int slotWidth(String label) {
        return Math.max(18, font.width(label) + 6);
    }

    private static int stateColor(String state) {
        return switch (state) {
            case "idle", "sleeping" -> EdgeShell.GREY;
            case "scanning", "accessing", "escalating" -> EdgeShell.CYAN;
            default -> state.startsWith("paused") ? EdgeShell.ORANGE : EdgeShell.TEXT;
        };
    }

    /** The terminal's own lines about a program are grey; crashes are pink; what it printed is white. */
    private static int lineColor(String text) {
        if (text.startsWith("crashed") || text.startsWith("on_stop failed")) {
            return EdgeShell.PINK;
        }
        if (text.startsWith("started (") || text.startsWith("stopped") || text.equals("finished")) {
            return EdgeShell.GREY;
        }
        return EdgeShell.TEXT;
    }

    private static int kernelColor(String text) {
        if (text.startsWith("PANIC")) {
            return EdgeShell.MAGENTA;
        }
        if (text.startsWith("denied") || text.startsWith("couldn't deny") || text.contains(" from ")) {
            return EdgeShell.PINK;
        }
        if (text.startsWith("resisting")) {
            return EdgeShell.ORANGE;
        }
        return text.startsWith("session") ? EdgeShell.DIM : EdgeShell.TEXT;
    }

    // endregion

    // region Input

    private void click(EdgeShell.Seg seg) {
        if (seg.runs()) {
            runCommand(seg.command());
        } else {
            setInput(seg.command());
            focusTerminal();
        }
    }

    private void runCommand(String command) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        shell.submit(command, data(), Util.getMillis());
        scrollTerminalToEnd();
    }

    /** ✕ and Ctrl+W: a program's window stops the program; the terminal's closes Edge. */
    private void close(EdgeWindows.Window window) {
        String id = window.id();
        if (id.equals(EdgeWindows.TERMINAL)) {
            onClose();
            return;
        }
        if (id.equals(EdgeWindows.EDITOR)) {
            editor.requestClose(() -> windows.close(EdgeWindows.EDITOR, Util.getMillis()));
            return;
        }
        if (EdgeWindows.isProgram(id) && running(EdgeWindows.programName(id)) != null) {
            ClientPacketDistributor.sendToServer(new PanelRequest(PanelRequest.Action.STOP, EdgeWindows.programName(id)));
        }
        windows.close(id, Util.getMillis());
    }

    private void titleClicked(EdgeWindows.Window window) {
        windows.focus(window.id());
        if (doubleClick) {
            windows.toggleMaximise(window.id());
        } else {
            startDrag(window, true);
        }
    }

    private void startDrag(EdgeWindows.Window window, boolean move) {
        windows.focus(window.id());
        EdgeWindows.Rect bounds = windows.bounds(window);
        drag = move
                // A maximised window comes back to size under the cursor, so keep the grab near its left.
                ? new Drag(window.id(), true, (int) Math.min(clickX - bounds.x(), window.maximised() ? 40 : bounds.width()), (int) (clickY - bounds.y()))
                : new Drag(window.id(), false, (int) (bounds.right() - clickX), (int) (bounds.bottom() - clickY));
    }

    private void focusTerminal() {
        windows.open(EdgeWindows.TERMINAL, Util.getMillis());
    }

    private void scrollTerminalToEnd() {
        windows.get(EdgeWindows.TERMINAL).ifPresent(window -> window.setScroll(0));
    }

    /** Whether the editor's window is the one on top, so it gets the keyboard. */
    private boolean editorFocused() {
        return windows.focused().map(window -> window.id().equals(EdgeWindows.EDITOR)).orElse(false);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event.codepoint() < 32 || event.codepoint() == 127) {
            return super.charTyped(event);
        }
        if (editorFocused() && editor.charTyped(event)) {
            return true;
        }
        focusTerminal();
        insert(event.codepointAsString());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean ctrl = event.hasControlDown();
        if (ctrl && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int index = key - GLFW.GLFW_KEY_1;
            if (index < pills.size()) {
                windows.pill(pills.get(index).id(), Util.getMillis());
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_W) {
            windows.focused().filter(window -> !window.id().equals(EdgeWindows.TERMINAL)).ifPresent(this::close);
            return true;
        }
        if (editorFocused()) {
            // The editor takes the keyboard; Esc still closes Edge once nothing in the editor wants it.
            if (editor.keyPressed(event)) {
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                return super.keyPressed(event);
            }
            if (editor.file() != null) {
                return true;
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                focusTerminal();
                shell.submit(input, data(), Util.getMillis());
                setInput("");
                scrollTerminalToEnd();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
                    input = input.substring(0, cursor - 1) + input.substring(cursor);
                    cursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < input.length()) {
                    input = input.substring(0, cursor) + input.substring(cursor + 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = Math.max(0, cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = Math.min(input.length(), cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = input.length();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                focusTerminal();
                setInput(shell.historyUp(input));
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                focusTerminal();
                setInput(shell.historyDown());
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                focusTerminal();
                setInput(shell.complete(input, data(), Util.getMillis()));
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                windows.focused().ifPresent(window -> window.setScroll(window.scroll() + 5));
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                windows.focused().ifPresent(window -> window.setScroll(window.scroll() - 5));
                return true;
            }
            default -> {
                if (ctrl && key == GLFW.GLFW_KEY_V) {
                    focusTerminal();
                    insert(minecraft.keyboardHandler.getClipboard().replace('\n', ' ').replace('\r', ' '));
                    return true;
                }
                if (ctrl && key == GLFW.GLFW_KEY_L) {
                    shell.submit("clear", data(), Util.getMillis());
                    return true;
                }
            }
        }
        // Esc closes, as on any screen. G is a letter here, so it types.
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            clickX = event.x();
            clickY = event.y();
            this.doubleClick = doubleClick;
            clickShift = event.hasShiftDown();
            // A click anywhere on a window brings it to the front, so it gets the keyboard.
            windows.at(event.x(), event.y()).ifPresent(window -> windows.focus(window.id()));
            // Topmost first: the taskbar, then windows from the top down.
            for (Hit hit : hits.reversed()) {
                if (inside(event.x(), event.y(), hit.x(), hit.y(), hit.width(), hit.height())) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (editor.dragging() && event.button() == 0) {
            editor.drag(event.x(), event.y());
            return true;
        }
        if (drag == null || event.button() != 0) {
            return super.mouseDragged(event, dx, dy);
        }
        Drag current = drag;
        windows.get(current.id()).ifPresentOrElse(window -> {
            if (current.move()) {
                windows.move(current.id(), (int) event.x() - current.offsetX(), (int) event.y() - current.offsetY());
            } else {
                EdgeWindows.Rect bounds = windows.bounds(window);
                windows.resize(current.id(), (int) event.x() + current.offsetX() - bounds.x(), (int) event.y() + current.offsetY() - bounds.y());
            }
        }, () -> drag = null);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        drag = null;
        editor.release();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        windows.at(x, y).ifPresent(window -> {
            if (window.id().equals(EdgeWindows.EDITOR)) {
                editor.scroll(x, scrollX, scrollY, minecraft.hasShiftDown());
            } else {
                window.setScroll(window.scroll() + (int) Math.signum(scrollY) * 3);
            }
        });
        return true;
    }

    private void insert(String text) {
        input = input.substring(0, cursor) + text + input.substring(cursor);
        cursor += text.length();
    }

    private void setInput(String text) {
        input = text;
        cursor = text.length();
    }

    private record Hit(int x, int y, int width, int height, Runnable action) {
    }

    private record Pill(String id, String label, int dot, int x, int width) {
    }

    /**
     * @param offsetX for a move, the grab point from the window's left; for a resize, from its right
     */
    private record Drag(String id, boolean move, int offsetX, int offsetY) {
    }

    /** The serial of the run seen when the start was asked for, and when. */
    private record Pending(int serial, long at) {
    }

    /** Lines from {@code from} on (counted from the program's start) type in, starting at {@code start}. */
    private static final class Typing {
        private int serial = -1;
        private int seen;
        private int from;
        private long start;

        void update(ProgramOutputs.Output output, long now) {
            if (output.serial() != serial) {
                // A window that has just opened shows what's there at once; a restart types from the top.
                seen = serial < 0 ? output.total() : 0;
                from = seen;
                serial = output.serial();
            }
            if (output.total() > seen) {
                from = seen;
                start = now;
                seen = output.total();
            }
        }
    }

    private static boolean inside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    // endregion

    // region What the windows see

    private static List<ProgramOutputs.Output> outputs() {
        return PanelState.snapshot().outputs();
    }

    private static ProgramOutputs.@Nullable Output output(String name) {
        return outputs().stream().filter(output -> output.name().equals(name)).findFirst().orElse(null);
    }

    private static PanelSnapshot.@Nullable ProgramRow running(String name) {
        return PanelState.snapshot().programs().stream().filter(program -> program.name().equals(name)).findFirst().orElse(null);
    }

    /** "S2" or "O1" if the program sits in a socket, else empty. */
    private static String socketLabel(EdgeShell.Data data, String id) {
        int standard = 0;
        int overload = 0;
        for (EdgeShell.Socket socket : data.sockets()) {
            if (socket.overload()) {
                overload++;
            } else {
                standard++;
            }
            if (!id.isEmpty() && socket.id().equals(id)) {
                return socket.overload() ? "O" + overload : "S" + standard;
            }
        }
        return "";
    }

    /** The shell's view of the gateway: the panel snapshot plus the synced gateway data. */
    private EdgeShell.Data data() {
        LocalPlayer player = minecraft.player;
        PanelSnapshot snapshot = PanelState.snapshot();
        GatewayData data = player == null ? null : player.getData(ModAttachments.GATEWAY);
        GatewayTier tier = data == null ? GatewayTier.NULL : data.tier();
        EdgeShell.Status status = data == null ? new EdgeShell.Status("", "", 0, 0, 0, 0, 0, 0, 0, 0)
                : new EdgeShell.Status(tier.label(), tier.band(), tier.ramMb() - data.freeRamMb(), tier.ramMb(), data.leakMb(),
                data.freeTops(), tier.tops(), data.bufferMb(), tier.bufferMb(), (int) Math.round(data.heat()));
        PanelSnapshot.KernelInfo kernel = snapshot.kernel();
        return new EdgeShell.Data(
                player == null ? "?" : player.getName().getString(),
                player == null ? "" : Addresses.of(player.getUUID()),
                snapshot.atStation(),
                status,
                new EdgeShell.Kernel(kernel.id(), kernel.name(), kernel.schedule(), kernel.gc(), kernel.verify(), kernel.rateLimit(),
                        kernel.hooks(), kernel.ramMb(), kernel.tops()),
                snapshot.scripts().stream().map(row -> new EdgeShell.Script(row.id(), row.name(), row.kernel(), row.preset())).toList(),
                snapshot.programs().stream().map(row -> new EdgeShell.Proc(row.id(), row.name(), row.type(), row.state(), row.ramMb(), row.tops())).toList(),
                snapshot.flaws().stream().map(row -> new EdgeShell.Flaw(row.className(), row.severity(), row.cause(), row.approaching())).toList(),
                snapshot.sockets().stream().map(row -> new EdgeShell.Socket(row.overload(), row.id(), row.name())).toList(),
                snapshot.outputs().stream().map(ProgramOutputs.Output::name).toList());
    }

    /** In singleplayer the storage folder is on this computer, so Edge can open it. */
    private Path localFolder() {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return null;
        }
        return ScriptStore.folder(server, minecraft.player.getUUID());
    }

    // endregion
}
