package com.mhm.mhm2027.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Flaw classes, loaded from datapacks: {@code data/<namespace>/edge_flaws/<name>.json}.
 *
 * Players can't change the rules. A datapack can add a class or replace one by using the same
 * class name (the part of {@code id} after the colon). A file that doesn't parse is skipped and
 * reported in the log; the rest still load. The check itself is in {@link FlawRules}.
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public final class FlawRegistry {
    private static final String FOLDER = "edge_flaws";
    /** The classes the design names, in the order the panel and the command list them. */
    private static final List<String> CLASS_ORDER = List.of("memory", "intake", "scheduler", "thermal", "motor", "core");

    private static volatile List<FlawRules.ClassDef> classes = List.of();

    private FlawRegistry() {
    }

    public static List<FlawRules.ClassDef> classes() {
        return classes;
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Mhm2027.MODID, FOLDER), new Loader());
    }

    private static List<FlawRules.ClassDef> inDesignOrder(Map<String, FlawRules.ClassDef> loaded) {
        List<FlawRules.ClassDef> sorted = new ArrayList<>(loaded.values());
        sorted.sort(Comparator.comparingInt((FlawRules.ClassDef def) -> {
            int index = CLASS_ORDER.indexOf(def.className());
            return index < 0 ? CLASS_ORDER.size() : index;
        }).thenComparing(FlawRules.ClassDef::className));
        return List.copyOf(sorted);
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<String, FlawRules.ClassDef>> {
        @Override
        protected Map<String, FlawRules.ClassDef> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<String, FlawRules.ClassDef> loaded = new LinkedHashMap<>();
            for (Map.Entry<Identifier, Resource> entry : manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).entrySet()) {
                Identifier file = entry.getKey();
                try (InputStream in = entry.getValue().open()) {
                    FlawRules.ClassDef def = FlawRules.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    FlawRules.ClassDef previous = loaded.put(def.className(), def);
                    if (previous != null) {
                        Mhm2027.LOGGER.warn("Edge flaw {} replaced {} (same class {})", def.id(), previous.id(), def.className());
                    }
                } catch (IOException | FlawRules.FlawFormatException | RuntimeException e) {
                    Mhm2027.LOGGER.error("Edge flaw {} was rejected: {}", file, e.getMessage());
                }
            }
            return loaded;
        }

        @Override
        protected void apply(Map<String, FlawRules.ClassDef> loaded, ResourceManager manager, ProfilerFiller profiler) {
            classes = inDesignOrder(loaded);
            Mhm2027.LOGGER.info("Loaded {} Edge flaw classes", classes.size());
        }
    }
}
