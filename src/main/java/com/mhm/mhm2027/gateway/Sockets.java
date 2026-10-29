package com.mhm.mhm2027.gateway;

import java.util.List;

import com.mhm.mhm2027.terminal.SocketRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What sits in the gateway's sockets: terminal ids from its storage. Standard sockets hold
 * programs that change the body; Overload sockets hold Overload programs. Changed only at a
 * Flash Station. Kept on death, like the level: nothing here is a physical item.
 */
public record Sockets(List<String> standard, List<String> overload) {
    public static final Sockets EMPTY = new Sockets(List.of(), List.of());
    public static final Codec<Sockets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("standard", List.of()).forGetter(Sockets::standard),
            Codec.STRING.listOf().optionalFieldOf("overload", List.of()).forGetter(Sockets::overload)
    ).apply(instance, Sockets::new));

    /** Sized to the gateway: a level-up adds empty sockets. */
    public Sockets fitted(GatewayTier tier) {
        return new Sockets(SocketRules.fit(standard, tier.standardSlots()), SocketRules.fit(overload, tier.overloadSlots()));
    }

    public boolean holds(String id, boolean overloadSocket) {
        return (overloadSocket ? overload : standard).contains(id);
    }

    public boolean holds(String id) {
        return standard.contains(id) || overload.contains(id);
    }

    public Sockets with(boolean overloadSocket, int index, String id) {
        return overloadSocket ? new Sockets(standard, SocketRules.with(overload, index, id)) : new Sockets(SocketRules.with(standard, index, id), overload);
    }

    public Sockets without(String id) {
        return new Sockets(SocketRules.without(standard, id), SocketRules.without(overload, id));
    }
}
