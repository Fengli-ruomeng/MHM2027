package com.mhm.mhm2027.client;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.item.LinkedShot;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * What a Programmable Bow's arrow looks like in flight, so nobody mistakes it for an ordinary
 * one. Tracking: a cyan trail, sparks, and a flickering line of light to its target. Wild: a
 * magenta trail and smoke, like something gone wrong.
 */
@EventBusSubscriber(modid = Mhm2027.MODID, value = Dist.CLIENT)
public final class LinkedArrowEffects {
    private static final DustParticleOptions TRAIL = new DustParticleOptions(0x3FE8FF, 1.0f);
    private static final DustParticleOptions BEAM = new DustParticleOptions(0xB4F6FF, 0.55f);
    private static final DustParticleOptions WILD_TRAIL = new DustParticleOptions(0xFF2E7E, 1.0f);
    private static final int TRAIL_STEPS = 4;
    private static final int BEAM_DOTS = 3;

    private LinkedArrowEffects() {
    }

    @SubscribeEvent
    static void onArrowTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !arrow.level().isClientSide() || !arrow.hasData(ModAttachments.LINKED_SHOT)) {
            return;
        }
        LinkedShot shot = arrow.getData(ModAttachments.LINKED_SHOT);
        if (shot.mode() == LinkedShot.Mode.FREE || arrow.getDeltaMovement().lengthSqr() < 1.0E-4) {
            return;
        }
        Level level = arrow.level();
        RandomSource random = arrow.getRandom();
        boolean wild = shot.mode() == LinkedShot.Mode.WILD;
        // A dense trail between where it was last tick and where it is now.
        for (int i = 0; i < TRAIL_STEPS; i++) {
            double t = i / (double) TRAIL_STEPS;
            level.addParticle(wild ? WILD_TRAIL : TRAIL, Mth.lerp(t, arrow.xo, arrow.getX()), Mth.lerp(t, arrow.yo, arrow.getY()),
                    Mth.lerp(t, arrow.zo, arrow.getZ()), 0, 0, 0);
        }
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, arrow.getX(), arrow.getY(), arrow.getZ(),
                    (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2);
        }
        if (wild) {
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SMOKE, arrow.getX(), arrow.getY(), arrow.getZ(), 0, 0.02, 0);
            }
            return;
        }
        if (arrow.tickCount % 3 == 0) {
            level.addParticle(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 0, 0, 0);
        }
        // The lock: dots of light along the line from the arrow to what it's chasing.
        Entity target = level.getEntity(shot.target());
        if (target != null && shot.blocked() == 0) {
            Vec3 to = target.getBoundingBox().getCenter();
            for (int i = 0; i < BEAM_DOTS; i++) {
                double t = random.nextDouble();
                level.addParticle(BEAM, Mth.lerp(t, arrow.getX(), to.x), Mth.lerp(t, arrow.getY(), to.y), Mth.lerp(t, arrow.getZ(), to.z), 0, 0, 0);
            }
        }
    }
}
