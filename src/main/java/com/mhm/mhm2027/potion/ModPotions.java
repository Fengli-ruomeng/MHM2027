package com.mhm.mhm2027.potion;

import java.util.ArrayList;
import java.util.List;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.item.ModItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Cyber Potions I to V. Level I is crafted from a glass bottle, redstone and two Data Shards;
 * each higher level is brewed from the one below with another Data Shard. Splash and
 * lingering versions brew the usual way.
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public final class ModPotions {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Mhm2027.MODID);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, Mhm2027.MODID);

    public static final DeferredHolder<MobEffect, BufferRestoreEffect> BUFFER_RESTORE = EFFECTS.register("buffer_restore",
            () -> new BufferRestoreEffect(MobEffectCategory.BENEFICIAL, 0x3FE8FF));

    private static final String[] NUMERALS = {"", "_ii", "_iii", "_iv", "_v"};
    /** Cyber Potion I to V, in order. They share one name, so the effect line shows the level. */
    public static final List<DeferredHolder<Potion, Potion>> CYBER = cyberPotions();

    private ModPotions() {
    }

    private static List<DeferredHolder<Potion, Potion>> cyberPotions() {
        List<DeferredHolder<Potion, Potion>> potions = new ArrayList<>();
        for (int level = 0; level < NUMERALS.length; level++) {
            int amplifier = level;
            potions.add(POTIONS.register("cyber" + NUMERALS[level],
                    () -> new Potion(Mhm2027.MODID + ".cyber", new MobEffectInstance(BUFFER_RESTORE, 1, amplifier))));
        }
        return List.copyOf(potions);
    }

    @SubscribeEvent
    static void onRegisterBrewing(RegisterBrewingRecipesEvent event) {
        for (int level = 1; level < CYBER.size(); level++) {
            event.getBuilder().addMix(CYBER.get(level - 1), ModItems.DATA_SHARD.get(), CYBER.get(level));
        }
    }
}
