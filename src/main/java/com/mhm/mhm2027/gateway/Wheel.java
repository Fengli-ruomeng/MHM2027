package com.mhm.mhm2027.gateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The quick wheel's eight shortcuts: an Edge command in each, such as "run Breach", or empty.
 * Set from Edge's terminal anywhere; kept on death, like the sockets.
 */
public record Wheel(List<String> slots) {
    public static final int SLOTS = 8;
    public static final int MAX_COMMAND = 64;
    public static final Wheel EMPTY = new Wheel(Collections.nCopies(SLOTS, ""));
    public static final Codec<Wheel> CODEC = Codec.STRING.listOf().xmap(Wheel::of, Wheel::slots);
    public static final StreamCodec<ByteBuf, Wheel> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())
            .map(Wheel::of, Wheel::slots);

    /** Always eight slots, whatever was saved. */
    public static Wheel of(List<String> slots) {
        List<String> fitted = new ArrayList<>(slots.subList(0, Math.min(SLOTS, slots.size())));
        while (fitted.size() < SLOTS) {
            fitted.add("");
        }
        return new Wheel(List.copyOf(fitted));
    }

    /** {@code index} is 0-based; an empty command clears the slot. */
    public Wheel with(int index, String command) {
        List<String> changed = new ArrayList<>(slots);
        changed.set(index, command);
        return new Wheel(List.copyOf(changed));
    }
}
