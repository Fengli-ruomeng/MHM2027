package com.mhm.mhm2027.hud;

import java.util.List;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: the gateways within scan range, for the target callout. The client picks
 * whichever is under the crosshair each frame, so the brackets lock on at once. What's shown
 * of each is what a free scan would show, plus what this player has learned: flaws from an
 * inspection in the last minute, their session on it, and an attempt under way.
 */
public record HudTargets(List<Seen> seen) implements CustomPacketPayload {
    public record Flaw(String className, String severity) {
        static final StreamCodec<RegistryFriendlyByteBuf, Flaw> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Flaw::className,
                ByteBufCodecs.STRING_UTF8, Flaw::severity,
                Flaw::new);
    }

    /**
     * One gateway.
     *
     * @param entityId      the entity's network id, so the client can follow it
     * @param inspectedAgo  ticks since this player inspected it, or -1 if not in the last minute
     * @param flaws         what that inspection found (empty if none, or not inspected)
     * @param session       "", "user" or "root": this player's session on it
     * @param sessionLeft   ticks before that session closes
     * @param attempt       "", "accessing", "escalating" or "scanning": this player's program at work on it
     * @param attemptTotal  the attempt's whole span, in ticks
     * @param attemptLeft   ticks still to go
     */
    public record Seen(int entityId, String name, String address, int level, boolean player, int inspectedAgo, List<Flaw> flaws,
                       String session, int sessionLeft, String attempt, int attemptTotal, int attemptLeft) {
        static final StreamCodec<RegistryFriendlyByteBuf, Seen> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Seen::entityId,
                ByteBufCodecs.STRING_UTF8, Seen::name,
                ByteBufCodecs.STRING_UTF8, Seen::address,
                ByteBufCodecs.VAR_INT, Seen::level,
                ByteBufCodecs.BOOL, Seen::player,
                ByteBufCodecs.VAR_INT, Seen::inspectedAgo,
                Flaw.STREAM_CODEC.apply(ByteBufCodecs.list()), Seen::flaws,
                ByteBufCodecs.STRING_UTF8, Seen::session,
                ByteBufCodecs.VAR_INT, Seen::sessionLeft,
                ByteBufCodecs.STRING_UTF8, Seen::attempt,
                ByteBufCodecs.VAR_INT, Seen::attemptTotal,
                ByteBufCodecs.VAR_INT, Seen::attemptLeft,
                Seen::new);

        public boolean inspected() {
            return inspectedAgo >= 0;
        }
    }

    public static final HudTargets NONE = new HudTargets(List.of());
    public static final Type<HudTargets> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "hud_targets"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudTargets> STREAM_CODEC = StreamCodec.composite(
            Seen.STREAM_CODEC.apply(ByteBufCodecs.list()), HudTargets::seen,
            HudTargets::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
