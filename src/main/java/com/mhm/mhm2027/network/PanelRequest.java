package com.mhm.mhm2027.network;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server: something the player did in the control panel. The server checks
 * everything again; the panel is only a remote control for the player's own terminal.
 */
public record PanelRequest(Action action, String argument) implements CustomPacketPayload {
    public enum Action {
        /** The panel opened: start sending snapshots. */
        OPEN,
        /** The panel closed: stop sending snapshots. */
        CLOSE,
        START,
        STOP,
        REBOOT,
        INSTALL_KERNEL
    }

    public static final Type<PanelRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "panel_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PanelRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(id -> Action.values()[id], Action::ordinal), PanelRequest::action,
            ByteBufCodecs.stringUtf8(64), PanelRequest::argument,
            PanelRequest::new);

    public static PanelRequest of(Action action) {
        return new PanelRequest(action, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
