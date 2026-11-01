package com.mhm.mhm2027.client;

import com.mhm.mhm2027.network.PanelMessage;
import com.mhm.mhm2027.network.PanelSnapshot;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** The latest panel data from the server, kept on the client while the panel is open. */
public final class PanelState {
    private static final long MESSAGE_MILLIS = 6000;

    private static PanelSnapshot snapshot = PanelSnapshot.EMPTY;
    private static Component message = Component.empty();
    private static boolean messageSuccess;
    private static long messageShownAt;
    private static int messageSerial;
    private static int snapshotSerial;

    private PanelState() {
    }

    static void accept(PanelSnapshot latest) {
        snapshot = latest;
        snapshotSerial++;
    }

    /** Goes up by one with every snapshot, so a screen can tell whether one has arrived since it opened. */
    static int snapshotSerial() {
        return snapshotSerial;
    }

    static void accept(PanelMessage latest) {
        message = latest.text();
        messageSuccess = latest.success();
        messageShownAt = Util.getMillis();
        messageSerial++;
    }

    /** Goes up by one with every message, so a screen can tell when a new one has arrived. */
    static int messageSerial() {
        return messageSerial;
    }

    static Component lastMessage() {
        return message;
    }

    static void reset() {
        snapshot = PanelSnapshot.EMPTY;
        message = Component.empty();
    }

    static PanelSnapshot snapshot() {
        return snapshot;
    }

    /** The last action's result, or empty once it has been shown for a few seconds. */
    static Component message() {
        return Util.getMillis() - messageShownAt < MESSAGE_MILLIS ? message : Component.empty();
    }

    static boolean messageSuccess() {
        return messageSuccess;
    }
}
