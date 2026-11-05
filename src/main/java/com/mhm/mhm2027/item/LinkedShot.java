package com.mhm.mhm2027.item;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What an arrow from a Programmable Bow is doing. It rides on the arrow and is synced to everyone
 * who can see it, so their clients steer it exactly as the server does.
 *
 * @param target  the entity it's chasing, by network id, or -1
 * @param speed   the speed it holds while tracking or wild, in blocks per tick; 0 until its first tick
 * @param blocked ticks its target has been out of sight behind a block
 */
public record LinkedShot(Mode mode, int target, float speed, int blocked) {
    public enum Mode {
        /** Chasing the nearest gateway its shooter has a session on. */
        TRACKING,
        /** Fired with nothing to lock onto: it wanders, then falls. */
        WILD,
        /** An ordinary arrow again. */
        FREE
    }

    public static final LinkedShot NONE = new LinkedShot(Mode.FREE, -1, 0, 0);

    public static final StreamCodec<ByteBuf, LinkedShot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(id -> Mode.values()[id], Mode::ordinal), LinkedShot::mode,
            ByteBufCodecs.VAR_INT, LinkedShot::target,
            ByteBufCodecs.FLOAT, LinkedShot::speed,
            ByteBufCodecs.VAR_INT, LinkedShot::blocked,
            LinkedShot::new);
}
