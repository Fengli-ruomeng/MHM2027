package com.mhm.mhm2027.terminal;

/** A running script that API calls are made from: a program or the kernel. */
interface ScriptHost {
    String name();

    LuaSandbox sandbox();

    boolean isKernel();

    /** False while the script's top-level code runs at install, when APIs may not be called. */
    boolean isActive();

    /**
     * The level locked preset code runs at, which may be above the player's gateway: the
     * privilege bypass. -1 for the player's own code, which runs at their gateway level.
     */
    int privilegeLevel();

    /**
     * Whether calls that change the body are allowed: only in a program whose install check
     * found them, since that is what sends it to a socket. A call reached under another name
     * (local m = me) was never seen by that check.
     */
    boolean mayChangeBody();
}
