package com.mhm.mhm2027.network;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: a file to show in the editor. An empty name means "nothing open" (after a delete). */
public record EditorFile(String name, String content) implements CustomPacketPayload {
    public static final Type<EditorFile> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "editor_file"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorFile> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), EditorFile::name,
            ByteBufCodecs.stringUtf8(256 * 1024), EditorFile::content,
            EditorFile::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
