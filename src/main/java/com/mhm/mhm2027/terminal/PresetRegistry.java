package com.mhm.mhm2027.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Preset programs and kernels, loaded from datapacks: {@code data/<namespace>/edge_presets/<name>.lua}.
 *
 * Presets are ordinary Lua for whoever writes the datapack. In game they are locked: a preset
 * chip carries only the preset's id, and running it reads the code from here, so players can
 * run a preset but never read or change it. Each one is checked when it loads; a preset whose
 * code reaches past its privilege is rejected with an error in the log.
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public final class PresetRegistry {
    public static final String PREFIX = "preset:";
    private static final String FOLDER = "edge_presets";

    /**
     * @param level    the gateway level needed to write and run it; the code runs at level + 1.
     *                 For an Overload preset, the level its code runs at: gateways up to the
     *                 Overload reach below it may run it, from an Overload socket.
     * @param overload marked {@code overload = true}: runs from an Overload socket and heats the gateway
     */
    public record Preset(Identifier id, String chunk, String source, ScriptSummary summary, int level, String description,
                         boolean overload) {
        public boolean isKernel() {
            return summary.isKernel();
        }

        /** The level its locked code runs at: the privilege bypass. */
        public int privilegeLevel() {
            return overload ? level : level + 1;
        }

        /** How the terminal and panel refer to it. */
        public String terminalId() {
            return PREFIX + id;
        }
    }

    private static volatile Map<Identifier, Preset> presets = Map.of();

    private PresetRegistry() {
    }

    public static Optional<Preset> get(Identifier id) {
        return Optional.ofNullable(presets.get(id));
    }

    /** Resolves a terminal id ({@code preset:<id>}), if it names a known preset. */
    public static Optional<Preset> byTerminalId(String terminalId) {
        if (!terminalId.startsWith(PREFIX)) {
            return Optional.empty();
        }
        Identifier id = Identifier.tryParse(terminalId.substring(PREFIX.length()));
        return id == null ? Optional.empty() : get(id);
    }

    public static Collection<Preset> all() {
        return presets.values();
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Mhm2027.MODID, FOLDER), new Loader());
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<Identifier, Preset>> {
        @Override
        protected Map<Identifier, Preset> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<Identifier, Preset> loaded = new LinkedHashMap<>();
            for (Map.Entry<Identifier, Resource> entry : manager.listResources(FOLDER, path -> path.getPath().endsWith(".lua")).entrySet()) {
                Identifier file = entry.getKey();
                String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - 4);
                Identifier id = Identifier.fromNamespaceAndPath(file.getNamespace(), path);
                try (InputStream in = entry.getValue().open()) {
                    String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    ScriptAnalyzer.Analysis analysis = ScriptAnalyzer.analyze(path, source, true);
                    loaded.put(id, new Preset(id, path, source, analysis.summary(), analysis.presetLevel(), analysis.description(), analysis.overload()));
                } catch (IOException | InstallException e) {
                    Mhm2027.LOGGER.error("Edge preset {} was rejected: {}", id, e.getMessage());
                }
            }
            return loaded;
        }

        @Override
        protected void apply(Map<Identifier, Preset> loaded, ResourceManager manager, ProfilerFiller profiler) {
            presets = Map.copyOf(loaded);
            Mhm2027.LOGGER.info("Loaded {} Edge presets", presets.size());
        }
    }
}
