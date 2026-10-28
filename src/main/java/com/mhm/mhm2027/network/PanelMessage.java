package com.mhm.mhm2027.network;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: the result of a panel action, shown on the panel's status line. */
public record PanelMessage(boolean success, Component text) implements CustomPacketPayload {
    public static final Type<PanelMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "panel_message"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PanelMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PanelMessage::success,
            ComponentSerialization.STREAM_CODEC, PanelMessage::text,
            PanelMessage::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
