package com.mhm.mhm2027.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextFxTest {
    @Test
    void garbledTextDecryptsFromTheLeft() {
        String text = "SYSTEM BREACH";
        String start = TextFx.garble(text, 0, 7);
        assertEquals(text.length(), start.length());
        assertEquals(' ', start.charAt(6), "spaces keep the word shapes");
        assertNotEquals(text, start);
        assertTrue(TextFx.garble(text, 0.5f, 7).startsWith("SYSTEM"));
        assertEquals(text, TextFx.garble(text, 1, 7));
    }

    @Test
    void noiseIsStableForASeed() {
        assertEquals(TextFx.noise(42, 8), TextFx.noise(42, 8));
        assertEquals(8, TextFx.noise(42, 8).length());
    }

    @Test
    void glitchesHalveEachHalfLife() {
        assertEquals(1f, TextFx.decay(1f, 0, 200), 1e-6);
        assertEquals(0.5f, TextFx.decay(1f, 200, 200), 1e-6);
        assertEquals(0.25f, TextFx.decay(1f, 400, 200), 1e-6);
        assertEquals(0f, TextFx.decay(0f, 400, 200), 1e-6);
    }
}
