package com.mhm.mhm2027.network;

import java.util.List;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: what the control panel shows about the player's terminal. Sent when the
 * panel opens, after every action, and twice a second while it stays open. The gateway's own
 * numbers (RAM, TOPS, Buffer, Heat) come from the synced attachment instead.
 */
public record PanelSnapshot(
        KernelInfo kernel,
        List<ProgramRow> programs,
        List<ScriptRow> scripts,
        List<String> log,
        String lockReason,
        int lockSecondsLeft,
        List<FlawRow> flaws,
        List<SocketRow> sockets,
        boolean atStation,
        int overloadLockSeconds) implements CustomPacketPayload {

    /**
     * @param id what it was installed from: "Baseline", a file name, or {@code preset:<id>}
     */
    public record KernelInfo(String id, String name, String schedule, String gc, String verify, int rateLimit,
                             List<String> hooks, int ramMb, int tops, boolean preset) {
        static final StreamCodec<RegistryFriendlyByteBuf, KernelInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, KernelInfo::id,
                ByteBufCodecs.STRING_UTF8, KernelInfo::name,
                ByteBufCodecs.STRING_UTF8, KernelInfo::schedule,
                ByteBufCodecs.STRING_UTF8, KernelInfo::gc,
                ByteBufCodecs.STRING_UTF8, KernelInfo::verify,
                ByteBufCodecs.VAR_INT, KernelInfo::rateLimit,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), KernelInfo::hooks,
                ByteBufCodecs.VAR_INT, KernelInfo::ramMb,
                ByteBufCodecs.VAR_INT, KernelInfo::tops,
                ByteBufCodecs.BOOL, KernelInfo::preset,
                KernelInfo::new);

        static final KernelInfo EMPTY = new KernelInfo("", "", "", "", "", 0, List.of(), 0, 0, false);
    }

    /**
     * @param id what started it: a file name, or {@code preset:<id>}
     */
    public record ProgramRow(String id, String name, String type, int ramMb, int tops, float averageTops, float peakTops,
                             String state, boolean preset) {
        static final StreamCodec<RegistryFriendlyByteBuf, ProgramRow> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ProgramRow::id,
                ByteBufCodecs.STRING_UTF8, ProgramRow::name,
                ByteBufCodecs.STRING_UTF8, ProgramRow::type,
                ByteBufCodecs.VAR_INT, ProgramRow::ramMb,
                ByteBufCodecs.VAR_INT, ProgramRow::tops,
                ByteBufCodecs.FLOAT, ProgramRow::averageTops,
                ByteBufCodecs.FLOAT, ProgramRow::peakTops,
                ByteBufCodecs.STRING_UTF8, ProgramRow::state,
                ByteBufCodecs.BOOL, ProgramRow::preset,
                ProgramRow::new);
    }

    /**
     * One flaw the panel shows. {@code approaching} is a near-miss: the severity is the one
     * the player is close to, and the flaw is not active yet.
     */
    public record FlawRow(String className, String severity, String cause, boolean approaching) {
        static final StreamCodec<RegistryFriendlyByteBuf, FlawRow> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, FlawRow::className,
                ByteBufCodecs.STRING_UTF8, FlawRow::severity,
                ByteBufCodecs.STRING_UTF8, FlawRow::cause,
                ByteBufCodecs.BOOL, FlawRow::approaching,
                FlawRow::new);
    }

    /**
     * Something in the player's storage: one of their own files, or a preset written into the gateway.
     *
     * @param id the file name, or {@code preset:<id>}
     */
    public record ScriptRow(String id, String name, boolean kernel, boolean preset) {
        static final StreamCodec<RegistryFriendlyByteBuf, ScriptRow> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ScriptRow::id,
                ByteBufCodecs.STRING_UTF8, ScriptRow::name,
                ByteBufCodecs.BOOL, ScriptRow::kernel,
                ByteBufCodecs.BOOL, ScriptRow::preset,
                ScriptRow::new);
    }

    /**
     * One socket, standard ones first.
     *
     * @param id what sits in it (a file name or {@code preset:<id>}), or empty
     */
    public record SocketRow(boolean overload, String id, String name, boolean preset) {
        static final StreamCodec<RegistryFriendlyByteBuf, SocketRow> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SocketRow::overload,
                ByteBufCodecs.STRING_UTF8, SocketRow::id,
                ByteBufCodecs.STRING_UTF8, SocketRow::name,
                ByteBufCodecs.BOOL, SocketRow::preset,
                SocketRow::new);

        public boolean empty() {
            return id.isEmpty();
        }
    }

    public static final Type<PanelSnapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "panel_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PanelSnapshot> STREAM_CODEC = StreamCodec.composite(
            KernelInfo.STREAM_CODEC, PanelSnapshot::kernel,
            ProgramRow.STREAM_CODEC.apply(ByteBufCodecs.list()), PanelSnapshot::programs,
            ScriptRow.STREAM_CODEC.apply(ByteBufCodecs.list()), PanelSnapshot::scripts,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PanelSnapshot::log,
            ByteBufCodecs.STRING_UTF8, PanelSnapshot::lockReason,
            ByteBufCodecs.VAR_INT, PanelSnapshot::lockSecondsLeft,
            FlawRow.STREAM_CODEC.apply(ByteBufCodecs.list()), PanelSnapshot::flaws,
            SocketRow.STREAM_CODEC.apply(ByteBufCodecs.list()), PanelSnapshot::sockets,
            ByteBufCodecs.BOOL, PanelSnapshot::atStation,
            ByteBufCodecs.VAR_INT, PanelSnapshot::overloadLockSeconds,
            PanelSnapshot::new);

    public static final PanelSnapshot EMPTY = new PanelSnapshot(KernelInfo.EMPTY, List.of(), List.of(), List.of(), "", 0, List.of(),
            List.of(), false, 0);

    public boolean socketed(String id) {
        return sockets.stream().anyMatch(socket -> socket.id().equals(id));
    }

    public boolean rebooting() {
        return lockSecondsLeft > 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
