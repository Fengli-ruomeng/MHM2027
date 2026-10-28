package com.mhm.mhm2027.terminal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.LuaFunction;

/**
 * One running program. Each has its own Lua state, so its globals are its own data.
 *
 * Events queue up and are handled one at a time, each in its own coroutine. Every tick the
 * program may spend {@code tops x instructions per TOPS}; when that runs out the handler is
 * paused mid-function and resumed next tick. A bad loop only slows itself.
 */
public final class Program implements ScriptHost {
    public static final String ON_START = "on_start";
    public static final String ON_STOP = "on_stop";
    public static final String ON_RUN = "on_run";
    public static final String ON_TIMER = "on_timer";
    public static final String ON_HIT_TAKEN = "on_hit_taken";
    public static final String ON_HIT_DEALT = "on_hit_dealt";
    public static final String ON_KILL = "on_kill";
    static final List<String> EVENTS = List.of(ON_START, ON_STOP, ON_RUN, ON_TIMER, ON_HIT_TAKEN, ON_HIT_DEALT, ON_KILL);

    private static final Set<String> HEADER_KEYS = Set.of("name", "type", "tops");
    private static final int MAX_QUEUED_EVENTS = 8;
    private static final int MIN_TIMER_TICKS = 5;
    private static final int PEAK_WINDOW_TICKS = 100;

    private final String name;
    private final ProgramType type;
    private final int tops;
    private final int ramMb;
    private final boolean needsSlot;
    private final LuaSandbox sandbox;
    private final Map<String, LuaFunction> handlers;
    private final Deque<QueuedEvent> queue = new ArrayDeque<>();

    private boolean active;
    private boolean stopRequested;
    private LuaThread current;
    private Varargs currentArgs = Constants.NONE;
    private long sleepUntil;
    private int timerTicks;
    private long nextTimer;
    private boolean pausedLastTick;
    private double averageTops;
    private double peakTops;
    private double windowPeak;
    private int windowTicks;

    /**
     * Loads and checks a program. Nothing runs in the world until {@link #begin}.
     *
     * @param level the gateway level the install checks run against
     */
    Program(Terminal terminal, String file, String source, int level, int maxTops) throws InstallException {
        try {
            sandbox = new LuaSandbox();
        } catch (LuaError e) {
            throw new InstallException("Couldn't create the Lua runtime: " + e.getMessage());
        }
        Apis.bindProgram(sandbox, terminal, this);

        LuaTable header = ScriptCompiler.load(sandbox, file, source, "program", "kernel");
        ScriptCompiler.checkKeys(header, HEADER_KEYS);
        name = ScriptCompiler.stringField(header, "name", file);
        String typeId = ScriptCompiler.stringField(header, "type", ProgramType.RESIDENT.id());
        type = ProgramType.byId(typeId).orElseThrow(() -> new InstallException("type must be resident, transient or query"));
        tops = ScriptCompiler.intField(header, "tops", 1, 1, Math.max(1, maxTops));

        Set<ApiSpec> apis = ScriptCompiler.scanApis(source, false, type, level);
        ramMb = type.baseRamMb() + apis.stream().mapToInt(ApiSpec::ramMb).sum();
        needsSlot = apis.stream().anyMatch(ApiSpec::needsSlot);
        handlers = ScriptCompiler.handlers(sandbox, EVENTS);
    }

    /** Starts the program: residents get on_start, others get on_run. */
    void begin() {
        active = true;
        enqueue(type == ProgramType.RESIDENT ? ON_START : ON_RUN, () -> Constants.NONE);
    }

    void enqueue(String event, Supplier<Varargs> args) {
        if (handlers.containsKey(event) && queue.size() < MAX_QUEUED_EVENTS) {
            queue.add(new QueuedEvent(event, args));
        }
    }

    /**
     * A program that stops itself can't run on_stop in the middle of its own handler, so the
     * terminal stops it once this tick's slice is over.
     */
    void requestStop() {
        stopRequested = true;
    }

    boolean stopRequested() {
        return stopRequested;
    }

    void setTimer(double seconds, long now) {
        timerTicks = seconds <= 0 ? 0 : Math.max(MIN_TIMER_TICKS, (int) Math.round(seconds * 20));
        nextTimer = now + timerTicks;
    }

    /**
     * Runs this program for one tick.
     *
     * @return false once the program has ended (finished or crashed) and should be removed
     */
    boolean tick(Terminal terminal, long now, int instructionsPerTops) {
        if (timerTicks > 0 && now >= nextTimer) {
            enqueue(ON_TIMER, () -> Constants.NONE);
            nextTimer = now + timerTicks;
        }

        int budget = tops * instructionsPerTops;
        int used = 0;
        pausedLastTick = false;
        try {
            while (used < budget && !stopRequested) {
                if (current == null) {
                    QueuedEvent event = queue.poll();
                    if (event == null) {
                        break;
                    }
                    current = sandbox.newThread(handlers.get(event.name()));
                    currentArgs = event.args().get();
                } else if (sleepUntil > now) {
                    break;
                }

                LuaSandbox.Slice slice = sandbox.run(current, currentArgs, budget - used);
                currentArgs = Constants.NONE;
                used += slice.used();
                if (slice.kind() == LuaSandbox.Slice.Kind.FINISHED) {
                    current = null;
                } else if (slice.kind() == LuaSandbox.Slice.Kind.SLEEPING) {
                    sleepUntil = now + Math.max(1, Math.round(slice.seconds() * 20));
                    break;
                } else {
                    pausedLastTick = true;
                    break;
                }
            }
        } catch (LuaError e) {
            terminal.log(name, "crashed: " + ScriptCompiler.firstLine(e.getMessage()));
            terminal.notifyCrash(this);
            return false;
        }

        recordUsage((double) used / instructionsPerTops);

        if (type != ProgramType.RESIDENT && current == null && queue.isEmpty()) {
            terminal.log(name, "finished");
            return false;
        }
        return true;
    }

    /** Gives on_stop one tick of budget. It can't sleep or run on; whatever is left is dropped. */
    void stop(Terminal terminal, int instructionsPerTops) {
        LuaFunction onStop = handlers.get(ON_STOP);
        active = false;
        if (onStop == null) {
            return;
        }
        active = true;
        try {
            sandbox.run(sandbox.newThread(onStop), Constants.NONE, tops * instructionsPerTops);
        } catch (LuaError e) {
            terminal.log(name, "on_stop failed: " + ScriptCompiler.firstLine(e.getMessage()));
        } finally {
            active = false;
        }
    }

    private void recordUsage(double topsUsed) {
        averageTops = averageTops * 0.95 + topsUsed * 0.05;
        windowPeak = Math.max(windowPeak, topsUsed);
        if (++windowTicks >= PEAK_WINDOW_TICKS) {
            peakTops = windowPeak;
            windowPeak = 0;
            windowTicks = 0;
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LuaSandbox sandbox() {
        return sandbox;
    }

    @Override
    public boolean isKernel() {
        return false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public ProgramType type() {
        return type;
    }

    public int tops() {
        return tops;
    }

    public int ramMb() {
        return ramMb;
    }

    public boolean needsSlot() {
        return needsSlot;
    }

    public double averageTops() {
        return averageTops;
    }

    public double peakTops() {
        return Math.max(peakTops, windowPeak);
    }

    /** idle, running, sleeping or paused, for the ps view. */
    public String state(long now) {
        if (current == null) {
            return "idle";
        }
        if (sleepUntil > now) {
            return "sleeping";
        }
        return pausedLastTick ? "paused (over budget)" : "running";
    }

    private record QueuedEvent(String name, Supplier<Varargs> args) {
    }
}
