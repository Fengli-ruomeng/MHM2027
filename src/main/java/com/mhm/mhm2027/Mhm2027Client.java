package com.mhm.mhm2027;

import org.lwjgl.glfw.GLFW;

import com.mhm.mhm2027.client.EdgeBootScreen;
import com.mhm.mhm2027.client.EdgeDesktopScreen;
import com.mhm.mhm2027.client.FlashStationScreen;
import com.mhm.mhm2027.client.HudFeed;
import com.mhm.mhm2027.client.LensHud;
import com.mhm.mhm2027.client.PanelNetworking;
import com.mhm.mhm2027.network.PanelRequest;
import com.mhm.mhm2027.station.ModBlocks;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Mhm2027.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Mhm2027.MODID, value = Dist.CLIENT)
public class Mhm2027Client {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "edge"));

    /** Boots the gateway when it's off, and opens Edge when it's on. G by default; Esc closes Edge. */
    public static final KeyMapping OPEN_PANEL = new KeyMapping("key.mhm2027.open_panel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    /** Held: the gateway's full numbers beside the Lens bars. Left Alt by default. */
    public static final KeyMapping LENS_DETAILS = new KeyMapping("key.mhm2027.lens_details", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    public Mhm2027Client(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "gateway"), new LensHud());
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_PANEL);
        event.register(LENS_DETAILS);
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModBlocks.FLASH_STATION_MENU.get(), FlashStationScreen::new);
    }

    @SubscribeEvent
    static void onRegisterClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        PanelNetworking.register(event);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_PANEL.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                switch (HudFeed.power()) {
                    case OFF -> {
                        ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.BOOT));
                        minecraft.setScreen(new EdgeBootScreen(OPEN_PANEL));
                    }
                    case BOOTING -> minecraft.setScreen(new EdgeBootScreen(OPEN_PANEL));
                    case ON -> minecraft.setScreen(new EdgeDesktopScreen());
                }
            }
        }
    }
}
