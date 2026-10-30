package com.mhm.mhm2027.terminal;

import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.Varargs;

import com.mhm.mhm2027.Config;
import com.mhm.mhm2027.gateway.GatewayTier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Access attempts: the short contest that opens a session. The attacker is a player's program
 * (remote.access, s.escalate) or a gateway mob's native kernel; both go through the same rules.
 * The defender's kernel decides once, at the start, through on_access_request: allow, deny
 * (pays Buffer), delay (buys time) or resist (commits TOPS and runs hot). Mobs have no hooks
 * and always allow.
 */
public final class Access {
    public static final String FORCE = "force";

    /** What a kernel's on_access_request answered. */
    record Decision(Kind kind, double seconds, int tops) {
        enum Kind {
            ALLOW, DENY, DELAY, RESIST
        }

        static final Decision ALLOW = new Decision(Kind.ALLOW, 0, 0);
    }

    /** Who is attempting access: a player's program, or a gateway mob. */
    public interface Attacker {
        LivingEntity entity();

        /** The TOPS committed to the attempt. */
        int tops();

        /** The attacker's kernel settings; verify decides how fast its attempts run. */
        KernelSettings kernel();

        /** Called when the attempt opens a session; the result goes back to whoever is waiting. */
        Varargs opened(Sessions.Session session);

        void log(String message);
    }

    private Access() {
    }

    // region Kernel decisions

    /** allow(), deny(), delay(seconds) and resist(tops): what on_access_request returns. */
    static void bindDecisions(LuaTable globals) {
        globals.rawset("allow", LuaSandbox.function("allow", (state, args) -> decision("allow", 0)));
        globals.rawset("deny", LuaSandbox.function("deny", (state, args) -> decision("deny", 0)));
        globals.rawset("delay", LuaSandbox.function("delay", (state, args) ->
                decision("delay", Math.clamp(args.arg(1).checkDouble(), 0, AccessRules.MAX_DELAY_SECONDS))));
        globals.rawset("resist", LuaSandbox.function("resist", (state, args) -> decision("resist", Math.max(0, args.arg(1).checkInteger()))));
    }

    private static LuaTable decision(String kind, double value) {
        LuaTable table = new LuaTable();
        table.rawset("decision", valueOf(kind));
        table.rawset("value", valueOf(value));
        return table;
    }

    /** Reads a hook's answer. Anything unexpected (or no answer) is allow, the hook's default. */
    static Decision parse(@Nullable Varargs answer) {
        if (answer == null || !(answer.first() instanceof LuaTable table)) {
            return Decision.ALLOW;
        }
        double value = table.rawget("value").toDouble();
        return switch (table.rawget("decision").toString()) {
            case "deny" -> new Decision(Decision.Kind.DENY, 0, 0);
            case "delay" -> new Decision(Decision.Kind.DELAY, value, 0);
            case "resist" -> new Decision(Decision.Kind.RESIST, 0, (int) value);
            default -> Decision.ALLOW;
        };
    }

    // endregion

    // region Starting

    /** remote.access(target, method) from a program. */
    static LuaSandbox.Awaitable begin(Terminal terminal, Program program, ServerPlayer player, LuaValue handle, String method) throws LuaError {
        if (!isMethod(method)) {
            throw new LuaError("method must be \"force\" or a flaw class such as \"memory\"");
        }
        GatewayTier tier = Terminal.data(player).tier();
        LivingEntity target = EntityHandles.resolve(handle, player, tier.accessRange());
        if (target == null || !Gateways.targetable(target, player)) {
            return Finished.fail("out_of_range");
        }
        return start(programAttacker(terminal, program, player), target, method);
    }

    /**
     * Starts an attempt on a target that is already known to be in range. Shared by programs
     * and mobs: lockouts, an existing session, a rebooting target and wrong guesses all end it
     * at once; otherwise the defender decides and the contest begins.
     */
    public static LuaSandbox.Awaitable start(Attacker attacker, LivingEntity target, String method) {
        LivingEntity entity = attacker.entity();
        if (Sessions.lockedOut(entity.getUUID(), target.getUUID(), entity.level().getServer())) {
            return Finished.fail("cooldown");
        }
        Sessions.Session existing = Sessions.find(entity.getUUID(), target.getUUID());
        if (existing != null) {
            return new Finished(attacker.opened(existing));
        }
        Terminal defender = defenderOf(target);
        if (defender != null && defender.isRebooting()) {
            return Finished.fail("rebooting");
        }
        FlawSeverity matched = FlawSeverity.NONE;
        if (!method.equals(FORCE)) {
            matched = severityOf(target, method);
            if (matched == FlawSeverity.NONE) {
                // A wrong guess fails at once and warns the target.
                warn(target, defender, entity, "mhm2027.access.wrong_guess", method);
                return Finished.fail("no_flaw");
            }
        }
        return contest(attacker, target, defender, method, matched, false, null);
    }

    /** s.escalate(): a second, shorter attempt under the same rules; success makes the session root. */
    static LuaSandbox.Awaitable escalate(Sessions.Session session, Program program) {
        LivingEntity target = session.target();
        if (!session.open || target == null || !(session.attackerEntity() instanceof ServerPlayer player)) {
            return Finished.fail("closed");
        }
        if (session.root) {
            return new Finished(Constants.TRUE);
        }
        Terminal terminal = Terminals.existing(player).orElse(null);
        String method = session.method == null ? FORCE : session.method;
        FlawSeverity matched = session.method == null ? FlawSeverity.NONE : severityOf(target, session.method);
        return contest(programAttacker(terminal, program, player), target, defenderOf(target), method, matched, true, session);
    }

    private static LuaSandbox.Awaitable contest(Attacker attacker, LivingEntity target, @Nullable Terminal defender, String method,
                                                FlawSeverity matched, boolean escalate, Sessions.@Nullable Session session) {
        LivingEntity entity = attacker.entity();
        int attackerTops = attacker.tops();
        int defenderTops = defender != null ? defender.defenceTops() : Sessions.mobDefence(target);
        Decision decision = Decision.ALLOW;
        if (defender != null) {
            decision = defender.decideAccess(entity, method, attackerTops, escalate);
            if (decision.kind() == Decision.Kind.DENY) {
                if (defender.spendBuffer(Config.DENY_BUFFER_MB.get())) {
                    Sessions.lockout(entity.getUUID(), target.getUUID(), AccessRules.DENY_LOCKOUT_TICKS, entity.level().getServer());
                    defender.log("kernel", "denied " + Addresses.of(entity.getUUID()));
                    return Finished.fail("denied");
                }
                defender.log("kernel", "couldn't deny " + Addresses.of(entity.getUUID()) + ": not enough Buffer");
                decision = Decision.ALLOW;
            }
        }

        int base = AccessRules.baseTicks(matched);
        double speed = attacker.kernel().accessSpeed();
        double delay = decision.kind() == Decision.Kind.DELAY ? decision.seconds() : 0;
        if (decision.kind() == Decision.Kind.RESIST && defender != null) {
            // Resisting holds the TOPS for as long as the attempt would take at the new odds.
            int estimate = AccessRules.durationTicks(base, attackerTops, defenderTops + decision.tops(), delay, escalate, speed);
            defenderTops += defender.commitResist(decision.tops(), estimate);
        }
        int ticks = AccessRules.durationTicks(base, attackerTops, defenderTops, delay, escalate, speed);
        warn(target, defender, entity, escalate ? "mhm2027.access.escalating" : "mhm2027.access.attempt", method);
        return new Attempt(attacker, target, defender, method, escalate, session, ticks);
    }

    // endregion

    /** The contest itself: waits out its time, then opens (or escalates) the session. */
    private static final class Attempt implements LuaSandbox.Awaitable {
        private final Attacker attacker;
        private final LivingEntity target;
        private final @Nullable Terminal defender;
        private final String method;
        private final boolean escalate;
        private final Sessions.@Nullable Session session;
        private int ticksLeft;
        private Varargs result = varargsOf(Constants.NIL, valueOf("failed"));

        Attempt(Attacker attacker, LivingEntity target, @Nullable Terminal defender, String method, boolean escalate,
                Sessions.@Nullable Session session, int ticks) {
            this.attacker = attacker;
            this.target = target;
            this.defender = defender;
            this.method = method;
            this.escalate = escalate;
            this.session = session;
            this.ticksLeft = ticks;
        }

        @Override
        public boolean tick() {
            LivingEntity entity = attacker.entity();
            if (!entity.isAlive() || entity.isRemoved() || !target.isAlive() || target.isRemoved()) {
                return fail("gone");
            }
            // A rebooting target can't be hacked: rebooting is a real escape.
            if (defender != null && defender.isRebooting()) {
                return fail("rebooting");
            }
            if (escalate && (session == null || !session.open)) {
                return fail("closed");
            }
            if (--ticksLeft > 0) {
                return false;
            }
            String address = Addresses.of(target.getUUID());
            if (escalate) {
                session.root = true;
                attacker.log("root on " + address);
                result = Constants.TRUE;
            } else {
                Sessions.Session opened = Sessions.open(entity, target, method.equals(FORCE) ? null : method);
                attacker.log("session on " + address + " opened");
                if (defender != null) {
                    defender.sessionOpened(entity);
                }
                result = attacker.opened(opened);
            }
            return true;
        }

        private boolean fail(String reason) {
            result = escalate ? varargsOf(Constants.FALSE, valueOf(reason)) : varargsOf(Constants.NIL, valueOf(reason));
            return true;
        }

        @Override
        public Varargs result() {
            return result;
        }

        @Override
        public String state() {
            return escalate ? "escalating" : "accessing";
        }
    }

    /** An await that is already over: a check failed, or a session already existed. */
    private record Finished(Varargs result) implements LuaSandbox.Awaitable {
        static Finished fail(String reason) {
            return new Finished(varargsOf(Constants.NIL, valueOf(reason)));
        }

        @Override
        public boolean tick() {
            return true;
        }
    }

    private static Attacker programAttacker(@Nullable Terminal terminal, Program program, ServerPlayer player) {
        return new Attacker() {
            @Override
            public LivingEntity entity() {
                return player;
            }

            @Override
            public int tops() {
                return program.tops();
            }

            @Override
            public KernelSettings kernel() {
                return terminal != null ? terminal.kernel().settings() : KernelSettings.DEFAULT;
            }

            @Override
            public Varargs opened(Sessions.Session session) {
                return Sessions.toLua(session, program, player);
            }

            @Override
            public void log(String message) {
                if (terminal != null) {
                    terminal.log("session", message);
                }
            }
        };
    }

    /** Whether a method name is force or a known flaw class. */
    public static boolean isMethod(String method) {
        return method.equals(FORCE) || FlawRegistry.classes().stream().anyMatch(def -> def.className().equals(method));
    }

    private static @Nullable Terminal defenderOf(LivingEntity target) {
        return target instanceof ServerPlayer player ? Terminals.existing(player).orElse(null) : null;
    }

    /**
     * A gateway mob finishing its one-second inspection of a player: the player's programs hear
     * about it through on_scanned, and the mob picks its method from what it saw, the worst
     * flaw or force when there is none.
     */
    public static String inspectedBy(LivingEntity scanner, ServerPlayer target) {
        Terminals.scanned(target, scanner);
        FlawRules.Hit worst = null;
        for (FlawRules.Hit hit : Gateways.flawsOf(target)) {
            if (worst == null || hit.severity().rank() > worst.severity().rank()) {
                worst = hit;
            }
        }
        return worst == null ? FORCE : worst.className();
    }

    static FlawSeverity severityOf(LivingEntity target, String flawClass) {
        for (FlawRules.Hit hit : Gateways.flawsOf(target)) {
            if (hit.className().equals(flawClass)) {
                return hit.severity();
            }
        }
        return FlawSeverity.NONE;
    }

    /** Tells a player target what is coming at them, on the action bar and in the log. */
    private static void warn(LivingEntity target, @Nullable Terminal defender, LivingEntity attacker, String key, String method) {
        if (target instanceof ServerPlayer player && defender != null) {
            String address = Addresses.of(attacker.getUUID());
            player.sendOverlayMessage(Component.translatable(key, address, method).withStyle(ChatFormatting.RED));
            defender.log("kernel", key.substring(key.lastIndexOf('.') + 1).replace('_', ' ') + " from " + address + " (" + method + ")");
        }
    }
}
