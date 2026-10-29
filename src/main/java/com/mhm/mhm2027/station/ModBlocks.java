package com.mhm.mhm2027.station;

import java.util.Set;
import java.util.function.Supplier;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mhm2027.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mhm2027.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Mhm2027.MODID);

    public static final DeferredBlock<FlashStationBlock> FLASH_STATION = BLOCKS.registerBlock("flash_station", FlashStationBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(3.5F).requiresCorrectToolForDrops().sound(SoundType.METAL));
    public static final Supplier<BlockEntityType<FlashStationBlockEntity>> FLASH_STATION_ENTITY = BLOCK_ENTITIES.register("flash_station",
            () -> new BlockEntityType<>(FlashStationBlockEntity::new, Set.of(FLASH_STATION.get())));
    public static final Supplier<MenuType<FlashStationMenu>> FLASH_STATION_MENU = MENUS.register("flash_station",
            () -> new MenuType<>(FlashStationMenu::new, FeatureFlags.VANILLA_SET));

    private ModBlocks() {
    }
}
