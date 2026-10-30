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
    /** Someone is trying to get in. Programs can react, but only the kernel's hook decides. */
    public static final String ON_ACCESS_ATTEMPT = "on_access_attempt";
    /**
     * Someone got in. Both a kernel hook and a program event: the kernel answers first (raise an
     * alarm, reboot, start a defence program), then programs hear about it.
     */
    public static final String ON_SESSION_OPENED = "on_session_opened";
    /** Health dropped below the threshold set with sys.low_health (30% of max until set). Fires once per drop. */
    public static final String ON_LOW_HEALTH = "on_low_health";
    /** Another of your programs crashed: an error, starved of TOPS, or overheated. */
    public static final String ON_PROGRAM_CRASH = "on_program_crash";
    public static final List<String> PROGRAM_EVENTS = List.of(ON_START, ON_STOP, ON_RUN, ON_TIMER, ON_HIT_TAKEN, ON_HIT_DEALT,
            ON_KILL, ON_FLAW_CHANGED, ON_SCANNED, ON_ACCESS_ATTEMPT, ON_SESSION_OPENED, ON_LOW_HEALTH, ON_PROGRAM_CRASH);

    public static final String ON_ACCESS_REQUEST = "on_access_request";
    public static final String ON_LOW_RAM = "on_low_ram";
    public static final String ON_PANIC = "on_panic";
    public static final List<String> KERNEL_HOOKS = List.of(ON_ACCESS_REQUEST, ON_SESSION_OPENED, ON_LOW_RAM, ON_PANIC);

    private ScriptEvents() {
    }
}
