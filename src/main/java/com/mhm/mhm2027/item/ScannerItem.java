package com.mhm.mhm2027.item;

import java.util.List;

import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.hud.Hud;
import com.mhm.mhm2027.hud.HudEvent;
import com.mhm.mhm2027.terminal.FlawRules;
import com.mhm.mhm2027.terminal.Gateways;
import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * The KuyoZ Mech Scanner: hold use on a gateway for a second to read it, as remote.inspect
 * would, with no program. The target's programs hear about it through on_scanned.
 */
public class ScannerItem extends KuyozItem {
    private static final int SCAN_TICKS = 20;
    private static final int COOLDOWN_TICKS = 20;

    public ScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return SCAN_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPYGLASS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !usable(player)) {
            return stack;
        }
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        LivingEntity target = Gateways.crosshair(player, player.getData(ModAttachments.GATEWAY).tier().scanRange());
        if (target == null) {
            Hud.event(player, HudEvent.Kind.QUIET, "SCANNER // IDLE", Component.translatable("message.mhm2027.scanner.nothing"));
            return stack;
        }
        report(player, target);
        if (target instanceof ServerPlayer scanned) {
            Terminals.scanned(scanned, player);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.4F, 1.8F);
        return stack;
    }

    /** A card: who it is, its level and distance, then its flaws. */
    private static void report(ServerPlayer player, LivingEntity target) {
        GatewayData data = Gateways.of(target);
        int level = data == null ? -1 : data.level();
        Component title = Component.translatable("message.mhm2027.scanner.card", target.getName(), level, Math.round(target.distanceTo(player)));
        String tag = Hud.tag(target, "SCAN");
        List<FlawRules.Hit> flaws = Gateways.flawsOf(target);
        if (flaws.isEmpty()) {
            Hud.event(player, HudEvent.Kind.INFO, tag, title, Component.translatable("message.mhm2027.scanner.no_flaws"));
            return;
        }
        MutableComponent line = Component.translatable("message.mhm2027.scanner.flaws");
        for (int i = 0; i < flaws.size(); i++) {
            FlawRules.Hit hit = flaws.get(i);
            line.append(i == 0 ? " " : ", ").append(Component.literal(hit.className() + " ")
                    .append(Component.translatable("mhm2027.flaw." + hit.severity().id()))
                    .withStyle(hit.severity().rank() >= 2 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        }
        Hud.event(player, HudEvent.Kind.INFO, tag, title, line);
    }
}
