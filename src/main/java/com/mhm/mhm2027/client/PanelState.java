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

    private PanelState() {
    }

    static void accept(PanelSnapshot latest) {
        snapshot = latest;
    }

    static void accept(PanelMessage latest) {
        message = latest.text();
        messageSuccess = latest.success();
        messageShownAt = Util.getMillis();
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
