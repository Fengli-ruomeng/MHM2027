package com.mhm.mhm2027.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A copy of a player's own code, carried on a chip.
 *
 * @param file   the file name it was burned from, and is written back under
 * @param author who burned it
 */
public record ScriptCode(String file, String source, String author) {
    public static final Codec<ScriptCode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("file").forGetter(ScriptCode::file),
            Codec.STRING.fieldOf("source").forGetter(ScriptCode::source),
            Codec.STRING.optionalFieldOf("author", "").forGetter(ScriptCode::author)
    ).apply(instance, ScriptCode::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScriptCode> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ScriptCode::file,
            ByteBufCodecs.stringUtf8(64 * 1024), ScriptCode::source,
            ByteBufCodecs.STRING_UTF8, ScriptCode::author,
            ScriptCode::new);
}
