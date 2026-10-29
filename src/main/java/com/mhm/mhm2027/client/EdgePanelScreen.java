package com.mhm.mhm2027.client;

import static com.mhm.mhm2027.gateway.GatewayData.formatMb;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.GatewayTier;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.network.PanelRequest;
import com.mhm.mhm2027.network.PanelSnapshot;
import com.mhm.mhm2027.terminal.ScriptStore;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
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
 * The gateway control panel, opened with a key. Everything a player does with their terminal
 * happens here; the /edge commands are for admins.
 *
 * Drawn immediate-mode: rows and buttons are laid out every frame from the latest snapshot,
 * and clicks are matched against the regions drawn in the last frame. The lists change twice
 * a second, so rebuilding widget objects would only add churn.
 */
public class EdgePanelScreen extends Screen {
    private enum Tab {
        STATUS, PROCESSES, KERNEL, LOG
    }

    private static final int MAX_WIDTH = 360;
    private static final int MAX_HEIGHT = 232;
    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 12;
    private static final int BUTTON_HEIGHT = 11;

    private static final int COLOR_BACKGROUND = 0xF00C1016;
    private static final int COLOR_BORDER = 0xFF1F7FA8;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF8A93A0;
    private static final int COLOR_ACCENT = 0xFF55FFFF;
    private static final int COLOR_OK = 0xFF55FF7F;
    private static final int COLOR_WARN = 0xFFFFC040;
    private static final int COLOR_ALERT = 0xFFFF5555;
    private static final int COLOR_PRESET = 0xFFFFAA00;
    private static final int COLOR_BUTTON = 0xFF1C2733;
    private static final int COLOR_BUTTON_HOVER = 0xFF2B3D52;
    private static final int COLOR_BUTTON_DISABLED = 0xFF151B22;
    private static final int COLOR_ROW_HOVER = 0x20FFFFFF;
    private static final int COLOR_BAR_BACK = 0xFF1A2028;
    private static final int COLOR_PROGRAMS = 0xFF3FA9F5;
    private static final int COLOR_LEAK = 0xFFB040FF;
    private static final int COLOR_BUFFER = 0xFF40C0FF;
    private static final int COLOR_HEAT = 0xFFFF8030;

    /** The tab is remembered between openings. */
    private static Tab lastTab = Tab.STATUS;

    private final KeyMapping toggleKey;
    private final List<Hit> hits = new ArrayList<>();
    private Tab tab = lastTab;
    private int scroll;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public EdgePanelScreen(KeyMapping toggleKey) {
        super(Component.translatable("mhm2027.panel.title"));
        this.toggleKey = toggleKey;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_WIDTH, width - 16);
        panelHeight = Math.min(MAX_HEIGHT, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
    }

    @Override
    public void added() {
        super.added();
        ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.OPEN));
    }

    @Override
    public void removed() {
        ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.CLOSE));
        lastTab = tab;
        super.removed();
    }

    /** Programs keep running while the panel is open, so the game must not pause. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // region Drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        hits.clear();
        super.extractRenderState(graphics, mouseX, mouseY, a);

        graphics.fill(left, top, left + panelWidth, top + panelHeight, COLOR_BACKGROUND);
        outline(graphics, left, top, panelWidth, panelHeight, COLOR_BORDER);

        LocalPlayer player = minecraft.player;
        GatewayData data = player == null ? null : player.getData(ModAttachments.GATEWAY);
        GatewayTier tier = data == null ? GatewayTier.NULL : data.tier();
        PanelSnapshot snapshot = PanelState.snapshot();

        text(graphics, Component.translatable("mhm2027.panel.title"), left + PADDING, top + 6, COLOR_ACCENT);
        Component level = Component.literal(tier.label() + " " + tier.band());
        text(graphics, level, left + panelWidth - PADDING - font.width(level), top + 6, COLOR_DIM);

        drawTabs(graphics, mouseX, mouseY);

        int contentTop = top + 34;
        int contentBottom = top + panelHeight - 16;
        if (tier.isNull()) {
            text(graphics, Component.translatable("mhm2027.terminal.null"), left + PADDING, contentTop, COLOR_DIM);
        } else {
            switch (tab) {
                case STATUS -> drawStatus(graphics, data, tier, snapshot, contentTop);
                case PROCESSES -> drawProcesses(graphics, snapshot, contentTop, contentBottom, mouseX, mouseY);
                case KERNEL -> drawKernel(graphics, snapshot, contentTop, contentBottom, mouseX, mouseY);
                case LOG -> drawLog(graphics, snapshot, contentTop, contentBottom);
            }
        }

        Component message = PanelState.message();
        int footerY = top + panelHeight - 12;
        text(graphics, clip(message, panelWidth - 90), left + PADDING, footerY, PanelState.messageSuccess() ? COLOR_OK : COLOR_ALERT);
        Component hint = Component.translatable("mhm2027.panel.close_hint", toggleKey.getTranslatedKeyMessage());
        text(graphics, hint, left + panelWidth - PADDING - font.width(hint), footerY, COLOR_DIM);
    }

    private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int tabWidth = (panelWidth - 2 * PADDING - 3 * 4) / 4;
        int x = left + PADDING;
        int y = top + 18;
        for (Tab each : Tab.values()) {
            boolean selected = each == tab;
            boolean hovered = inside(mouseX, mouseY, x, y, tabWidth, 12);
            graphics.fill(x, y, x + tabWidth, y + 12, selected ? COLOR_BORDER : hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
            Component label = Component.translatable("mhm2027.panel.tab." + each.name().toLowerCase(Locale.ROOT));
            text(graphics, label, x + (tabWidth - font.width(label)) / 2, y + 2, selected ? 0xFF0C1016 : COLOR_TEXT);
            if (!selected) {
                hits.add(new Hit(x, y, tabWidth, 12, () -> {
                    tab = each;
                    scroll = 0;
                }));
            }
            x += tabWidth + 4;
        }
    }

    private void drawStatus(GuiGraphicsExtractor graphics, GatewayData data, GatewayTier tier, PanelSnapshot snapshot, int y) {
        int x = left + PADDING;
        int barWidth = panelWidth - 2 * PADDING;

        double ram = tier.ramMb();
        double programs = Math.min(data.programRamMb(), ram);
        double leak = Math.min(data.leakMb(), Math.max(0, ram - programs));
        boolean outOfMemory = data.isOutOfMemory();
        y = labelledBar(graphics, x, y, barWidth, Component.translatable("mhm2027.panel.ram"),
                Component.translatable("mhm2027.panel.ram_value", formatMb(ram - data.freeRamMb()), formatMb(ram),
                        formatMb(data.programRamMb()), formatMb(data.leakMb())),
                outOfMemory ? COLOR_ALERT : COLOR_TEXT,
                new double[]{programs / ram, leak / ram}, new int[]{COLOR_PROGRAMS, outOfMemory ? COLOR_ALERT : COLOR_LEAK});

        double tops = tier.tops();
        int freeTops = data.freeTops();
        y = labelledBar(graphics, x, y, barWidth, Component.literal("TOPS"),
                Component.translatable("mhm2027.panel.tops_value", freeTops, tier.tops()),
                freeTops <= 1 ? COLOR_ALERT : COLOR_TEXT,
                new double[]{data.topsInUse() / tops}, new int[]{freeTops <= 1 ? COLOR_ALERT : COLOR_OK});

        y = labelledBar(graphics, x, y, barWidth, Component.translatable("mhm2027.panel.buffer"),
                Component.literal(formatMb(data.bufferMb()) + " / " + formatMb(tier.bufferMb())), COLOR_TEXT,
                new double[]{data.bufferMb() / tier.bufferMb()}, new int[]{COLOR_BUFFER});

        y = labelledBar(graphics, x, y, barWidth, Component.translatable("mhm2027.panel.heat"),
                Component.literal(Math.round(data.heat()) + " / 100"), data.heat() >= 70 ? COLOR_WARN : COLOR_TEXT,
                new double[]{data.heat() / GatewayData.MAX_HEAT}, new int[]{COLOR_HEAT});

        y += 2;
        text(graphics, Component.translatable("mhm2027.terminal.kernel_line", snapshot.kernel().name(), snapshot.programs().size()), x, y, COLOR_TEXT);
        y += ROW_HEIGHT;
        text(graphics, Component.translatable("mhm2027.terminal.slots", tier.standardSlots(), tier.overloadSlots(), tier.hookPoints()), x, y, COLOR_DIM);
        y += ROW_HEIGHT;
        List<PanelSnapshot.FlawRow> activeFlaws = snapshot.flaws().stream().filter(flaw -> !flaw.approaching()).toList();
        if (!activeFlaws.isEmpty()) {
            String names = String.join(", ", activeFlaws.stream().map(PanelSnapshot.FlawRow::className).toList());
            text(graphics, clip(Component.translatable("mhm2027.panel.flaws_brief", names), panelWidth - 2 * PADDING), x, y, COLOR_WARN);
            y += ROW_HEIGHT;
        }
        if (snapshot.rebooting()) {
            text(graphics, Component.translatable("mhm2027.terminal.rebooting", snapshot.lockReason(), snapshot.lockSecondsLeft()), x, y, COLOR_WARN);
            y += ROW_HEIGHT;
        }
        if (outOfMemory) {
            text(graphics, Component.translatable("mhm2027.terminal.oom"), x, y, COLOR_ALERT);
        }
    }

    private void drawProcesses(GuiGraphicsExtractor graphics, PanelSnapshot snapshot, int contentTop, int contentBottom, int mouseX, int mouseY) {
        int x = left + PADDING;
        int right = left + panelWidth - PADDING;
        boolean rebooting = snapshot.rebooting();

        List<Row> rows = new ArrayList<>();
        rows.add((g, y, hover) -> {
            text(g, Component.translatable("mhm2027.panel.running", snapshot.programs().size()), x, y + 2, COLOR_ACCENT);
            text(g, Component.translatable("mhm2027.panel.columns"), x + 96, y + 2, COLOR_DIM);
        });
        if (snapshot.programs().isEmpty()) {
            rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.terminal.none"), x + 4, y + 2, COLOR_DIM));
        }
        for (PanelSnapshot.ProgramRow program : snapshot.programs()) {
            rows.add((g, y, hover) -> {
                text(g, clip(Component.literal(program.name()), 90), x + 4, y + 2, program.preset() ? COLOR_PRESET : COLOR_TEXT);
                text(g, Component.literal(program.type() + "  " + formatMb(program.ramMb()) + "  " + program.tops()
                        + "  " + format(program.averageTops()) + "/" + format(program.peakTops())), x + 96, y + 2, COLOR_DIM);
                Component state = stateLabel(program.state());
                text(g, state, right - 40 - font.width(state), y + 2, stateColor(program.state()));
                button(g, right - 36, y, 36, Component.translatable("mhm2027.panel.stop"), true, hover,
                        () -> send(PanelRequest.Action.STOP, program.name()));
            });
        }

        addSocketRows(rows, snapshot, x, right);

        rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.panel.storage"), x, y + 4, COLOR_ACCENT));
        List<PanelSnapshot.ScriptRow> runnable = snapshot.scripts().stream()
                .filter(script -> !script.kernel())
                .filter(script -> snapshot.programs().stream().noneMatch(program -> program.id().equals(script.id())))
                .toList();
        if (runnable.isEmpty()) {
            rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.terminal.none"), x + 4, y + 2, COLOR_DIM));
        }
        for (PanelSnapshot.ScriptRow script : runnable) {
            rows.add((g, y, hover) -> {
                storageLabel(g, script, x + 4, y + 2);
                button(g, right - 36, y, 36, Component.translatable("mhm2027.panel.run"), !rebooting, hover,
                        () -> send(PanelRequest.Action.START, script.id()));
                if (snapshot.socketed(script.id())) {
                    Component tag = Component.translatable("mhm2027.panel.socketed_tag");
                    text(g, tag, right - 116 - font.width(tag), y + 2, COLOR_OK);
                } else {
                    button(g, right - 160, y, 44, Component.translatable("mhm2027.panel.socket"), snapshot.atStation(), hover,
                            () -> send(PanelRequest.Action.SOCKET, script.id()));
                }
                if (!script.preset()) {
                    button(g, right - 76, y, 38, Component.translatable("mhm2027.panel.burn"), true, hover,
                            () -> send(PanelRequest.Action.BURN, script.id()));
                    button(g, right - 112, y, 34, Component.translatable("mhm2027.panel.edit"), true, hover,
                            () -> minecraft.setScreen(new CodeEditorScreen(script.id())));
                }
            });
        }

        int listBottom = contentBottom - BUTTON_HEIGHT - 4;
        drawRows(graphics, rows, contentTop, listBottom, mouseX, mouseY);

        int buttonsY = contentBottom - BUTTON_HEIGHT;
        button(graphics, x, buttonsY, 60, Component.translatable("mhm2027.panel.reboot"), !rebooting, mouseOver(mouseX, mouseY),
                () -> send(PanelRequest.Action.REBOOT, ""));
        button(graphics, x + 64, buttonsY, 74, Component.translatable("mhm2027.panel.code_editor"), true, mouseOver(mouseX, mouseY),
                () -> minecraft.setScreen(new CodeEditorScreen(null)));
        Path folder = localFolder();
        if (folder != null) {
            button(graphics, x + 142, buttonsY, 76, Component.translatable("mhm2027.panel.open_folder"), true, mouseOver(mouseX, mouseY),
                    () -> Util.getPlatform().openPath(folder));
        }
        if (rebooting) {
            Component status = Component.translatable("mhm2027.terminal.rebooting", snapshot.lockReason(), snapshot.lockSecondsLeft());
            text(graphics, status, right - font.width(status), buttonsY + 2, COLOR_WARN);
        }
    }

    /**
     * The sockets: standard ones (S1, S2...) then Overload ones (O1...). They can only be
     * changed at a Flash Station, so the buttons are greyed out anywhere else.
     */
    private void addSocketRows(List<Row> rows, PanelSnapshot snapshot, int x, int right) {
        rows.add((g, y, hover) -> {
            text(g, Component.translatable("mhm2027.panel.sockets"), x, y + 4, COLOR_ACCENT);
            if (!snapshot.atStation()) {
                Component hint = Component.translatable("mhm2027.panel.sockets_hint");
                text(g, hint, right - font.width(hint), y + 4, COLOR_DIM);
            }
        });
        if (snapshot.overloadLockSeconds() > 0) {
            rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.panel.overload_locked", snapshot.overloadLockSeconds()),
                    x + 4, y + 2, COLOR_ALERT));
        }
        int standard = 0;
        int overload = 0;
        for (PanelSnapshot.SocketRow socket : snapshot.sockets()) {
            String label = socket.overload() ? "O" + ++overload : "S" + ++standard;
            rows.add((g, y, hover) -> {
                text(g, Component.literal(label), x + 4, y + 2, socket.overload() ? COLOR_HEAT : COLOR_DIM);
                if (socket.empty()) {
                    text(g, Component.translatable("mhm2027.panel.socket_empty"), x + 24, y + 2, COLOR_DIM);
                    return;
                }
                text(g, clip(Component.literal(socket.name()), right - x - 100), x + 24, y + 2, socket.preset() ? COLOR_PRESET : COLOR_TEXT);
                button(g, right - 44, y, 44, Component.translatable("mhm2027.panel.remove"), snapshot.atStation(), hover,
                        () -> send(PanelRequest.Action.UNSOCKET, socket.id()));
            });
        }
    }

    private void drawKernel(GuiGraphicsExtractor graphics, PanelSnapshot snapshot, int contentTop, int contentBottom, int mouseX, int mouseY) {
        int x = left + PADDING;
        int right = left + panelWidth - PADDING;
        boolean rebooting = snapshot.rebooting();

        PanelSnapshot.KernelInfo kernelInfo = snapshot.kernel();
        List<Row> rows = new ArrayList<>();
        rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.panel.kernel_installed_label", kernelInfo.name()), x, y + 2,
                kernelInfo.preset() ? COLOR_PRESET : COLOR_ACCENT));
        rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.terminal.kernel_settings",
                kernelInfo.schedule(), kernelInfo.gc(), kernelInfo.verify(), kernelInfo.rateLimit()), x + 4, y + 2, COLOR_TEXT));
        rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.terminal.kernel_hooks",
                kernelInfo.hooks().isEmpty() ? "-" : String.join(", ", kernelInfo.hooks())), x + 4, y + 2, COLOR_TEXT));
        rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.terminal.kernel_cost",
                formatMb(kernelInfo.ramMb()), kernelInfo.tops()), x + 4, y + 2, COLOR_DIM));

        rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.panel.flaws"), x, y + 4, COLOR_ACCENT));
        if (snapshot.flaws().isEmpty()) {
            rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.terminal.none"), x + 4, y + 2, COLOR_DIM));
        }
        for (PanelSnapshot.FlawRow flaw : snapshot.flaws()) {
            rows.add((g, y, hover) -> {
                Component severity = Component.translatable("mhm2027.flaw." + flaw.severity());
                Component line = flaw.approaching()
                        ? Component.translatable("mhm2027.terminal.flaw_close", flaw.className(), severity, flaw.cause())
                        : Component.translatable("mhm2027.terminal.flaw_line", flaw.className(), severity, flaw.cause());
                text(g, clip(line, panelWidth - 2 * PADDING - 8), x + 4, y + 2, flawColor(flaw.severity(), flaw.approaching()));
            });
        }

        rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.panel.kernels_in_storage"), x, y + 4, COLOR_ACCENT));
        List<PanelSnapshot.ScriptRow> kernels = new ArrayList<>();
        kernels.add(new PanelSnapshot.ScriptRow(GatewayData.DEFAULT_KERNEL, GatewayData.DEFAULT_KERNEL, true, false));
        snapshot.scripts().stream().filter(PanelSnapshot.ScriptRow::kernel)
                .filter(script -> !script.id().equals(GatewayData.DEFAULT_KERNEL)).forEach(kernels::add);
        for (PanelSnapshot.ScriptRow kernel : kernels) {
            boolean builtin = kernel.id().equals(GatewayData.DEFAULT_KERNEL);
            boolean installed = kernel.id().equals(kernelInfo.id());
            rows.add((g, y, hover) -> {
                if (builtin) {
                    text(g, Component.translatable("mhm2027.panel.builtin", kernel.name()), x + 4, y + 2, installed ? COLOR_OK : COLOR_TEXT);
                } else {
                    storageLabel(g, kernel, x + 4, y + 2);
                }
                if (installed) {
                    Component tag = Component.translatable("mhm2027.panel.installed");
                    text(g, tag, right - font.width(tag), y + 2, COLOR_OK);
                } else {
                    button(g, right - 44, y, 44, Component.translatable("mhm2027.panel.install"), !rebooting, hover,
                            () -> send(PanelRequest.Action.INSTALL_KERNEL, kernel.id()));
                }
                if (!builtin && !kernel.preset()) {
                    button(g, right - 86, y, 38, Component.translatable("mhm2027.panel.burn"), true, hover,
                            () -> send(PanelRequest.Action.BURN, kernel.id()));
                    button(g, right - 122, y, 34, Component.translatable("mhm2027.panel.edit"), true, hover,
                            () -> minecraft.setScreen(new CodeEditorScreen(kernel.id())));
                }
            });
        }
        rows.add((g, y, hover) -> text(g, Component.translatable("mhm2027.panel.install_note"), x, y + 4, COLOR_DIM));

        drawRows(graphics, rows, contentTop, contentBottom, mouseX, mouseY);
    }

    private void drawLog(GuiGraphicsExtractor graphics, PanelSnapshot snapshot, int contentTop, int contentBottom) {
        int x = left + PADDING;
        int visible = (contentBottom - contentTop) / 10;
        List<String> log = snapshot.log();
        if (log.isEmpty()) {
            text(graphics, Component.translatable("mhm2027.terminal.none"), x, contentTop, COLOR_DIM);
            return;
        }
        // Newest at the bottom; scrolling moves back in time.
        scroll = Math.min(scroll, Math.max(0, log.size() - visible));
        int end = log.size() - scroll;
        int start = Math.max(0, end - visible);
        int y = contentTop;
        for (String line : log.subList(start, end)) {
            int color = line.contains("crashed") || line.contains("PANIC") || line.contains("failed") ? COLOR_ALERT : COLOR_TEXT;
            text(graphics, Component.literal(font.plainSubstrByWidth(line, panelWidth - 2 * PADDING, false)), x, y, color);
            y += 10;
        }
    }

    // endregion

    // region Widgets

    /** Something drawn on one row of a scrolling list, at the given y. */
    @FunctionalInterface
    private interface Row {
        void draw(GuiGraphicsExtractor graphics, int y, Hover hover);
    }

    /** Where the mouse is, for hover effects inside rows. */
    private record Hover(int mouseX, int mouseY) {
    }

    private void drawRows(GuiGraphicsExtractor graphics, List<Row> rows, int listTop, int listBottom, int mouseX, int mouseY) {
        int visible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        Hover hover = mouseOver(mouseX, mouseY);
        int y = listTop;
        for (Row row : rows.subList(scroll, Math.min(rows.size(), scroll + visible))) {
            if (inside(mouseX, mouseY, left + PADDING, y, panelWidth - 2 * PADDING, ROW_HEIGHT)) {
                graphics.fill(left + PADDING, y, left + panelWidth - PADDING, y + ROW_HEIGHT, COLOR_ROW_HOVER);
            }
            row.draw(graphics, y, hover);
            y += ROW_HEIGHT;
        }
        if (rows.size() > visible) {
            // A thin scrollbar in the right margin.
            int trackX = left + panelWidth - PADDING + 3;
            int trackHeight = visible * ROW_HEIGHT;
            int thumbHeight = Math.max(6, trackHeight * visible / rows.size());
            int thumbY = listTop + (trackHeight - thumbHeight) * scroll / (rows.size() - visible);
            graphics.fill(trackX, listTop, trackX + 2, listTop + trackHeight, COLOR_BAR_BACK);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, COLOR_BORDER);
        }
    }

    /** A storage entry's name: own files as name.lua, presets in gold with a tag. */
    private void storageLabel(GuiGraphicsExtractor graphics, PanelSnapshot.ScriptRow script, int x, int y) {
        if (script.preset()) {
            text(graphics, Component.literal(script.name()), x, y, COLOR_PRESET);
            text(graphics, Component.translatable("mhm2027.panel.preset_tag"), x + font.width(script.name()) + 4, y, COLOR_DIM);
        } else {
            text(graphics, Component.literal(script.name() + ".lua"), x, y, COLOR_TEXT);
        }
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, boolean enabled, Hover hover, Runnable action) {
        boolean hovered = enabled && inside(hover.mouseX(), hover.mouseY(), x, y, width, BUTTON_HEIGHT);
        graphics.fill(x, y, x + width, y + BUTTON_HEIGHT, !enabled ? COLOR_BUTTON_DISABLED : hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        outline(graphics, x, y, width, BUTTON_HEIGHT, enabled ? COLOR_BORDER : COLOR_BUTTON);
        text(graphics, label, x + (width - font.width(label)) / 2, y + 2, enabled ? COLOR_TEXT : COLOR_DIM);
        if (enabled) {
            hits.add(new Hit(x, y, width, BUTTON_HEIGHT, action));
        }
    }

    private int labelledBar(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, Component value, int valueColor,
                            double[] fractions, int[] colors) {
        text(graphics, label, x, y, COLOR_DIM);
        text(graphics, value, x + width - font.width(value), y, valueColor);
        int barY = y + 10;
        graphics.fill(x, barY, x + width, barY + 4, COLOR_BAR_BACK);
        int filled = 0;
        for (int i = 0; i < fractions.length; i++) {
            int segment = (int) Math.round(width * Math.clamp(fractions[i], 0.0, 1.0));
            segment = Math.min(segment, width - filled);
            if (segment > 0) {
                graphics.fill(x + filled, barY, x + filled + segment, barY + 4, colors[i]);
                filled += segment;
            }
        }
        return barY + 10;
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
        return Component.literal(font.plainSubstrByWidth(plain, width - font.width("..."), false) + "...");
    }

    // endregion

    // region Input

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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (toggleKey.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private record Hit(int x, int y, int width, int height, Runnable action) {
    }

    private static boolean inside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    private static Hover mouseOver(int mouseX, int mouseY) {
        return new Hover(mouseX, mouseY);
    }

    // endregion

    private static void send(PanelRequest.Action action, String argument) {
        ClientPacketDistributor.sendToServer(new PanelRequest(action, argument));
    }

    /** In singleplayer the storage folder is on this computer, so the panel can open it. */
    private Path localFolder() {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return null;
        }
        return ScriptStore.folder(server, minecraft.player.getUUID());
    }

    private static Component stateLabel(String state) {
        String key = state.startsWith("paused") ? "paused" : state;
        return Component.translatable("mhm2027.panel.state." + key);
    }

    private static int flawColor(String severity, boolean approaching) {
        if (approaching) {
            return COLOR_DIM;
        }
        return switch (severity) {
            case "critical", "major" -> COLOR_ALERT;
            case "minor" -> COLOR_WARN;
            default -> COLOR_TEXT;
        };
    }

    private static int stateColor(String state) {
        if (state.startsWith("paused")) {
            return COLOR_WARN;
        }
        return switch (state) {
            case "running" -> COLOR_OK;
            case "sleeping" -> COLOR_DIM;
            default -> COLOR_TEXT;
        };
    }

    private static String format(float value) {
        return value >= 10 ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
