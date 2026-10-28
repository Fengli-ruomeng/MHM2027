package com.mhm.mhm2027.gateway;

import com.mhm.mhm2027.Config;
import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runs the passive side of the gateway on the server: garbage collection, Heat cooling,
 * and the out-of-memory Wither. Mobs will get the same loop once gateway mobs exist.
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

        collectGarbage(player, data);

        if (data.heat() > 0) {
            data.setHeat(data.heat() - Config.HEAT_COOLING_PER_SECOND.get() / TICKS_PER_SECOND);
        }

        // Out of memory: Wither for as long as free RAM is below zero, refreshed every second.
        if (player.tickCount % TICKS_PER_SECOND == 0 && data.isOutOfMemory()) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 2 * TICKS_PER_SECOND, 0, false, true, true));
        }

        if (player.tickCount % SYNC_INTERVAL_TICKS == 0 && data.consumeDirty()) {
            player.syncData(ModAttachments.GATEWAY);
        }
    }

    /**
     * GC clears Leak at a rate set by saturation, and stops entirely when the hunger bar is low.
     * This keeps the link between RAM and food from the original design.
     */
    private static void collectGarbage(ServerPlayer player, GatewayData data) {
        if (data.leakMb() <= 0) {
            return;
        }
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() <= Config.GC_MIN_FOOD_LEVEL.get()) {
            return;
        }
        double saturation = Mth.clamp(food.getSaturationLevel() / MAX_SATURATION, 0.0f, 1.0f);
        double ratePerSecond = Mth.lerp(saturation, Config.GC_RATE_ZERO_SATURATION.get(), Config.GC_RATE_FULL_SATURATION.get())
                * Terminals.existing(player).map(terminal -> terminal.kernel().settings().gcMultiplier()).orElse(1.0);
        data.setLeakMb(data.leakMb() - data.tier().ramMb() * ratePerSecond / TICKS_PER_SECOND);
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
