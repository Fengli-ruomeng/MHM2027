package com.mhm.mhm2027.network;

import java.util.List;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: the live check of the code being edited, the same analysis an install runs.
 *
 * @param line  0-based line of the error or first warning, or -1
 * @param kind  resident, transient, query or kernel; empty when there is an error
 * @param level own code: the highest API level used
 */
public record EditorCheck(int requestId, boolean ok, String message, int line, String kind, int tops, int ramMb, int level,
                          List<String> details, List<String> warnings) implements CustomPacketPayload {
    public static final Type<EditorCheck> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "editor_check"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorCheck> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EditorCheck::requestId,
            ByteBufCodecs.BOOL, EditorCheck::ok,
            ByteBufCodecs.STRING_UTF8, EditorCheck::message,
            ByteBufCodecs.VAR_INT, EditorCheck::line,
            ByteBufCodecs.STRING_UTF8, EditorCheck::kind,
            ByteBufCodecs.VAR_INT, EditorCheck::tops,
            ByteBufCodecs.VAR_INT, EditorCheck::ramMb,
            ByteBufCodecs.VAR_INT, EditorCheck::level,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), EditorCheck::details,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), EditorCheck::warnings,
            EditorCheck::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
