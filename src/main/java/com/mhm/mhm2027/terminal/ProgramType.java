package com.mhm.mhm2027.terminal;

import java.util.Locale;
import java.util.Optional;

/** Programs are grouped by how long they live, not by purpose. */
public enum ProgramType {
    /** Runs until stopped: defences, monitors, passive enhancements. */
    RESIDENT(2),
    /** Runs once and exits. */
    TRANSIENT(2),
    /** Reads data cheaply and exits; may never change your body. */
    QUERY(1);

    private final int baseRamMb;

    ProgramType(int baseRamMb) {
        this.baseRamMb = baseRamMb;
    }

    /** Code RAM before any API footprint is added. */
    public int baseRamMb() {
        return baseRamMb;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ProgramType> byId(String id) {
        for (ProgramType type : values()) {
            if (type.id().equals(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
