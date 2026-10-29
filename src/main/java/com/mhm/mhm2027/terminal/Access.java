package com.mhm.mhm2027.terminal;

import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

import java.util.UUID;

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
 * Access attempts: the short contest that opens a session. The attacker's program waits on
 * it (remote.access and s.escalate). The defender's kernel decides once, at the start, through
 * on_access_request: allow, deny (pays Buffer), delay (buys time) or resist (commits TOPS and
 * runs hot). Mobs have no hooks and always allow.
 */
final class Access {
    static final String FORCE = "force";

    /** What a kernel's on_access_request answered. */
    record Decision(Kind kind, double seconds, int tops) {
        enum Kind {
            ALLOW, DENY, DELAY, RESIST
        }

        static final Decision ALLOW = new Decision(Kind.ALLOW, 0, 0);
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

    /** remote.access(target, method): checks, the defender's decision, then the timed contest. */
    static LuaSandbox.Awaitable begin(Terminal terminal, Program program, ServerPlayer attacker, LuaValue handle, String method) throws LuaError {
        if (!method.equals(FORCE) && FlawRegistry.classes().stream().noneMatch(def -> def.className().equals(method))) {
            throw new LuaError("method must be \"force\" or a flaw class such as \"memory\"");
        }
        GatewayTier tier = Terminal.data(attacker).tier();
        LivingEntity target = EntityHandles.resolve(handle, attacker, tier.accessRange());
        if (target == null || !Gateways.targetable(target, attacker)) {
            return Finished.fail("out_of_range");
        }
        if (Sessions.lockedOut(attacker.getUUID(), target.getUUID(), attacker.level().getServer())) {
            return Finished.fail("cooldown");
        }
        Sessions.Session existing = Sessions.find(attacker.getUUID(), target.getUUID());
        if (existing != null) {
            return new Finished(Sessions.toLua(existing, program, attacker));
        }
        Terminal defender = target instanceof ServerPlayer player ? Terminals.existing(player).orElse(null) : null;
        if (defender != null && defender.isRebooting()) {
            return Finished.fail("rebooting");
        }

        FlawSeverity matched = FlawSeverity.NONE;
        if (!method.equals(FORCE)) {
            matched = severityOf(target, method);
            if (matched == FlawSeverity.NONE) {
                // A wrong guess fails at once and warns the target.
                warn(target, defender, attacker, "mhm2027.access.wrong_guess", method);
                return Finished.fail("no_flaw");
            }
        }
        return contest(attacker, program, target, defender, method, matched, false, null);
    }

    /** s.escalate(): a second, shorter attempt under the same rules; success makes the session root. */
    static LuaSandbox.Awaitable escalate(Sessions.Session session, Program program) throws LuaError {
        ServerPlayer attacker = session.attackerPlayer();
        LivingEntity target = session.target();
        if (!session.open || attacker == null || target == null) {
            return Finished.fail("closed");
        }
        if (session.root) {
            return new Finished(Constants.TRUE);
        }
        Terminal defender = target instanceof ServerPlayer player ? Terminals.existing(player).orElse(null) : null;
        String method = session.method == null ? FORCE : session.method;
        FlawSeverity matched = session.method == null ? FlawSeverity.NONE : severityOf(target, session.method);
        return contest(attacker, program, target, defender, method, matched, true, session);
    }

    private static LuaSandbox.Awaitable contest(ServerPlayer attacker, Program program, LivingEntity target, @Nullable Terminal defender,
                                                String method, FlawSeverity matched, boolean escalate, Sessions.@Nullable Session session) {
        int attackerTops = program.tops();
        int defenderTops = defender != null ? defender.kernel().topsReserve() : Sessions.mobDefence(target);
        Decision decision = Decision.ALLOW;
        if (defender != null) {
            decision = defender.decideAccess(attacker, method, attackerTops, escalate);
            if (decision.kind() == Decision.Kind.DENY) {
                if (defender.spendBuffer(Config.DENY_BUFFER_MB.get())) {
                    Sessions.lockout(attacker.getUUID(), target.getUUID(), AccessRules.DENY_LOCKOUT_TICKS, attacker.level().getServer());
                    defender.log("kernel", "denied " + Addresses.of(attacker.getUUID()));
                    return Finished.fail("denied");
                }
                defender.log("kernel", "couldn't deny " + Addresses.of(attacker.getUUID()) + ": not enough Buffer");
                decision = Decision.ALLOW;
            }
        }

        int base = AccessRules.baseTicks(matched);
        double delay = decision.kind() == Decision.Kind.DELAY ? decision.seconds() : 0;
        if (decision.kind() == Decision.Kind.RESIST && defender != null) {
            // Resisting holds the TOPS for as long as the attempt would take at the new odds.
            int estimate = AccessRules.durationTicks(base, attackerTops, defenderTops + decision.tops(), delay, escalate);
            defenderTops += defender.commitResist(decision.tops(), estimate);
        }
        int ticks = AccessRules.durationTicks(base, attackerTops, defenderTops, delay, escalate);
        warn(target, defender, attacker, escalate ? "mhm2027.access.escalating" : "mhm2027.access.attempt", method);
        return new Attempt(attacker.getUUID(), target, defender, program, method, escalate, session, ticks);
    }

    // endregion

    /** The contest itself: waits out its time, then opens (or escalates) the session. */
    private static final class Attempt implements LuaSandbox.Awaitable {
        private final UUID attacker;
        private final LivingEntity target;
        private final @Nullable Terminal defender;
        private final Program program;
        private final String method;
        private final boolean escalate;
        private final Sessions.@Nullable Session session;
        private int ticksLeft;
        private Varargs result = varargsOf(Constants.NIL, valueOf("failed"));

        Attempt(UUID attacker, LivingEntity target, @Nullable Terminal defender, Program program, String method, boolean escalate,
                Sessions.@Nullable Session session, int ticks) {
            this.attacker = attacker;
            this.target = target;
            this.defender = defender;
            this.program = program;
            this.method = method;
            this.escalate = escalate;
            this.session = session;
            this.ticksLeft = ticks;
        }

        @Override
        public boolean tick() {
            ServerPlayer player = target.level().getServer().getPlayerList().getPlayer(attacker);
            if (player == null || !target.isAlive() || target.isRemoved()) {
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
            if (escalate) {
                session.root = true;
                logBoth(player, "root on ");
                result = Constants.TRUE;
            } else {
                Sessions.Session opened = Sessions.open(player, target, method.equals(FORCE) ? null : method);
                logBoth(player, "session on ");
                if (defender != null) {
                    defender.sessionOpened(player);
                }
                result = Sessions.toLua(opened, program, player);
            }
            return true;
        }

        private boolean fail(String reason) {
            result = escalate ? varargsOf(Constants.FALSE, valueOf(reason)) : varargsOf(Constants.NIL, valueOf(reason));
            return true;
        }

        private void logBoth(ServerPlayer player, String what) {
            Terminals.existing(player).ifPresent(terminal -> terminal.log("session", what + Addresses.of(target.getUUID()) + " opened"));
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

    private static FlawSeverity severityOf(LivingEntity target, String flawClass) {
        for (FlawRules.Hit hit : Gateways.flawsOf(target)) {
            if (hit.className().equals(flawClass)) {
                return hit.severity();
            }
        }
        return FlawSeverity.NONE;
    }

    /** Tells a player target what is coming at them, on the action bar and in the log. */
    private static void warn(LivingEntity target, @Nullable Terminal defender, ServerPlayer attacker, String key, String method) {
        if (target instanceof ServerPlayer player && defender != null) {
            String address = Addresses.of(attacker.getUUID());
            player.sendOverlayMessage(Component.translatable(key, address, method).withStyle(ChatFormatting.RED));
            defender.log("kernel", key.substring(key.lastIndexOf('.') + 1).replace('_', ' ') + " from " + address + " (" + method + ")");
        }
    }
}
