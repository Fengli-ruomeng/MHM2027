package com.mhm.mhm2027.terminal;

import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

import java.util.function.Supplier;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.function.RegisteredFunction;

import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.GatewayTier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;

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
        bindCommon(globals, terminal, host);
    }

    static void bindKernel(LuaSandbox sandbox, Terminal terminal, Supplier<? extends ScriptHost> host) {
        LuaTable globals = sandbox.globals();
        globals.rawset("sys", sys(terminal, host::get));
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
        // sys.sleep yields back to the terminal, so it's bound separately from the plain functions.
        sys.rawset(ApiSpec.SYS_SLEEP.member(), RegisteredFunction.ofFactory(ApiSpec.SYS_SLEEP.member(), () -> LuaSandbox.sleepFunction(seconds -> {
            ScriptHost caller = host.get();
            if (caller.isKernel()) {
                throw new LuaError("kernel hooks must answer immediately and can't sleep");
            }
            checkCallable(ApiSpec.SYS_SLEEP, terminal, caller);
            if (seconds < 0.05 || seconds > 60) {
                throw new LuaError("sys.sleep takes 0.05 to 60 seconds");
            }
        })).create());
        return sys;
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
