package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LuaFunction;

class LuaSandboxTest {
    private static LuaFunction compile(LuaSandbox sandbox, String code) throws Exception {
        return LoadState.load(sandbox.state(), new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)), "@test", sandbox.globals());
    }

    @Test
    void overBudgetPausesAndResumesWhereItStopped() throws Exception {
        LuaSandbox sandbox = new LuaSandbox();
        LuaThread thread = sandbox.newThread(compile(sandbox, """
                local total = 0
                for i = 1, 5000 do total = total + i end
                return total
                """));

        int slices = 0;
        LuaSandbox.Slice slice;
        do {
            slice = sandbox.run(thread, Constants.NONE, 200);
            slices++;
            assertTrue(slices < 1000, "never finished");
        } while (slice.kind() == LuaSandbox.Slice.Kind.PAUSED);

        assertEquals(LuaSandbox.Slice.Kind.FINISHED, slice.kind());
        assertEquals(5000 * 5001 / 2, slice.result().first().checkInteger());
        assertTrue(slices > 10, "a 5000-step loop should take many 200-instruction slices, took " + slices);
    }

    @Test
    void endlessLoopOnlyEverPauses() throws Exception {
        LuaSandbox sandbox = new LuaSandbox();
        LuaThread thread = sandbox.newThread(compile(sandbox, "while true do end"));
        for (int tick = 0; tick < 50; tick++) {
            LuaSandbox.Slice slice = sandbox.run(thread, Constants.NONE, 100);
            assertEquals(LuaSandbox.Slice.Kind.PAUSED, slice.kind());
        }
    }

    @Test
    void sleepYieldsToTheTerminalAndResumes() throws Exception {
        LuaSandbox sandbox = new LuaSandbox();
        sandbox.globals().rawset("sleep", LuaSandbox.sleepFunction(seconds -> {
        }));
        LuaThread thread = sandbox.newThread(compile(sandbox, """
                local before = 1
                sleep(1.5)
                return before + 41
                """));

        LuaSandbox.Slice first = sandbox.run(thread, Constants.NONE, 1000);
        assertEquals(LuaSandbox.Slice.Kind.SLEEPING, first.kind());
        assertEquals(1.5, first.seconds());

        LuaSandbox.Slice second = sandbox.run(thread, Constants.NONE, 1000);
        assertEquals(LuaSandbox.Slice.Kind.FINISHED, second.kind());
        assertEquals(42, second.result().first().checkInteger());
    }

    @Test
    void staleInterruptDoesNotPauseTheNextRun() throws Exception {
        LuaSandbox sandbox = new LuaSandbox();
        // Use up the budget exactly at the end of a run, leaving the interrupt flag raised.
        sandbox.run(sandbox.newThread(compile(sandbox, "for i = 1, 100 do end")), Constants.NONE, 1);
        LuaSandbox.Slice next = sandbox.run(sandbox.newThread(compile(sandbox, "return 7")), Constants.NONE, 1000);
        assertEquals(LuaSandbox.Slice.Kind.FINISHED, next.kind());
    }

    @Test
    void sandboxHasNoWayToLoadCodeOrReachTheSystem() throws Exception {
        LuaSandbox sandbox = new LuaSandbox();
        for (String name : new String[]{"load", "loadstring", "dofile", "loadfile", "require", "getfenv", "setfenv",
                "io", "os", "debug", "coroutine", "package"}) {
            assertTrue(sandbox.globals().rawget(name).isNil(), name + " should not exist");
        }
        for (String name : new String[]{"math", "string", "table", "pairs", "pcall"}) {
            assertTrue(!sandbox.globals().rawget(name).isNil(), name + " should exist");
        }
    }

    @Test
    void stringRepIsCapped() throws Exception {
        LuaSandbox sandbox = new LuaSandbox();
        LuaThread thread = sandbox.newThread(compile(sandbox, "return string.rep('x', 4194304)"));
        assertThrows(LuaError.class, () -> sandbox.run(thread, Constants.NONE, 1000));

        LuaThread small = sandbox.newThread(compile(sandbox, "return #string.rep('ab', 3)"));
        assertEquals(6, sandbox.run(small, Constants.NONE, 1000).result().first().checkInteger());
    }

    @Test
    void scriptErrorsSurfaceAsLuaErrors() throws Exception {
        LuaSandbox sandbox = new LuaSandbox();
        LuaThread thread = sandbox.newThread(compile(sandbox, "error('boom')"));
        LuaError error = assertThrows(LuaError.class, () -> sandbox.run(thread, Constants.NONE, 1000));
        assertTrue(error.getMessage().contains("boom"));
    }
}
