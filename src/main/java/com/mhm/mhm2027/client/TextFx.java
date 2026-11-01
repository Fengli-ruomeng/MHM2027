package com.mhm.mhm2027.client;

import java.util.Random;

/**
 * The Lens HUD's text effects: garbled text that "decrypts" into the real words, random
 * noise, and how fast a glitch dies away. Kept free of game classes so it can be unit-tested.
 */
public final class TextFx {
    private static final String GLYPHS = "#$%&@!?/\\<>[]{}=+*01";

    private TextFx() {
    }

    /**
     * Text being decrypted: characters before {@code progress} of the length are real, the rest
     * are glyphs that change with {@code seed}. Spaces stay spaces, so word shapes show through.
     */
    public static String garble(String text, float progress, long seed) {
        int real = Math.round(Math.clamp(progress, 0f, 1f) * text.length());
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(i < real || c == ' ' ? c : glyph(seed, i));
        }
        return out.toString();
    }

    /** A run of noise glyphs, the same for the same seed. */
    public static String noise(long seed, int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(glyph(seed, i));
        }
        return out.toString();
    }

    /** A level that halves every {@code halfLifeMillis}. */
    public static float decay(float level, long elapsedMillis, long halfLifeMillis) {
        if (level <= 0 || elapsedMillis < 0) {
            return Math.max(0, level);
        }
        return (float) (level * Math.pow(0.5, (double) elapsedMillis / halfLifeMillis));
    }

    private static char glyph(long seed, int index) {
        return GLYPHS.charAt(new Random(seed * 31 + index).nextInt(GLYPHS.length()));
    }
}
