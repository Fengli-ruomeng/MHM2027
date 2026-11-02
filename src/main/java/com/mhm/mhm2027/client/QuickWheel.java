package com.mhm.mhm2027.client;

import java.util.List;
import java.util.Locale;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.Mhm2027Client;
import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.hud.HudEvent;
import com.mhm.mhm2027.hud.HudTargets;
import com.mhm.mhm2027.network.PanelRequest;
import com.mhm.mhm2027.network.WheelInfo;
import com.mhm.mhm2027.terminal.Terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The quick wheel: hold V, point at one of eight shortcuts and let go, or press its number to
 * fire it at once (as many as you like while V is held). Right-click cancels. You keep moving
 * while it's open; only the camera stops, so the mouse can point. Each shortcut is an Edge
 * command, run as if typed in the terminal.
 */
@EventBusSubscriber(modid = Mhm2027.MODID, value = Dist.CLIENT)
public final class QuickWheel {
    private static final int SLOTS = WheelMath.SLOTS;
    private static final double DEAD_ZONE = 10;
    /** How far the pointer can travel from the centre, in GUI pixels. */
    private static final double REACH = 50;
    private static final int RADIUS = 66;
    private static final int SLOT_WIDTH = 58;
    private static final int SLOT_HEIGHT = 18;
    private static final int INFO_WIDTH = 150;
    private static final long OPEN_MILLIS = 120;
    private static final long FIRED_MILLIS = 250;
    private static final int SHADE = 0x50000000;
    private static final int PANEL = 0xE00B0F17;
    private static final int RULE = 0xFF2C3550;
    /** Sensitivity that makes the camera's turn exactly zero: (s * 0.6 + 0.2) is 0. */
    private static final double FROZEN = -1.0 / 3.0;

    private static boolean open;
    /** A number fired a shortcut, or the wheel was cancelled: letting go of V does nothing more. */
    private static boolean spent;
    /** Closed while V was still held: it opens again only after V is let go. */
    private static boolean blocked;
    private static long openedAt;
    private static double pointerX;
    private static double pointerY;
    private static double lastMouseX;
    private static double lastMouseY;
    private static int firedSlot = -1;
    private static long firedAt;
    private static List<WheelInfo.Row> info = List.of();

    private QuickWheel() {
    }

    static void accept(WheelInfo next) {
        info = next.rows();
    }

    static void reset() {
        open = false;
        info = List.of();
    }

    // region Input

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        while (Mhm2027Client.QUICK_WHEEL.consumeClick()) {
            // Only whether it's held matters.
        }
        boolean held = Mhm2027Client.QUICK_WHEEL.isDown();
        if (!held) {
            blocked = false;
        }
        boolean usable = player != null && minecraft.screen == null && !player.getData(ModAttachments.GATEWAY).tier().isNull()
                && HudFeed.power() == Terminal.Power.ON;
        if (!open) {
            if (held && usable && !blocked) {
                open(minecraft);
            }
            return;
        }
        if (!usable) {
            close(minecraft);
            blocked = true;
            return;
        }
        // The number keys fire at once, and keep the hotbar from moving.
        for (int slot = 0; slot < SLOTS; slot++) {
            while (minecraft.options.keyHotbarSlots[slot].consumeClick()) {
                fire(minecraft, slot);
            }
        }
        if (!held) {
            int slot = WheelMath.slotAt(pointerX, pointerY, DEAD_ZONE);
            boolean fireOnRelease = !spent && slot >= 0;
            // Close first, so Edge (if the shortcut opens it) starts watching after the wheel stops.
            close(minecraft);
            if (fireOnRelease) {
                fire(minecraft, slot);
            }
        }
    }

    /** While the wheel is open the camera stays put, so the mouse can point. */
    @SubscribeEvent
    static void onTurn(CalculatePlayerTurnEvent event) {
        if (open) {
            event.setMouseSensitivity(FROZEN);
            event.setCinematicCameraEnabled(false);
        }
    }

    /** No swinging or using while the wheel is open; a right-click cancels it. */
    @SubscribeEvent
    static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!open) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isUseItem()) {
            spent = true;
            close(Minecraft.getInstance());
            blocked = true;
        }
    }

    /** The hotbar doesn't scroll under the wheel. */
    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (open) {
            event.setCanceled(true);
        }
    }

    private static void open(Minecraft minecraft) {
        open = true;
        spent = false;
        openedAt = Util.getMillis();
        pointerX = 0;
        pointerY = 0;
        lastMouseX = minecraft.mouseHandler.xpos();
        lastMouseY = minecraft.mouseHandler.ypos();
        // Watch the terminal while the wheel is up, so a shortcut finds your programs as they are now.
        ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.OPEN));
        ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.WHEEL_INFO));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.3F));
    }

    private static void close(Minecraft minecraft) {
        open = false;
        // If a shortcut opened Edge, Edge is watching now; leave that be.
        if (minecraft.screen == null) {
            ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.CLOSE));
        }
    }

    private static void fire(Minecraft minecraft, int slot) {
        if (minecraft.player == null) {
            return;
        }
        spent = true;
        firedSlot = slot;
        firedAt = Util.getMillis();
        String command = minecraft.player.getData(ModAttachments.WHEEL).slots().get(slot);
        if (command.isBlank()) {
            HudFeed.accept(new HudEvent(HudEvent.Kind.INFO, "WHEEL // " + (slot + 1), Component.literal("Slot " + (slot + 1) + " is empty"),
                    Component.literal("In Edge: wheel set " + (slot + 1) + " <command>"), "wheel", 60, false));
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.6F));
        EdgeDesktopScreen.shortcut(command, "WHEEL // " + WheelMath.label(command));
    }

    // endregion

    // region Drawing

    /** Over the rest of the HUD while V is held. */
    static void draw(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, int width, int height, long now) {
        LocalPlayer player = minecraft.player;
        if (!open || player == null) {
            return;
        }
        followMouse(minecraft);
        List<String> slots = player.getData(ModAttachments.WHEEL).slots();
        int hovered = WheelMath.slotAt(pointerX, pointerY, DEAD_ZONE);
        float grow = Math.clamp((now - openedAt) / (float) OPEN_MILLIS, 0, 1);
        int centerX = width / 2;
        int centerY = height / 2;
        graphics.fill(0, 0, width, height, SHADE);

        // The pointer: a dotted line from the centre to where the mouse has gone.
        double length = Math.hypot(pointerX, pointerY);
        for (int step = 4; step < length; step += 3) {
            int dotX = centerX + (int) Math.round(pointerX * step / length);
            int dotY = centerY + (int) Math.round(pointerY * step / length);
            graphics.fill(dotX, dotY, dotX + 1, dotY + 1, hovered >= 0 ? EdgeShell.MAGENTA : EdgeShell.GREY);
        }
        graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, EdgeShell.TEXT);

        for (int slot = 0; slot < SLOTS; slot++) {
            double[] at = WheelMath.position(slot, RADIUS * (0.6 + 0.4 * grow));
            int x = centerX + (int) Math.round(at[0]) - SLOT_WIDTH / 2;
            int y = centerY + (int) Math.round(at[1]) - SLOT_HEIGHT / 2;
            slot(graphics, font, slot, slots.get(slot), x, y, slot == hovered, slot == firedSlot && now - firedAt < FIRED_MILLIS);
        }
        infoPanel(graphics, font, player, slots, hovered, centerX, centerY, width, height);
        String hint = "let go to run · 1-8 fire now · right-click cancels";
        graphics.text(font, hint, centerX - font.width(hint) / 2, centerY + RADIUS + SLOT_HEIGHT / 2 + 6, EdgeShell.GREY, false);
    }

    /** The mouse moves the pointer instead of the camera, kept within reach of the centre. */
    private static void followMouse(Minecraft minecraft) {
        double scale = minecraft.getWindow().getGuiScale();
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        pointerX += (mouseX - lastMouseX) / scale;
        pointerY += (mouseY - lastMouseY) / scale;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        double length = Math.hypot(pointerX, pointerY);
        if (length > REACH) {
            pointerX *= REACH / length;
            pointerY *= REACH / length;
        }
    }

    private static void slot(GuiGraphicsExtractor graphics, Font font, int slot, String command, int x, int y, boolean hovered, boolean fired) {
        boolean empty = command.isBlank();
        int border = fired ? EdgeShell.TEXT : hovered ? EdgeShell.MAGENTA : empty ? RULE : EdgeShell.GREY;
        int fill = fired ? EdgeShell.TEXT : hovered ? EdgeShell.MAGENTA : PANEL;
        LensHud.panel(graphics, x, y, SLOT_WIDTH, SLOT_HEIGHT, border, fill);
        int text = hovered || fired ? 0xFF0B0F17 : empty ? EdgeShell.GREY : EdgeShell.TEXT;
        int sub = hovered || fired ? 0xFF2A0A16 : EdgeShell.DIM;
        String label = font.plainSubstrByWidth(WheelMath.label(command), SLOT_WIDTH - 12, false);
        graphics.text(font, String.valueOf(slot + 1), x + 3, y + 1, hovered || fired ? sub : EdgeShell.GREY, false);
        graphics.text(font, label, x + (SLOT_WIDTH - font.width(label)) / 2 + 2, y + 1, text, false);
        String cost = empty ? "wheel set " + (slot + 1) : slot < info.size() && !info.get(slot).cost().isEmpty() ? info.get(slot).cost()
                : command.toLowerCase(Locale.ROOT).startsWith("run ") ? "" : "command";
        cost = font.plainSubstrByWidth(cost, SLOT_WIDTH - 6, false);
        graphics.text(font, cost, x + (SLOT_WIDTH - font.width(cost)) / 2, y + 9, sub, false);
    }

    /** Beside the wheel: what the pointed-at shortcut does, and what it would hit. */
    private static void infoPanel(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, List<String> slots, int hovered,
                                  int centerX, int centerY, int width, int height) {
        int x = centerX + RADIUS + SLOT_WIDTH / 2 + 10;
        int panelWidth = Math.min(INFO_WIDTH, width - x - 4);
        if (panelWidth < 90) {
            return;
        }
        int y = centerY - 40;
        int lineY = y + 5;
        LensHud.panel(graphics, x, y, panelWidth, 80, RULE, PANEL);
        int textWidth = panelWidth - 10;
        if (hovered < 0) {
            graphics.text(font, "point at a shortcut", x + 5, lineY, EdgeShell.GREY, false);
            lineY += 10;
        } else {
            String command = slots.get(hovered);
            WheelInfo.Row row = hovered < info.size() ? info.get(hovered) : WheelInfo.Row.NONE;
            graphics.text(font, font.plainSubstrByWidth("◆ " + WheelMath.label(command), textWidth, false), x + 5, lineY, EdgeShell.MAGENTA, false);
            lineY += 10;
            graphics.text(font, font.plainSubstrByWidth(command.isBlank() ? "empty: wheel set " + (hovered + 1) + " <command>" : command, textWidth, false),
                    x + 5, lineY, EdgeShell.DIM, false);
            lineY += 10;
            if (!row.detail().isEmpty()) {
                graphics.text(font, font.plainSubstrByWidth(row.detail(), textWidth, false), x + 5, lineY, EdgeShell.TEXT, false);
                lineY += 10;
            }
            if (!row.description().isEmpty()) {
                for (FormattedCharSequence line : font.split(Component.literal(row.description()), textWidth).stream().limit(2).toList()) {
                    graphics.text(font, line, x + 5, lineY, EdgeShell.DIM, false);
                    lineY += 9;
                }
            }
        }
        // What it would land on, and what you have to spend.
        HudTargets.Seen target = TargetCallout.current();
        String on = target == null ? "no target" : target.name() + " " + (target.level() < 0 ? "LV?" : "LV" + target.level());
        GatewayData data = player.getData(ModAttachments.GATEWAY);
        graphics.text(font, font.plainSubstrByWidth(on, textWidth, false), x + 5, y + 60, target == null ? EdgeShell.GREY : EdgeShell.CYAN, false);
        graphics.text(font, "TOPS free " + data.freeTops(), x + 5, y + 70, EdgeShell.DIM, false);
    }

    // endregion
}
