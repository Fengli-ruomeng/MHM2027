package com.mhm.mhm2027.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.gateway.Sockets;
import com.mhm.mhm2027.gateway.Wheel;
import com.mhm.mhm2027.hud.Hud;
import com.mhm.mhm2027.item.ChipActions;
import com.mhm.mhm2027.station.FlashStationBlock;
import com.mhm.mhm2027.terminal.Addresses;
import com.mhm.mhm2027.terminal.FlawRules;
import com.mhm.mhm2027.terminal.Gateways;
import com.mhm.mhm2027.terminal.InstallException;
import com.mhm.mhm2027.terminal.Kernel;
import com.mhm.mhm2027.terminal.KernelSettings;
import com.mhm.mhm2027.terminal.LuaSandbox;
import com.mhm.mhm2027.terminal.PresetRegistry;
import com.mhm.mhm2027.terminal.Program;
import com.mhm.mhm2027.terminal.ScriptAnalyzer;
import com.mhm.mhm2027.terminal.ScriptStore;
import com.mhm.mhm2027.terminal.ScriptSummary;
import com.mhm.mhm2027.terminal.Terminal;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
    /** Edge's scan takes as long as an inspection would (rate_limit speeds it up), so it can't be spammed. */
    private static final int SCAN_BASE_TICKS = 20;
    private static final Map<UUID, Integer> LAST_SCAN = new HashMap<>();
    /** Describing the wheel reads and checks up to eight scripts, so a burst of opens reuses the last answer. */
    private static final int WHEEL_INFO_TICKS = 20;
    private static final Map<UUID, WheelAnswer> LAST_WHEEL_INFO = new HashMap<>();

    private record WheelAnswer(int tick, Wheel wheel, WheelInfo info) {
    }

    private PanelService() {
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(PanelRequest.TYPE, PanelRequest.STREAM_CODEC, PanelService::handle);
        // Client handlers are registered on the client side, in Mhm2027Client.
        registrar.playToClient(PanelSnapshot.TYPE, PanelSnapshot.STREAM_CODEC);
        registrar.playToClient(PanelMessage.TYPE, PanelMessage.STREAM_CODEC);
        registrar.playToClient(ScanResult.TYPE, ScanResult.STREAM_CODEC);
        registrar.playToClient(WheelInfo.TYPE, WheelInfo.STREAM_CODEC);
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
        if (request.action() == PanelRequest.Action.BOOT) {
            if (!Terminals.isNull(player) && terminal.bootFromOff()) {
                Hud.pushState(player);
            }
            return;
        }
        if (request.action() == PanelRequest.Action.LOOK_AWAY) {
            terminal.lookedAway(player);
            return;
        }
        if (request.action() == PanelRequest.Action.SCAN) {
            scan(player, terminal, request.argument());
            return;
        }
        if (request.action() == PanelRequest.Action.WHEEL_SET) {
            setWheel(player, request.argument());
            return;
        }
        if (request.action() == PanelRequest.Action.WHEEL_INFO) {
            PacketDistributor.sendToPlayer(player, wheelInfo(player));
            return;
        }
        String name = request.argument();
        try {
            if (!Terminals.isNull(player)) {
                terminal.requireOn();
            }
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
                    if (terminal.isFlashing()) {
                        throw new InstallException("The gateway is flashing firmware");
                    }
                    terminal.reboot();
                    yield Component.translatable("mhm2027.panel.rebooted");
                }
                case INSTALL_KERNEL -> {
                    if (!FlashStationBlock.inReach(player)) {
                        throw new InstallException("Kernels can only be installed at a Flash Station");
                    }
                    terminal.checkKernelCooldown();
                    Kernel kernel = terminal.installKernel(name);
                    yield Component.translatable("mhm2027.panel.kernel_installed", kernel.name());
                }
                case BURN -> Component.translatable("mhm2027.panel.burned", ChipActions.burn(player, name));
                case SOCKET -> {
                    requireStation(player);
                    yield Component.translatable("mhm2027.panel.socketed", terminal.socket(name));
                }
                case UNSOCKET -> {
                    requireStation(player);
                    terminal.unsocket(name);
                    yield Component.translatable("mhm2027.panel.unsocketed", displayName(name));
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
        LAST_SCAN.remove(event.getEntity().getUUID());
        LAST_WHEEL_INFO.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        WATCHERS.clear();
        LAST_SCAN.clear();
        LAST_WHEEL_INFO.clear();
    }

    /** "3 run Breach" puts a shortcut in slot 3; "3" alone clears it. Allowed anywhere, even while off. */
    private static void setWheel(ServerPlayer player, String argument) {
        String[] parts = argument.strip().split(" ", 2);
        int slot;
        try {
            slot = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            slot = 0;
        }
        String command = parts.length > 1 ? parts[1].strip() : "";
        String problem = Terminals.isNull(player) ? "wheel: a Null gateway has no wheel"
                : slot < 1 || slot > Wheel.SLOTS ? "wheel: the slots are 1 to " + Wheel.SLOTS
                : command.length() > Wheel.MAX_COMMAND ? "wheel: a shortcut is at most " + Wheel.MAX_COMMAND + " characters" : null;
        if (problem != null) {
            PacketDistributor.sendToPlayer(player, new PanelMessage(false, Component.literal(problem)));
            return;
        }
        player.setData(ModAttachments.WHEEL, player.getData(ModAttachments.WHEEL).with(slot - 1, command));
        player.syncData(ModAttachments.WHEEL);
        PacketDistributor.sendToPlayer(player, new PanelMessage(true,
                Component.literal(command.isEmpty() ? "wheel: slot " + slot + " cleared" : "wheel: slot " + slot + " set to " + command)));
    }

    /** A line on each wheel slot. Within a second of the last answer, the same wheel gets the same answer. */
    private static WheelInfo wheelInfo(ServerPlayer player) {
        Wheel wheel = player.getData(ModAttachments.WHEEL);
        int now = player.level().getServer().getTickCount();
        WheelAnswer last = LAST_WHEEL_INFO.get(player.getUUID());
        if (last != null && last.wheel().equals(wheel) && now - last.tick() < WHEEL_INFO_TICKS) {
            return last.info();
        }
        List<WheelInfo.Row> rows = new ArrayList<>();
        for (String command : wheel.slots()) {
            rows.add(describe(player, command));
        }
        WheelInfo info = new WheelInfo(List.copyOf(rows));
        LAST_WHEEL_INFO.put(player.getUUID(), new WheelAnswer(now, wheel, info));
        return info;
    }

    /** For "run X": a written preset by name, else one of the player's files, as the shell finds them. */
    private static WheelInfo.Row describe(ServerPlayer player, String command) {
        String[] words = command.strip().split(" ", 2);
        if (words.length < 2 || !words[0].equalsIgnoreCase("run") || Terminals.isNull(player)) {
            return WheelInfo.Row.NONE;
        }
        String wanted = words[1].strip();
        if (wanted.toLowerCase(Locale.ROOT).endsWith(".lua")) {
            wanted = wanted.substring(0, wanted.length() - 4);
        }
        for (Identifier id : player.getData(ModAttachments.PRESETS).ids()) {
            PresetRegistry.Preset preset = PresetRegistry.get(id).orElse(null);
            if (preset != null && !preset.isKernel()
                    && (preset.summary().name().equalsIgnoreCase(wanted) || preset.terminalId().equalsIgnoreCase(wanted))) {
                return new WheelInfo.Row(preset.summary().tops() + " TOPS", detail(preset.summary()), preset.description());
            }
        }
        for (ScriptStore.Entry entry : ScriptStore.list(player.level().getServer(), player.getUUID())) {
            if (entry.kind() == ScriptStore.Kind.PROGRAM && entry.name().equalsIgnoreCase(wanted)) {
                try {
                    String source = ScriptStore.read(player.level().getServer(), player.getUUID(), entry.name());
                    ScriptSummary summary = ScriptAnalyzer.analyze(entry.name(), source, false).summary();
                    return new WheelInfo.Row(summary.tops() + " TOPS", detail(summary), "");
                } catch (InstallException e) {
                    return new WheelInfo.Row("won't run", "won't run: " + e.getMessage(), "");
                }
            }
        }
        return new WheelInfo.Row("missing", "no program called " + wanted, "");
    }

    private static String detail(ScriptSummary summary) {
        return summary.kind() + " · " + summary.tops() + " TOPS · " + summary.ramMb() + " MB";
    }

    /**
     * Edge's scan: the gateways remote.scan would return, as rows for the terminal. Free, like
     * the other read-only commands, but limited to one per inspection time.
     */
    private static void scan(ServerPlayer player, Terminal terminal, String argument) {
        try {
            if (Terminals.isNull(player)) {
                throw new InstallException("scan: a Null gateway can't scan");
            }
            terminal.requireOn();
            if (terminal.isRebooting()) {
                throw new InstallException("scan: the gateway is rebooting");
            }
            int now = player.level().getServer().getTickCount();
            Integer last = LAST_SCAN.get(player.getUUID());
            if (last != null && now - last < terminal.kernel().settings().inspectTicks(SCAN_BASE_TICKS)) {
                throw new InstallException("scan: still scanning, try again in a moment");
            }
            LAST_SCAN.put(player.getUUID(), now);
            int range = player.getData(ModAttachments.GATEWAY).tier().scanRange();
            int radius = range;
            if (!argument.isEmpty()) {
                try {
                    radius = Math.clamp(Integer.parseInt(argument), 1, range);
                } catch (NumberFormatException e) {
                    radius = range;
                }
            }
            List<ScanResult.Row> rows = new ArrayList<>();
            for (LivingEntity entity : Gateways.around(player, radius, Gateways.MAX_SCAN_RESULTS)) {
                GatewayData data = Gateways.of(entity);
                rows.add(new ScanResult.Row(Addresses.of(entity.getUUID()), entity.getName().getString(), entity instanceof Player,
                        data == null ? -1 : data.level(), entity.distanceTo(player)));
            }
            PacketDistributor.sendToPlayer(player, new ScanResult(radius, rows));
        } catch (InstallException e) {
            PacketDistributor.sendToPlayer(player, new PanelMessage(false, Component.literal(e.getMessage())));
        }
    }

    private static void requireStation(ServerPlayer player) throws InstallException {
        if (!FlashStationBlock.inReach(player)) {
            throw new InstallException("Sockets can only be changed at a Flash Station");
        }
    }

    /** A storage entry as the player knows it: a preset's name, or the file name. */
    private static String displayName(String id) {
        return PresetRegistry.byTerminalId(id).map(preset -> preset.summary().name()).orElse(id);
    }

    static void sendSnapshot(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, snapshot(player));
    }

    static PanelSnapshot snapshot(ServerPlayer player) {
        if (Terminals.isNull(player)) {
            return PanelSnapshot.EMPTY;
        }
        Terminal terminal = Terminals.get(player);
        Kernel kernel = terminal.kernel();
        KernelSettings settings = kernel.settings();
        PanelSnapshot.KernelInfo kernelInfo = new PanelSnapshot.KernelInfo(player.getData(ModAttachments.GATEWAY).kernel(),
                kernel.name(), settings.schedule(), settings.gc(), settings.verify(), settings.rateLimit(),
                kernel.hookNames(), kernel.ramMb(), kernel.topsReserve(), kernel.isPreset());
        List<PanelSnapshot.ProgramRow> programs = terminal.programs().stream()
                .map(program -> new PanelSnapshot.ProgramRow(program.id(), program.name(), program.type().id(), program.ramMb(),
                        program.tops(), (float) program.averageTops(), (float) program.peakTops(), program.state(terminal.now()),
                        program.isPreset(), progress(program.awaiting())))
                .toList();

        // Storage: the player's own files, then the presets written into the gateway.
        List<PanelSnapshot.ScriptRow> scripts = new ArrayList<>();
        for (ScriptStore.Entry entry : ScriptStore.list(player.level().getServer(), player.getUUID())) {
            if (entry.kind() != ScriptStore.Kind.UNKNOWN) {
                scripts.add(new PanelSnapshot.ScriptRow(entry.name(), entry.name(), entry.kind() == ScriptStore.Kind.KERNEL, false));
            }
        }
        for (Identifier id : player.getData(ModAttachments.PRESETS).ids()) {
            PresetRegistry.get(id).ifPresent(preset -> scripts.add(
                    new PanelSnapshot.ScriptRow(preset.terminalId(), preset.summary().name(), preset.isKernel(), true)));
        }

        List<PanelSnapshot.FlawRow> flaws = new ArrayList<>();
        FlawRules.Profile profile = terminal.flawProfile();
        for (FlawRules.Hit hit : profile.active()) {
            flaws.add(new PanelSnapshot.FlawRow(hit.className(), hit.severity().id(), hit.cause(), false));
        }
        for (FlawRules.Approach approach : profile.approaching()) {
            flaws.add(new PanelSnapshot.FlawRow(approach.className(), approach.severity().id(), approach.cause(), true));
        }

        List<PanelSnapshot.SocketRow> sockets = new ArrayList<>();
        Sockets loadout = terminal.sockets(player);
        loadout.standard().forEach(id -> sockets.add(socketRow(false, id)));
        loadout.overload().forEach(id -> sockets.add(socketRow(true, id)));

        return new PanelSnapshot(kernelInfo, programs, scripts, terminal.outputs().outputs(), terminal.outputs().kernel(),
                terminal.isRebooting() ? terminal.lockReason() : "", terminal.isRebooting() ? terminal.lockSecondsLeft() : 0, flaws,
                sockets, FlashStationBlock.inReach(player), terminal.overloadLockSecondsLeft());
    }

    private static PanelSnapshot.Progress progress(LuaSandbox.@Nullable Awaitable waiting) {
        if (waiting == null || waiting.totalTicks() <= 0) {
            return PanelSnapshot.Progress.NONE;
        }
        return new PanelSnapshot.Progress(waiting.detail(), waiting.totalTicks(), Math.min(waiting.ticksLeft(), waiting.totalTicks()));
    }

    private static PanelSnapshot.SocketRow socketRow(boolean overload, String id) {
        return new PanelSnapshot.SocketRow(overload, id, id.isEmpty() ? "" : displayName(id), id.startsWith(PresetRegistry.PREFIX));
    }
}
