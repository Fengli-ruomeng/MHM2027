package com.mhm.mhm2027.client;

import java.util.ArrayList;
import java.util.List;

import com.mhm.mhm2027.network.PanelMessage;
import com.mhm.mhm2027.network.PanelSnapshot;
import com.mhm.mhm2027.network.ScanResult;

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
    private static long snapshotAt;
    private static final List<ScanResult> scans = new ArrayList<>();

    private PanelState() {
    }

    static void accept(PanelSnapshot latest) {
        snapshot = latest;
        snapshotSerial++;
        snapshotAt = Util.getMillis();
    }

    /** When the latest snapshot arrived, so progress bars can count down between snapshots. */
    static long snapshotAt() {
        return snapshotAt;
    }

    static void accept(ScanResult result) {
        scans.add(result);
    }

    /** Scan results that arrived since the last call, for Edge's terminal to print. */
    static List<ScanResult> takeScans() {
        List<ScanResult> out = List.copyOf(scans);
        scans.clear();
        return out;
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
        scans.clear();
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
