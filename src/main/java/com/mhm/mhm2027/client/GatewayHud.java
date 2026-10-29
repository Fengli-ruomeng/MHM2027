package com.mhm.mhm2027.client;

import static com.mhm.mhm2027.gateway.GatewayData.formatMb;

import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.GatewayTier;
import com.mhm.mhm2027.gateway.ModAttachments;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * A compact gateway readout in the top-left corner: level, RAM (with Leak), Buffer, Heat,
 * and an icon while a dynamic flaw is active. Hidden for Null gateways, with F1, and while
 * the F3 debug screen is open.
 */
public class GatewayHud implements GuiLayer {
    private static final int X = 4;
    private static final int Y = 4;
    private static final int BAR_WIDTH = 82;
    private static final int BAR_HEIGHT = 3;
    private static final int LINE_HEIGHT = 10;

    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_TITLE = 0xFF55FFFF;
    private static final int COLOR_WARN = 0xFFFFC040;
    private static final int COLOR_ALERT = 0xFFFF5555;
    private static final int COLOR_BAR_BACK = 0x80000000;
    private static final int COLOR_LEAK = 0xFFB040FF;
    private static final int COLOR_BUFFER = 0xFF40C0FF;
    private static final int COLOR_HEAT = 0xFFFF8030;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.gui.getDebugOverlay().showDebugScreen()) {
            return;
        }
        GatewayData data = player.getData(ModAttachments.GATEWAY);
        GatewayTier tier = data.tier();
        if (tier.isNull()) {
            return;
        }

        Font font = minecraft.font;
        int y = Y;

        graphics.text(font, Component.translatable("mhm2027.hud.title", tier.label(), data.freeTops(), tier.tops()), X, y, COLOR_TITLE);
        y += LINE_HEIGHT;

        // RAM bar shows what is taken: kernel and programs plus Leak.
        double ramMb = tier.ramMb();
        double usedMb = ramMb - data.freeRamMb();
        boolean outOfMemory = data.isOutOfMemory();
        graphics.text(font, Component.translatable("mhm2027.hud.ram", formatMb(usedMb), formatMb(ramMb)), X, y, outOfMemory ? COLOR_ALERT : COLOR_TEXT);
        y += LINE_HEIGHT - 1;
        bar(graphics, y, usedMb / ramMb, outOfMemory ? COLOR_ALERT : COLOR_LEAK);
        y += BAR_HEIGHT + 3;

        double bufferMax = tier.bufferMb();
        graphics.text(font, Component.translatable("mhm2027.hud.buffer", formatMb(data.bufferMb()), formatMb(bufferMax)), X, y, COLOR_TEXT);
        y += LINE_HEIGHT - 1;
        bar(graphics, y, data.bufferMb() / bufferMax, COLOR_BUFFER);
        y += BAR_HEIGHT + 3;

        if (data.heat() > 0) {
            graphics.text(font, Component.translatable("mhm2027.hud.heat", Math.round(data.heat())), X, y, COLOR_TEXT);
            y += LINE_HEIGHT - 1;
            bar(graphics, y, data.heat() / GatewayData.MAX_HEAT, COLOR_HEAT);
            y += BAR_HEIGHT;
        }
        if (!data.dynamicFlaws().isEmpty()) {
            y += 3;
            boolean severe = data.dynamicFlaws().stream().anyMatch(flaw -> !flaw.severity().equals("minor"));
            int color = severe ? COLOR_ALERT : COLOR_WARN;
            String names = String.join(", ", data.dynamicFlaws().stream().map(GatewayData.FlawMark::className).toList());
            graphics.fill(X, y + 2, X + 5, y + 7, color);
            graphics.text(font, Component.translatable("mhm2027.hud.flaws", names), X + 8, y, color);
        }
    }

    private static void bar(GuiGraphicsExtractor graphics, int y, double fraction, int color) {
        int filled = (int) Math.round(BAR_WIDTH * Math.clamp(fraction, 0.0, 1.0));
        graphics.fill(X, y, X + BAR_WIDTH, y + BAR_HEIGHT, COLOR_BAR_BACK);
        if (filled > 0) {
            graphics.fill(X, y, X + filled, y + BAR_HEIGHT, color);
        }
    }
}
