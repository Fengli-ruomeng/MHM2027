package com.mhm.mhm2027.item;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mhm.mhm2027.terminal.PresetRegistry;
import com.mhm.mhm2027.terminal.ScriptSummary;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A program or kernel chip: a physical copy of code, for loot and trading. Only files in the
 * gateway's storage run, so using a chip means writing it in: hold use for a moment and its
 * code is copied into storage. Your own code chips are kept; preset chips are used up.
 */
public class ChipItem extends Item {
    private static final int WRITE_TICKS = 30;
    private static final int MAX_DETAILS_SHOWN = 4;

    private final boolean kernel;

    public ChipItem(Properties properties, boolean kernel) {
        super(properties);
        this.kernel = kernel;
    }

    public boolean isKernelChip() {
        return kernel;
    }

    private static boolean hasContents(ItemStack stack) {
        return stack.has(ModComponents.SCRIPT_CODE.get()) || stack.has(ModComponents.PRESET.get());
    }

    // region Writing

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!hasContents(player.getItemInHand(hand))) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return WRITE_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TOOT_HORN;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            ChipActions.write(player, stack);
        }
        return stack;
    }

    // endregion

    // region Looks

    /** Presets and kernels shimmer, like enchanted items. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return kernel || stack.has(ModComponents.PRESET.get()) || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        ScriptSummary summary = stack.get(ModComponents.SUMMARY.get());
        if (summary == null) {
            return super.getName(stack);
        }
        return Component.translatable(kernel ? "item.mhm2027.kernel_chip.named" : "item.mhm2027.program_chip.named", summary.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        Identifier preset = stack.get(ModComponents.PRESET.get());
        ScriptCode code = stack.get(ModComponents.SCRIPT_CODE.get());
        ScriptSummary summary = stack.get(ModComponents.SUMMARY.get());
        if (preset == null && code == null) {
            lines.accept(Component.translatable("tooltip.mhm2027.chip.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (preset != null) {
            lines.accept(Component.translatable("tooltip.mhm2027.chip.preset", summary == null ? "?" : summary.level())
                    .withStyle(ChatFormatting.GOLD));
        } else if (!code.author().isEmpty()) {
            lines.accept(Component.translatable("tooltip.mhm2027.chip.author", code.author()).withStyle(ChatFormatting.GRAY));
        }

        if (summary == null) {
            lines.accept(Component.literal(String.valueOf(preset)).withStyle(ChatFormatting.DARK_GRAY));
        } else if (summary.isKernel()) {
            lines.accept(Component.translatable("tooltip.mhm2027.chip.kernel_cost", summary.tops(), summary.ramMb()).withStyle(ChatFormatting.AQUA));
            lines.accept(Component.translatable("tooltip.mhm2027.chip.hooks", list(summary.details())).withStyle(ChatFormatting.GRAY));
        } else {
            lines.accept(Component.translatable("tooltip.mhm2027.chip.program_cost", summary.kind(), summary.tops(), summary.ramMb())
                    .withStyle(ChatFormatting.AQUA));
            if (code != null) {
                lines.accept(Component.translatable("tooltip.mhm2027.chip.level", summary.level()).withStyle(ChatFormatting.GRAY));
            }
            if (!summary.details().isEmpty()) {
                lines.accept(Component.translatable("tooltip.mhm2027.chip.apis", list(summary.details())).withStyle(ChatFormatting.GRAY));
            }
        }

        lines.accept(Component.translatable(preset != null ? "tooltip.mhm2027.chip.write_preset" : "tooltip.mhm2027.chip.write")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String list(List<String> items) {
        if (items.isEmpty()) {
            return "-";
        }
        if (items.size() <= MAX_DETAILS_SHOWN) {
            return String.join(", ", items);
        }
        return String.join(", ", items.subList(0, MAX_DETAILS_SHOWN)) + " +" + (items.size() - MAX_DETAILS_SHOWN);
    }

    /**
     * Preset chips from loot carry only an id. The server fills in the summary the first time
     * the chip sits in an inventory, and refreshes it if the datapack changed the preset.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        Identifier id = stack.get(ModComponents.PRESET.get());
        if (id == null) {
            return;
        }
        PresetRegistry.get(id).ifPresent(preset -> {
            if (!preset.summary().equals(stack.get(ModComponents.SUMMARY.get()))) {
                stack.set(ModComponents.SUMMARY.get(), preset.summary());
            }
        });
    }

    // endregion
}
