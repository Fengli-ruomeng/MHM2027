package com.mhm.mhm2027.network;

import java.util.List;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: what Edge's scan found, nearest first. */
public record ScanResult(int radius, List<Row> rows) implements CustomPacketPayload {
    /**
     * One gateway, as a remote.scan handle would show it.
     *
     * @param level its gateway level, or -1 if it can't be read
     */
    public record Row(String address, String name, boolean player, int level, float distance) {
        static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Row::address,
                ByteBufCodecs.STRING_UTF8, Row::name,
                ByteBufCodecs.BOOL, Row::player,
                ByteBufCodecs.VAR_INT, Row::level,
                ByteBufCodecs.FLOAT, Row::distance,
                Row::new);
    }

    public static final Type<ScanResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "scan_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanResult> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScanResult::radius,
            Row.STREAM_CODEC.apply(ByteBufCodecs.list()), ScanResult::rows,
            ScanResult::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
