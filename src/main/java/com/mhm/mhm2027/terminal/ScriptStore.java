package com.mhm.mhm2027.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Scripts are ordinary .lua files in the world folder, under {@code edge/scripts}. Players edit
 * them in any editor; the terminal reads the file fresh every time a script is started.
 *
 * For now there is one shared folder per world. Sending scripts from each player's own computer
 * to the server is a later step.
 */
public final class ScriptStore {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");
    private static final long MAX_SIZE = 64 * 1024;
    private static final String EXAMPLES_ROOT = "/" + Mhm2027.MODID + "/example_scripts/";
    private static final List<String> EXAMPLES = List.of(
            "README.txt", "Adrenaline.lua", "Monitor.lua", "Status.lua", "Spinner.lua", "Bulwark.lua");

    private ScriptStore() {
    }

    public static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("edge").resolve("scripts");
    }

    /** Creates the folder with the example scripts the first time it's needed. */
    public static Path ensureFolder(MinecraftServer server) {
        Path folder = folder(server);
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

    /** Script names, without the .lua extension, sorted. */
    public static List<String> list(MinecraftServer server) {
        Path folder = ensureFolder(server);
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(file -> file.endsWith(".lua"))
                    .map(file -> file.substring(0, file.length() - 4))
                    .filter(name -> NAME.matcher(name).matches())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static String read(MinecraftServer server, String name) throws InstallException {
        if (!NAME.matcher(name).matches()) {
            throw new InstallException("Script names may only use letters, digits, - and _ (up to 32)");
        }
        Path file = ensureFolder(server).resolve(name + ".lua");
        try {
            if (!Files.isRegularFile(file)) {
                throw new InstallException("No script named " + name + ".lua in " + folder(server));
            }
            if (Files.size(file) > MAX_SIZE) {
                throw new InstallException(name + ".lua is larger than 64 KB");
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InstallException("Couldn't read " + name + ".lua: " + e.getMessage());
        }
    }
}
