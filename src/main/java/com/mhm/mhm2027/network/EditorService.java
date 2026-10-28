package com.mhm.mhm2027.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.terminal.InstallException;
import com.mhm.mhm2027.terminal.ScriptDiagnostics;
import com.mhm.mhm2027.terminal.ScriptStore;
import com.mhm.mhm2027.terminal.ScriptSummary;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The server side of the in-game code editor: file operations on the player's own storage,
 * and the live check. Presets are never files, so they can't be opened here.
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public final class EditorService {
    /** Live checks compile and run top-level code, so each player gets at most a few per second. */
    private static final int CHECK_INTERVAL_TICKS = 4;
    private static final Map<UUID, Integer> LAST_CHECK = new HashMap<>();

    private EditorService() {
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(EditorRequest.TYPE, EditorRequest.STREAM_CODEC, EditorService::handle);
        registrar.playToClient(EditorFile.TYPE, EditorFile.STREAM_CODEC);
        registrar.playToClient(EditorCheck.TYPE, EditorCheck.STREAM_CODEC);
    }

    private static void handle(EditorRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        UUID uuid = player.getUUID();
        String name = request.name();
        try {
            if (Terminals.isNull(player)) {
                throw new InstallException("A Null gateway has no storage");
            }
            switch (request.action()) {
                case OPEN -> PacketDistributor.sendToPlayer(player, new EditorFile(name, ScriptStore.read(server, uuid, name)));
                case SAVE -> {
                    ScriptStore.save(server, uuid, name, request.content());
                    reply(player, true, Component.translatable("mhm2027.editor.saved", name + ".lua"));
                }
                case CREATE_PROGRAM, CREATE_KERNEL -> {
                    String template = request.action() == EditorRequest.Action.CREATE_KERNEL ? kernelTemplate(name) : programTemplate(name);
                    ScriptStore.create(server, uuid, name, template);
                    PacketDistributor.sendToPlayer(player, new EditorFile(name, template));
                    reply(player, true, Component.translatable("mhm2027.editor.created", name + ".lua"));
                }
                case RENAME -> {
                    ScriptStore.rename(server, uuid, name, request.argument());
                    PacketDistributor.sendToPlayer(player, new EditorFile(request.argument(), ScriptStore.read(server, uuid, request.argument())));
                    reply(player, true, Component.translatable("mhm2027.editor.renamed", name + ".lua", request.argument() + ".lua"));
                }
                case DELETE -> {
                    ScriptStore.delete(server, uuid, name);
                    PacketDistributor.sendToPlayer(player, new EditorFile("", ""));
                    reply(player, true, Component.translatable("mhm2027.editor.deleted", name + ".lua"));
                }
                case CHECK -> check(player, request);
            }
        } catch (InstallException e) {
            reply(player, false, Component.literal(e.getMessage()));
        }
        if (request.action() != EditorRequest.Action.CHECK && request.action() != EditorRequest.Action.OPEN) {
            PanelService.sendSnapshot(player);
        }
    }

    private static void check(ServerPlayer player, EditorRequest request) {
        int now = player.level().getServer().getTickCount();
        Integer last = LAST_CHECK.get(player.getUUID());
        if (last != null && now - last < CHECK_INTERVAL_TICKS) {
            return;
        }
        LAST_CHECK.put(player.getUUID(), now);
        ScriptDiagnostics.Result result = ScriptDiagnostics.check(request.name(), request.content(), Terminals.level(player));
        ScriptSummary summary = result.summary();
        PacketDistributor.sendToPlayer(player, new EditorCheck(request.requestId(), result.ok(), result.message(), result.line(),
                summary == null ? "" : summary.kind(), summary == null ? 0 : summary.tops(), summary == null ? 0 : summary.ramMb(),
                summary == null ? 0 : summary.level(), summary == null ? List.of() : summary.details(), result.warnings()));
    }

    private static void reply(ServerPlayer player, boolean success, Component message) {
        PacketDistributor.sendToPlayer(player, new PanelMessage(success, message));
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_CHECK.remove(event.getEntity().getUUID());
    }

    private static String programTemplate(String name) {
        return """
                program { name = "%s", type = "resident", tops = 2 }

                function on_start()
                  sys.log("%s started")
                end
                """.formatted(name, name);
    }

    private static String kernelTemplate(String name) {
        return """
                kernel { name = "%s", schedule = "fair", gc = "normal", verify = "full", rate_limit = 4 }

                -- Kernels define hooks. Your gateway's hook points limit how many.
                function on_panic(reason)
                  sys.log("recovered from " .. reason)
                end
                """.formatted(name);
    }
}
