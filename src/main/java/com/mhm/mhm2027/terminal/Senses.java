package com.mhm.mhm2027.terminal;

import static org.squiddev.cobalt.ValueFactory.valueOf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.squiddev.cobalt.LuaTable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The read-only senses behind world.* and me's item and effect reads. Nothing here changes
 * the world or the body. Reads about other entities stay within the player's scan range.
 */
final class Senses {
    private Senses() {
    }

    /**
     * The Overworld clock's total ticks, wherever you are: the Nether and the End have no day of
     * their own. Empty if the Overworld has no clock, which only a modded world could do.
     */
    static Optional<Long> clockTicks(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().overworld();
        Optional<Holder<WorldClock>> clock = overworld.dimensionType().defaultClock();
        return clock.map(each -> overworld.clockManager().getTotalTicks(each));
    }

    static int light(ServerPlayer player) {
        return player.level().getMaxLocalRawBrightness(player.blockPosition());
    }

    /** Light from torches, lava and other blocks only. */
    static int blockLight(ServerPlayer player) {
        // Raw brightness is the larger of block light and sky light minus the amount given. Sky
        // light minus 15 is never above 0, so this leaves the block light.
        return player.level().getRawBrightness(player.blockPosition(), 15);
    }

    static String biome(ServerPlayer player) {
        return player.level().getBiome(player.blockPosition()).getRegisteredName();
    }

    static String dimension(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    static String weather(ServerPlayer player) {
        return WorldRules.weather(player.level().isRaining(), player.level().isThundering());
    }

    /**
     * world.block: the block at an offset from the player's feet, or {@link WorldRules#HIDDEN}
     * when every block next to it blocks the view. That keeps it from finding ore through rock:
     * it sees what you could see from somewhere close.
     */
    static String block(ServerPlayer player, int dx, int dy, int dz) {
        Level level = player.level();
        BlockPos pos = player.blockPosition().offset(dx, dy, dz);
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (!level.getBlockState(next).isViewBlocking(level, next)) {
                return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
            }
        }
        return WorldRules.HIDDEN;
    }

    /**
     * world.mobs: living mobs without a gateway within {@code radius}, nearest first, at most
     * {@code max}. Players are never listed, and gateways are remote.scan's job.
     */
    static LuaTable mobs(ServerPlayer player, double radius, int max) {
        List<Mob> found = new ArrayList<>(player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                mob -> mob.isAlive() && Gateways.of(mob) == null && mob.distanceTo(player) <= radius));
        found.sort(Comparator.comparingDouble(mob -> mob.distanceTo(player)));
        LuaTable list = new LuaTable();
        for (int i = 0; i < Math.min(max, found.size()); i++) {
            Mob mob = found.get(i);
            LuaTable entry = new LuaTable();
            entry.rawset("id", valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString()));
            entry.rawset("name", valueOf(mob.getName().getString()));
            entry.rawset("distance", valueOf(mob.distanceTo(player)));
            entry.rawset("hostile", valueOf(mob.getType().getCategory() == MobCategory.MONSTER));
            list.rawset(i + 1, entry);
        }
        return list;
    }

    /** me.inventory: every non-empty slot, by the inventory's own slot number. */
    static LuaTable inventory(ServerPlayer player) {
        LuaTable list = new LuaTable();
        int index = 1;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                LuaTable entry = item(stack);
                entry.rawset("slot", valueOf(slot));
                list.rawset(index++, entry);
            }
        }
        return list;
    }

    /** An item stack as scripts see it. {@code damage} and {@code max_damage} only for things that wear out. */
    static LuaTable item(ItemStack stack) {
        LuaTable entry = new LuaTable();
        entry.rawset("item", valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        entry.rawset("count", valueOf(stack.getCount()));
        if (stack.isDamageableItem()) {
            entry.rawset("damage", valueOf(stack.getDamageValue()));
            entry.rawset("max_damage", valueOf(stack.getMaxDamage()));
        }
        return entry;
    }

    /** me.effects: each active effect, with its level counted from 1 as the game shows it. */
    static LuaTable effects(ServerPlayer player) {
        LuaTable list = new LuaTable();
        int index = 1;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            LuaTable entry = new LuaTable();
            entry.rawset("id", valueOf(effect.getEffect().getRegisteredName()));
            entry.rawset("level", valueOf(effect.getAmplifier() + 1));
            entry.rawset("seconds", valueOf(WorldRules.effectSeconds(effect.getDuration(), effect.isInfiniteDuration())));
            list.rawset(index++, entry);
        }
        return list;
    }
}
