package com.mhm.mhm2027.terminal;

/**
 * How exposed a flaw is. Higher ranks replace lower ones when several rules of one class match.
 * {@link #NONE} is only the severity passed to {@code on_flaw_changed} when a flaw goes away.
 */
public enum FlawSeverity {
    NONE("none", 0),
    MINOR("minor", 1),
    MAJOR("major", 2),
    CRITICAL("critical", 3);

    private final String id;
    private final int rank;

    FlawSeverity(String id, int rank) {
        this.id = id;
        this.rank = rank;
    }

    public String id() {
        return id;
    }

    public int rank() {
        return rank;
    }

    /** Parses a severity written in a rule file. {@code none} is not a rule severity. */
    public static FlawSeverity parse(String text) throws FlawRules.FlawFormatException {
        for (FlawSeverity severity : values()) {
            if (severity != NONE && severity.id.equals(text)) {
                return severity;
            }
        }
        throw new FlawRules.FlawFormatException("severity must be minor, major or critical");
    }
}
