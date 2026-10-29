package com.mhm.mhm2027.station;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.InstalledPresets;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.item.FirmwareItem;
import com.mhm.mhm2027.item.ModItems;
import com.mhm.mhm2027.terminal.Terminal;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Flash Station's items and the flash in progress. A flash takes 10 seconds, during which
 * the player's gateway is down (Buffer 0, every program stopped). The firmware, shards and XP
 * are only taken when it completes; walking away, dying or logging out cancels it at no cost.
 * A flash is not saved: if the station unloads, the terminal notices and puts things back.
 */
public class FlashStationBlockEntity extends BaseContainerBlockEntity {
    public static final int FIRMWARE_SLOT = 0;
    /** Three shard slots, so even LV10's 128 shards fit. */
    public static final int FIRST_SHARD_SLOT = 1;
    public static final int SIZE = 4;
    public static final int FLASH_TICKS = 200;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private @Nullable UUID flasher;
    private int progress;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return progress;
        }

        @Override
        public void set(int index, int value) {
            progress = value;
        }

        @Override
        public int getCount() {
            return 1;
        }
    };

    public FlashStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FLASH_STATION_ENTITY.get(), pos, state);
    }

    // region Rules

    /**
     * What stops this player flashing the firmware in these slots, or null if nothing does.
     * The screen uses it too, with the client's copy of the player's gateway.
     */
    public static @Nullable Component problem(Player player, Container slots) {
        if (!(slots.getItem(FIRMWARE_SLOT).getItem() instanceof FirmwareItem item)) {
            return Component.translatable("mhm2027.flash.insert");
        }
        Firmware firmware = item.firmware();
        GatewayData data = player.getData(ModAttachments.GATEWAY);
        Firmware.Refusal refusal = firmware.refusal(data.level());
        if (refusal != null) {
            return Component.translatable("mhm2027.flash.refusal." + refusal.key(), FirmwareItem.levelName(refusal.level()));
        }
        int shards = shards(slots);
        if (shards < firmware.shards()) {
            return Component.translatable("mhm2027.flash.need_shards", firmware.shards(), shards);
        }
        if (!player.hasInfiniteMaterials() && player.experienceLevel < firmware.xpLevels()) {
            return Component.translatable("mhm2027.flash.need_levels", firmware.xpLevels(), player.experienceLevel);
        }
        return null;
    }

    public static int shards(Container slots) {
        int count = 0;
        for (int slot = FIRST_SHARD_SLOT; slot < SIZE; slot++) {
            ItemStack stack = slots.getItem(slot);
            if (stack.is(ModItems.DATA_SHARD.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void takeShards(int count) {
        for (int slot = FIRST_SHARD_SLOT; slot < SIZE && count > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.is(ModItems.DATA_SHARD.get())) {
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                count -= taken;
            }
        }
    }

    // endregion

    // region Flashing

    /** The Flash button. */
    void start(ServerPlayer player) {
        Terminal terminal = Terminals.get(player);
        Component problem;
        if (flasher != null) {
            problem = Component.translatable("mhm2027.flash.busy");
        } else if (terminal.isFlashing()) {
            problem = Component.translatable("mhm2027.flash.already_flashing");
        } else if (terminal.isRebooting()) {
            problem = Component.translatable("mhm2027.flash.rebooting");
        } else {
            problem = problem(player, this);
        }
        if (problem != null) {
            player.sendSystemMessage(problem.copy().withStyle(ChatFormatting.RED));
            return;
        }
        flasher = player.getUUID();
        progress = 0;
        terminal.beginFlash(player);
        player.sendSystemMessage(Component.translatable("mhm2027.flash.started").withStyle(ChatFormatting.GOLD));
        playSound(SoundEvents.BEACON_POWER_SELECT);
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FlashStationBlockEntity station) {
        if (station.flasher == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(station.flasher);
        Terminal terminal = player == null ? null : Terminals.existing(player).orElse(null);
        if (player == null || terminal == null || !terminal.isFlashing()) {
            // Logged out, died or timed out: the terminal has already put the gateway back.
            station.reset(player, "interrupted");
            return;
        }
        if (player.level() != level || !FlashStationBlock.near(player, pos)) {
            station.cancel(player, terminal, "left");
            return;
        }
        terminal.holdFlash();
        station.progress++;
        if (station.progress >= FLASH_TICKS) {
            station.complete(player, terminal);
            return;
        }
        if (station.progress % 20 == 0) {
            int secondsLeft = (FLASH_TICKS - station.progress) / 20;
            player.sendOverlayMessage(Component.translatable("mhm2027.flash.progress", secondsLeft).withStyle(ChatFormatting.GOLD));
            ((ServerLevel) level).sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                    4, 0.25, 0.05, 0.25, 0.0);
        }
    }

    private void complete(ServerPlayer player, Terminal terminal) {
        Component problem = problem(player, this);
        if (problem != null || !(items.get(FIRMWARE_SLOT).getItem() instanceof FirmwareItem item)) {
            terminal.endFlash(player);
            reset(player, "items");
            return;
        }
        Firmware firmware = item.firmware();
        items.get(FIRMWARE_SLOT).shrink(1);
        takeShards(firmware.shards());
        if (!player.hasInfiniteMaterials()) {
            player.giveExperienceLevels(-firmware.xpLevels());
        }
        apply(player, firmware);
        terminal.endFlash(player);
        flasher = null;
        progress = 0;
        setChanged();
        player.sendSystemMessage(Component.translatable("mhm2027.flash.done", FirmwareItem.levelName(firmware.target()))
                .withStyle(ChatFormatting.GREEN));
        playSound(SoundEvents.BEACON_ACTIVATE);
    }

    /** Writes the firmware. Going Null wipes every program slot and the kernel. */
    private static void apply(ServerPlayer player, Firmware firmware) {
        GatewayData data = player.getData(ModAttachments.GATEWAY);
        data.setLevel(firmware.target());
        if (firmware == Firmware.NULL) {
            player.setData(ModAttachments.PRESETS, InstalledPresets.EMPTY);
            data.setKernel(GatewayData.DEFAULT_KERNEL);
        }
    }

    private void cancel(ServerPlayer player, Terminal terminal, String reason) {
        terminal.endFlash(player);
        reset(player, reason);
    }

    private void reset(@Nullable ServerPlayer player, String reason) {
        flasher = null;
        progress = 0;
        setChanged();
        if (player != null) {
            player.sendSystemMessage(Component.translatable("mhm2027.flash.cancelled",
                    Component.translatable("mhm2027.flash.reason." + reason)).withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (flasher != null && level != null && !level.isClientSide()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(flasher);
            Terminal terminal = player == null ? null : Terminals.existing(player).orElse(null);
            if (terminal != null && terminal.isFlashing()) {
                cancel(player, terminal, "removed");
            }
            flasher = null;
        }
    }

    private void playSound(SoundEvent sound) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1.0F, 1.4F);
        }
    }

    // endregion

    // region Container

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.mhm2027.flash_station");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(slot, stack);
    }

    /** Firmware in the first slot, Data Shards in the rest. The client's menu checks this too. */
    static boolean accepts(int slot, ItemStack stack) {
        return slot == FIRMWARE_SLOT ? stack.getItem() instanceof FirmwareItem : stack.is(ModItems.DATA_SHARD.get());
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return flasher == null;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new FlashStationMenu(containerId, inventory, this, data, this);
    }

    boolean isFlashing() {
        return flasher != null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    // endregion
}
