package com.mhm.mhm2027.item;

import java.util.function.Consumer;

import com.mhm.mhm2027.terminal.Terminals;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * KuyoZ Mech: general-purpose cyber tools that sit outside the mod's lore. Each one shows the
 * brand and a one-line tagline, and none works on a Null gateway.
 */
public abstract class KuyozItem extends Item {
    protected KuyozItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.mhm2027.kuyoz").withStyle(ChatFormatting.WHITE));
        lines.accept(Component.translatable(getDescriptionId() + ".tagline").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** Null players can't use cyber gear. Tells them so and returns false. */
    protected static boolean usable(ServerPlayer player) {
        if (Terminals.isNull(player)) {
            player.sendOverlayMessage(Component.translatable("message.mhm2027.kuyoz.null").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }
}
