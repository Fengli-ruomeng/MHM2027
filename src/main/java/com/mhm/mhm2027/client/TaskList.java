package com.mhm.mhm2027.client;

import java.util.List;
import java.util.Locale;

import com.mhm.mhm2027.hud.HudState;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The task list, bottom right: anything that takes time. Attempts on you first, then programs
 * waiting on a call, sessions, Overload Heat, locks and cooldowns; idle residents shrink to
 * one dim line. Timers count down between the server's updates.
 */
final class TaskList {
    private static final int WIDTH = 150;
    private static final int MARGIN = 4;
    private static final int ROW = 21;
    private static final int IDLE_ROW = 10;
    private static final int PANEL = 0xB00B0F17;

    private TaskList() {
    }

    static void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, long now) {
        List<HudState.Task> tasks = HudFeed.state().tasks();
        if (tasks.isEmpty()) {
            return;
        }
        double age = HudFeed.stateAge(now);
        // Rows stack up from the bottom; the least urgent go when there's no room above the middle.
        int bottom = height - MARGIN;
        int count = 0;
        int listHeight = 12;
        for (HudState.Task task : tasks) {
            int rowHeight = task.kind() == HudState.Task.Kind.IDLE ? IDLE_ROW : ROW;
            if (bottom - listHeight - rowHeight < height / 2) {
                break;
            }
            listHeight += rowHeight;
            count++;
        }
        if (count == 0) {
            return;
        }
        int x = width - MARGIN - WIDTH;
        int y = bottom - listHeight;
        LensHud.panel(graphics, x - 4, y - 3, WIDTH + 8, listHeight + 4, 0x602C3550, PANEL);
        graphics.text(font, "TASKS", x, y, EdgeShell.GREY, false);
        if (count < tasks.size()) {
            String more = "+" + (tasks.size() - count);
            graphics.text(font, more, x + WIDTH - font.width(more), y, EdgeShell.GREY, false);
        }
        y += 12;
        for (HudState.Task task : tasks.subList(0, count)) {
            y = row(graphics, font, task, x, y, age, now);
        }
    }

    private static int row(GuiGraphicsExtractor graphics, Font font, HudState.Task task, int x, int y, double age, long now) {
        int color = color(task.kind());
        if (task.kind() == HudState.Task.Kind.IDLE) {
            graphics.text(font, font.plainSubstrByWidth(task.label(), WIDTH - 30, false), x, y, EdgeShell.GREY, false);
            graphics.text(font, "idle", x + WIDTH - font.width("idle"), y, EdgeShell.GREY, false);
            return y + IDLE_ROW;
        }
        // An attack on you blinks, so it can't be missed.
        if (task.kind() == HudState.Task.Kind.INCOMING && (now / 300) % 2 == 0) {
            color = EdgeShell.TEXT;
        }
        double left = task.kind().countsDown() ? Math.max(0, task.left() - age) : task.left();
        String value = switch (task.kind()) {
            case OVERLOAD -> (task.total() - task.left()) + "/" + task.total();
            case FLASH -> Math.round(100.0 * (task.total() - task.left()) / Math.max(1, task.total())) + "%";
            case BUSY, IDLE -> "";
            default -> String.format(Locale.ROOT, "%.1f s", left / 20);
        };
        int valueX = x + WIDTH - font.width(value);
        graphics.text(font, font.plainSubstrByWidth(task.label(), valueX - x - 6, false), x, y, color, false);
        graphics.text(font, value, valueX, y, EdgeShell.TEXT, false);
        graphics.text(font, font.plainSubstrByWidth(task.detail(), WIDTH, false), x, y + 9, EdgeShell.DIM, false);
        if (task.total() > 0) {
            float done = (float) Math.clamp(1 - left / task.total(), 0, 1);
            graphics.fill(x, y + 18, x + WIDTH, y + 19, 0x30F2F6FF);
            graphics.fill(x, y + 18, x + Math.round(WIDTH * done), y + 19, color);
        }
        return y + ROW;
    }

    private static int color(HudState.Task.Kind kind) {
        return switch (kind) {
            case INCOMING -> EdgeShell.MAGENTA;
            case WAITING, FLASH -> EdgeShell.CYAN;
            case SESSION -> EdgeShell.PINK;
            case OVERLOAD, LOCKED -> EdgeShell.ORANGE;
            case COOLDOWN -> EdgeShell.DIM;
            case BUSY -> EdgeShell.TEXT;
            case IDLE -> EdgeShell.GREY;
        };
    }
}
