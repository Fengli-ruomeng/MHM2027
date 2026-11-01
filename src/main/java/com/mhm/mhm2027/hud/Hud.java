package com.mhm.mhm2027.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.gateway.Sockets;
import com.mhm.mhm2027.terminal.Addresses;
import com.mhm.mhm2027.terminal.PresetRegistry;
import com.mhm.mhm2027.terminal.Terminal;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The server side of the HUD: event cards in place of chat and action-bar messages, and the
 * socket slots. Everything the mod tells a player in play goes through {@link #event}.
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public final class Hud {
    private static final int STATE_INTERVAL_TICKS = 10;
    private static final int STARTUP_BOOT_TICKS = 60;
    private static final Map<UUID, HudState> LAST_STATE = new HashMap<>();

    private Hud() {
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Client handlers are registered on the client side, in Mhm2027Client.
        registrar.playToClient(HudEvent.TYPE, HudEvent.STREAM_CODEC);
        registrar.playToClient(HudState.TYPE, HudState.STREAM_CODEC);
        registrar.playToClient(HudBoot.TYPE, HudBoot.STREAM_CODEC);
    }

    // region Events

    public static void event(ServerPlayer player, HudEvent.Kind kind, String tag, Component title) {
        event(player, kind, tag, title, Component.empty(), "", 0);
    }

    public static void event(ServerPlayer player, HudEvent.Kind kind, String tag, Component title, Component detail) {
        event(player, kind, tag, title, detail, "", 0);
    }

    /**
     * Shows a card on the player's HUD.
     *
     * @param key   a card with the same key replaces the last one, as a countdown would; empty for none
     * @param ticks how long it stays; 0 for the kind's default
     */
    public static void event(ServerPlayer player, HudEvent.Kind kind, String tag, Component title, Component detail, String key, int ticks) {
        PacketDistributor.sendToPlayer(player, new HudEvent(kind, tag, title, detail, key, ticks, false));
    }

    /**
     * A warning banner, for the moments that matter most: a breach, a panic, an overheat, a
     * flash. Just a headline: anything to read goes on a card beside it.
     */
    public static void banner(ServerPlayer player, HudEvent.Kind kind, Component title) {
        PacketDistributor.sendToPlayer(player, new HudEvent(kind, "", title, Component.empty(), "", 0, true));
    }

    /** The gateway is booting: the client plays the boot sequence for {@code ticks}, with this log. */
    public static void boot(ServerPlayer player, String reason, int ticks, List<HudBoot.Line> lines) {
        PacketDistributor.sendToPlayer(player, new HudBoot(reason, ticks, lines));
    }

    /** Joining a world boots the gateway too. */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !Terminals.isNull(player)) {
            boot(player, HudBoot.STARTUP, STARTUP_BOOT_TICKS, Terminals.get(player).bootLog(player, HudBoot.STARTUP));
        }
    }

    /** "10.71.4.200 // ACCESS": the source is an entity's address. */
    public static String tag(Entity source, String channel) {
        return Addresses.of(source.getUUID()) + " // " + channel;
    }

    /** "WATCHER // NOTIFY": the source is a name, such as a program or a device. */
    public static String tag(String source, String channel) {
        return source.toUpperCase(Locale.ROOT) + " // " + channel;
    }

    // endregion

    // region Socket slots

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % STATE_INTERVAL_TICKS != 0) {
            return;
        }
        HudState state = stateOf(player);
        if (!state.equals(LAST_STATE.get(player.getUUID()))) {
            LAST_STATE.put(player.getUUID(), state);
            PacketDistributor.sendToPlayer(player, state);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_STATE.remove(event.getEntity().getUUID());
    }

    private static HudState stateOf(ServerPlayer player) {
        GatewayData data = player.getData(ModAttachments.GATEWAY);
        Terminal terminal = Terminals.existing(player).orElse(null);
        if (terminal == null) {
            return HudState.EMPTY;
        }
        float flash = terminal.flashShare();
        if (data.tier().isNull()) {
            // A Null gateway has no sockets, but it can still be flashing Restore Firmware.
            return flash < 0 ? HudState.EMPTY : new HudState(List.of(), flash);
        }
        Sockets sockets = terminal.sockets(player);
        List<HudState.Slot> slots = new ArrayList<>();
        float heat = (float) (data.heat() / GatewayData.MAX_HEAT);
        for (String id : sockets.standard()) {
            slots.add(slot(terminal, id, false, 0));
        }
        for (String id : sockets.overload()) {
            slots.add(slot(terminal, id, true, heat));
        }
        return new HudState(List.copyOf(slots), flash);
    }

    private static HudState.Slot slot(Terminal terminal, String id, boolean overload, float fill) {
        if (id.isEmpty()) {
            return new HudState.Slot(overload, "", false, 0);
        }
        boolean running = terminal.programs().stream().anyMatch(program -> program.id().equals(id));
        String name = PresetRegistry.byTerminalId(id).map(preset -> preset.summary().name()).orElse(id);
        return new HudState.Slot(overload, label(name), running, running ? fill : 0);
    }

    /** Three letters for a slot: "Adrenaline Shot" becomes ADR. */
    static String label(String name) {
        String letters = name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return letters.isEmpty() ? "?" : letters.substring(0, Math.min(3, letters.length()));
    }

    // endregion
}
