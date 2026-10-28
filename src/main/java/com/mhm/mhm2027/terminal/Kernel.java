package com.mhm.mhm2027.terminal;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.LuaFunction;

import com.mhm.mhm2027.gateway.GatewayData;

/**
 * The rules every program runs under: settings plus a few hooks. A kernel is not a program.
 *
 * Hooks work like cancellable events: the game stops, asks the hook what to do, and uses the
 * answer. They must answer immediately: no sleeping, and a small instruction cap. A hook that
 * fails or runs over gets its default behaviour instead.
 */
public final class Kernel implements ScriptHost {
    public static final String ON_ACCESS_REQUEST = ScriptEvents.ON_ACCESS_REQUEST;
    public static final String ON_SESSION_OPENED = ScriptEvents.ON_SESSION_OPENED;
    public static final String ON_LOW_RAM = ScriptEvents.ON_LOW_RAM;
    public static final String ON_PANIC = ScriptEvents.ON_PANIC;

    private static final int HOOK_INSTRUCTIONS = 2_000;

    private final String name;
    private final KernelSettings settings;
    private final Map<String, LuaFunction> hooks;
    private final @Nullable LuaSandbox sandbox;
    private final int privilegeLevel;
    private boolean active;

    private Kernel(String name, KernelSettings settings, Map<String, LuaFunction> hooks, @Nullable LuaSandbox sandbox, int privilegeLevel) {
        this.name = name;
        this.settings = settings;
        this.hooks = hooks;
        this.sandbox = sandbox;
        this.privilegeLevel = privilegeLevel;
    }

    /** The built-in kernel: default settings, no hooks. Every gateway starts with it. */
    public static Kernel baseline() {
        return new Kernel(GatewayData.DEFAULT_KERNEL, KernelSettings.DEFAULT, Map.of(), null, -1);
    }

    /**
     * Loads and checks a kernel script.
     *
     * @param level          the gateway level API checks run against
     * @param hookPoints     how many hooks it may define
     * @param privilegeLevel for a preset kernel, the level its locked code runs at; -1 for your own
     */
    static Kernel load(Terminal terminal, String chunk, String source, int level, int hookPoints, int privilegeLevel) throws InstallException {
        LuaSandbox sandbox;
        try {
            sandbox = new LuaSandbox();
        } catch (LuaError e) {
            throw new InstallException("Couldn't create the Lua runtime: " + e.getMessage());
        }
        Kernel[] self = new Kernel[1];
        Apis.bindKernel(sandbox, terminal, () -> self[0]);

        LuaTable header = ScriptCompiler.load(sandbox, chunk, source, "kernel", "program");
        ScriptCompiler.checkKernelKeys(header, privilegeLevel >= 0);
        String name = ScriptCompiler.stringField(header, "name", chunk);
        KernelSettings settings = KernelSettings.fromHeader(header);
        ScriptCompiler.scanApis(source, true, null, level);

        Map<String, LuaFunction> hooks = ScriptCompiler.kernelHooks(sandbox);
        if (hooks.size() > hookPoints) {
            throw new InstallException("This kernel defines " + hooks.size() + " hooks, but your gateway has " + hookPoints + " hook points");
        }

        self[0] = new Kernel(name, settings, hooks, sandbox, privilegeLevel);
        self[0].active = true;
        return self[0];
    }

    /**
     * Calls a hook and returns its results, or null if the kernel doesn't define it, it failed,
     * or it ran past its cap. Null means: use the default.
     */
    @Nullable Varargs callHook(Terminal terminal, String hook, Varargs args) {
        LuaFunction function = hooks.get(hook);
        if (function == null || sandbox == null) {
            return null;
        }
        try {
            LuaSandbox.Slice slice = sandbox.run(sandbox.newThread(function), args, HOOK_INSTRUCTIONS);
            if (slice.kind() == LuaSandbox.Slice.Kind.FINISHED) {
                return slice.result();
            }
            terminal.log(name, hook + " ran over its limit; the default was used");
        } catch (LuaError e) {
            terminal.log(name, hook + " failed: " + ScriptCompiler.firstLine(e.getMessage()) + "; the default was used");
        }
        return null;
    }

    public boolean hasHook(String hook) {
        return hooks.containsKey(hook);
    }

    public List<String> hookNames() {
        return List.copyOf(hooks.keySet());
    }

    public KernelSettings settings() {
        return settings;
    }

    public boolean isBaseline() {
        return sandbox == null;
    }

    public boolean isPreset() {
        return privilegeLevel >= 0;
    }

    public int ramMb() {
        return KernelSettings.ramFor(hooks.size());
    }

    public int topsReserve() {
        return settings.topsReserve();
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
        return true;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public int privilegeLevel() {
        return privilegeLevel;
    }
}
