package com.mhm.mhm2027.terminal;

import java.util.List;

/** The names scripts may define handlers for. Kept apart from Program and Kernel so analysis has no game dependencies. */
public final class ScriptEvents {
    public static final String ON_START = "on_start";
    public static final String ON_STOP = "on_stop";
    public static final String ON_RUN = "on_run";
    public static final String ON_TIMER = "on_timer";
    public static final String ON_HIT_TAKEN = "on_hit_taken";
    public static final String ON_HIT_DEALT = "on_hit_dealt";
    public static final String ON_KILL = "on_kill";
    /** A flaw appeared, changed severity, or cleared. The severity is {@code none} when it cleared. */
    public static final String ON_FLAW_CHANGED = "on_flaw_changed";
    /** Someone's remote.inspect finished on you. The argument is a handle on whoever scanned. */
    public static final String ON_SCANNED = "on_scanned";
    public static final List<String> PROGRAM_EVENTS = List.of(
            ON_START, ON_STOP, ON_RUN, ON_TIMER, ON_HIT_TAKEN, ON_HIT_DEALT, ON_KILL, ON_FLAW_CHANGED, ON_SCANNED);

    public static final String ON_ACCESS_REQUEST = "on_access_request";
    public static final String ON_SESSION_OPENED = "on_session_opened";
    public static final String ON_LOW_RAM = "on_low_ram";
    public static final String ON_PANIC = "on_panic";
    public static final List<String> KERNEL_HOOKS = List.of(ON_ACCESS_REQUEST, ON_SESSION_OPENED, ON_LOW_RAM, ON_PANIC);

    private ScriptEvents() {
    }
}
