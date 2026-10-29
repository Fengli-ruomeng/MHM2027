package com.mhm.mhm2027.station;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The Flash Station's screen: a firmware slot, three shard slots and the Flash button. */
public class FlashStationMenu extends AbstractContainerMenu {
    public static final int FLASH_BUTTON = 0;
    public static final int SLOT_Y = 22;
    public static final int FIRMWARE_X = 17;
    public static final int SHARDS_X = 53;

    private final Container container;
    private final ContainerData data;
    /** The station itself, on the server only. */
    private final @Nullable FlashStationBlockEntity station;

    /** The client's copy, filled in by the usual slot and data syncing. */
    public FlashStationMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(FlashStationBlockEntity.SIZE), new SimpleContainerData(1), null);
    }

    FlashStationMenu(int containerId, Inventory inventory, Container container, ContainerData data, @Nullable FlashStationBlockEntity station) {
        super(ModBlocks.FLASH_STATION_MENU.get(), containerId);
        checkContainerSize(container, FlashStationBlockEntity.SIZE);
        checkContainerDataCount(data, 1);
        this.container = container;
        this.data = data;
        this.station = station;
        addSlot(new StationSlot(container, FlashStationBlockEntity.FIRMWARE_SLOT, FIRMWARE_X, SLOT_Y));
        for (int i = 0; i < FlashStationBlockEntity.SIZE - FlashStationBlockEntity.FIRST_SHARD_SLOT; i++) {
            addSlot(new StationSlot(container, FlashStationBlockEntity.FIRST_SHARD_SLOT + i, SHARDS_X + i * 18, SLOT_Y));
        }
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    /** Ticks into the current flash, 0 when idle. */
    public int progress() {
        return data.get(0);
    }

    public Container stationSlots() {
        return container;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == FLASH_BUTTON && station != null && player instanceof ServerPlayer serverPlayer) {
            station.start(serverPlayer);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int stationSlots = FlashStationBlockEntity.SIZE;
        if (slotIndex < stationSlots) {
            if (!slot.mayPickup(player) || !moveItemStackTo(stack, stationSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (FlashStationBlockEntity.accepts(FlashStationBlockEntity.FIRMWARE_SLOT, stack)) {
            if (!moveItemStackTo(stack, FlashStationBlockEntity.FIRMWARE_SLOT, FlashStationBlockEntity.FIRST_SHARD_SLOT, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!FlashStationBlockEntity.accepts(FlashStationBlockEntity.FIRST_SHARD_SLOT, stack)
                || !moveItemStackTo(stack, FlashStationBlockEntity.FIRST_SHARD_SLOT, stationSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    /** Takes only what the station accepts, and holds its items while a flash runs. */
    private final class StationSlot extends Slot {
        StationSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return FlashStationBlockEntity.accepts(getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return progress() == 0;
        }
    }
}
