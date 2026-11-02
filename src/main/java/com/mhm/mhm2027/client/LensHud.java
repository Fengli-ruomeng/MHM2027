package com.mhm.mhm2027.client;

import java.util.List;
import java.util.Locale;
import java.util.Random;

import com.mhm.mhm2027.ClientConfig;
import com.mhm.mhm2027.Mhm2027Client;
import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.GatewayTier;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.hud.HudBoot;
import com.mhm.mhm2027.hud.HudEvent;
import com.mhm.mhm2027.hud.HudState;
import com.mhm.mhm2027.terminal.Terminal;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Edge Lens, the cyberpunk HUD.
 * <ul>
 * <li>Left edge: Buffer and RAM as two slim vertical bars (Heat joins them while you run warm).
 * Numbers show for a moment when they change, and in full while the details key is held.</li>
 * <li>Beside the hotbar: the socket loadout as slots.</li>
 * <li>Right side: event cards in place of chat and action-bar messages; their text decrypts in.</li>
 * <li>Upper third: scrolling warning banners for critical moments.</li>
 * <li>Over everything: glitches when you're hit or attacked, and the boot screen after a reboot.</li>
 * </ul>
 * Hidden with F1 and while the F3 screen is open.
 */
public class LensHud implements GuiLayer {
    private static final int PANEL = 0xE00B0F17;
    private static final int TEXT = 0xFFF2F6FF;
    private static final int DIM = 0xFFB9C3D6;
    private static final int CELL_OFF = 0x40F2F6FF;
    private static final int CYAN = 0xFF3FE8FF;
    private static final int ORANGE = 0xFFFFB347;
    private static final int MAGENTA = 0xFFFF2E7E;
    private static final int VIOLET = 0xFF8B5CFF;
    private static final int GREY = 0xFF5B6478;
    private static final int EMPTY = 0xFF2C3550;
    private static final int RAM_PROGRAMS = 0xFFDDE6F5;
    private static final int CHIP_TEXT = 0xFF0B0F17;

    private static final int MARGIN = 4;
    private static final int CUT = 4;
    private static final int BAR_CELLS = 14;
    private static final int CELL_HEIGHT = 5;
    private static final int BAR_WIDTH = 5;
    private static final int BAR_GAP = 4;
    private static final int DETAILS_WIDTH = 176;
    private static final int CELLS = 10;
    private static final int CELL_WIDTH = 5;
    private static final int SLOT = 20;
    private static final int CARD_WIDTH = 176;
    private static final long SLIDE_MILLIS = 180;
    private static final long FADE_MILLIS = 400;
    private static final long DECRYPT_MILLIS = 280;
    private static final long POPUP_MILLIS = 2500;
    private static final long BANNER_OPEN_MILLIS = 220;
    private static final int BANNER_HEIGHT = 28;
    private static final int TAPE_HEIGHT = 10;
    private static final int STRIPES = 14;
    private static final float TITLE_SCALE = 1.25f;
    private static final float WARM_HEAT = 5;
    private static final int BOOT_SHADE = 0xEB05070C;
    /** Shares of the boot: the log streams in first, the logo follows, then the screen clears and the HUD returns. */
    private static final float BOOT_LOG_START = 0.05f;
    private static final float BOOT_LOG_SPAN = 0.6f;
    private static final float BOOT_LOGO = 0.45f;
    private static final float BOOT_CLEAR = 0.8f;
    private static final float BARS_RETURN = 0.82f;
    private static final float SLOTS_RETURN = 0.88f;
    private static final float CARDS_RETURN = 0.94f;
    private static final float LOGO_SCALE = 4f;

    private static int lastHurtTime;
    private static double lastBuffer = -1;
    private static double lastUsed = -1;
    private static long popupUntil;
    private static long outOfMemorySince;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.gui.getDebugOverlay().showDebugScreen()) {
            return;
        }
        GatewayData data = player.getData(ModAttachments.GATEWAY);
        if (data.tier().isNull()) {
            // The Lens is part of the gateway, so a Null player has none.
            return;
        }
        long now = Util.getMillis();
        if (player.hurtTime > lastHurtTime) {
            HudFeed.glitch(0.7f);
        }
        lastHurtTime = player.hurtTime;
        float glitch = ClientConfig.GLITCH_EFFECTS.get() ? HudFeed.glitch(now) * ClientConfig.GLITCH_STRENGTH.get().floatValue() : 0;
        int jitter = glitch > 0.05f ? Math.round((new Random(now / 50).nextFloat() * 2 - 1) * glitch * 3) : 0;

        Font font = minecraft.font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (HudFeed.power() == Terminal.Power.OFF) {
            // Off: no HUD at all, just how to switch it on.
            Component hint = Component.translatable("mhm2027.hud.offline", Mhm2027Client.OPEN_PANEL.getTranslatedKeyMessage());
            graphics.text(font, hint, MARGIN, height - 12, GREY, false);
            if (glitch > 0.02f) {
                glitchOverlay(graphics, font, width, height, now, glitch);
            }
            return;
        }
        // While booting, the HUD is gone; it comes back piece by piece as the boot finishes.
        float boot = HudFeed.boot(now);
        if (boot < 0 || boot >= BARS_RETURN) {
            bars(graphics, font, data, height, now, jitter);
        }
        if (boot < 0 || boot >= SLOTS_RETURN) {
            slots(graphics, font, width, height);
        }
        if (boot < 0 || boot >= CARDS_RETURN) {
            TargetCallout.draw(graphics, font, minecraft, width, height, deltaTracker.getGameTimeDeltaPartialTick(true), now);
            TaskList.draw(graphics, font, width, height, now);
            flashCode(graphics, font, width, height, now);
            cards(graphics, font, player, width, now, glitch, jitter);
            if (ClientConfig.BANNERS.get()) {
                banner(graphics, font, data, width, height, now);
            }
        }
        if (boot >= 0 && !(minecraft.screen instanceof EdgeBootScreen)) {
            bootScreen(graphics, font, width, height, now, boot);
        }
        if (glitch > 0.02f) {
            glitchOverlay(graphics, font, width, height, now, glitch);
        }
        QuickWheel.draw(graphics, font, minecraft, width, height, now);
    }

    // region Vital bars

    private static void bars(GuiGraphicsExtractor graphics, Font font, GatewayData data, int height, long now, int jitter) {
        GatewayTier tier = data.tier();
        double ram = tier.ramMb();
        double used = ram - data.freeRamMb();
        if (lastBuffer >= 0 && (Math.abs(data.bufferMb() - lastBuffer) >= tier.bufferMb() * 0.01 || Math.abs(used - lastUsed) >= ram * 0.02)) {
            popupUntil = now + POPUP_MILLIS;
        }
        lastBuffer = data.bufferMb();
        lastUsed = used;

        boolean warm = data.heat() >= WARM_HEAT;
        int count = warm ? 3 : 2;
        int barHeight = BAR_CELLS * (CELL_HEIGHT + 1) - 1;
        int innerWidth = count * BAR_WIDTH + (count - 1) * BAR_GAP;
        int x = 8 + jitter;
        int y = height / 2 - barHeight / 2 - 8;
        int panelBottom = y + barHeight + 14;
        panel(graphics, x - 4, y - 5, innerWidth + 8, panelBottom - (y - 5), VIOLET, PANEL);

        boolean outOfMemory = data.isOutOfMemory();
        vertical(graphics, x, y, barHeight, data.bufferMb() / tier.bufferMb(), CYAN, 0, 0);
        vertical(graphics, x + BAR_WIDTH + BAR_GAP, y, barHeight, Math.min(data.programRamMb(), ram) / ram,
                outOfMemory ? MAGENTA : RAM_PROGRAMS, Math.min(data.leakMb(), ram) / ram, VIOLET);
        label(graphics, font, "B", x, y + barHeight + 4, DIM);
        label(graphics, font, "R", x + BAR_WIDTH + BAR_GAP, y + barHeight + 4, outOfMemory ? MAGENTA : DIM);
        if (warm) {
            int heatX = x + 2 * (BAR_WIDTH + BAR_GAP);
            vertical(graphics, heatX, y, barHeight, data.heat() / GatewayData.MAX_HEAT, data.heat() >= 70 ? MAGENTA : ORANGE, 0, 0);
            label(graphics, font, "H", heatX, y + barHeight + 4, data.heat() >= 70 ? MAGENTA : DIM);
        }

        int besideX = x + innerWidth + 10;
        if (Mhm2027Client.LENS_DETAILS.isDown()) {
            details(graphics, font, data, besideX, y - 5);
        } else if (now < popupUntil) {
            float alpha = Math.clamp((popupUntil - now) / (float) FADE_MILLIS, 0f, 1f);
            graphics.text(font, "BUF " + size(data.bufferMb(), tier.bufferMb()), besideX, y + 2, fade(CYAN, alpha), false);
            graphics.text(font, "RAM " + size(used, ram), besideX, y + 13, fade(outOfMemory ? MAGENTA : TEXT, alpha), false);
        }

        // Things worth knowing without a key: low TOPS, and flaws that come and go.
        int below = panelBottom + 4;
        if (data.freeTops() <= 2) {
            graphics.text(font, "TOPS " + data.freeTops(), x - 3, below, MAGENTA, false);
            below += 11;
        }
        for (GatewayData.FlawMark flaw : data.dynamicFlaws()) {
            chip(graphics, font, flaw, x - 4, below);
            below += 12;
        }
    }

    /** One vertical bar of cells, filling from the bottom: a first share, then a second in another color. */
    private static void vertical(GuiGraphicsExtractor graphics, int x, int y, int barHeight, double share, int color, double extra, int extraColor) {
        int lit = (int) Math.round(Math.clamp(share, 0, 1) * BAR_CELLS);
        int lit2 = Math.min(BAR_CELLS, (int) Math.round(Math.clamp(share + extra, 0, 1) * BAR_CELLS));
        for (int i = 0; i < BAR_CELLS; i++) {
            int cellY = y + barHeight - (i + 1) * (CELL_HEIGHT + 1) + 1;
            graphics.fill(x, cellY, x + BAR_WIDTH, cellY + CELL_HEIGHT, i < lit ? color : i < lit2 ? extraColor : CELL_OFF);
        }
    }

    private static void label(GuiGraphicsExtractor graphics, Font font, String text, int barX, int y, int color) {
        graphics.text(font, text, centered(font, text, barX, BAR_WIDTH), y, color, false);
    }

    /** Where text starts to sit centered over a box. A glyph's last pixel column is spacing, so it doesn't count. */
    private static int centered(Font font, String text, int x, int width) {
        return x + (width - (font.width(text) - 1)) / 2;
    }

    /** The full numbers, beside the bars while the details key is held. */
    private static void details(GuiGraphicsExtractor graphics, Font font, GatewayData data, int x, int y) {
        GatewayTier tier = data.tier();
        int height = 5 + 9 + 3 + 3 * 11 + 3;
        panel(graphics, x, y, DETAILS_WIDTH, height, VIOLET, PANEL);
        graphics.text(font, "◆ " + tier.label() + " // " + kernelName(data.kernel()), x + 6, y + 5, MAGENTA, false);
        int row = y + 17;
        int cellsX = x + 6 + font.width("BUFFER") + 6;
        int valueX = cellsX + CELLS * (CELL_WIDTH + 1) + 5;

        graphics.text(font, "BUFFER", x + 6, row, DIM, false);
        cells(graphics, cellsX, row, data.bufferMb() / tier.bufferMb(), CYAN, 0, 0);
        graphics.text(font, size(data.bufferMb(), tier.bufferMb()), valueX, row, TEXT, false);
        row += 11;

        double ram = tier.ramMb();
        boolean outOfMemory = data.isOutOfMemory();
        graphics.text(font, "RAM", x + 6, row, outOfMemory ? MAGENTA : DIM, false);
        cells(graphics, cellsX, row, Math.min(data.programRamMb(), ram) / ram, outOfMemory ? MAGENTA : RAM_PROGRAMS,
                Math.min(data.leakMb(), ram) / ram, VIOLET);
        graphics.text(font, size(ram - data.freeRamMb(), ram), valueX, row, outOfMemory ? MAGENTA : TEXT, false);
        row += 11;

        int heat = (int) Math.round(data.heat());
        String tops = data.freeTops() + "/" + tier.tops();
        int topsX = x + 6 + font.width("TOPS") + 5;
        graphics.text(font, "TOPS", x + 6, row, DIM, false);
        graphics.text(font, tops, topsX, row, data.freeTops() <= 1 ? MAGENTA : TEXT, false);
        int heatX = topsX + font.width(tops) + 10;
        graphics.text(font, "HEAT", heatX, row, DIM, false);
        graphics.text(font, String.valueOf(heat), heatX + font.width("HEAT") + 4, row, heat >= 70 ? ORANGE : TEXT, false);
    }

    /** A horizontal bar of ten cells, for the details panel. */
    private static void cells(GuiGraphicsExtractor graphics, int x, int y, double share, int color, double extra, int extraColor) {
        int lit = (int) Math.round(Math.clamp(share, 0, 1) * CELLS);
        int lit2 = Math.min(CELLS, (int) Math.round(Math.clamp(share + extra, 0, 1) * CELLS));
        for (int i = 0; i < CELLS; i++) {
            int cellX = x + i * (CELL_WIDTH + 1);
            graphics.fill(cellX, y + 1, cellX + CELL_WIDTH, y + 7, i < lit ? color : i < lit2 ? extraColor : CELL_OFF);
        }
    }

    private static void chip(GuiGraphicsExtractor graphics, Font font, GatewayData.FlawMark flaw, int x, int y) {
        int width = font.width(flaw.className()) + 6;
        graphics.fill(x, y, x + width, y + 10, flaw.severity().equals("minor") ? ORANGE : MAGENTA);
        graphics.text(font, flaw.className(), x + 3, y + 1, CHIP_TEXT, false);
    }

    /** "8.4/10G" or "412/768": gigabytes once the total reaches one. */
    private static String size(double mb, double totalMb) {
        if (totalMb >= 1024) {
            return trim(mb / 1024) + "/" + trim(totalMb / 1024) + "G";
        }
        return Math.round(mb) + "/" + Math.round(totalMb);
    }

    private static String trim(double gb) {
        return gb >= 10 || gb == Math.rint(gb) ? String.valueOf(Math.round(gb)) : String.format(Locale.ROOT, "%.1f", gb);
    }

    /** "Baseline", a file name, or a preset's id without its namespace. */
    private static String kernelName(String id) {
        return id.substring(id.lastIndexOf(':') + 1).toUpperCase(Locale.ROOT);
    }

    // endregion

    // region Socket slots

    private static void slots(GuiGraphicsExtractor graphics, Font font, int width, int height) {
        List<HudState.Slot> slots = HudFeed.state().slots();
        if (slots.isEmpty()) {
            return;
        }
        int total = slots.size() * (SLOT + 2) - 2;
        int x = MARGIN;
        int y = height - SLOT - 1;
        // On a narrow screen, sit above the hotbar's row instead of running into it.
        if (x + total > width / 2 - 91 - MARGIN) {
            y -= SLOT + 14;
        }
        int standard = 0;
        int overload = 0;
        for (HudState.Slot slot : slots) {
            String name = slot.overload() ? "O" + ++overload : "S" + ++standard;
            int border;
            int text;
            if (slot.empty()) {
                border = EMPTY;
                text = GREY;
            } else if (slot.overload()) {
                border = slot.running() ? ORANGE : 0xFF7A5A2E;
                text = slot.running() ? TEXT : DIM;
            } else {
                border = slot.running() ? CYAN : GREY;
                text = slot.running() ? TEXT : DIM;
            }
            graphics.fill(x, y, x + SLOT, y + SLOT, border);
            graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, PANEL);
            String label = slot.empty() ? name : slot.label();
            graphics.text(font, label, centered(font, label, x, SLOT), y + 6, text, false);
            if (slot.fill() > 0) {
                int fill = (int) Math.round((SLOT - 2) * Math.clamp(slot.fill(), 0f, 1f));
                graphics.fill(x + 1, y + SLOT - 3, x + 1 + fill, y + SLOT - 1, border);
            }
            x += SLOT + 2;
        }
    }

    // endregion

    // region Event cards

    private static void cards(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int width, long now, float glitch, int jitter) {
        int x = width - MARGIN - CARD_WIDTH + jitter;
        int y = MARGIN + effectRows(player);
        int index = 0;
        for (EventStack.Entry<HudEvent> entry : HudFeed.cards().visible(now)) {
            HudEvent event = entry.value();
            long age = now - entry.bornAt();
            float in = Math.clamp(age / (float) SLIDE_MILLIS, 0f, 1f);
            float eased = 1 - (1 - in) * (1 - in) * (1 - in);
            float alpha = Math.min(eased, Math.clamp((entry.expiresAt() - now) / (float) FADE_MILLIS, 0f, 1f));

            // New text decrypts from the left; a strong glitch scrambles it again for a moment.
            float decrypt = Math.min(age / (float) DECRYPT_MILLIS, glitch > 0.3f ? 1 - glitch * 0.5f : 1f);
            long seed = now / 60 + index;
            String tag = "◆ " + (decrypt < 1 ? TextFx.garble(event.tag(), decrypt, seed) : event.tag());
            Component titleText = decrypt < 1 ? Component.literal(TextFx.garble(event.title().getString(), decrypt, seed + 7)) : event.title();
            List<FormattedCharSequence> title = lines(font, titleText, 2);
            List<FormattedCharSequence> detail = event.detail().getString().isEmpty() ? List.of() : lines(font, event.detail(), 2);
            int height = 4 + 9 + 3 + (title.size() + detail.size()) * 10 + 2;
            if (alpha > 0.05f) {
                int cardX = x + Math.round((1 - eased) * 24);
                int color = color(event.kind());
                panel(graphics, cardX, y, CARD_WIDTH, height, fade(color, alpha), fade(PANEL, alpha));
                graphics.text(font, tag, cardX + 6, y + 4, fade(color, alpha), false);
                int lineY = y + 16;
                for (FormattedCharSequence line : title) {
                    graphics.text(font, line, cardX + 6, lineY, fade(TEXT, alpha), false);
                    lineY += 10;
                }
                for (FormattedCharSequence line : detail) {
                    graphics.text(font, line, cardX + 6, lineY, fade(DIM, alpha), false);
                    lineY += 10;
                }
            }
            y += height + 3;
            index++;
        }
    }

    private static List<FormattedCharSequence> lines(Font font, Component text, int max) {
        List<FormattedCharSequence> lines = font.split(text, CARD_WIDTH - 12);
        return lines.size() > max ? lines.subList(0, max) : lines;
    }

    /** The cards start below vanilla's effect icons: one row for good effects, one for bad. */
    private static int effectRows(LocalPlayer player) {
        boolean good = false;
        boolean bad = false;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.showIcon()) {
                if (effect.getEffect().value().isBeneficial()) {
                    good = true;
                } else {
                    bad = true;
                }
            }
        }
        return bad ? 52 : good ? 26 : 0;
    }

    private static int color(HudEvent.Kind kind) {
        return switch (kind) {
            case INFO -> CYAN;
            case WARN -> ORANGE;
            case ALERT -> MAGENTA;
            case QUIET -> GREY;
        };
    }

    // endregion

    // region Banners

    /**
     * A compact band in the upper part of the screen: the headline in large type between hazard
     * stripes, decrypting as the band opens, over a strip of caution tape with DANGER or WARNING
     * running along it. Anything to read is on a card. Running out of memory holds a banner of
     * its own for as long as it lasts.
     */
    private static void banner(GuiGraphicsExtractor graphics, Font font, GatewayData data, int width, int height, long now) {
        boolean outOfMemory = data.isOutOfMemory();
        if (!outOfMemory) {
            outOfMemorySince = 0;
        } else if (outOfMemorySince == 0) {
            outOfMemorySince = now;
            HudFeed.accept(new HudEvent(HudEvent.Kind.ALERT, "RAM // OUT OF MEMORY", Component.translatable("mhm2027.banner.oom.ticker"),
                    Component.empty(), "oom", 160, false));
        }
        HudFeed.Banner active = HudFeed.banner(now);
        HudEvent event;
        long start;
        float alpha;
        if (active != null) {
            event = active.event();
            start = active.start();
            alpha = Math.clamp((active.end() - now) / 300f, 0f, 1f);
        } else if (outOfMemory) {
            event = new HudEvent(HudEvent.Kind.ALERT, "", Component.translatable("mhm2027.banner.oom"), Component.empty(), "", 0, true);
            start = outOfMemorySince;
            alpha = 1;
        } else {
            return;
        }

        float open = Math.clamp((now - start) / (float) BANNER_OPEN_MILLIS, 0f, 1f);
        open = 1 - (1 - open) * (1 - open);
        int color = color(event.kind());
        long age = now - start;
        String title = event.title().getString();
        int fullWidth = Math.clamp(Math.round(font.width(title) * TITLE_SCALE) + 2 * STRIPES + 40, 180, Math.max(180, width * 3 / 5));
        int bandWidth = Math.round(fullWidth * open);
        int x0 = (width - bandWidth) / 2;
        int x1 = x0 + bandWidth;
        int y = height / 6;
        int tapeY = y + BANNER_HEIGHT - TAPE_HEIGHT;
        graphics.fill(x0, y, x1, tapeY, fade(PANEL, alpha));
        graphics.fill(x0, y, x1, y + 1, fade(color, alpha));
        graphics.fill(x0, tapeY, x1, y + BANNER_HEIGHT, fade(color, alpha));
        if (bandWidth < 2 * STRIPES + 20) {
            return;
        }
        stripes(graphics, x0, y + 1, STRIPES, tapeY - y - 1, fade(color, alpha));
        stripes(graphics, x1 - STRIPES, y + 1, STRIPES, tapeY - y - 1, fade(color, alpha));

        if (age < 450) {
            title = TextFx.garble(title, age / 450f, now / 50);
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2f, y + 4);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.text(font, title, -font.width(title) / 2, 0, fade(color, alpha), false);
        graphics.pose().popMatrix();

        // Caution tape: one word, repeated, running along the bottom.
        String unit = hazardWord(event.kind()) + "  ◆  ";
        int unitWidth = font.width(unit);
        int offset = (int) ((age * 0.04) % unitWidth);
        graphics.enableScissor(x0, tapeY, x1, y + BANNER_HEIGHT);
        for (int tx = x0 - offset; tx < x1; tx += unitWidth) {
            graphics.text(font, unit, tx, tapeY + 1, fade(CHIP_TEXT, alpha), false);
        }
        graphics.disableScissor();
    }

    private static String hazardWord(HudEvent.Kind kind) {
        return switch (kind) {
            case ALERT -> "DANGER";
            case WARN -> "WARNING";
            case INFO, QUIET -> "NOTICE";
        };
    }

    /** Diagonal hazard stripes in a box. */
    private static void stripes(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        for (int row = 0; row < height; row++) {
            for (int start = -height; start < width; start += 8) {
                int from = Math.max(x, x + start + row);
                int to = Math.min(x + width, x + start + row + 4);
                if (from < to) {
                    graphics.fill(from, y + row, to, y + row + 1, color);
                }
            }
        }
    }

    // endregion

    // region Flash animation

    /**
     * While firmware flashes, the right side of the screen fills with faint garbled text that
     * decrypts, line by line, into the loader's source code as the flash goes on.
     */
    private static void flashCode(GuiGraphicsExtractor graphics, Font font, int width, int height, long now) {
        float share = HudFeed.flash(now);
        if (share < 0) {
            return;
        }
        int regionWidth = Math.min(240, width / 3);
        int x = width - MARGIN - regionWidth;
        int top = height * 2 / 5;
        int bottom = height - 32;
        int visible = Math.max(1, (bottom - top) / 9 - 1);
        String[] code = FlashCode.LINES;
        int first = Math.round(share * Math.max(0, code.length - visible));
        for (int i = 0; i < visible && first + i < code.length; i++) {
            int index = first + i;
            String line = code[index];
            // Lines resolve in order over the length of the flash.
            float resolved = Math.clamp(share * (code.length + 6) - index, 0f, 1f);
            String shown = resolved >= 1 ? line : TextFx.garble(line, resolved, now / 70 + index);
            int color = resolved >= 1 ? fade(CYAN, 0.6f) : fade(resolved > 0 ? TEXT : MAGENTA, 0.25f + 0.2f * resolved);
            graphics.text(font, font.plainSubstrByWidth(shown, regionWidth), x, top + i * 9, color, false);
        }
        int percent = Math.round(share * 100);
        String footer = "WRITING FIRMWARE  " + percent + "%";
        graphics.text(font, footer, x, bottom, ORANGE, false);
        int barX = x + font.width(footer) + 6;
        int barWidth = Math.max(0, x + regionWidth - barX);
        graphics.fill(barX, bottom + 2, barX + barWidth, bottom + 6, CELL_OFF);
        graphics.fill(barX, bottom + 2, barX + Math.round(barWidth * share), bottom + 6, ORANGE);
    }

    // endregion

    // region Boot

    /**
     * The boot sequence, over everything: the gateway's real boot log streams down the left,
     * the plain white EDGE logo appears, a bar fills, and for the last fifth the screen clears
     * while the HUD comes back. It lasts exactly as long as the reboot, and can't be skipped.
     */
    static void bootScreen(GuiGraphicsExtractor graphics, Font font, int width, int height, long now, float boot) {
        HudBoot event = HudFeed.bootEvent();
        float alpha = boot < BOOT_CLEAR ? 1f : Math.clamp((1f - boot) / (1f - BOOT_CLEAR), 0f, 1f);
        graphics.fill(0, 0, width, height, fade(BOOT_SHADE, alpha));
        if (event == null || HudFeed.boot(now) < 0) {
            // Pressed G; the server's answer is on its way.
            if ((now / 300) % 2 == 0) {
                graphics.fill(16, 16, 21, 23, CYAN);
            }
            return;
        }

        int x = 16;
        int y = 14;
        graphics.text(font, "EDGE GATEWAY FIRMWARE", x, y, fade(VIOLET, alpha), false);
        y += 13;
        List<HudBoot.Line> lines = event.lines();
        int unitWidth = font.width("storage") + 8;
        int shown = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (boot < BOOT_LOG_START + i * (BOOT_LOG_SPAN / lines.size())) {
                break;
            }
            HudBoot.Line line = lines.get(i);
            graphics.text(font, line.unit(), x, y, fade(DIM, alpha), false);
            graphics.text(font, line.text(), x + unitWidth, y, fade(tone(line.tone()), alpha), false);
            y += 10;
            shown++;
        }
        if (shown < lines.size() && (now / 300) % 2 == 0) {
            graphics.fill(x, y + 1, x + 5, y + 8, fade(CYAN, alpha));
        }

        // Wide screens put the logo to the right of the log; narrow ones below it.
        boolean wide = width >= 520;
        int logoX = wide ? width * 2 / 3 : width / 2;
        int logoY = wide ? height * 2 / 5 : height * 3 / 5;
        if (boot >= BOOT_LOGO) {
            float logo = Math.min(alpha, Math.clamp((boot - BOOT_LOGO) / 0.08f, 0f, 1f));
            graphics.pose().pushMatrix();
            graphics.pose().translate(logoX, logoY - 4 * LOGO_SCALE);
            graphics.pose().scale(LOGO_SCALE, LOGO_SCALE);
            graphics.text(font, "EDGE", -(font.width("EDGE") - 1) / 2, 0, fade(TEXT, logo), false);
            graphics.pose().popMatrix();

            String subtitle = "GATEWAY OPERATING SYSTEM";
            graphics.text(font, subtitle, logoX - font.width(subtitle) / 2, logoY + 30, fade(DIM, logo), false);
            int cells = 24;
            int cellsX = logoX - cells * 7 / 2;
            int lit = Math.round(boot * cells);
            for (int i = 0; i < cells; i++) {
                graphics.fill(cellsX + i * 7, logoY + 43, cellsX + i * 7 + 5, logoY + 49, fade(i < lit ? CYAN : CELL_OFF, logo));
            }
            boolean online = boot >= BOOT_CLEAR;
            String status = online ? "all systems online" : event.reason().equals(HudBoot.STARTUP) ? "starting" : "rebooting · " + event.reason();
            graphics.text(font, status, logoX - font.width(status) / 2, logoY + 55, fade(online ? CYAN : DIM, logo), false);
        }

        String footer = event.reason().equals(HudBoot.STARTUP) ? "STARTING UP" : "REBOOTING · " + event.reason().toUpperCase(Locale.ROOT);
        graphics.text(font, footer, (width - font.width(footer)) / 2, height - 14, fade(GREY, alpha), false);
    }

    private static int tone(HudBoot.Tone tone) {
        return switch (tone) {
            case PLAIN -> TEXT;
            case OK -> CYAN;
            case FLAW -> MAGENTA;
            case WARN -> ORANGE;
        };
    }

    // endregion

    // region Glitch

    /**
     * Torn scanlines in the HUD's two colors, flickers of noise text, and faint scanlines once it's
     * strong. Never a full-screen flash; players can turn it down or off in the client config.
     */
    private static void glitchOverlay(GuiGraphicsExtractor graphics, Font font, int width, int height, long now, float glitch) {
        Random random = new Random(now / 45);
        int tears = 2 + Math.round(glitch * 8);
        for (int i = 0; i < tears; i++) {
            int y = random.nextInt(height);
            int h = 1 + random.nextInt(1 + Math.round(glitch * 4));
            int x = random.nextInt(width) - width / 3;
            int w = width / 4 + random.nextInt(width / 2);
            boolean magenta = random.nextBoolean();
            graphics.fill(x, y, x + w, y + h, fade(magenta ? MAGENTA : CYAN, glitch * 0.28f));
            graphics.fill(x + 3, y + h, x + w + 3, y + h + 1, fade(magenta ? CYAN : MAGENTA, glitch * 0.18f));
        }
        int noise = Math.round(glitch * 5);
        for (int i = 0; i < noise; i++) {
            String text = TextFx.noise(now / 45 + i, 4 + random.nextInt(9));
            graphics.text(font, text, random.nextInt(Math.max(1, width - 60)), random.nextInt(Math.max(1, height - 10)),
                    fade(random.nextBoolean() ? MAGENTA : TEXT, glitch * 0.55f), false);
        }
        if (glitch > 0.35f) {
            for (int y = 0; y < height; y += 3) {
                graphics.fill(0, y, width, y + 1, fade(0xFF000000, glitch * 0.10f));
            }
        }
    }

    // endregion

    /** A panel with its top-left and bottom-right corners cut, and a one-pixel border. */
    static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int border, int fill) {
        chamfer(graphics, x, y, width, height, CUT, border);
        chamfer(graphics, x + 1, y + 1, width - 2, height - 2, CUT, fill);
    }

    private static void chamfer(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int cut, int color) {
        for (int i = 0; i < cut; i++) {
            graphics.fill(x + cut - i, y + i, x + width, y + i + 1, color);
            graphics.fill(x, y + height - 1 - i, x + width - cut + i, y + height - i, color);
        }
        graphics.fill(x, y + cut, x + width, y + height - cut, color);
    }

    /** The same color at a fraction of its opacity. */
    static int fade(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.clamp(alpha, 0f, 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
