package com.mhm.mhm2027.terminal;

/** A running script that API calls are made from: a program or the kernel. */
interface ScriptHost {
    String name();

    LuaSandbox sandbox();

    boolean isKernel();

    /** False while the script's top-level code runs at install, when APIs may not be called. */
    boolean isActive();
}
