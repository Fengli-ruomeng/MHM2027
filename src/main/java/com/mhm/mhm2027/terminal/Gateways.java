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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Questions about other entities' gateways, shared by the remote APIs. Reading a mob never
 * creates gateway data on it: ordinary vanilla mobs stay untouched (LV-1, no gateway).
 */
public final class Gateways {
    private Gateways() {
    }

    /** The entity's gateway, or null for anything without one: vanilla mobs, Null players, spectators, the dead. */
    public static @Nullable GatewayData of(Entity entity) {
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

    /** The gateway entity under the crosshair, up to {@code range} blocks, not through walls. */
    public static @Nullable LivingEntity crosshair(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult wall = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }
        AABB area = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
                entity -> Gateways.targetable(entity, player), eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /** Whether a player's programs may aim at this entity at all. */
    static boolean targetable(Entity entity, ServerPlayer viewer) {
        return entity != viewer && of(entity) != null && (!(entity instanceof Player) || Config.PLAYER_TARGETS.get());
    }

    /**
     * The flaws an inspection shows: exactly what the target itself sees. A player's come from
     * their terminal; a mob's from its native kernel settings and current state.
     */
    public static List<FlawRules.Hit> flawsOf(LivingEntity entity) {
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
