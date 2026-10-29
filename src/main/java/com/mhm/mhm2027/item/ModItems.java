package com.mhm.mhm2027.item;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.potion.ModPotions;
import com.mhm.mhm2027.station.Firmware;
import com.mhm.mhm2027.station.ModBlocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mhm2027.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mhm2027.MODID);

    /** Crafted, then burned with code from storage. */
    public static final DeferredItem<Item> BLANK_CHIP = ITEMS.registerSimpleItem("blank_chip");
    public static final DeferredItem<ChipItem> PROGRAM_CHIP = ITEMS.registerItem("program_chip",
            properties -> new ChipItem(properties, false), properties -> properties.stacksTo(1));
    public static final DeferredItem<ChipItem> KERNEL_CHIP = ITEMS.registerItem("kernel_chip",
            properties -> new ChipItem(properties, true), properties -> properties.stacksTo(1));
    /** Dropped by gateway mobs; every firmware needs them. */
    public static final DeferredItem<Item> DATA_SHARD = ITEMS.registerSimpleItem("data_shard");
    public static final Map<Firmware, DeferredItem<FirmwareItem>> FIRMWARE = firmwareItems();
    public static final DeferredItem<BlockItem> FLASH_STATION = ITEMS.registerSimpleBlockItem(ModBlocks.FLASH_STATION);

    /** The presets this mod ships, for the creative tab. Loot tables and other datapacks can add more. */
    private static final List<Identifier> BUILTIN_PROGRAM_PRESETS = List.of(
            id("adrenaline_shot"), id("featherfall"), id("field_medic"), id("overclock"));
    private static final List<Identifier> BUILTIN_KERNEL_PRESETS = List.of(id("aegis"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EDGE_TAB = CREATIVE_TABS.register("edge", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mhm2027.edge"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> PROGRAM_CHIP.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(BLANK_CHIP.get());
                output.accept(DATA_SHARD.get());
                output.accept(FLASH_STATION.get());
                ModPotions.CYBER.forEach(potion -> output.accept(PotionContents.createItemStack(Items.POTION, potion)));
                FIRMWARE.values().forEach(item -> output.accept(item.get()));
                BUILTIN_PROGRAM_PRESETS.forEach(preset -> output.accept(ChipActions.presetChip(preset, false)));
                BUILTIN_KERNEL_PRESETS.forEach(preset -> output.accept(ChipActions.presetChip(preset, true)));
            }).build());

    private ModItems() {
    }

    private static Map<Firmware, DeferredItem<FirmwareItem>> firmwareItems() {
        Map<Firmware, DeferredItem<FirmwareItem>> items = new EnumMap<>(Firmware.class);
        for (Firmware firmware : Firmware.values()) {
            items.put(firmware, ITEMS.registerItem(firmware.itemName(), properties -> new FirmwareItem(properties, firmware)));
        }
        return Collections.unmodifiableMap(items);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mhm2027.MODID, path);
    }
}
