package com.mhm.mhm2027.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Alontesi Tech's Programmable Bow: a vanilla bow in every way but one. Its arrows plot a course
 * for the nearest gateway you hold a session on and follow it as it moves; with no session open
 * there's nothing to lock onto and they fly wild (see {@link LinkedArrows}). Made by flashing a
 * plain bow at a Flash Station.
 */
public class ProgrammableBowItem extends BowItem {
    public ProgrammableBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        AbstractArrow shot = super.customArrow(arrow, projectileStack, weaponStack);
        LinkedArrows.arm(shot);
        return shot;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.mhm2027.alontesi").withStyle(ChatFormatting.AQUA));
        lines.accept(Component.translatable(getDescriptionId() + ".tagline").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.accept(Component.translatable(getDescriptionId() + ".how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
