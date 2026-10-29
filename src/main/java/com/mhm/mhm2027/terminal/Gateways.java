package com.mhm.mhm2027.terminal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mhm.mhm2027.Config;
import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Questions about other entities' gateways, shared by the remote APIs. Reading a mob never
 * creates gateway data on it: ordinary vanilla mobs stay untouched (LV-1, no gateway).
 */
final class Gateways {
    private Gateways() {
    }

    /** The entity's gateway, or null for anything without one: vanilla mobs, Null players, spectators, the dead. */
    static @Nullable GatewayData of(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return null;
        }
        GatewayData data;
        if (living instanceof Player player) {
            if (player.isSpectator()) {
                return null;
            }
            data = player.getData(ModAttachments.GATEWAY);
        } else if (living.hasData(ModAttachments.GATEWAY)) {
            data = living.getData(ModAttachments.GATEWAY);
        } else {
            return null;
        }
        return data.tier().isNull() ? null : data;
    }

    /** Whether a player's programs may aim at this entity at all. */
    static boolean targetable(Entity entity, ServerPlayer viewer) {
        return entity != viewer && of(entity) != null && (!(entity instanceof Player) || Config.PLAYER_TARGETS.get());
    }

    /**
     * The flaws an inspection shows: exactly what the target itself sees. A player's come from
     * their terminal; a mob's from its native kernel settings and current state.
     */
    static List<FlawRules.Hit> flawsOf(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            return Terminals.existing(player).map(Terminal::flaws).orElse(List.of());
        }
        GatewayData data = of(entity);
        if (data == null) {
            return List.of();
        }
        KernelSettings settings = entity.getData(ModAttachments.MOB_KERNEL);
        FlawRules.Reading reading = FlawRules.Reading.of(settings, 0, data.heat(), 0, 0, data.tier().hookPoints());
        return FlawRules.evaluate(FlawRegistry.classes(), reading).active();
    }
}
