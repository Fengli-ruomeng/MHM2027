package com.mhm.mhm2027.network;

import java.util.List;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client, when the quick wheel opens: a line on each slot. For "run X", the
 * program's type and cost and, for a preset, its description; other commands get nothing.
 */
public record WheelInfo(List<Row> rows) implements CustomPacketPayload {
    /**
     * @param cost        short enough for the slot: "4 TOPS"; empty for other commands
     * @param detail      "transient · 4 TOPS · 14 MB", or why it won't run; empty for other commands
     * @param description a preset's description, else empty
     */
    public record Row(String cost, String detail, String description) {
        public static final Row NONE = new Row("", "", "");

        static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Row::cost,
                ByteBufCodecs.STRING_UTF8, Row::detail,
                ByteBufCodecs.STRING_UTF8, Row::description,
                Row::new);
    }

    public static final Type<WheelInfo> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "wheel_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WheelInfo> STREAM_CODEC = StreamCodec.composite(
            Row.STREAM_CODEC.apply(ByteBufCodecs.list()), WheelInfo::rows,
            WheelInfo::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
