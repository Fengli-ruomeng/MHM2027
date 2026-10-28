package com.mhm.mhm2027.terminal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.function.LuaFunction;

import com.mhm.mhm2027.gateway.GatewayTier;

/**
 * Works out what a script is (name, type, cost, level) without a player or a world: for
 * burning code onto a chip, and for checking datapack presets when they load. It runs the
 * same install checks as the terminal, so a script that analyses cleanly will install.
 */
public final class ScriptAnalyzer {
    /**
     * @param presetLevel for a preset, the gateway level it requires; 0 otherwise
     */
    public record Analysis(ScriptSummary summary, int presetLevel, String description) {
    }

    private ScriptAnalyzer() {
    }

    /**
     * Analyses a script.
     *
     * Own code may use any API (the level shown is the highest one it needs). A preset that
     * requires LV N runs as if the gateway were LV N+1, so its code may use APIs and hook points
     * up to that level and no further.
     */
    public static Analysis analyze(String chunk, String source, boolean preset) throws InstallException {
        LuaSandbox sandbox;
        try {
            sandbox = new LuaSandbox();
        } catch (LuaError e) {
            throw new InstallException("Couldn't create the Lua runtime: " + e.getMessage());
        }
        return switch (ScriptStore.kindOf(source)) {
            case KERNEL -> analyzeKernel(sandbox, chunk, source, preset);
            case PROGRAM -> analyzeProgram(sandbox, chunk, source, preset);
            case UNKNOWN -> throw new InstallException("No program { } or kernel { } header");
        };
    }

    private static Analysis analyzeProgram(LuaSandbox sandbox, String chunk, String source, boolean preset) throws InstallException {
        LuaTable header = ScriptCompiler.load(sandbox, chunk, source, "program", "kernel");
        ScriptCompiler.ProgramHeader parsed = ScriptCompiler.programHeader(header, chunk, preset, GatewayTier.LV11.tops());
        int presetLevel = preset ? ScriptCompiler.presetLevel(header) : 0;
        int allowed = preset ? presetLevel + 1 : GatewayTier.MAX_LEVEL;
        Set<ApiSpec> apis = ScriptCompiler.scanApis(source, false, parsed.type(), allowed);
        ScriptCompiler.handlers(sandbox, ScriptEvents.PROGRAM_EVENTS);

        int level = preset ? presetLevel : apis.stream().mapToInt(ApiSpec::minLevel).max().orElse(0);
        ScriptSummary summary = new ScriptSummary(parsed.name(), parsed.type().id(), parsed.tops(),
                ScriptCompiler.programRam(parsed.type(), apis), level, apis.stream().map(ApiSpec::id).toList());
        return new Analysis(summary, presetLevel, preset ? ScriptCompiler.presetDescription(header) : "");
    }

    private static Analysis analyzeKernel(LuaSandbox sandbox, String chunk, String source, boolean preset) throws InstallException {
        LuaTable header = ScriptCompiler.load(sandbox, chunk, source, "kernel", "program");
        ScriptCompiler.checkKernelKeys(header, preset);
        String name = ScriptCompiler.stringField(header, "name", chunk);
        KernelSettings settings = KernelSettings.fromHeader(header);
        int presetLevel = preset ? ScriptCompiler.presetLevel(header) : 0;
        ScriptCompiler.scanApis(source, true, null, preset ? presetLevel + 1 : GatewayTier.MAX_LEVEL);

        Map<String, LuaFunction> hooks = ScriptCompiler.kernelHooks(sandbox);
        if (preset && hooks.size() > GatewayTier.of(presetLevel + 1).hookPoints()) {
            throw new InstallException("A preset requiring LV" + presetLevel + " may use up to "
                    + GatewayTier.of(presetLevel + 1).hookPoints() + " hooks; this one defines " + hooks.size());
        }
        int level = preset ? presetLevel : levelForHooks(hooks.size());
        ScriptSummary summary = new ScriptSummary(name, ScriptSummary.KERNEL, settings.topsReserve(),
                KernelSettings.ramFor(hooks.size()), level, List.copyOf(hooks.keySet()));
        return new Analysis(summary, presetLevel, preset ? ScriptCompiler.presetDescription(header) : "");
    }

    /** The lowest gateway level with enough hook points for this many hooks. */
    static int levelForHooks(int hooks) {
        for (int level = 0; level <= GatewayTier.MAX_LEVEL; level++) {
            if (GatewayTier.of(level).hookPoints() >= hooks) {
                return level;
            }
        }
        return GatewayTier.MAX_LEVEL;
    }
}
