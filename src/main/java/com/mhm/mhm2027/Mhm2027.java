package com.mhm.mhm2027;

import org.slf4j.Logger;

import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.item.ModComponents;
import com.mhm.mhm2027.item.ModItems;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

// MoreHarderMinecraft: Edge. Event handlers live in their own classes via @EventBusSubscriber.
@Mod(Mhm2027.MODID)
public class Mhm2027 {
    public static final String MODID = "mhm2027";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mhm2027(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModComponents.COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);

        // Server config: balance values are per world and synced to clients.
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}
