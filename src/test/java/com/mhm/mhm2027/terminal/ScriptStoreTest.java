package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScriptStoreTest {
    @Test
    void tellsProgramsFromKernelsByTheirHeader() {
        assertEquals(ScriptStore.Kind.PROGRAM, ScriptStore.kindOf("-- a comment\nprogram { name = \"Monitor\" }"));
        assertEquals(ScriptStore.Kind.KERNEL, ScriptStore.kindOf("kernel{ name = \"Bulwark\" }\nfunction on_panic() end"));
        assertEquals(ScriptStore.Kind.UNKNOWN, ScriptStore.kindOf("print('no header')"));
    }

    @Test
    void ignoresHeaderWordsInsideOtherLines() {
        assertEquals(ScriptStore.Kind.PROGRAM, ScriptStore.kindOf("local x = 'kernel { }'\nprogram { }"));
    }
}
