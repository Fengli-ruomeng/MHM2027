package com.mhm.mhm2027.client;

import java.util.Locale;

/** The quick wheel's geometry and labels. Pure, so it can be tested. */
final class WheelMath {
    static final int SLOTS = EdgeShell.WHEEL_SLOTS;

    private WheelMath() {
    }

    /** The slot a pointer offset points at: 0 at the top, then clockwise. -1 inside the dead zone. */
    static int slotAt(double dx, double dy, double deadZone) {
        if (dx * dx + dy * dy < deadZone * deadZone) {
            return -1;
        }
        // 0 degrees straight up, 90 to the right: the way the slots are numbered.
        double degrees = Math.toDegrees(Math.atan2(dx, -dy));
        return Math.floorMod(Math.round(degrees / (360.0 / SLOTS)), SLOTS);
    }

    /** Where a slot's centre sits on a ring of {@code radius}, as an offset from the wheel's centre. */
    static double[] position(int slot, double radius) {
        double angle = Math.toRadians(slot * 360.0 / SLOTS);
        return new double[]{Math.sin(angle) * radius, -Math.cos(angle) * radius};
    }

    /** A slot's big label: the program for "run X", otherwise the command itself, in capitals. */
    static String label(String command) {
        String label = command.strip();
        if (label.isEmpty()) {
            return "EMPTY";
        }
        if (label.toLowerCase(Locale.ROOT).startsWith("run ")) {
            label = label.substring(4).strip();
        }
        if (label.toLowerCase(Locale.ROOT).endsWith(".lua")) {
            label = label.substring(0, label.length() - 4);
        }
        return label.toUpperCase(Locale.ROOT);
    }
}
