package com.mhm.mhm2027.client;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mhm.mhm2027.item.FirmwareItem;
import com.mhm.mhm2027.station.FlashStationBlockEntity;
import com.mhm.mhm2027.station.FlashStationMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The Flash Station screen. Plain on purpose until the UI pass: slots, the Flash button, a
 * progress bar and one status line saying what's missing.
 */
public class FlashStationScreen extends AbstractContainerScreen<FlashStationMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_EDGE = 0xFF555555;
    private static final int SLOT_EDGE = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int BAR_FILL = 0xFF3FE8FF;
    private static final int TEXT_PROBLEM = 0xFF9E2A2A;
    private static final int TEXT_READY = 0xFF2E7D32;
    private static final int TEXT_FLASHING = 0xFF1565C0;
    private static final int BAR_X = 17;
    private static final int BAR_Y = 44;
    private static final int BAR_WIDTH = 142;
    private static final int STATUS_Y = 52;

    private @Nullable Button flash;

    public FlashStationScreen(FlashStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        flash = addRenderableWidget(Button.builder(Component.translatable("mhm2027.flash.button"), button -> {
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FlashStationMenu.FLASH_BUTTON);
            }
        }).bounds(leftPos + 116, topPos + 20, 50, 20).build());
        updateButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButton();
    }

    private void updateButton() {
        if (flash != null) {
            flash.active = menu.progress() == 0 && problem() == null;
        }
    }

    private @Nullable Component problem() {
        return minecraft.player == null ? Component.empty() : FlashStationBlockEntity.problem(minecraft.player, menu.stationSlots());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_EDGE);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        for (Slot slot : menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, SLOT_EDGE);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_FILL);
        }
        graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_WIDTH, y + BAR_Y + 5, SLOT_EDGE);
        int filled = BAR_WIDTH * menu.progress() / FlashStationBlockEntity.FLASH_TICKS;
        if (filled > 0) {
            graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + filled, y + BAR_Y + 5, BAR_FILL);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component status;
        int color;
        if (menu.progress() > 0) {
            int secondsLeft = (int) Math.ceil((FlashStationBlockEntity.FLASH_TICKS - menu.progress()) / 20.0);
            status = Component.translatable("mhm2027.flash.progress", secondsLeft);
            color = TEXT_FLASHING;
        } else {
            Component problem = problem();
            if (problem != null) {
                status = problem;
                color = TEXT_PROBLEM;
            } else {
                FirmwareItem item = (FirmwareItem) menu.stationSlots().getItem(FlashStationBlockEntity.FIRMWARE_SLOT).getItem();
                status = Component.translatable("mhm2027.flash.ready", FirmwareItem.levelName(item.firmware().target()));
                color = TEXT_READY;
            }
        }
        List<FormattedCharSequence> lines = font.split(status, BAR_WIDTH);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.text(font, lines.get(i), BAR_X, STATUS_Y + i * 9, color, false);
        }
    }
}
