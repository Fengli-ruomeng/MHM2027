package com.mhm.mhm2027.item;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.terminal.Sessions;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The Programmable Bow's arrows. Fired while you hold a session on some gateway, an arrow locks
 * onto the nearest one and steers for it every tick, following it as it moves. It lets go, and
 * is an ordinary arrow again, when no linked gateway is within 50 blocks (the session ended, or
 * it's too far), when they've all been out of sight for half a second, or after 10 seconds.
 * Fired with no session open, it has nothing to aim for and flies wild.
 *
 * Both sides steer, with the same rules ({@link BowRules}), so the flight stays smooth; the
 * server decides the target and sends the arrow's motion every tick to keep clients in line.
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public final class LinkedArrows {
    static final int TRACKING_COLOR = 0x3FE8FF;
    static final int WILD_COLOR = 0xFF2E7E;

    private LinkedArrows() {
    }

    /** Called by the bow as it fires: lock on if the shooter holds a session on someone, else fly wild. */
    static void arm(AbstractArrow arrow) {
        if (!(arrow.getOwner() instanceof ServerPlayer owner)) {
            return;
        }
        boolean linked = !Terminals.isNull(owner) && !Terminals.get(owner).isOff()
                && !Sessions.linkedTargets(owner.getUUID()).isEmpty();
        arrow.setData(ModAttachments.LINKED_SHOT, new LinkedShot(linked ? LinkedShot.Mode.TRACKING : LinkedShot.Mode.WILD, -1, 0, 0));
        arrow.setNoGravity(true);
    }

    @SubscribeEvent
    static void onArrowTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !arrow.hasData(ModAttachments.LINKED_SHOT)) {
            return;
        }
        LinkedShot shot = arrow.getData(ModAttachments.LINKED_SHOT);
        Vec3 motion = arrow.getDeltaMovement();
        // FREE, or stopped: stuck in the ground or caught on something.
        if (shot.mode() == LinkedShot.Mode.FREE || motion.lengthSqr() < 1.0E-4) {
            return;
        }
        ServerLevel server = arrow.level() instanceof ServerLevel level ? level : null;
        float speed = shot.speed();
        if (speed <= 0) {
            // The first tick: keep the speed the bow gave it (tracking arrows never crawl).
            speed = (float) (shot.mode() == LinkedShot.Mode.TRACKING ? BowRules.trackingSpeed(motion.length()) : motion.length());
            if (server != null) {
                shot = update(arrow, new LinkedShot(shot.mode(), shot.target(), speed, shot.blocked()));
            }
        }

        BowRules.Vec heading = vec(motion);
        if (shot.mode() == LinkedShot.Mode.WILD) {
            if (arrow.tickCount > BowRules.WILD_TICKS) {
                if (server != null) {
                    release(arrow);
                }
                return;
            }
            heading = BowRules.wild(heading, arrow.getId(), arrow.tickCount);
        } else {
            if (server != null) {
                shot = retarget(server, arrow, shot);
                if (shot.mode() != LinkedShot.Mode.TRACKING) {
                    return;
                }
            }
            Entity target = arrow.level().getEntity(shot.target());
            // Out of sight, or not on this client yet: hold the course.
            if (target != null && shot.blocked() == 0) {
                heading = BowRules.steer(heading, vec(target.getBoundingBox().getCenter().subtract(arrow.position())), speed);
            }
        }
        heading = heading.normalize();
        arrow.setDeltaMovement(heading.x() * speed, heading.y() * speed, heading.z() * speed);
        arrow.setNoGravity(true);
        if (server != null) {
            arrow.needsSync = true;
        }
    }

    /** On a hit, a burst of sparks and a chime, and the arrow is an ordinary one again. */
    @SubscribeEvent
    static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !arrow.hasData(ModAttachments.LINKED_SHOT)
                || !(arrow.level() instanceof ServerLevel level)) {
            return;
        }
        LinkedShot shot = arrow.getData(ModAttachments.LINKED_SHOT);
        if (shot.mode() == LinkedShot.Mode.FREE) {
            return;
        }
        Vec3 at = event.getRayTraceResult().getLocation();
        int color = shot.mode() == LinkedShot.Mode.TRACKING ? TRACKING_COLOR : WILD_COLOR;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 18, 0.2, 0.2, 0.2, 0.4);
        level.sendParticles(new DustParticleOptions(color, 1.4f), at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.08);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.7f);
        release(arrow);
    }

    /**
     * The nearest linked gateway in range with a clear line from the arrow. None in range ends the
     * chase; all of them out of sight holds course for a moment, then ends it too.
     */
    private static LinkedShot retarget(ServerLevel level, AbstractArrow arrow, LinkedShot shot) {
        if (arrow.tickCount > BowRules.MAX_TRACK_TICKS || !(arrow.getOwner() instanceof ServerPlayer owner)) {
            return release(arrow);
        }
        double range = BowRules.TRACK_RANGE * BowRules.TRACK_RANGE;
        LivingEntity best = null;
        double nearest = range;
        boolean inRange = false;
        for (LivingEntity target : Sessions.linkedTargets(owner.getUUID())) {
            if (target.level() != level) {
                continue;
            }
            double distance = target.getBoundingBox().getCenter().distanceToSqr(arrow.position());
            if (distance > range) {
                continue;
            }
            inRange = true;
            if (distance < nearest && inSight(level, arrow, target)) {
                best = target;
                nearest = distance;
            }
        }
        if (!inRange) {
            return release(arrow);
        }
        if (best == null) {
            return shot.blocked() >= BowRules.BLOCKED_TICKS ? release(arrow)
                    : update(arrow, new LinkedShot(LinkedShot.Mode.TRACKING, shot.target(), shot.speed(), shot.blocked() + 1));
        }
        if (best.getId() != shot.target() || shot.blocked() > 0) {
            return update(arrow, new LinkedShot(LinkedShot.Mode.TRACKING, best.getId(), shot.speed(), 0));
        }
        return shot;
    }

    private static boolean inSight(ServerLevel level, AbstractArrow arrow, Entity target) {
        HitResult wall = level.clip(new ClipContext(arrow.position(), target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, arrow));
        return wall.getType() == HitResult.Type.MISS;
    }

    /** An ordinary arrow from here on: gravity back, no more steering or effects. */
    private static LinkedShot release(AbstractArrow arrow) {
        arrow.setNoGravity(false);
        return update(arrow, LinkedShot.NONE);
    }

    private static LinkedShot update(AbstractArrow arrow, LinkedShot shot) {
        arrow.setData(ModAttachments.LINKED_SHOT, shot);
        arrow.syncData(ModAttachments.LINKED_SHOT);
        return shot;
    }

    private static BowRules.Vec vec(Vec3 v) {
        return new BowRules.Vec(v.x, v.y, v.z);
    }
}
