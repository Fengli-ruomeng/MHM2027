package com.mhm.mhm2027.potion;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mhm.mhm2027.Config;
import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.terminal.Gateways;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.InstantenousMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Buffer Restore, the effect in Cyber Potions: an instant refill of a share of the gateway's
 * maximum Buffer, larger at each level up to V. Buffer never comes back by itself, so this is
 * the only refill. Works on gateway mobs too; does nothing to anything without a gateway.
 */
public class BufferRestoreEffect extends InstantenousMobEffect {
    public BufferRestoreEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        restore(mob, amplification, 1.0);
        return true;
    }

    @Override
    public void applyInstantenousEffect(ServerLevel level, @Nullable Entity source, @Nullable Entity owner, LivingEntity mob,
                                        int amplification, double scale) {
        // Splash potions scale with distance from the impact.
        restore(mob, amplification, scale);
    }

    private static void restore(LivingEntity entity, int amplification, double scale) {
        GatewayData data = Gateways.of(entity);
        if (data == null) {
            return;
        }
        double mb = data.tier().bufferMb() * share(amplification) * scale;
        if (entity instanceof ServerPlayer player) {
            Terminals.get(player).restoreBuffer(player, mb);
        } else {
            data.setBufferMb(Math.min(data.tier().bufferMb(), data.bufferMb() + mb));
        }
    }

    /** The configured share for this level; levels past the list use its last entry. */
    static double share(int amplification) {
        List<? extends Double> shares = Config.BUFFER_RESTORE_SHARES.get();
        return shares.isEmpty() ? 0 : shares.get(Math.clamp(amplification, 0, shares.size() - 1));
    }
}
