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
    public static final String ON_ACCESS_REQUEST = "on_access_request";
    public static final String ON_SESSION_OPENED = "on_session_opened";
    public static final String ON_LOW_RAM = "on_low_ram";
    public static final String ON_PANIC = "on_panic";
    static final List<String> HOOKS = List.of(ON_ACCESS_REQUEST, ON_SESSION_OPENED, ON_LOW_RAM, ON_PANIC);

    /** TOPS always held for hook work, so a busy fight never starves the kernel. */
    private static final int HOOK_RESERVE_TOPS = 2;
    private static final int HOOK_INSTRUCTIONS = 2_000;
    private static final int BASE_RAM_MB = 8;
    private static final int RAM_PER_HOOK_MB = 2;

    private final String name;
    private final KernelSettings settings;
    private final Map<String, LuaFunction> hooks;
    private final @Nullable LuaSandbox sandbox;
    private boolean active;

    private Kernel(String name, KernelSettings settings, Map<String, LuaFunction> hooks, @Nullable LuaSandbox sandbox) {
        this.name = name;
        this.settings = settings;
        this.hooks = hooks;
        this.sandbox = sandbox;
    }

    /** The built-in kernel: default settings, no hooks. Every gateway starts with it. */
    public static Kernel baseline() {
        return new Kernel(GatewayData.DEFAULT_KERNEL, KernelSettings.DEFAULT, Map.of(), null);
    }

    static Kernel load(Terminal terminal, String file, String source, int level, int hookPoints) throws InstallException {
        LuaSandbox sandbox;
        try {
            sandbox = new LuaSandbox();
        } catch (LuaError e) {
            throw new InstallException("Couldn't create the Lua runtime: " + e.getMessage());
        }
        Kernel[] self = new Kernel[1];
        Apis.bindKernel(sandbox, terminal, () -> self[0]);

        LuaTable header = ScriptCompiler.load(sandbox, file, source, "kernel", "program");
        ScriptCompiler.checkKeys(header, KernelSettings.HEADER_KEYS);
        String name = ScriptCompiler.stringField(header, "name", file);
        KernelSettings settings = KernelSettings.fromHeader(header);
        ScriptCompiler.scanApis(source, true, null, level);

        Map<String, LuaFunction> hooks = ScriptCompiler.handlers(sandbox, HOOKS);
        for (String event : Program.EVENTS) {
            if (!sandbox.globals().rawget(event).isNil()) {
                throw new InstallException(event + " is a program event; kernels define hooks (" + String.join(", ", HOOKS) + ")");
            }
        }
        if (hooks.size() > hookPoints) {
            throw new InstallException("This kernel defines " + hooks.size() + " hooks, but your gateway has " + hookPoints + " hook points");
        }

        self[0] = new Kernel(name, settings, hooks, sandbox);
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

    public int ramMb() {
        return BASE_RAM_MB + RAM_PER_HOOK_MB * hooks.size();
    }

    public int topsReserve() {
        return HOOK_RESERVE_TOPS + settings.gcTops();
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
}
