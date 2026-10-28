package com.mhm.mhm2027.network;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server: a code editor action on one of the player's own files. The server only
 * ever touches the requesting player's storage, and checks every name and size again.
 *
 * @param argument   the new name for RENAME
 * @param content    the file text for SAVE and CHECK
 * @param requestId  echoed back with a CHECK result, so the editor can ignore stale answers
 */
public record EditorRequest(Action action, String name, String argument, String content, int requestId) implements CustomPacketPayload {
    public enum Action {
        OPEN, SAVE, CREATE_PROGRAM, CREATE_KERNEL, RENAME, DELETE, CHECK
    }

    /** Scripts are capped at 64 KB; this leaves room for multi-byte characters. */
    private static final int MAX_CONTENT = 256 * 1024;

    public static final Type<EditorRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "editor_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(id -> Action.values()[id], Action::ordinal), EditorRequest::action,
            ByteBufCodecs.stringUtf8(64), EditorRequest::name,
            ByteBufCodecs.stringUtf8(64), EditorRequest::argument,
            ByteBufCodecs.stringUtf8(MAX_CONTENT), EditorRequest::content,
            ByteBufCodecs.VAR_INT, EditorRequest::requestId,
            EditorRequest::new);

    public static EditorRequest of(Action action, String name) {
        return new EditorRequest(action, name, "", "", 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
