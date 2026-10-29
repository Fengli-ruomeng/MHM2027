package com.mhm.mhm2027.gateway;

import com.mhm.mhm2027.Config;
import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runs the passive side of every gateway on the server, players and mobs alike: garbage
 * collection, Heat cooling, and the out-of-memory Wither.
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public class GatewayEvents {
    private static final int TICKS_PER_SECOND = 20;
    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final float MAX_SATURATION = 20.0f;

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        GatewayData data = player.getData(ModAttachments.GATEWAY);
        if (data.tier().isNull()) {
            return;
        }
        double gcMultiplier = Terminals.existing(player).map(terminal -> terminal.kernel().settings().gcMultiplier()).orElse(1.0);
        tickBody(player, data, playerGcRate(player) * gcMultiplier);

        if (player.tickCount % SYNC_INTERVAL_TICKS == 0 && data.consumeDirty()) {
            player.syncData(ModAttachments.GATEWAY);
        }
    }

    /**
     * Mobs with a gateway. Only entities that already carry gateway data are touched, so
     * ordinary vanilla mobs cost nothing here.
     */
    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity mob) || mob instanceof Player
                || !(mob.level() instanceof ServerLevel) || !mob.hasData(ModAttachments.GATEWAY)) {
            return;
        }
        GatewayData data = mob.getData(ModAttachments.GATEWAY);
        if (data.tier().isNull()) {
            return;
        }
        // Mobs don't eat, so they collect garbage at the full-saturation rate.
        tickBody(mob, data, Config.GC_RATE_FULL_SATURATION.get() * mob.getData(ModAttachments.MOB_KERNEL).gcMultiplier());
        data.consumeDirty();
    }

    /** The shared part: GC, cooling, and Wither while out of memory. */
    private static void tickBody(LivingEntity entity, GatewayData data, double gcRatePerSecond) {
        if (data.leakMb() > 0 && gcRatePerSecond > 0) {
            data.setLeakMb(data.leakMb() - data.tier().ramMb() * gcRatePerSecond / TICKS_PER_SECOND);
        }
        if (data.heat() > 0) {
            data.setHeat(data.heat() - Config.HEAT_COOLING_PER_SECOND.get() / TICKS_PER_SECOND);
        }
        // Out of memory: Wither for as long as free RAM is below zero, refreshed every second.
        if (entity.tickCount % TICKS_PER_SECOND == 0 && data.isOutOfMemory()) {
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 2 * TICKS_PER_SECOND, 0, false, true, true));
        }
    }

    /**
     * GC clears Leak at a rate set by saturation, and stops entirely when the hunger bar is low.
     * This keeps the link between RAM and food from the original design.
     */
    private static double playerGcRate(ServerPlayer player) {
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() <= Config.GC_MIN_FOOD_LEVEL.get()) {
            return 0;
        }
        double saturation = Mth.clamp(food.getSaturationLevel() / MAX_SATURATION, 0.0f, 1.0f);
        return Mth.lerp(saturation, Config.GC_RATE_ZERO_SATURATION.get(), Config.GC_RATE_FULL_SATURATION.get());
    }

    // Runs after NeoForge has copied the attachment over. Level and Buffer survive death;
    // Leak and Heat belong to the old body.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getEntity().getData(ModAttachments.GATEWAY).resetBody();
        }
    }
}
