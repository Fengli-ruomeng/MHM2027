package com.mhm.mhm2027.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int MAX_FILES = 64;
    private static final String EXAMPLES_ROOT = "/" + Mhm2027.MODID + "/example_scripts/";
    private static final List<String> EXAMPLES = List.of(
            "README.txt", "Adrenaline.lua", "Monitor.lua", "Status.lua", "Spinner.lua", "Bulwark.lua", "Scanner.lua",
            "Counterpunch.lua", "Gatekeeper.lua");
    /** The examples shipped before the offered-examples record existed. */
    private static final List<String> FIRST_EXAMPLES = EXAMPLES.subList(0, 6);
    private static final String OFFERED_FILE = ".examples";
    /** Folders already brought up to date since the server started, so the panel's frequent reads stay cheap. */
    private static final Set<Path> UP_TO_DATE = ConcurrentHashMap.newKeySet();

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
        if (UP_TO_DATE.contains(folder) && Files.isDirectory(folder)) {
            return folder;
        }
        try {
            Files.createDirectories(folder);
            offerExamples(folder);
            UP_TO_DATE.add(folder);
        } catch (IOException e) {
            Mhm2027.LOGGER.error("Couldn't create the Edge script folder at {}", folder, e);
        }
        return folder;
    }

    /**
     * Copies each example into the folder once. New examples reach players who already have a
     * folder, and an example a player deleted never comes back. The ones already offered are
     * listed in a hidden file next to the scripts.
     */
    private static void offerExamples(Path folder) throws IOException {
        Path record = folder.resolve(OFFERED_FILE);
        Set<String> offered = new HashSet<>();
        if (Files.exists(record)) {
            offered.addAll(Files.readAllLines(record, StandardCharsets.UTF_8));
        } else {
            try (Stream<Path> files = Files.list(folder)) {
                // A folder from before this record existed already received the first examples.
                if (files.findAny().isPresent()) {
                    offered.addAll(FIRST_EXAMPLES);
                }
            }
        }
        boolean changed = !Files.exists(record);
        for (String example : EXAMPLES) {
            if (offered.add(example)) {
                changed = true;
                Path target = folder.resolve(example);
                if (!Files.exists(target)) {
                    try (InputStream in = ScriptStore.class.getResourceAsStream(EXAMPLES_ROOT + example)) {
                        if (in != null) {
                            Files.copy(in, target);
                        }
                    }
                }
            }
        }
        if (changed) {
            Files.write(record, offered.stream().sorted().toList(), StandardCharsets.UTF_8);
        }
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

    /**
     * Writes code into the player's storage, as written from a chip. An existing file with the
     * same name is never overwritten: the copy goes under {@code name_2}, {@code name_3}, ...
     *
     * @return the name it was saved under, or empty if identical code is already stored
     */
    public static Optional<String> writeCopy(MinecraftServer server, UUID player, String name, String source) throws InstallException {
        if (!NAME.matcher(name).matches()) {
            throw new InstallException("Script names may only use letters, digits, - and _ (up to 32)");
        }
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_SIZE) {
            throw new InstallException(name + " is larger than 64 KB");
        }
        Path folder = ensureFolder(server, player);
        try {
            try (Stream<Path> files = Files.list(folder)) {
                if (files.filter(file -> file.toString().endsWith(".lua")).count() >= MAX_FILES) {
                    throw new InstallException("Your storage is full (" + MAX_FILES + " scripts)");
                }
            }
            String base = name.length() > 29 ? name.substring(0, 29) : name;
            for (int copy = 1; copy <= 9; copy++) {
                String candidate = copy == 1 ? name : base + "_" + copy;
                Path file = folder.resolve(candidate + ".lua");
                if (!Files.exists(file)) {
                    Files.writeString(file, source, StandardCharsets.UTF_8);
                    return Optional.of(candidate);
                }
                if (Files.readString(file, StandardCharsets.UTF_8).equals(source)) {
                    return Optional.empty();
                }
            }
            throw new InstallException("Too many copies of " + name + " in your storage");
        } catch (IOException e) {
            throw new InstallException("Couldn't write " + name + ".lua: " + e.getMessage());
        }
    }

    // region Editor file operations

    /** Saves a file from the editor, creating it if needed. */
    public static void save(MinecraftServer server, UUID player, String name, String source) throws InstallException {
        Path file = fileFor(server, player, name);
        checkSize(name, source);
        if (!Files.exists(file)) {
            checkRoom(file.getParent());
        }
        try {
            Files.writeString(file, source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InstallException("Couldn't save " + name + ".lua: " + e.getMessage());
        }
    }

    /** Creates a new file; refuses if one with that name exists. */
    public static void create(MinecraftServer server, UUID player, String name, String source) throws InstallException {
        Path file = fileFor(server, player, name);
        if (Files.exists(file)) {
            throw new InstallException(name + ".lua already exists");
        }
        save(server, player, name, source);
    }

    public static void rename(MinecraftServer server, UUID player, String from, String to) throws InstallException {
        Path source = fileFor(server, player, from);
        Path target = fileFor(server, player, to);
        if (!Files.isRegularFile(source)) {
            throw new InstallException("No script named " + from + ".lua in your storage");
        }
        if (Files.exists(target)) {
            throw new InstallException(to + ".lua already exists");
        }
        try {
            Files.move(source, target);
        } catch (IOException e) {
            throw new InstallException("Couldn't rename " + from + ".lua: " + e.getMessage());
        }
    }

    public static void delete(MinecraftServer server, UUID player, String name) throws InstallException {
        Path file = fileFor(server, player, name);
        try {
            if (!Files.deleteIfExists(file)) {
                throw new InstallException("No script named " + name + ".lua in your storage");
            }
        } catch (IOException e) {
            throw new InstallException("Couldn't delete " + name + ".lua: " + e.getMessage());
        }
    }

    /** The file for a script name, only ever inside the player's own folder. */
    private static Path fileFor(MinecraftServer server, UUID player, String name) throws InstallException {
        if (!NAME.matcher(name).matches()) {
            throw new InstallException("Script names may only use letters, digits, - and _ (up to 32)");
        }
        return ensureFolder(server, player).resolve(name + ".lua");
    }

    private static void checkSize(String name, String source) throws InstallException {
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_SIZE) {
            throw new InstallException(name + " is larger than 64 KB");
        }
    }

    private static void checkRoom(Path folder) throws InstallException {
        try (Stream<Path> files = Files.list(folder)) {
            if (files.filter(file -> file.toString().endsWith(".lua")).count() >= MAX_FILES) {
                throw new InstallException("Your storage is full (" + MAX_FILES + " scripts)");
            }
        } catch (IOException e) {
            throw new InstallException("Couldn't read your storage: " + e.getMessage());
        }
    }

    // endregion

    /** Guesses the kind from the first header in the file. Install still checks it properly. */
    static Kind kindOf(String source) {
        Matcher matcher = HEADER.matcher(source);
        if (!matcher.find()) {
            return Kind.UNKNOWN;
        }
        return matcher.group(1).equals("kernel") ? Kind.KERNEL : Kind.PROGRAM;
    }
}
