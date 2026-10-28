package com.mhm.mhm2027.terminal;

import java.util.List;

/**
 * What a chip's tooltip shows about the code on it, worked out without running it in the world.
 *
 * @param kind    resident, transient, query or kernel
 * @param tops    reserved TOPS (for a kernel, its reserve)
 * @param ramMb   code RAM (for a kernel, its RAM)
 * @param level   own code: the highest API level it uses; preset: the gateway level it requires
 * @param details the APIs a program uses, or the hooks a kernel defines
 */
public record ScriptSummary(String name, String kind, int tops, int ramMb, int level, List<String> details) {
    public static final String KERNEL = "kernel";

    public boolean isKernel() {
        return KERNEL.equals(kind);
    }
}
