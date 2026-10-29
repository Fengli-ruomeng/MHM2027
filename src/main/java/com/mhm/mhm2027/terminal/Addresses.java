package com.mhm.mhm2027.terminal;

import java.util.UUID;

/**
 * Gateway addresses: an IP-like form of an entity's UUID, such as 10.47.203.9. Stable for the
 * entity's whole life, so planned actions can name a target. In combat, players target by
 * crosshair or from a scan list instead.
 */
public final class Addresses {
    private Addresses() {
    }

    public static String of(UUID uuid) {
        long mixed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        int hash = (int) (mixed ^ (mixed >>> 32));
        return "10." + ((hash >>> 16) & 0xFF) + "." + ((hash >>> 8) & 0xFF) + "." + (hash & 0xFF);
    }
}
