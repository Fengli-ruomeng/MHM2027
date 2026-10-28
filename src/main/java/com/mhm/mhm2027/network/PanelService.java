package com.mhm.mhm2027.network;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.terminal.InstallException;
import com.mhm.mhm2027.terminal.Kernel;
import com.mhm.mhm2027.terminal.KernelSettings;
import com.mhm.mhm2027.terminal.Program;
import com.mhm.mhm2027.terminal.ScriptStore;
import com.mhm.mhm2027.terminal.Terminal;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The server side of the control panel. Every action goes through the same checks as the
 * terminal itself and only ever touches the requesting player's own terminal and storage.
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public final class PanelService {
    private static final int SNAPSHOT_INTERVAL_TICKS = 10;
    private static final Set<UUID> WATCHERS = new HashSet<>();

    private PanelService() {
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(PanelRequest.TYPE, PanelRequest.STREAM_CODEC, PanelService::handle);
        // Client handlers are registered on the client side, in Mhm2027Client.
        registrar.playToClient(PanelSnapshot.TYPE, PanelSnapshot.STREAM_CODEC);
        registrar.playToClient(PanelMessage.TYPE, PanelMessage.STREAM_CODEC);
    }

    private static void handle(PanelRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (request.action() == PanelRequest.Action.CLOSE) {
            WATCHERS.remove(player.getUUID());
            return;
        }
        if (request.action() == PanelRequest.Action.OPEN) {
            WATCHERS.add(player.getUUID());
            sendSnapshot(player);
            return;
        }

        Terminal terminal = Terminals.get(player);
        String name = request.argument();
        try {
            Component result = switch (request.action()) {
                case START -> {
                    Program program = terminal.start(name);
                    yield Component.translatable("mhm2027.panel.started", program.name());
                }
                case STOP -> {
                    if (!terminal.stop(name)) {
                        throw new InstallException(name + " isn't running");
                    }
                    yield Component.translatable("mhm2027.panel.stopped", name);
                }
                case REBOOT -> {
                    if (Terminals.isNull(player)) {
                        throw new InstallException("A Null gateway has no terminal to reboot");
                    }
                    terminal.reboot();
                    yield Component.translatable("mhm2027.panel.rebooted");
                }
                case INSTALL_KERNEL -> {
                    Kernel kernel = terminal.installKernel(name);
                    yield Component.translatable("mhm2027.panel.kernel_installed", kernel.name());
                }
                default -> throw new IllegalStateException();
            };
            PacketDistributor.sendToPlayer(player, new PanelMessage(true, result));
        } catch (InstallException e) {
            PacketDistributor.sendToPlayer(player, new PanelMessage(false, Component.literal(e.getMessage())));
        }
        sendSnapshot(player);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (WATCHERS.isEmpty() || event.getServer().getTickCount() % SNAPSHOT_INTERVAL_TICKS != 0) {
            return;
        }
        for (UUID uuid : List.copyOf(WATCHERS)) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                WATCHERS.remove(uuid);
            } else {
                sendSnapshot(player);
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WATCHERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        WATCHERS.clear();
    }

    private static void sendSnapshot(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, snapshot(player));
    }

    static PanelSnapshot snapshot(ServerPlayer player) {
        if (Terminals.isNull(player)) {
            return PanelSnapshot.EMPTY;
        }
        Terminal terminal = Terminals.get(player);
        Kernel kernel = terminal.kernel();
        KernelSettings settings = kernel.settings();
        List<PanelSnapshot.ProgramRow> programs = terminal.programs().stream()
                .map(program -> new PanelSnapshot.ProgramRow(program.name(), program.type().id(), program.ramMb(), program.tops(),
                        (float) program.averageTops(), (float) program.peakTops(), program.state(terminal.now())))
                .toList();
        List<PanelSnapshot.ScriptRow> scripts = ScriptStore.list(player.level().getServer(), player.getUUID()).stream()
                .filter(entry -> entry.kind() != ScriptStore.Kind.UNKNOWN)
                .map(entry -> new PanelSnapshot.ScriptRow(entry.name(), entry.kind() == ScriptStore.Kind.KERNEL))
                .toList();
        return new PanelSnapshot(kernel.name(), settings.schedule(), settings.gc(), settings.verify(), settings.rateLimit(),
                kernel.hookNames(), kernel.ramMb(), kernel.topsReserve(), programs, scripts, terminal.log(),
                terminal.isRebooting() ? terminal.lockReason() : "", terminal.isRebooting() ? terminal.lockSecondsLeft() : 0);
    }
}
