package com.mhm.mhm2027.item;

import java.util.function.Consumer;

import com.mhm.mhm2027.station.Firmware;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A firmware image, crafted from its key materials and flashed at a Flash Station. */
public class FirmwareItem extends Item {
    private final Firmware firmware;

    public FirmwareItem(Properties properties, Firmware firmware) {
        super(properties);
        this.firmware = firmware;
    }

    public Firmware firmware() {
        return firmware;
    }

    /** "LV3", or "Null" for level -1. */
    public static Component levelName(int level) {
        return level < 0 ? Component.translatable("mhm2027.firmware.null") : Component.translatable("mhm2027.firmware.level", level);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("mhm2027.firmware.target", levelName(firmware.target())).withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("mhm2027.firmware.cost", firmware.shards(), firmware.xpLevels()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
