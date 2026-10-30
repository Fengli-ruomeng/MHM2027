package com.mhm.mhm2027.item;

import com.mhm.mhm2027.Config;
import com.mhm.mhm2027.mob.WiredMobs;
import com.mhm.mhm2027.terminal.BodyEffects;
import com.mhm.mhm2027.terminal.Gateways;
import com.mhm.mhm2027.terminal.Terminal;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * The KuyoZ Mech EMP Activator: hold use to charge it, then it pulses and is used up. Every
 * gateway in range is forced to reboot, the user's own included. Gateway mobs are stunned and
 * drop their hacks. Null players and ordinary mobs have no gateway to hit.
 */
public class EmpActivatorItem extends KuyozItem {
    private static final int CHARGE_TICKS = 30;

    public EmpActivatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return CHARGE_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TOOT_HORN;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel server) || !usable(player)) {
            return stack;
        }
        int hit = pulse(server, player);
        stack.consume(1, player);
        player.sendSystemMessage(Component.translatable("message.mhm2027.emp.pulse", hit).withStyle(ChatFormatting.GOLD));
        return stack;
    }

    /** @return how many gateways the pulse hit */
    private static int pulse(ServerLevel level, ServerPlayer user) {
        double radius = Config.EMP_RADIUS.get();
        int stunTicks = Config.EMP_MOB_STUN_SECONDS.get() * 20;
        int hit = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(radius),
                target -> target.distanceTo(user) <= radius && Gateways.of(target) != null)) {
            if (target instanceof ServerPlayer player) {
                Terminal terminal = Terminals.get(player);
                // A gateway mid-flash is already down; there's nothing running to knock out.
                if (terminal.isFlashing()) {
                    continue;
                }
                terminal.reboot();
                terminal.log("kernel", "forced reboot: EMP");
                player.sendOverlayMessage(Component.translatable("message.mhm2027.emp.hit").withStyle(ChatFormatting.RED));
            } else {
                BodyEffects.neutralize(target, stunTicks);
                if (target instanceof Mob mob) {
                    WiredMobs.disrupt(mob);
                }
            }
            hit++;
        }
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, user.getX(), user.getY() + 1, user.getZ(), 120, radius / 3, 0.6, radius / 3, 0.2);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), user.getX(), user.getY() + 1, user.getZ(), 1, 0, 0, 0, 0);
        return hit;
    }
}
