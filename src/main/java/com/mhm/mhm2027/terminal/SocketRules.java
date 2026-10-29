package com.mhm.mhm2027.terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * The numbers and list handling behind sockets and Overload, kept apart so they can be
 * unit-tested. A socket holds the terminal id of something in the gateway's storage (a file
 * name, or {@code preset:<id>}); an empty string is an empty socket.
 */
public final class SocketRules {
    public static final String EMPTY = "";
    /** Every running Overload program heats the gateway by this much a second, plus the level gap. */
    public static final int BASE_HEAT_PER_SECOND = 2;
    /** How far above the gateway a player's own Overload script may reach, unless the config says otherwise. */
    public static final int DEFAULT_SCRIPT_REACH = 2;

    private SocketRules() {
    }

    /** A socket list padded or cut to the number of sockets the gateway has. */
    public static List<String> fit(List<String> sockets, int size) {
        List<String> fitted = new ArrayList<>(sockets.subList(0, Math.min(size, sockets.size())));
        while (fitted.size() < size) {
            fitted.add(EMPTY);
        }
        return List.copyOf(fitted);
    }

    /** The first empty socket, or -1 if all are taken. */
    public static int firstFree(List<String> sockets) {
        return sockets.indexOf(EMPTY);
    }

    /** The list with {@code id} put in socket {@code index}. */
    public static List<String> with(List<String> sockets, int index, String id) {
        List<String> next = new ArrayList<>(sockets);
        next.set(index, id);
        return List.copyOf(next);
    }

    /** The list with every socket holding {@code id} emptied. */
    public static List<String> without(List<String> sockets, String id) {
        return sockets.stream().map(each -> each.equals(id) ? EMPTY : each).toList();
    }

    /**
     * Heat an Overload program adds each second: 2, plus how far its code reaches above the
     * gateway. An LV4 gateway running LV10 code gains 8 a second.
     */
    public static int heatPerSecond(int codeLevel, int gatewayLevel) {
        return BASE_HEAT_PER_SECOND + Math.max(0, codeLevel - gatewayLevel);
    }

    /** The lowest gateway level that may run an Overload preset whose code runs at {@code codeLevel}. */
    public static int presetRequirement(int codeLevel, int reach) {
        return Math.max(0, codeLevel - reach);
    }
}
