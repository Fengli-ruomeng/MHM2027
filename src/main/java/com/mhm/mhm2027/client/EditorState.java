package com.mhm.mhm2027.client;

import org.jspecify.annotations.Nullable;

import com.mhm.mhm2027.network.EditorCheck;
import com.mhm.mhm2027.network.EditorFile;

/** Editor replies from the server, waiting for the editor screen to pick them up. */
final class EditorState {
    private static @Nullable EditorFile pendingFile;
    private static @Nullable EditorCheck latestCheck;

    private EditorState() {
    }

    static void accept(EditorFile file) {
        pendingFile = file;
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
    }
}
