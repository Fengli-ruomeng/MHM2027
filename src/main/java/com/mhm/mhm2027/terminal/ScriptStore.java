package com.mhm.mhm2027.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Each player's local storage: a folder of .lua files on the server, under
 * {@code <world>/edge/players/<uuid>}. Only these files can run; items are just copies for
 * passing code around. A player can only ever reach their own folder, and file names are
 * restricted so a name can't point outside it.
 *
 * The terminal reads a file fresh every time a script is started.
 */
public final class ScriptStore {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");
    private static final Pattern HEADER = Pattern.compile("(?m)^\\s*(program|kernel)\\s*\\{");
    private static final long MAX_SIZE = 64 * 1024;
    private static final String EXAMPLES_ROOT = "/" + Mhm2027.MODID + "/example_scripts/";
    private static final List<String> EXAMPLES = List.of(
            "README.txt", "Adrenaline.lua", "Monitor.lua", "Status.lua", "Spinner.lua", "Bulwark.lua");

    /** What a file is, from its header, without running it. */
    public enum Kind {
        PROGRAM, KERNEL, UNKNOWN
    }

    public record Entry(String name, Kind kind) {
    }

    private ScriptStore() {
    }

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("edge").resolve("players");
    }

    public static Path folder(MinecraftServer server, UUID player) {
        return root(server).resolve(player.toString());
    }

    /** Creates the player's folder with the example scripts the first time it's needed. */
    public static Path ensureFolder(MinecraftServer server, UUID player) {
        Path folder = folder(server, player);
        if (Files.isDirectory(folder)) {
            return folder;
        }
        try {
            Files.createDirectories(folder);
            for (String example : EXAMPLES) {
                try (InputStream in = ScriptStore.class.getResourceAsStream(EXAMPLES_ROOT + example)) {
                    if (in != null) {
                        Files.copy(in, folder.resolve(example));
                    }
                }
            }
        } catch (IOException e) {
            Mhm2027.LOGGER.error("Couldn't create the Edge script folder at {}", folder, e);
        }
        return folder;
    }

    /** The player's scripts, sorted by name, each marked as a program or a kernel. */
    public static List<Entry> list(MinecraftServer server, UUID player) {
        Path folder = ensureFolder(server, player);
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.sorted().toList()) {
                String fileName = file.getFileName().toString();
                if (!fileName.endsWith(".lua")) {
                    continue;
                }
                String name = fileName.substring(0, fileName.length() - 4);
                if (NAME.matcher(name).matches() && Files.size(file) <= MAX_SIZE) {
                    entries.add(new Entry(name, kindOf(Files.readString(file, StandardCharsets.UTF_8))));
                }
            }
        } catch (IOException e) {
            Mhm2027.LOGGER.warn("Couldn't list Edge scripts in {}", folder, e);
        }
        entries.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()));
        return entries;
    }

    public static String read(MinecraftServer server, UUID player, String name) throws InstallException {
        if (!NAME.matcher(name).matches()) {
            throw new InstallException("Script names may only use letters, digits, - and _ (up to 32)");
        }
        Path file = ensureFolder(server, player).resolve(name + ".lua");
        try {
            if (!Files.isRegularFile(file)) {
                throw new InstallException("No script named " + name + ".lua in your storage");
            }
            if (Files.size(file) > MAX_SIZE) {
                throw new InstallException(name + ".lua is larger than 64 KB");
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InstallException("Couldn't read " + name + ".lua: " + e.getMessage());
        }
    }

    /** Guesses the kind from the first header in the file. Install still checks it properly. */
    static Kind kindOf(String source) {
        Matcher matcher = HEADER.matcher(source);
        if (!matcher.find()) {
            return Kind.UNKNOWN;
        }
        return matcher.group(1).equals("kernel") ? Kind.KERNEL : Kind.PROGRAM;
    }
}
