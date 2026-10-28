package com.mhm.mhm2027.item;

import java.util.Optional;

import com.mhm.mhm2027.gateway.InstalledPresets;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.terminal.InstallException;
import com.mhm.mhm2027.terminal.PresetRegistry;
import com.mhm.mhm2027.terminal.ScriptAnalyzer;
import com.mhm.mhm2027.terminal.ScriptStore;
import com.mhm.mhm2027.terminal.ScriptSummary;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Moving code between chips and storage, on the server.
 *
 * Writing copies a chip into the gateway: own code becomes a file in storage, a preset is
 * recorded by id. Burning copies a stored file onto a blank chip so it can be handed on.
 */
public final class ChipActions {
    private ChipActions() {
    }

    /** Writes a chip into the player's gateway. Preset chips are used up; own code chips are kept. */
    static void write(ServerPlayer player, ItemStack stack) {
        try {
            if (Terminals.isNull(player)) {
                throw new InstallException("A Null gateway has no storage to write into");
            }
            Identifier presetId = stack.get(ModComponents.PRESET.get());
            ScriptCode code = stack.get(ModComponents.SCRIPT_CODE.get());
            Component result;
            if (presetId != null) {
                result = writePreset(player, presetId);
                stack.shrink(1);
            } else if (code != null) {
                result = writeCode(player, code);
            } else {
                return;
            }
            player.sendOverlayMessage(result.copy().withStyle(ChatFormatting.GREEN));
        } catch (InstallException e) {
            player.sendOverlayMessage(Component.literal(e.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    private static Component writePreset(ServerPlayer player, Identifier id) throws InstallException {
        PresetRegistry.Preset preset = PresetRegistry.get(id)
                .orElseThrow(() -> new InstallException("This chip's preset (" + id + ") no longer exists"));
        int level = Terminals.level(player);
        if (level < preset.level()) {
            throw new InstallException(preset.summary().name() + " requires gateway LV" + preset.level() + " (you are LV" + level + ")");
        }
        InstalledPresets installed = player.getData(ModAttachments.PRESETS);
        if (installed.contains(id)) {
            throw new InstallException(preset.summary().name() + " is already in your gateway");
        }
        player.setData(ModAttachments.PRESETS, installed.with(id));
        return Component.translatable("message.mhm2027.chip.wrote_preset", preset.summary().name());
    }

    private static Component writeCode(ServerPlayer player, ScriptCode code) throws InstallException {
        Optional<String> saved = ScriptStore.writeCopy(player.level().getServer(), player.getUUID(), code.file(), code.source());
        return saved.map(name -> Component.translatable("message.mhm2027.chip.wrote", name + ".lua"))
                .orElseGet(() -> Component.translatable("message.mhm2027.chip.already_stored", code.file() + ".lua"));
    }

    /**
     * Burns a file from the player's storage onto a blank chip from their inventory.
     *
     * @return the name of the script that was burned
     */
    public static String burn(ServerPlayer player, String file) throws InstallException {
        if (Terminals.isNull(player)) {
            throw new InstallException("A Null gateway has no storage");
        }
        String source = ScriptStore.read(player.level().getServer(), player.getUUID(), file);
        ScriptSummary summary = ScriptAnalyzer.analyze(file, source, false).summary();

        ItemStack blank = findBlank(player.getInventory());
        if (blank == null) {
            throw new InstallException("You need a Blank Data Chip in your inventory");
        }
        ItemStack chip = new ItemStack(summary.isKernel() ? ModItems.KERNEL_CHIP.get() : ModItems.PROGRAM_CHIP.get());
        chip.set(ModComponents.SCRIPT_CODE.get(), new ScriptCode(file, source, player.getScoreboardName()));
        chip.set(ModComponents.SUMMARY.get(), summary);
        blank.shrink(1);
        player.getInventory().placeItemBackInInventory(chip);
        return summary.name();
    }

    /** A preset chip as loot or the creative tab gives it: just the id; the summary fills in on the server. */
    public static ItemStack presetChip(Identifier id, boolean kernel) {
        ItemStack chip = new ItemStack(kernel ? ModItems.KERNEL_CHIP.get() : ModItems.PROGRAM_CHIP.get());
        chip.set(ModComponents.PRESET.get(), id);
        PresetRegistry.get(id).ifPresent(preset -> chip.set(ModComponents.SUMMARY.get(), preset.summary()));
        return chip;
    }

    private static ItemStack findBlank(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.BLANK_CHIP.get())) {
                return stack;
            }
        }
        return null;
    }
}
