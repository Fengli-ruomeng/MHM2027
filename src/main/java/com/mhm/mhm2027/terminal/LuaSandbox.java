package com.mhm.mhm2027.terminal;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaThread;
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

    private static final LuaValue SLEEP_MARKER = ValueFactory.valueOf("edge:sleep");

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
            return new Slice(Slice.Kind.FINISHED, used, 0, result);
        }
        if (result == null) {
            return new Slice(Slice.Kind.PAUSED, instructions, 0, Constants.NONE);
        }
        // The only way to yield to us is sys.sleep, which yields (marker, seconds).
        double seconds = SLEEP_MARKER.equals(result.first()) ? result.arg(2).toDouble() : 0;
        return new Slice(Slice.Kind.SLEEPING, used, seconds, Constants.NONE);
    }

    /** A named Lua function backed by Java. */
    public static LibFunction function(String name, LibFunction.ManyArgs body) {
        return RegisteredFunction.ofV(name, body).create();
    }

    /** A function that suspends the calling program for some seconds. Bound as sys.sleep. */
    public static LibFunction sleepFunction(SleepGuard guard) {
        return new ResumableVarArgFunction<Void>() {
            @Override
            protected Varargs invoke(LuaState state, DebugFrame di, Varargs args) throws LuaError, UnwindThrowable {
                double seconds = args.arg(1).checkDouble();
                guard.check(seconds);
                return LuaThread.yield(state, ValueFactory.varargsOf(SLEEP_MARKER, ValueFactory.valueOf(seconds)));
            }

            @Override
            public Varargs resume(LuaState state, Void object, Varargs value) {
                return Constants.NONE;
            }
        };
    }

    /** Lets the caller refuse a sleep, for example inside a kernel hook. */
    @FunctionalInterface
    public interface SleepGuard {
        void check(double seconds) throws LuaError;
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
     * @param used    instructions spent
     * @param seconds how long to sleep, for {@link Kind#SLEEPING}
     * @param result  return values, for {@link Kind#FINISHED}
     */
    public record Slice(Kind kind, int used, double seconds, Varargs result) {
        public enum Kind {
            /** The function returned. */
            FINISHED,
            /** The budget ran out; resume next tick. */
            PAUSED,
            /** The script called sys.sleep. */
            SLEEPING
        }
    }
}
