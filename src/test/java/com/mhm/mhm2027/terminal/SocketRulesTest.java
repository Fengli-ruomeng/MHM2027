package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class SocketRulesTest {
    @Test
    void socketListsFollowTheGatewaySize() {
        assertEquals(List.of("", ""), SocketRules.fit(List.of(), 2));
        assertEquals(List.of("a", "b", ""), SocketRules.fit(List.of("a", "b"), 3), "a level-up adds empty sockets");
        assertEquals(List.of("a"), SocketRules.fit(List.of("a", "b"), 1));
    }

    @Test
    void socketingAndRemoving() {
        List<String> sockets = SocketRules.fit(List.of("a"), 3);
        assertEquals(1, SocketRules.firstFree(sockets));
        sockets = SocketRules.with(sockets, 1, "b");
        assertEquals(List.of("a", "b", ""), sockets);
        assertEquals(List.of("", "b", ""), SocketRules.without(sockets, "a"));
        assertEquals(-1, SocketRules.firstFree(List.of("a", "b")));
    }

    @Test
    void overloadHeatFollowsTheDesignExample() {
        assertEquals(8, SocketRules.heatPerSecond(10, 4), "LV4 running LV10 code");
        assertEquals(2, SocketRules.heatPerSecond(3, 5), "code at or below your level still runs warm");
        assertEquals(4, SocketRules.presetRequirement(10, 6));
        assertEquals(0, SocketRules.presetRequirement(3, 6));
    }
}
