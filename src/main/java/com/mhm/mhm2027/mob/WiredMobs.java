package com.mhm.mhm2027.mob;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.Varargs;

import com.mhm.mhm2027.Config;
import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.GatewayTier;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.item.ModItems;
import com.mhm.mhm2027.terminal.Access;
import com.mhm.mhm2027.terminal.BodyEffects;
import com.mhm.mhm2027.terminal.Gateways;
import com.mhm.mhm2027.terminal.LuaSandbox;
import com.mhm.mhm2027.terminal.Sessions;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Gateway ("wired") mobs: vanilla zombies and skeletons that spawn with a gateway, a native
 * kernel and a physical upgrade. They drop Data Shards, and can be hacked back like anyone.
 *
 * The Wired Zombie gets more health and a speed burst when hurt. The Wired Skeleton is faster
 * and armoured, inspects the player it targets, and when one of its arrows lands it tries to
 * open a session through the same access rules a program uses; a session slows the player
 * for a moment and closes again.
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public final class WiredMobs {
    /** The kinds of wired mob, their level range, and their physical upgrade. */
    private enum Kind {
        ZOMBIE(1, 3),
        SKELETON(3, 4);

        final int minLevel;
        final int maxLevel;

        Kind(int minLevel, int maxLevel) {
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
        }

        static @Nullable Kind of(Entity entity) {
            if (entity.getType() == EntityType.ZOMBIE) {
                return ZOMBIE;
            }
            if (entity.getType() == EntityType.SKELETON) {
                return SKELETON;
            }
            return null;
        }

        void upgrade(Mob mob) {
            switch (this) {
                case ZOMBIE -> {
                    modify(mob, Attributes.MAX_HEALTH, "health", 4, AttributeModifier.Operation.ADD_VALUE);
                    mob.setHealth(mob.getMaxHealth());
                }
                case SKELETON -> {
                    modify(mob, Attributes.MOVEMENT_SPEED, "speed", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                    modify(mob, Attributes.ARMOR, "armor", 2, AttributeModifier.Operation.ADD_VALUE);
                }
            }
        }
    }

    /** A skeleton's look at its target: ready at {@code readyAt}; {@code method} is set once it has finished. */
    private record Scan(UUID target, long readyAt, @Nullable String method) {
    }

    /** A mob's native kernel making an access attempt. A session it opens is used at once and closed. */
    private record MobAttacker(Mob entity, int level) implements Access.Attacker {
        @Override
        public int tops() {
            return WiredRules.attackTops(level);
        }

        @Override
        public Varargs opened(Sessions.Session session) {
            LivingEntity target = session.target();
            if (target != null) {
                BodyEffects.neutralize(target, NEUTRALIZE_TICKS);
                sparks(target, 6);
            }
            Sessions.close(session, "done");
            return Constants.TRUE;
        }

        @Override
        public void log(String message) {
        }
    }

    /** Spawns that count as the world making a mob, not a player or a farm. */
    private static final Set<EntitySpawnReason> NATURAL = Set.of(EntitySpawnReason.NATURAL, EntitySpawnReason.CHUNK_GENERATION,
            EntitySpawnReason.STRUCTURE, EntitySpawnReason.REINFORCEMENT, EntitySpawnReason.JOCKEY, EntitySpawnReason.PATROL);
    private static final int TICKS_PER_DAY = 24000;
    private static final int BURST_TICKS = 60;
    private static final int BURST_COOLDOWN_TICKS = 200;
    private static final int SCAN_TICKS = 20;
    /** How long a skeleton trusts what it saw before it looks again. */
    private static final int SCAN_MEMORY_TICKS = 600;
    private static final int NEUTRALIZE_TICKS = 30;

    // Combat state lives in memory only: after a restart a skeleton simply looks again.
    private static final Map<UUID, Scan> SCANS = new HashMap<>();
    private static final Map<UUID, LuaSandbox.Awaitable> ATTEMPTS = new HashMap<>();
    private static final Map<UUID, Long> BURSTS = new HashMap<>();

    private WiredMobs() {
    }

    /**
     * Turns a zombie or skeleton into its wired version at the given level. False for any other
     * mob. Used by natural spawns and by /edge wire.
     */
    public static boolean wire(Mob mob, int level) {
        Kind kind = Kind.of(mob);
        if (kind == null) {
            return false;
        }
        mob.setData(ModAttachments.GATEWAY, GatewayData.fresh(GatewayTier.of(level)));
        if (Config.RANDOM_MOB_KERNELS.get() && mob.level() instanceof ServerLevel server) {
            UUID id = mob.getUUID();
            mob.setData(ModAttachments.MOB_KERNEL, WiredRules.rollKernel(server.getSeed() ^ id.getMostSignificantBits() ^ id.getLeastSignificantBits()));
        }
        kind.upgrade(mob);
        return true;
    }

    /** As {@link #wire(Mob, int)}, at the level a natural spawn at its position would get. */
    public static boolean wire(Mob mob) {
        Kind kind = Kind.of(mob);
        return kind != null && wire(mob, naturalLevel(kind, mob.level(), mob.getY()));
    }

    /** Deeper is stronger; outside the Overworld everything counts as deep. */
    private static int naturalLevel(Kind kind, Level level, double y) {
        return WiredRules.level(kind.minLevel, kind.maxLevel, level.dimension() == Level.OVERWORLD ? (int) Math.floor(y) : Integer.MIN_VALUE);
    }

    /** An EMP hit: the mob loses its look at its target and any access attempt it has running. */
    public static void disrupt(Mob mob) {
        SCANS.remove(mob.getUUID());
        ATTEMPTS.remove(mob.getUUID());
    }

    // region Spawning and drops

    @SubscribeEvent
    static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        Kind kind = Kind.of(mob);
        if (kind == null || !Config.WIRED_MOBS.get() || !NATURAL.contains(event.getSpawnType())) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        long day = level.getOverworldClockTime() / TICKS_PER_DAY + 1;
        double chance = WiredRules.spawnChance(day, Config.WIRED_START_DAY.get(), Config.WIRED_FULL_DAY.get(), Config.WIRED_MAX_CHANCE.get());
        if (mob.getRandom().nextDouble() >= chance) {
            return;
        }
        wire(mob, naturalLevel(kind, level, event.getY()));
    }

    /** Every gateway mob drops Data Shards when a player had a hand in the kill. */
    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob instanceof Player || !event.isRecentlyHit() || !mob.hasData(ModAttachments.GATEWAY)
                || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        GatewayData data = mob.getData(ModAttachments.GATEWAY);
        if (data.tier().isNull()) {
            return;
        }
        int count = WiredRules.shards(data.level(), mob.getRandom().nextDouble());
        ItemEntity shards = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), new ItemStack(ModItems.DATA_SHARD.get(), count));
        shards.setDefaultPickUpDelay();
        event.getDrops().add(shards);
    }

    // endregion

    // region Behaviour

    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Kind kind = Kind.of(mob);
        if (kind == null || !mob.hasData(ModAttachments.GATEWAY)) {
            return;
        }
        GatewayData data = Gateways.of(mob);
        if (data == null) {
            return;
        }
        // The visual tell: now and then a few sparks, about once every two seconds.
        if (mob.tickCount % 10 == 0 && mob.getRandom().nextInt(4) == 0) {
            sparks(mob, 2);
        }
        if (kind == Kind.SKELETON) {
            look(mob, data, level.getServer().getTickCount());
        }
    }

    /** A skeleton inspects the player it targets: about a second in range and in sight. */
    private static void look(Mob skeleton, GatewayData data, long now) {
        UUID id = skeleton.getUUID();
        Scan scan = SCANS.get(id);
        if (!(skeleton.getTarget() instanceof ServerPlayer player) || !hackable(player)
                || skeleton.distanceTo(player) > data.tier().scanRange() || !skeleton.hasLineOfSight(player)) {
            // An unfinished look is lost; a finished one is remembered.
            if (scan != null && scan.method() == null) {
                SCANS.remove(id);
            }
            return;
        }
        boolean stale = scan != null && scan.method() != null && now - scan.readyAt() > SCAN_MEMORY_TICKS;
        if (scan == null || !scan.target().equals(player.getUUID()) || stale) {
            SCANS.put(id, new Scan(player.getUUID(), now + SCAN_TICKS, null));
        } else if (scan.method() == null && now >= scan.readyAt()) {
            SCANS.put(id, new Scan(player.getUUID(), now, Access.inspectedBy(skeleton, player)));
        }
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getInflictedDamage() <= 0) {
            return;
        }
        if (Kind.of(victim) == Kind.ZOMBIE && victim instanceof Mob zombie && Gateways.of(zombie) != null) {
            burst(zombie);
        }
        if (victim instanceof ServerPlayer player && event.getSource().getDirectEntity() instanceof AbstractArrow
                && event.getSource().getEntity() instanceof Mob shooter && Kind.of(shooter) == Kind.SKELETON) {
            hacked(shooter, player);
        }
    }

    /** A Wired Zombie that gets hurt runs hot for a few seconds, then has to wait. */
    private static void burst(Mob zombie) {
        long now = zombie.level().getServer().getTickCount();
        Long last = BURSTS.get(zombie.getUUID());
        if (last != null && now - last < BURST_COOLDOWN_TICKS) {
            return;
        }
        BURSTS.put(zombie.getUUID(), now);
        zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, BURST_TICKS, 1, false, false));
        sparks(zombie, 6);
    }

    /** A Wired Skeleton's arrow landed: try for a session, using what it saw if it looked first. */
    private static void hacked(Mob skeleton, ServerPlayer player) {
        GatewayData data = Gateways.of(skeleton);
        if (data == null || !hackable(player) || ATTEMPTS.containsKey(skeleton.getUUID())) {
            return;
        }
        Scan scan = SCANS.get(skeleton.getUUID());
        String method = scan != null && scan.method() != null && scan.target().equals(player.getUUID()) ? scan.method() : Access.FORCE;
        ATTEMPTS.put(skeleton.getUUID(), Access.start(new MobAttacker(skeleton, data.level()), player, method));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (ATTEMPTS.isEmpty()) {
            return;
        }
        ATTEMPTS.entrySet().removeIf(entry -> {
            if (!entry.getValue().tick()) {
                return false;
            }
            // Any failure (a wrong guess most of all) means the next try starts with a fresh look.
            if (entry.getValue().result().first().isNil()) {
                SCANS.remove(entry.getKey());
            }
            return true;
        });
    }

    @SubscribeEvent
    static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || Kind.of(event.getEntity()) == null) {
            return;
        }
        UUID id = event.getEntity().getUUID();
        SCANS.remove(id);
        ATTEMPTS.remove(id);
        BURSTS.remove(id);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SCANS.clear();
        ATTEMPTS.clear();
        BURSTS.clear();
    }

    // endregion

    /** Players a mob can hack: on a gateway (not Null), not in creative or spectator. */
    private static boolean hackable(ServerPlayer player) {
        return !player.isCreative() && Gateways.of(player) != null;
    }

    private static void sparks(LivingEntity entity, int count) {
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    count, 0.25, 0.3, 0.25, 0.0);
        }
    }

    private static void modify(Mob mob, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.addOrReplacePermanentModifier(new AttributeModifier(
                    Identifier.fromNamespaceAndPath(Mhm2027.MODID, "wired/" + name), amount, operation));
        }
    }
}
