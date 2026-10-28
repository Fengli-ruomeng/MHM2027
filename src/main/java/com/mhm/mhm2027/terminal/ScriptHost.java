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
}
