package com.mhm.mhm2027.terminal;

import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.Varargs;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.ModAttachments;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Open sessions: what a successful access attempt gives the attacker's program for about
 * 6 seconds. User level by default; s.escalate() makes it root. A session ends when it runs
 * out, when the attacker closes it, when the defender pays Buffer to close it, or when the
 * defender reboots. Afterwards the same attacker is locked out of that target for a while.
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public final class Sessions {
    private static final int STEPS_PER_ACTION = 3;
    private static final List<Session> OPEN = new ArrayList<>();
    private static final Map<Pair, Long> LOCKOUTS = new HashMap<>();

    private record Pair(UUID attacker, UUID target) {
    }

    /** One open session. */
    static final class Session {
        final UUID attacker;
        final UUID target;
        final ServerLevel level;
        final MinecraftServer server;
        /** The flaw method that opened it, reused by escalate(); null for force. */
        final @Nullable String method;
        final long expiresAt;
        boolean root;
        boolean open = true;
        private final Map<String, Long> cooldowns = new HashMap<>();

        private Session(UUID attacker, UUID target, ServerLevel level, @Nullable String method, long expiresAt) {
            this.attacker = attacker;
            this.target = target;
            this.level = level;
            this.server = level.getServer();
            this.method = method;
            this.expiresAt = expiresAt;
        }

        @Nullable LivingEntity target() {
            Entity entity = level.getEntity(target);
            return entity instanceof LivingEntity living && living.isAlive() ? living : null;
        }

        @Nullable ServerPlayer attackerPlayer() {
            return server.getPlayerList().getPlayer(attacker);
        }

        long now() {
            return server.getTickCount();
        }

        double secondsLeft() {
            return open ? Math.max(0, (expiresAt - now()) / 20.0) : 0;
        }

        /** Starts an action's cooldown; false while it is still cooling down. */
        boolean ready(String action, int cooldownTicks) {
            long readyAt = cooldowns.getOrDefault(action, 0L);
            if (now() < readyAt) {
                return false;
            }
            cooldowns.put(action, now() + cooldownTicks);
            return true;
        }
    }

    private Sessions() {
    }

    // region Registry

    static Session open(ServerPlayer attacker, LivingEntity target, @Nullable String method) {
        Session session = new Session(attacker.getUUID(), target.getUUID(), (ServerLevel) target.level(), method,
                attacker.level().getServer().getTickCount() + AccessRules.SESSION_TICKS);
        OPEN.add(session);
        return session;
    }

    static @Nullable Session find(UUID attacker, UUID target) {
        for (Session session : OPEN) {
            if (session.open && session.attacker.equals(attacker) && session.target.equals(target)) {
                return session;
            }
        }
        return null;
    }

    static void close(Session session, String reason) {
        if (!session.open) {
            return;
        }
        session.open = false;
        OPEN.remove(session);
        lockout(session.attacker, session.target, AccessRules.SESSION_LOCKOUT_TICKS, session.server);
        ServerPlayer attacker = session.attackerPlayer();
        if (attacker != null) {
            Terminals.existing(attacker).ifPresent(terminal ->
                    terminal.log("session", "closed on " + Addresses.of(session.target) + " (" + reason + ")"));
        }
        if (session.server.getPlayerList().getPlayer(session.target) instanceof ServerPlayer defender) {
            Terminals.existing(defender).ifPresent(terminal ->
                    terminal.log("session", "from " + Addresses.of(session.attacker) + " ended (" + reason + ")"));
        }
    }

    /** Ends every session on a target: a reboot, a panic, a kernel swap, death or logout. */
    static void endAllOn(UUID target, String reason) {
        for (Session session : List.copyOf(OPEN)) {
            if (session.target.equals(target)) {
                close(session, reason);
            }
        }
    }

    /** Ends every session a player holds: logout or death. */
    static void endAllBy(UUID attacker, String reason) {
        for (Session session : List.copyOf(OPEN)) {
            if (session.attacker.equals(attacker)) {
                close(session, reason);
            }
        }
    }

    static void lockout(UUID attacker, UUID target, int ticks, MinecraftServer server) {
        LOCKOUTS.put(new Pair(attacker, target), (long) server.getTickCount() + ticks);
    }

    static boolean lockedOut(UUID attacker, UUID target, MinecraftServer server) {
        Long until = LOCKOUTS.get(new Pair(attacker, target));
        return until != null && server.getTickCount() < until;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (OPEN.isEmpty() && LOCKOUTS.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        for (Iterator<Session> it = List.copyOf(OPEN).iterator(); it.hasNext(); ) {
            Session session = it.next();
            if (now >= session.expiresAt) {
                close(session, "timed out");
            } else if (session.target() == null || session.attackerPlayer() == null) {
                close(session, "gone");
            }
        }
        LOCKOUTS.values().removeIf(until -> now >= until);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        endAllBy(event.getEntity().getUUID(), "logged out");
        endAllOn(event.getEntity().getUUID(), "logged out");
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        OPEN.clear();
        LOCKOUTS.clear();
    }

    // endregion

    // region Lua

    /** The session object the attacker's program gets back from remote.access. */
    static LuaTable toLua(Session session, Program caller, ServerPlayer attacker) {
        LuaTable s = new LuaTable();
        LivingEntity target = session.target();
        s.rawset("target", EntityHandles.of(target, attacker));

        LuaTable motor = new LuaTable();
        motor.rawset("neutralize", action("neutralize", session, caller, 80, false, (body, args) -> {
            double seconds = Math.clamp(args.arg(1).checkDouble(), 0.25, AccessRules.NEUTRALIZE_MAX_SECONDS);
            BodyEffects.neutralize(body, (int) Math.round(seconds * 20));
            return Constants.TRUE;
        }));
        motor.rawset("look", action("look", session, caller, 20, false, (body, args) -> {
            BodyEffects.look(body, args.arg(1).checkString());
            return Constants.TRUE;
        }));
        motor.rawset("jump", action("jump", session, caller, 20, false, (body, args) -> valueOf(BodyEffects.jump(body))));
        s.rawset("motor", motor);

        LuaTable memory = new LuaTable();
        memory.rawset("leak", action("leak", session, caller, 40, false, (body, args) -> {
            GatewayData data = Gateways.of(body);
            if (data == null) {
                return Constants.FALSE;
            }
            double mb = Math.clamp(args.arg(1).checkDouble(), 0, data.tier().ramMb() * AccessRules.LEAK_MAX_SHARE);
            data.setLeakMb(data.leakMb() + mb);
            return valueOf(mb);
        }));
        s.rawset("memory", memory);

        LuaTable thermal = new LuaTable();
        thermal.rawset("heat", action("heat", session, caller, 20, false, (body, args) -> {
            GatewayData data = Gateways.of(body);
            if (data == null) {
                return Constants.FALSE;
            }
            double amount = Math.clamp(args.arg(1).checkDouble(), 0, AccessRules.HEAT_MAX_PER_CALL);
            data.setHeat(data.heat() + amount);
            return valueOf(amount);
        }));
        s.rawset("thermal", thermal);

        LuaTable storage = new LuaTable();
        storage.rawset("peek", action("peek", session, caller, 0, false, (body, args) -> inventory(body)));
        s.rawset("storage", storage);

        s.rawset("ps", action("ps", session, caller, 0, false, (body, args) -> processes(body)));
        s.rawset("kill", action("kill", session, caller, 20, true, (body, args) -> {
            String name = args.arg(1).checkString();
            if (body instanceof ServerPlayer player) {
                return valueOf(Terminals.existing(player).map(terminal -> terminal.kill(name, Addresses.of(session.attacker))).orElse(false));
            }
            return Constants.FALSE;
        }));

        LuaTable core = new LuaTable();
        core.rawset("drop", action("drop", session, caller, 40, true, (body, args) -> valueOf(BodyEffects.dropHeld(body))));
        s.rawset("core", core);

        s.rawset("close", LuaSandbox.function("close", (state, args) -> {
            close(session, "closed by attacker");
            return Constants.NONE;
        }));
        s.rawset("time_left", LuaSandbox.function("time_left", (state, args) -> valueOf(session.secondsLeft())));
        s.rawset("is_root", LuaSandbox.function("is_root", (state, args) -> valueOf(session.open && session.root)));
        s.rawset("escalate", LuaSandbox.awaitFunction("escalate", args -> Access.escalate(session, caller)));
        return s;
    }

    @FunctionalInterface
    private interface ActionBody {
        Varargs apply(LivingEntity target, Varargs args) throws LuaError;
    }

    /**
     * A session action. It returns false when the session is closed, the target is gone, the
     * action is cooling down, or it needs root that the session doesn't have.
     */
    private static LuaValue action(String name, Session session, Program caller, int cooldownTicks, boolean needsRoot, ActionBody body) {
        return LuaSandbox.function(name, (state, args) -> {
            caller.sandbox().charge(STEPS_PER_ACTION * ApiSpec.STEP_INSTRUCTIONS);
            LivingEntity target = session.target();
            if (!session.open || target == null) {
                return varargsOf(Constants.FALSE, valueOf("closed"));
            }
            if (needsRoot && !session.root) {
                return varargsOf(Constants.FALSE, valueOf("needs_root"));
            }
            if (!session.ready(name, cooldownTicks)) {
                return varargsOf(Constants.FALSE, valueOf("cooldown"));
            }
            return body.apply(target, args);
        });
    }

    private static LuaTable inventory(LivingEntity target) {
        LuaTable list = new LuaTable();
        int index = 1;
        if (target instanceof ServerPlayer player) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                index = addStack(list, index, player.getInventory().getItem(slot));
            }
        } else {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                index = addStack(list, index, target.getItemBySlot(slot));
            }
        }
        return list;
    }

    private static int addStack(LuaTable list, int index, ItemStack stack) {
        if (stack.isEmpty()) {
            return index;
        }
        LuaTable entry = new LuaTable();
        entry.rawset("item", valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        entry.rawset("count", valueOf(stack.getCount()));
        list.rawset(index, entry);
        return index + 1;
    }

    private static LuaTable processes(LivingEntity target) {
        LuaTable list = new LuaTable();
        if (target instanceof ServerPlayer player) {
            Terminals.existing(player).ifPresent(terminal -> {
                int index = 1;
                for (Program program : terminal.programs()) {
                    LuaTable entry = new LuaTable();
                    entry.rawset("name", valueOf(program.name()));
                    entry.rawset("type", valueOf(program.type().id()));
                    entry.rawset("state", valueOf(program.state(terminal.now())));
                    list.rawset(index++, entry);
                }
            });
        }
        return list;
    }

    /** Whether the attacker holds a session on the target right now, for sys.end_session. */
    static @Nullable Session on(UUID target, UUID attacker) {
        return find(attacker, target);
    }

    /** For mobs, the kernel reserve they defend with. */
    static int mobDefence(LivingEntity mob) {
        return mob.getData(ModAttachments.MOB_KERNEL).topsReserve();
    }

    // endregion
}
