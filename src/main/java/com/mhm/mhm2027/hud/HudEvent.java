package com.mhm.mhm2027.hud;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: one event card for the HUD, in place of a chat or action-bar message.
 *
 * @param tag    who is speaking and on which channel, shown as "SOURCE // CHANNEL"
 * @param detail a second, quieter line; may be empty
 * @param key    a card with the same key replaces the old one instead of stacking; empty for none
 * @param ticks  how long it stays; 0 for the kind's default
 * @param banner a warning banner instead of a card: the title alone, in large type
 */
public record HudEvent(Kind kind, String tag, Component title, Component detail, String key, int ticks, boolean banner)
        implements CustomPacketPayload {
    public enum Kind {
        /** Something you did, or your programs telling you something. */
        INFO,
        /** Needs your attention: a refusal, a crash, a flash in progress. */
        WARN,
        /** Someone or something is acting against you. */
        ALERT,
        /** Background noise. */
        QUIET
    }

    public static final Type<HudEvent> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "hud_event"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudEvent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(id -> Kind.values()[id], Kind::ordinal), HudEvent::kind,
            ByteBufCodecs.STRING_UTF8, HudEvent::tag,
            ComponentSerialization.STREAM_CODEC, HudEvent::title,
            ComponentSerialization.STREAM_CODEC, HudEvent::detail,
            ByteBufCodecs.STRING_UTF8, HudEvent::key,
            ByteBufCodecs.VAR_INT, HudEvent::ticks,
            ByteBufCodecs.BOOL, HudEvent::banner,
            HudEvent::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
