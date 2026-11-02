package com.mhm.mhm2027.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mhm.mhm2027.network.EditorCheck;
import com.mhm.mhm2027.network.EditorFile;

/** Editor replies from the server, waiting for Edge's editor (or, for cat, its terminal) to pick them up. */
final class EditorState {
    private static @Nullable EditorFile pendingFile;
    private static @Nullable EditorCheck latestCheck;
    private static final List<EditorFile> prints = new ArrayList<>();

    private EditorState() {
    }

    static void accept(EditorFile file) {
        if (file.print()) {
            prints.add(file);
        } else {
            pendingFile = file;
        }
    }

    /** Files sent for cat since the last call. */
    static List<EditorFile> takePrints() {
        List<EditorFile> out = List.copyOf(prints);
        prints.clear();
        return out;
    }

    static void accept(EditorCheck check) {
        latestCheck = check;
    }

    /** A file the server sent since the last call, or null. */
    static @Nullable EditorFile takeFile() {
        EditorFile file = pendingFile;
        pendingFile = null;
        return file;
    }

    static @Nullable EditorCheck latestCheck() {
        return latestCheck;
    }

    static void reset() {
        pendingFile = null;
        latestCheck = null;
        prints.clear();
    }
}
