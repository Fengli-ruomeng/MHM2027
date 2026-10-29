package com.mhm.mhm2027.terminal;

import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.function.RegisteredFunction;

import com.mhm.mhm2027.Config;
import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.GatewayTier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Lua side of the first API set: {@code me} (your body), {@code sys} (your terminal),
 * plus {@code chance} and {@code print}.
 *
 * Every call checks the gateway level again at runtime and charges its steps against the
 * caller's TOPS budget. Calls fail for in-game reasons (cooldown, not enough food) by
 * returning false; mistakes in the code raise errors.
 */
final class Apis {
    private static final int MAX_MESSAGE_LENGTH = 120;

    private Apis() {
    }

    private static LibFunction function(String name, LibFunction.ManyArgs body) {
        return LuaSandbox.function(name, body);
    }

    static void bindProgram(LuaSandbox sandbox, Terminal terminal, Program program) {
        Supplier<ScriptHost> host = () -> program;
        LuaTable globals = sandbox.globals();
        globals.rawset("me", me(terminal, host));
        globals.rawset("sys", sys(terminal, host));
        globals.rawset("remote", remote(terminal, host));
        bindCommon(globals, terminal, host);
    }

    static void bindKernel(LuaSandbox sandbox, Terminal terminal, Supplier<? extends ScriptHost> host) {
        LuaTable globals = sandbox.globals();
        globals.rawset("sys", sys(terminal, host::get));
        Access.bindDecisions(globals);
        bindCommon(globals, terminal, host::get);
    }

    private static void bindCommon(LuaTable globals, Terminal terminal, Supplier<ScriptHost> host) {
        globals.rawset("chance", function("chance", (state, args) -> {
            double percent = args.arg(1).checkDouble();
            ServerPlayer player = player(terminal);
            return valueOf(player.getRandom().nextDouble() * 100 < percent);
        }));
        globals.rawset("print", function("print", (state, args) -> {
            log(terminal, host.get(), args);
            return Constants.NONE;
        }));
    }

    // region me

    private static LuaTable me(Terminal terminal, Supplier<ScriptHost> host) {
        LuaTable me = new LuaTable();
        api(me, ApiSpec.ME_HEALTH, terminal, host, (player, args) -> valueOf(player.getHealth()));
        api(me, ApiSpec.ME_MAX_HEALTH, terminal, host, (player, args) -> valueOf(player.getMaxHealth()));
        api(me, ApiSpec.ME_HUNGER, terminal, host, (player, args) -> {
            FoodData food = player.getFoodData();
            return varargsOf(valueOf(food.getFoodLevel()), valueOf(food.getSaturationLevel()));
        });
        api(me, ApiSpec.ME_POS, terminal, host, (player, args) ->
                varargsOf(valueOf(player.getX()), valueOf(player.getY()), valueOf(player.getZ())));

        api(me, ApiSpec.ME_SPEED_BOOST, terminal, host, (player, args) -> {
            double amount = range(args, 1, 0.05, 0.5);
            int ticks = seconds(args, 2, 1, 10);
            return timed(terminal, player, ApiSpec.ME_SPEED_BOOST, () -> terminal.addTimedModifier(player, Attributes.MOVEMENT_SPEED,
                    "speed_boost", amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, ticks));
        });
        api(me, ApiSpec.ME_JUMP_BOOST, terminal, host, (player, args) -> {
            double amount = range(args, 1, 0.05, 0.5);
            int ticks = seconds(args, 2, 1, 10);
            return timed(terminal, player, ApiSpec.ME_JUMP_BOOST, () -> terminal.addTimedModifier(player, Attributes.JUMP_STRENGTH,
                    "jump_boost", amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, ticks));
        });
        api(me, ApiSpec.ME_FALL_GUARD, terminal, host, (player, args) -> {
            int ticks = seconds(args, 1, 1, 10);
            return timed(terminal, player, ApiSpec.ME_FALL_GUARD, () -> terminal.addTimedModifier(player, Attributes.FALL_DAMAGE_MULTIPLIER,
                    "fall_guard", -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, ticks));
        });
        api(me, ApiSpec.ME_REGEN_BURST, terminal, host, (player, args) -> {
            int amount = (int) Math.round(range(args, 1, 1, 6));
            FoodData food = player.getFoodData();
            // Healing is paid in hunger: one food point per health point.
            if (food.getFoodLevel() < amount || !terminal.tryCooldown(ApiSpec.ME_REGEN_BURST)) {
                return Constants.FALSE;
            }
            food.setFoodLevel(food.getFoodLevel() - amount);
            player.heal(amount);
            return Constants.TRUE;
        });
        api(me, ApiSpec.ME_MAX_HEALTH_BOOST, terminal, host, (player, args) -> {
            double amount = range(args, 1, 1, 10);
            int ticks = seconds(args, 2, 1, 30);
            return timed(terminal, player, ApiSpec.ME_MAX_HEALTH_BOOST, () -> terminal.addTimedModifier(player, Attributes.MAX_HEALTH,
                    "max_health_boost", amount, AttributeModifier.Operation.ADD_VALUE, ticks));
        });
        api(me, ApiSpec.ME_DAMAGE_BOOST, terminal, host, (player, args) -> {
            double amount = range(args, 1, 0.05, 0.5);
            int ticks = seconds(args, 2, 1, 10);
            return timed(terminal, player, ApiSpec.ME_DAMAGE_BOOST, () -> terminal.addTimedModifier(player, Attributes.ATTACK_DAMAGE,
                    "damage_boost", amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, ticks));
        });
        api(me, ApiSpec.ME_KNOCKBACK_RESIST, terminal, host, (player, args) -> {
            int ticks = seconds(args, 1, 1, 10);
            return timed(terminal, player, ApiSpec.ME_KNOCKBACK_RESIST, () -> terminal.addTimedModifier(player, Attributes.KNOCKBACK_RESISTANCE,
                    "knockback_resist", 1, AttributeModifier.Operation.ADD_VALUE, ticks));
        });
        return me;
    }

    private static Varargs timed(Terminal terminal, ServerPlayer player, ApiSpec spec, Runnable apply) {
        if (!terminal.tryCooldown(spec)) {
            return Constants.FALSE;
        }
        apply.run();
        return Constants.TRUE;
    }

    // endregion

    // region sys

    private static LuaTable sys(Terminal terminal, Supplier<ScriptHost> host) {
        LuaTable sys = new LuaTable();
        api(sys, ApiSpec.SYS_RAM, terminal, host, (player, args) -> {
            GatewayData data = Terminal.data(player);
            return varargsOf(valueOf(data.freeRamMb()), valueOf(data.tier().ramMb()));
        });
        api(sys, ApiSpec.SYS_TOPS, terminal, host, (player, args) ->
                varargsOf(valueOf(terminal.freeTops(player)), valueOf(Terminal.data(player).tier().tops())));
        api(sys, ApiSpec.SYS_HEAT, terminal, host, (player, args) -> valueOf(Terminal.data(player).heat()));
        api(sys, ApiSpec.SYS_LEAK, terminal, host, (player, args) -> valueOf(Terminal.data(player).leakMb()));
        api(sys, ApiSpec.SYS_BUFFER, terminal, host, (player, args) -> {
            GatewayData data = Terminal.data(player);
            return varargsOf(valueOf(data.bufferMb()), valueOf(data.tier().bufferMb()));
        });
        api(sys, ApiSpec.SYS_PROGRAMS, terminal, host, (player, args) -> {
            LuaTable list = new LuaTable();
            int index = 1;
            for (Program program : terminal.programs()) {
                LuaTable entry = new LuaTable();
                entry.rawset("name", valueOf(program.name()));
                entry.rawset("type", valueOf(program.type().id()));
                entry.rawset("ram", valueOf(program.ramMb()));
                entry.rawset("tops", valueOf(program.tops()));
                entry.rawset("state", valueOf(program.state(terminal.now())));
                list.rawset(index++, entry);
            }
            return list;
        });
        api(sys, ApiSpec.SYS_START, terminal, host, (player, args) -> {
            String name = args.arg(1).checkString();
            try {
                terminal.start(name);
                return Constants.TRUE;
            } catch (InstallException e) {
                return varargsOf(Constants.FALSE, valueOf(e.getMessage()));
            }
        });
        api(sys, ApiSpec.SYS_STOP, terminal, host, (player, args) -> {
            String name = args.arg(1).checkString();
            ScriptHost caller = host.get();
            if (caller instanceof Program self && self.name().equals(name)) {
                self.requestStop();
                return Constants.TRUE;
            }
            return valueOf(terminal.stop(name));
        });
        api(sys, ApiSpec.SYS_EVERY, terminal, host, (player, args) -> {
            if (!(host.get() instanceof Program program)) {
                throw new LuaError("sys.every is only for programs");
            }
            program.setTimer(args.arg(1).optDouble(0), terminal.now());
            return Constants.NONE;
        });
        api(sys, ApiSpec.SYS_NOTIFY, terminal, host, (player, args) -> {
            player.sendOverlayMessage(Component.literal("[" + host.get().name() + "] " + clip(args.arg(1).toString())));
            return Constants.NONE;
        });
        api(sys, ApiSpec.SYS_LOG, terminal, host, (player, args) -> {
            log(terminal, host.get(), args);
            return Constants.NONE;
        });
        api(sys, ApiSpec.SYS_FLAWS, terminal, host, (player, args) -> {
            LuaTable list = new LuaTable();
            int index = 1;
            for (FlawRules.Hit hit : terminal.flaws()) {
                LuaTable entry = new LuaTable();
                entry.rawset("class", valueOf(hit.className()));
                entry.rawset("severity", valueOf(hit.severity().id()));
                entry.rawset("cause", valueOf(hit.cause()));
                list.rawset(index++, entry);
            }
            return list;
        });
        api(sys, ApiSpec.SYS_END_SESSION, terminal, host, (player, args) -> {
            EntityHandles.Handle who = EntityHandles.check(args.arg(1), ApiSpec.SYS_END_SESSION.id());
            Sessions.Session session = Sessions.on(player.getUUID(), who.target());
            if (session == null) {
                return varargsOf(Constants.FALSE, valueOf("no_session"));
            }
            if (!terminal.spendBuffer(Config.END_SESSION_BUFFER_MB.get())) {
                return varargsOf(Constants.FALSE, valueOf("no_buffer"));
            }
            Sessions.close(session, "closed by defender");
            return Constants.TRUE;
        });
        api(sys, ApiSpec.SYS_REBOOT, terminal, host, (player, args) -> {
            terminal.reboot();
            return Constants.TRUE;
        });
        awaitApi(sys, ApiSpec.SYS_SLEEP, terminal, host, (player, args) -> {
            double seconds = args.arg(1).checkDouble();
            if (seconds < 0.05 || seconds > 60) {
                throw new LuaError("sys.sleep takes 0.05 to 60 seconds");
            }
            return new LuaSandbox.Timer(seconds);
        });
        return sys;
    }

    @FunctionalInterface
    private interface AwaitBody {
        LuaSandbox.Awaitable start(ServerPlayer player, Varargs args) throws LuaError;
    }

    /**
     * An API that suspends the calling program until it finishes (sleeping, scanning, access).
     * Kernel hooks must answer immediately, so they can't call these.
     */
    private static void awaitApi(LuaTable table, ApiSpec spec, Terminal terminal, Supplier<ScriptHost> host, AwaitBody body) {
        table.rawset(spec.member(), LuaSandbox.awaitFunction(spec.member(), args -> {
            ScriptHost caller = host.get();
            if (caller != null && caller.isKernel()) {
                throw new LuaError(spec.id() + " waits, and kernel hooks must answer immediately");
            }
            return body.start(checkCallable(spec, terminal, caller), args);
        }));
    }

    // endregion

    // region remote

    /** How long remote.inspect takes: about a second, per the design. */
    private static final int INSPECT_TICKS = 20;
    private static final int MAX_SCAN_RESULTS = 8;

    private static LuaTable remote(Terminal terminal, Supplier<ScriptHost> host) {
        LuaTable remote = new LuaTable();
        api(remote, ApiSpec.REMOTE_TARGET, terminal, host, (player, args) ->
                EntityHandles.of(crosshairTarget(player, Terminal.data(player).tier().scanRange()), player));
        api(remote, ApiSpec.REMOTE_SCAN, terminal, host, (player, args) -> {
            int range = Terminal.data(player).tier().scanRange();
            double radius = Math.clamp(args.arg(1).optDouble(range), 1, range);
            List<LivingEntity> found = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                    entity -> Gateways.targetable(entity, player) && entity.distanceTo(player) <= radius);
            found.sort(Comparator.comparingDouble(entity -> entity.distanceTo(player)));
            LuaTable list = new LuaTable();
            for (int i = 0; i < Math.min(MAX_SCAN_RESULTS, found.size()); i++) {
                list.rawset(i + 1, EntityHandles.of(found.get(i), player));
            }
            return list;
        });
        awaitApi(remote, ApiSpec.REMOTE_INSPECT, terminal, host, (player, args) -> {
            LuaValue target = args.arg(1);
            EntityHandles.check(target, ApiSpec.REMOTE_INSPECT.id());
            return new Inspection(terminal, target);
        });
        awaitApi(remote, ApiSpec.REMOTE_ACCESS, terminal, host, (player, args) -> {
            LuaValue target = args.arg(1);
            EntityHandles.check(target, ApiSpec.REMOTE_ACCESS.id());
            String method = args.arg(2).isNil() ? Access.FORCE : args.arg(2).checkString();
            // awaitApi already refuses kernels, so the caller is a program.
            return Access.begin(terminal, (Program) host.get(), player, target, method);
        });
        return remote;
    }

    /** The gateway entity under the crosshair, up to {@code range} blocks, not through walls. */
    private static @Nullable Entity crosshairTarget(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult wall = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }
        AABB area = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
                entity -> Gateways.targetable(entity, player), eye.distanceToSqr(end));
        return hit == null ? null : hit.getEntity();
    }

    /**
     * A one-second scan of another gateway. When it finishes the target must still be in range;
     * the result is its level and the flaws it currently has, exactly as it sees them, and the
     * target's programs hear about it through on_scanned.
     */
    private static final class Inspection implements LuaSandbox.Awaitable {
        private final Terminal terminal;
        private final LuaValue target;
        private int ticksLeft = INSPECT_TICKS;
        private Varargs result = Constants.NIL;

        Inspection(Terminal terminal, LuaValue target) {
            this.terminal = terminal;
            this.target = target;
        }

        @Override
        public boolean tick() {
            if (--ticksLeft > 0) {
                return false;
            }
            ServerPlayer viewer = terminal.player();
            if (viewer != null) {
                LivingEntity entity = EntityHandles.resolve(target, viewer, Terminal.data(viewer).tier().scanRange());
                if (entity != null && Gateways.targetable(entity, viewer)) {
                    result = report(entity, viewer);
                    if (entity instanceof ServerPlayer scanned) {
                        Terminals.existing(scanned).ifPresent(other ->
                                other.fire(ScriptEvents.ON_SCANNED, () -> EntityHandles.of(viewer, scanned)));
                    }
                }
            }
            return true;
        }

        private static LuaTable report(LivingEntity entity, ServerPlayer viewer) {
            LuaTable report = new LuaTable();
            report.rawset("address", valueOf(Addresses.of(entity.getUUID())));
            report.rawset("name", valueOf(entity.getName().getString()));
            report.rawset("is_player", valueOf(entity instanceof Player));
            report.rawset("distance", valueOf(entity.distanceTo(viewer)));
            GatewayData data = Gateways.of(entity);
            report.rawset("level", valueOf(data == null ? -1 : data.level()));
            LuaTable flaws = new LuaTable();
            for (FlawRules.Hit hit : Gateways.flawsOf(entity)) {
                flaws.rawset(hit.className(), valueOf(hit.severity().id()));
            }
            report.rawset("flaws", flaws);
            return report;
        }

        @Override
        public Varargs result() {
            return result;
        }

        @Override
        public String state() {
            return "scanning";
        }
    }

    // endregion

    // region Plumbing

    @FunctionalInterface
    private interface Body {
        Varargs call(ServerPlayer player, Varargs args) throws LuaError;
    }

    private static void api(LuaTable table, ApiSpec spec, Terminal terminal, Supplier<ScriptHost> host, Body body) {
        table.rawset(spec.member(), function(spec.member(), (state, args) -> {
            ScriptHost caller = host.get();
            ServerPlayer player = checkCallable(spec, terminal, caller);
            return body.call(player, args);
        }));
    }

    /** The runtime half of the install checks, plus charging the call's steps. */
    private static ServerPlayer checkCallable(ApiSpec spec, Terminal terminal, ScriptHost caller) throws LuaError {
        if (caller == null || !caller.isActive()) {
            throw new LuaError(spec.id() + " can't be called while the script is loading");
        }
        ServerPlayer player = player(terminal);
        GatewayTier tier = Terminal.data(player).tier();
        // Preset code keeps its privilege bypass: it may call APIs above the player's level.
        if (spec.minLevel() > Math.max(tier.level(), caller.privilegeLevel())) {
            throw new LuaError(spec.id() + " needs gateway LV" + spec.minLevel());
        }
        caller.sandbox().charge(spec.steps() * ApiSpec.STEP_INSTRUCTIONS);
        return player;
    }

    private static ServerPlayer player(Terminal terminal) throws LuaError {
        ServerPlayer player = terminal.player();
        if (player == null) {
            throw new LuaError("the terminal is offline");
        }
        return player;
    }

    private static void log(Terminal terminal, ScriptHost caller, Varargs args) {
        StringBuilder line = new StringBuilder();
        for (int i = 1; i <= args.count(); i++) {
            if (i > 1) {
                line.append(' ');
            }
            line.append(args.arg(i).toString());
        }
        terminal.log(caller == null ? "?" : caller.name(), clip(line.toString()));
    }

    private static double range(Varargs args, int index, double min, double max) throws LuaError {
        double value = args.arg(index).checkDouble();
        if (value < min || value > max) {
            throw new LuaError("argument #" + index + " must be from " + min + " to " + max);
        }
        return value;
    }

    private static int seconds(Varargs args, int index, double min, double max) throws LuaError {
        return Mth.ceil(range(args, index, min, max) * 20);
    }

    private static String clip(String text) {
        return text.length() <= MAX_MESSAGE_LENGTH ? text : text.substring(0, MAX_MESSAGE_LENGTH) + "...";
    }

    // endregion
}
