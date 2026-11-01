package com.mhm.mhm2027.hud;

import java.util.List;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.terminal.Terminal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: what the HUD shows that the synced gateway data doesn't carry. Sent when
 * it changes, at most twice a second.
 *
 * @param flash how far a firmware flash has got, 0 to 1; -1 when there is none
 * @param power off, booting or on: the HUD hides itself while the gateway is off
 */
public record HudState(List<Slot> slots, float flash, Terminal.Power power) implements CustomPacketPayload {
    /**
     * One socket.
     *
     * @param label a short label for what sits in it, empty when the socket is empty
     * @param fill  0 to 1: an Overload socket's share of max Heat
     */
    public record Slot(boolean overload, String label, boolean running, float fill) {
        static final StreamCodec<RegistryFriendlyByteBuf, Slot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Slot::overload,
                ByteBufCodecs.STRING_UTF8, Slot::label,
                ByteBufCodecs.BOOL, Slot::running,
                ByteBufCodecs.FLOAT, Slot::fill,
                Slot::new);

        public boolean empty() {
            return label.isEmpty();
        }
    }

    public static final HudState EMPTY = new HudState(List.of(), -1, Terminal.Power.ON);
    /** What the client assumes before the server says otherwise: a gateway starts off. */
    public static final HudState OFFLINE = new HudState(List.of(), -1, Terminal.Power.OFF);
    public static final Type<HudState> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "hud_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudState> STREAM_CODEC = StreamCodec.composite(
            Slot.STREAM_CODEC.apply(ByteBufCodecs.list()), HudState::slots,
            ByteBufCodecs.FLOAT, HudState::flash,
            ByteBufCodecs.idMapper(id -> Terminal.Power.values()[id], Terminal.Power::ordinal), HudState::power,
            HudState::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
