package com.mhm.mhm2027.terminal;

import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Owns every online player's terminal and feeds game events into them. Server side only. */
@EventBusSubscriber(modid = Mhm2027.MODID)
public final class Terminals {
    private static final Map<UUID, Terminal> TERMINALS = new HashMap<>();

    private Terminals() {
    }

    /** The player's terminal, created (and its saved kernel loaded) on first use. */
    public static Terminal get(ServerPlayer player) {
        return TERMINALS.computeIfAbsent(player.getUUID(), uuid -> new Terminal(player));
    }

    /** LV-1: no cyber layer, so no terminal. */
    public static boolean isNull(ServerPlayer player) {
        return Terminal.data(player).tier().isNull();
    }

    public static Optional<Terminal> existing(ServerPlayer player) {
        return Optional.ofNullable(TERMINALS.get(player.getUUID()));
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (Terminal.data(player).tier().isNull()) {
            // A Null gateway has no cyber layer: drop whatever was running before the switch.
            Terminal terminal = TERMINALS.remove(player.getUUID());
            if (terminal != null) {
                terminal.discard(player);
                Terminal.data(player).setUsage(0, 0);
            }
            return;
        }
        get(player).tick(player);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Terminal terminal = TERMINALS.remove(player.getUUID());
            if (terminal != null) {
                terminal.discard(player);
            }
        }
    }

    @SubscribeEvent
    static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            existing(player).ifPresent(Terminal::onDeath);
        }
    }

    /** Checks the Lua runtime works, so a broken install fails loudly at startup. */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        try {
            new LuaSandbox();
            Mhm2027.LOGGER.info("Edge terminal ready: Lua runtime loaded, player storage in {}", ScriptStore.root(event.getServer()));
        } catch (LuaError | LinkageError e) {
            Mhm2027.LOGGER.error("Edge terminal: the Lua runtime failed to start; programs won't run", e);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        TERMINALS.clear();
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        // Damage after armor and other reductions, before absorption hearts.
        float amount = event.getInflictedDamage();
        Entity attacker = event.getSource().getEntity();
        if (event.getEntity() instanceof ServerPlayer victim) {
            existing(victim).ifPresent(terminal -> terminal.fire(Program.ON_HIT_TAKEN,
                    () -> varargsOf(describe(attacker, victim), valueOf(amount))));
        }
        if (attacker instanceof ServerPlayer dealer) {
            Entity target = event.getEntity();
            existing(dealer).ifPresent(terminal -> terminal.fire(Program.ON_HIT_DEALT,
                    () -> varargsOf(describe(target, dealer), valueOf(amount))));
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            Entity victim = event.getEntity();
            existing(killer).ifPresent(terminal -> terminal.fire(Program.ON_KILL, () -> describe(victim, killer)));
        }
    }

    /**
     * A read-only snapshot of another entity, as seen from the player. This stands in for the
     * full entity handles (address, level, ...) that come with cyber combat.
     */
    private static LuaValue describe(Entity entity, ServerPlayer viewer) {
        if (entity == null) {
            return Constants.NIL;
        }
        LuaTable table = new LuaTable();
        table.rawset("kind", valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
        table.rawset("name", valueOf(entity.getName().getString()));
        table.rawset("is_player", valueOf(entity instanceof ServerPlayer));
        table.rawset("distance", valueOf(viewer.distanceTo(entity)));
        return table;
    }
}
