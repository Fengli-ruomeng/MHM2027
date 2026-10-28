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
        String kernelName,
        String schedule,
        String gc,
        String verify,
        int rateLimit,
        List<String> hooks,
        int kernelRamMb,
        int kernelTops,
        List<ProgramRow> programs,
        List<ScriptRow> scripts,
        List<String> log,
        String lockReason,
        int lockSecondsLeft) implements CustomPacketPayload {

    public record ProgramRow(String name, String type, int ramMb, int tops, float averageTops, float peakTops, String state) {
        static final StreamCodec<RegistryFriendlyByteBuf, ProgramRow> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ProgramRow::name,
                ByteBufCodecs.STRING_UTF8, ProgramRow::type,
                ByteBufCodecs.VAR_INT, ProgramRow::ramMb,
                ByteBufCodecs.VAR_INT, ProgramRow::tops,
                ByteBufCodecs.FLOAT, ProgramRow::averageTops,
                ByteBufCodecs.FLOAT, ProgramRow::peakTops,
                ByteBufCodecs.STRING_UTF8, ProgramRow::state,
                ProgramRow::new);
    }

    /** A file in the player's storage. kernel is true for kernel scripts. */
    public record ScriptRow(String name, boolean kernel) {
        static final StreamCodec<RegistryFriendlyByteBuf, ScriptRow> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ScriptRow::name,
                ByteBufCodecs.BOOL, ScriptRow::kernel,
                ScriptRow::new);
    }

    public static final Type<PanelSnapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "panel_snapshot"));

    // composite takes at most 12 fields, so the lock status rides in a small pair.
    private record Lock(String reason, int secondsLeft) {
        static final StreamCodec<RegistryFriendlyByteBuf, Lock> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Lock::reason,
                ByteBufCodecs.VAR_INT, Lock::secondsLeft,
                Lock::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PanelSnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PanelSnapshot::kernelName,
            ByteBufCodecs.STRING_UTF8, PanelSnapshot::schedule,
            ByteBufCodecs.STRING_UTF8, PanelSnapshot::gc,
            ByteBufCodecs.STRING_UTF8, PanelSnapshot::verify,
            ByteBufCodecs.VAR_INT, PanelSnapshot::rateLimit,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PanelSnapshot::hooks,
            ByteBufCodecs.VAR_INT, PanelSnapshot::kernelRamMb,
            ByteBufCodecs.VAR_INT, PanelSnapshot::kernelTops,
            ProgramRow.STREAM_CODEC.apply(ByteBufCodecs.list()), PanelSnapshot::programs,
            ScriptRow.STREAM_CODEC.apply(ByteBufCodecs.list()), PanelSnapshot::scripts,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PanelSnapshot::log,
            Lock.STREAM_CODEC, snapshot -> new Lock(snapshot.lockReason(), snapshot.lockSecondsLeft()),
            (kernelName, schedule, gc, verify, rateLimit, hooks, kernelRamMb, kernelTops, programs, scripts, log, lock) ->
                    new PanelSnapshot(kernelName, schedule, gc, verify, rateLimit, hooks, kernelRamMb, kernelTops,
                            programs, scripts, log, lock.reason(), lock.secondsLeft()));

    public static final PanelSnapshot EMPTY = new PanelSnapshot("", "", "", "", 0, List.of(), 0, 0,
            List.of(), List.of(), List.of(), "", 0);

    public boolean rebooting() {
        return lockSecondsLeft > 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
