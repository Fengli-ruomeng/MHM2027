package com.mhm.mhm2027.hud;

import java.util.List;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: the gateway is booting. The client plays the boot sequence over the
 * whole screen for exactly as long as the reboot lasts, with this gateway's real boot log.
 *
 * @param reason "startup" when joining, else why it rebooted ("reboot", "kernel panic", ...)
 */
public record HudBoot(String reason, int ticks, List<Line> lines) implements CustomPacketPayload {
    public static final String STARTUP = "startup";

    /** One line of the boot log. */
    public record Line(String unit, String text, Tone tone) {
        static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Line::unit,
                ByteBufCodecs.STRING_UTF8, Line::text,
                ByteBufCodecs.idMapper(id -> Tone.values()[id], Tone::ordinal), Line::tone,
                Line::new);
    }

    public enum Tone {
        PLAIN, OK, FLAW, WARN
    }

    public static final Type<HudBoot> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "hud_boot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudBoot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HudBoot::reason,
            ByteBufCodecs.VAR_INT, HudBoot::ticks,
            Line.STREAM_CODEC.apply(ByteBufCodecs.list()), HudBoot::lines,
            HudBoot::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
