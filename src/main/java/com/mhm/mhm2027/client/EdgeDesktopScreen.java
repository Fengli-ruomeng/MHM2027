package com.mhm.mhm2027.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.GatewayTier;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.hud.HudState;
import com.mhm.mhm2027.network.PanelRequest;
import com.mhm.mhm2027.network.PanelSnapshot;
import com.mhm.mhm2027.terminal.Addresses;
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
 * Edge, the gateway's operating system: a top bar, a terminal and a taskbar. Everything the old
 * panel did is a command now, and the output is clickable. The shell keeps its history and
 * output for the session, so reopening Edge picks up where you left off.
 *
 * Drawn immediate-mode, like the rest of the HUD: clicks are matched against the regions
 * drawn in the last frame.
 */
public class EdgeDesktopScreen extends Screen {
    private static final int BACKGROUND = 0xF0090D14;
    private static final int BAR = 0xFF0B0F17;
    private static final int RULE = 0xFF2C3550;
    private static final int TITLE_BAR = 0xFF10263A;
    private static final int HOVER = 0x26FFFFFF;
    private static final int LINE_HEIGHT = 10;
    /** Output types itself in at this many milliseconds per character. */
    private static final long TYPE_MILLIS = 3;
    private static final List<String> CHIPS = List.of("help", "ls", "ps", "status", "socket ls", "flaws");

    /** The shell outlives the screen for the session: history and output stay. */
    private static EdgeShell shell = new EdgeShell();

    private final List<Hit> hits = new ArrayList<>();
    private String input = "";
    private int cursor;
    private int scroll;
    private int lastMessage;

    public EdgeDesktopScreen() {
        super(Component.translatable("mhm2027.desktop.title"));
    }

    /** A new world, a new session. */
    static void reset() {
        shell = new EdgeShell();
    }

    @Override
    public void added() {
        super.added();
        ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.OPEN));
        lastMessage = PanelState.messageSerial();
        if (shell.lines().isEmpty()) {
            shell.welcome(data(), Util.getMillis());
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
        if (PanelState.messageSerial() != lastMessage) {
            lastMessage = PanelState.messageSerial();
            shell.message(PanelState.lastMessage().getString(), PanelState.messageSuccess(), Util.getMillis());
            scroll = 0;
        }
        for (EdgeShell.Request request : shell.drain()) {
            handle(request);
        }
    }

    private void handle(EdgeShell.Request request) {
        switch (request.action()) {
            case "EDIT" -> minecraft.setScreen(new CodeEditorScreen(request.argument()));
            case "EXIT" -> onClose();
            case "FOLDER" -> {
                Path folder = localFolder();
                if (folder == null) {
                    shell.message("folder: your storage is on the server; open it in singleplayer", false, Util.getMillis());
                } else {
                    Util.getPlatform().openPath(folder);
                }
            }
            default -> ClientPacketDistributor.sendToServer(new PanelRequest(PanelRequest.Action.valueOf(request.action()), request.argument()));
        }
    }

    // region Drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        hits.clear();
        long now = Util.getMillis();
        graphics.fill(0, 0, width, height, BACKGROUND);
        EdgeShell.Data data = data();
        topBar(graphics, data, now);
        terminal(graphics, data, mouseX, mouseY, now);
        taskbar(graphics, data, mouseX, mouseY, now);
    }

    private void topBar(GuiGraphicsExtractor graphics, EdgeShell.Data data, long now) {
        graphics.fill(0, 0, width, 14, BAR);
        graphics.fill(0, 14, width, 15, RULE);
        int x = 6;
        x = text(graphics, "EDGE", x, 3, EdgeShell.TEXT) + 10;
        x = text(graphics, data.user() + "@" + data.address(), x, 3, EdgeShell.CYAN) + 10;
        if (!data.isNull()) {
            text(graphics, data.status().level() + " · " + data.kernel().name(), x, 3, EdgeShell.DIM);
        }
        // Right side: the four resources as small cell meters, then the clock.
        String clock = clock();
        int right = width - 6 - font.width(clock);
        text(graphics, clock, right, 3, EdgeShell.DIM);
        if (!data.isNull()) {
            EdgeShell.Status status = data.status();
            right = meter(graphics, "HEAT", status.heat() / 100.0, status.heat() >= 70 ? EdgeShell.ORANGE : EdgeShell.TEXT, right - 12);
            right = meter(graphics, "TOPS", 1 - (double) status.topsFree() / Math.max(1, status.tops()), EdgeShell.TEXT, right - 10);
            right = meter(graphics, "RAM", status.ramUsed() / Math.max(1, status.ram()), status.ramUsed() > status.ram() ? EdgeShell.MAGENTA : EdgeShell.TEXT, right - 10);
            meter(graphics, "BUF", status.buffer() / Math.max(1, status.bufferMax()), EdgeShell.CYAN, right - 10);
        }
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

    private void terminal(GuiGraphicsExtractor graphics, EdgeShell.Data data, int mouseX, int mouseY, long now) {
        int x0 = 6;
        int y0 = 20;
        int x1 = width - 6;
        int y1 = height - 22;
        LensHud.panel(graphics, x0, y0, x1 - x0, y1 - y0, EdgeShell.CYAN, 0xFF0B0F17);
        graphics.fill(x0 + 1, y0 + 4, x1 - 1, y0 + 14, TITLE_BAR);
        text(graphics, "◆ terminal", x0 + 8, y0 + 5, EdgeShell.CYAN);
        text(graphics, "— ~", x0 + 12 + font.width("◆ terminal"), y0 + 5, EdgeShell.DIM);
        button(graphics, "×", x1 - 14, y0 + 4, EdgeShell.PINK, mouseX, mouseY, this::onClose);

        int left = x0 + 8;
        int right = x1 - 8;
        int chipsY = y1 - 15;
        int promptY = chipsY - 13;
        int top = y0 + 19;
        int visible = Math.max(1, (promptY - 3 - top) / LINE_HEIGHT);

        // Output, newest at the bottom; the wheel scrolls back.
        List<EdgeShell.Line> lines = shell.lines().stream().filter(line -> line.at() <= now).toList();
        scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - visible));
        int first = Math.max(0, lines.size() - visible - scroll);
        graphics.enableScissor(left, top, right, promptY - 2);
        int y = top;
        for (EdgeShell.Line line : lines.subList(first, Math.min(lines.size(), first + visible))) {
            drawLine(graphics, line, left, y, right, mouseX, mouseY, now);
            y += LINE_HEIGHT;
        }
        graphics.disableScissor();
        if (scroll > 0) {
            String more = "▼ " + scroll + " newer";
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

        // Quick commands, one click each.
        int chipX = left;
        for (String chip : CHIPS) {
            int chipWidth = font.width(chip) + 8;
            boolean hovered = inside(mouseX, mouseY, chipX, chipsY, chipWidth, 11);
            graphics.fill(chipX, chipsY, chipX + chipWidth, chipsY + 11, hovered ? 0xFF1C2A3C : 0xFF121826);
            graphics.fill(chipX, chipsY, chipX + chipWidth, chipsY + 1, RULE);
            text(graphics, chip, chipX + 4, chipsY + 2, EdgeShell.DIM);
            hits.add(new Hit(chipX, chipsY, chipWidth, 11, () -> runCommand(chip)));
            chipX += chipWidth + 4;
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

    private void taskbar(GuiGraphicsExtractor graphics, EdgeShell.Data data, int mouseX, int mouseY, long now) {
        int y = height - 17;
        graphics.fill(0, y - 1, width, y, RULE);
        graphics.fill(0, y, width, height, BAR);
        int x = 6;
        // The EDGE button: help.
        int edgeWidth = font.width("◆ EDGE") + 10;
        graphics.fill(x, y + 3, x + edgeWidth, y + 14, EdgeShell.MAGENTA);
        text(graphics, "◆ EDGE", x + 5, y + 5, 0xFF0B0F17);
        hits.add(new Hit(x, y + 3, edgeWidth, 11, () -> runCommand("help")));
        x += edgeWidth + 6;
        x = pill(graphics, "terminal", EdgeShell.CYAN, true, x, y, mouseX, mouseY, null);
        for (EdgeShell.Proc program : data.programs()) {
            int dot = switch (program.state()) {
                case "idle", "sleeping" -> EdgeShell.GREY;
                case "scanning", "accessing", "escalating" -> EdgeShell.CYAN;
                default -> program.state().startsWith("paused") ? EdgeShell.ORANGE : EdgeShell.TEXT;
            };
            if (x + font.width(program.name()) + 20 > width / 2 + 60) {
                break;
            }
            x = pill(graphics, program.name(), dot, false, x, y, mouseX, mouseY, "log " + program.name());
        }

        // Right side: the socket slots, as on the HUD.
        int slotX = width - 6;
        List<HudState.Slot> slots = HudFeed.state().slots();
        for (int i = slots.size() - 1; i >= 0; i--) {
            HudState.Slot slot = slots.get(i);
            String label = slot.empty() ? "-" : slot.label();
            int slotWidth = Math.max(18, font.width(label) + 6);
            slotX -= slotWidth;
            int border = slot.empty() ? RULE : slot.overload() ? EdgeShell.ORANGE : slot.running() ? EdgeShell.CYAN : EdgeShell.GREY;
            graphics.fill(slotX, y + 3, slotX + slotWidth, y + 14, border);
            graphics.fill(slotX + 1, y + 4, slotX + slotWidth - 1, y + 13, BAR);
            text(graphics, label, slotX + (slotWidth - font.width(label)) / 2 + 1, y + 5, slot.empty() ? EdgeShell.GREY : EdgeShell.TEXT);
            slotX -= 3;
        }
    }

    private int pill(GuiGraphicsExtractor graphics, String name, int dot, boolean active, int x, int y, int mouseX, int mouseY, String command) {
        int pillWidth = font.width(name) + 16;
        boolean hovered = command != null && inside(mouseX, mouseY, x, y + 3, pillWidth, 11);
        graphics.fill(x, y + 3, x + pillWidth, y + 14, active ? 0xFF3FE8FF : RULE);
        graphics.fill(x + 1, y + 4, x + pillWidth - 1, y + 13, active ? TITLE_BAR : hovered ? 0xFF1C2A3C : 0xFF121826);
        graphics.fill(x + 4, y + 7, x + 7, y + 10, dot);
        text(graphics, name, x + 10, y + 5, EdgeShell.TEXT);
        if (command != null) {
            hits.add(new Hit(x, y + 3, pillWidth, 11, () -> runCommand(command)));
        }
        return x + pillWidth + 4;
    }

    private void button(GuiGraphicsExtractor graphics, String label, int x, int y, int color, int mouseX, int mouseY, Runnable action) {
        boolean hovered = inside(mouseX, mouseY, x, y, 10, 10);
        graphics.fill(x, y, x + 10, y + 10, color);
        graphics.fill(x + 1, y + 1, x + 9, y + 9, hovered ? 0xFF2A1220 : TITLE_BAR);
        text(graphics, label, x + 3, y + 1, color);
        hits.add(new Hit(x, y, 10, 10, action));
    }

    /** Draws text and returns where it ends. */
    private int text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, false);
        return x + font.width(text);
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

    // endregion

    // region Input

    private void click(EdgeShell.Seg seg) {
        if (seg.runs()) {
            runCommand(seg.command());
        } else {
            input = seg.command();
            cursor = input.length();
        }
    }

    private void runCommand(String command) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        shell.submit(command, data(), Util.getMillis());
        scroll = 0;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event.codepoint() < 32 || event.codepoint() == 127) {
            return super.charTyped(event);
        }
        insert(event.codepointAsString());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean ctrl = event.hasControlDown();
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                shell.submit(input, data(), Util.getMillis());
                input = "";
                cursor = 0;
                scroll = 0;
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
                setInput(shell.historyUp(input));
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                setInput(shell.historyDown());
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                setInput(shell.complete(input, data(), Util.getMillis()));
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                scroll += 5;
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                scroll = Math.max(0, scroll - 5);
                return true;
            }
            default -> {
                if (ctrl && key == GLFW.GLFW_KEY_V) {
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
            for (Hit hit : List.copyOf(hits)) {
                if (inside(event.x(), event.y(), hit.x(), hit.y(), hit.width(), hit.height())) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll + (int) Math.signum(scrollY) * 3);
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

    private static boolean inside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    // endregion

    // region What the shell sees

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
                tier.isNull(),
                snapshot.atStation(),
                status,
                new EdgeShell.Kernel(kernel.id(), kernel.name(), kernel.schedule(), kernel.gc(), kernel.verify(), kernel.rateLimit(),
                        kernel.hooks(), kernel.ramMb(), kernel.tops()),
                snapshot.scripts().stream().map(row -> new EdgeShell.Script(row.id(), row.name(), row.kernel(), row.preset())).toList(),
                snapshot.programs().stream().map(row -> new EdgeShell.Proc(row.id(), row.name(), row.type(), row.state(), row.ramMb(), row.tops())).toList(),
                snapshot.flaws().stream().map(row -> new EdgeShell.Flaw(row.className(), row.severity(), row.cause(), row.approaching())).toList(),
                snapshot.sockets().stream().map(row -> new EdgeShell.Socket(row.overload(), row.id(), row.name())).toList(),
                snapshot.log());
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
