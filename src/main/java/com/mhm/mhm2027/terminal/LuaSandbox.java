package com.mhm.mhm2027.terminal;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.LuaUserdata;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.UnwindThrowable;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.debug.DebugHook;
import org.squiddev.cobalt.debug.DebugState;
import org.squiddev.cobalt.function.Dispatch;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.RegisteredFunction;
import org.squiddev.cobalt.function.ResumableVarArgFunction;
import org.squiddev.cobalt.interrupt.InterruptAction;
import org.squiddev.cobalt.lib.BaseLib;
import org.squiddev.cobalt.lib.MathLib;
import org.squiddev.cobalt.lib.StringLib;
import org.squiddev.cobalt.lib.TableLib;

/**
 * One isolated Lua state with an instruction budget.
 *
 * Only Lua's safe libraries are installed: base (minus code loading), math, string and table.
 * There is no coroutine, io, os or debug library, so scripts can't load code or reach the disk.
 *
 * TOPS is a speed limit measured while code runs: {@link #run} gives a coroutine a number of
 * instructions, and when they run out the coroutine is suspended mid-function and can be
 * resumed next tick from exactly where it stopped.
 */
public final class LuaSandbox {
    /** How often the count hook fires. Budget overshoots by at most this many instructions. */
    private static final int HOOK_GRANULARITY = 10;
    /** Longest string string.rep may build, so one call can't exhaust server memory. */
    private static final int MAX_REP_LENGTH = 1 << 20;

    private static final LuaValue AWAIT_MARKER = ValueFactory.valueOf("edge:await");

    private final LuaState state;
    private final DebugHook budgetHook = new DebugHook() {
        @Override
        public void onCount(LuaState state, DebugState ds, DebugFrame frame) {
            charge(HOOK_GRANULARITY);
        }
    };

    private int budget;

    public LuaSandbox() throws LuaError {
        // The interrupt handler is only asked when an interrupt was raised; checking the budget
        // here means a stale interrupt from an earlier run never pauses a fresh one.
        state = LuaState.builder().interruptHandler(() -> budget <= 0 ? InterruptAction.SUSPEND : InterruptAction.CONTINUE).build();
        BaseLib.add(state);
        TableLib.add(state);
        StringLib.add(state);
        MathLib.add(state);

        LuaTable globals = state.globals();
        for (String unsafe : new String[]{"load", "loadstring", "getfenv", "setfenv"}) {
            globals.rawset(unsafe, Constants.NIL);
        }
        capStringRep(globals);
    }

    public LuaState state() {
        return state;
    }

    public LuaTable globals() {
        return state.globals();
    }

    /** Takes instructions off the current budget, pausing the script at the next instruction once it runs out. */
    public void charge(int instructions) {
        budget -= instructions;
        if (budget <= 0) {
            state.interrupt();
        }
    }

    /** Starts a new coroutine for a function. Run it with {@link #run}. */
    public LuaThread newThread(LuaFunction function) {
        LuaThread thread = new LuaThread(state, function);
        thread.getDebugState().setHook(budgetHook, false, false, false, HOOK_GRANULARITY);
        return thread;
    }

    /**
     * Runs or resumes a coroutine with up to {@code instructions} of budget.
     *
     * @throws LuaError if the script raised an error; the coroutine is dead afterwards
     */
    public Slice run(LuaThread thread, Varargs args, int instructions) throws LuaError {
        budget = instructions;
        Varargs result = LuaThread.run(thread, args);
        int used = Math.max(0, instructions - Math.max(budget, 0));
        if (thread.getStatus() == LuaThread.Status.DEAD) {
            return new Slice(Slice.Kind.FINISHED, used, null, result);
        }
        if (result == null) {
            return new Slice(Slice.Kind.PAUSED, instructions, null, Constants.NONE);
        }
        // The only way to yield to us is an await function, which yields (marker, awaitable).
        if (AWAIT_MARKER.equals(result.first()) && result.arg(2) instanceof LuaUserdata userdata
                && userdata.toUserdata() instanceof Awaitable awaitable) {
            return new Slice(Slice.Kind.AWAITING, used, awaitable, Constants.NONE);
        }
        throw new LuaError("unexpected yield");
    }

    /** A named Lua function backed by Java. */
    public static LibFunction function(String name, LibFunction.ManyArgs body) {
        return RegisteredFunction.ofV(name, body).create();
    }

    /**
     * Something a program waits for: a timer, a scan, an access attempt. The program is
     * suspended mid-call until it completes, then the call returns {@link #result()}.
     */
    public interface Awaitable {
        /** Called once per tick while the program waits; true once it has finished. */
        boolean tick();

        /** What the waiting call returns. */
        Varargs result();

        /** How the panel describes a program waiting on this. */
        default String state() {
            return "waiting";
        }
    }

    @FunctionalInterface
    public interface AwaitStarter {
        Awaitable start(Varargs args) throws LuaError;
    }

    /**
     * A Lua function that suspends the calling program until the Java side finishes. Bound as
     * sys.sleep, remote.inspect and later remote.access.
     */
    public static LibFunction awaitFunction(String name, AwaitStarter starter) {
        LibFunction function = new ResumableVarArgFunction<Void>() {
            @Override
            protected Varargs invoke(LuaState state, DebugFrame di, Varargs args) throws LuaError, UnwindThrowable {
                Awaitable awaitable = starter.start(args);
                return LuaThread.yield(state, ValueFactory.varargsOf(AWAIT_MARKER, new LuaUserdata(awaitable)));
            }

            @Override
            public Varargs resume(LuaState state, Void object, Varargs value) {
                return value;
            }
        };
        return RegisteredFunction.ofFactory(name, () -> function).create();
    }

    /** Waits a number of ticks. Behind sys.sleep. */
    public static final class Timer implements Awaitable {
        private int ticksLeft;

        public Timer(double seconds) {
            ticksLeft = Math.max(1, (int) Math.round(seconds * 20));
        }

        @Override
        public boolean tick() {
            return --ticksLeft <= 0;
        }

        @Override
        public Varargs result() {
            return Constants.NONE;
        }

        @Override
        public String state() {
            return "sleeping";
        }
    }

    private void capStringRep(LuaTable globals) throws LuaError {
        LuaTable string = (LuaTable) globals.rawget("string");
        LuaFunction rep = (LuaFunction) string.rawget("rep");
        string.rawset("rep", function("rep", (s, args) -> {
            long length = (long) args.arg(1).checkLuaString().length() * Math.max(0, args.arg(2).checkInteger());
            if (length > MAX_REP_LENGTH) {
                throw new LuaError("string.rep result too large");
            }
            try {
                return Dispatch.invoke(s, rep, args);
            } catch (UnwindThrowable e) {
                throw new LuaError("string.rep can't yield");
            }
        }));
    }

    /**
     * The outcome of one {@link #run}.
     *
     * @param used      instructions spent
     * @param awaitable what the script is waiting for, for {@link Kind#AWAITING}
     * @param result    return values, for {@link Kind#FINISHED}
     */
    public record Slice(Kind kind, int used, Awaitable awaitable, Varargs result) {
        public enum Kind {
            /** The function returned. */
            FINISHED,
            /** The budget ran out; resume next tick. */
            PAUSED,
            /** The script called an await function (sys.sleep, remote.inspect, ...). */
            AWAITING
        }
    }
}
