package com.mhm.mhm2027.gateway;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;

/**
 * The presets a player has written into their gateway's storage, by id. The code itself stays
 * in the datapack, which is what keeps presets locked. Survives death, like the level.
 */
public record InstalledPresets(List<Identifier> ids) {
    public static final InstalledPresets EMPTY = new InstalledPresets(List.of());
    public static final Codec<InstalledPresets> CODEC = Identifier.CODEC.listOf().xmap(InstalledPresets::new, InstalledPresets::ids);

    public boolean contains(Identifier id) {
        return ids.contains(id);
    }

    public InstalledPresets with(Identifier id) {
        List<Identifier> next = new ArrayList<>(ids);
        next.add(id);
        return new InstalledPresets(List.copyOf(next));
    }
}
