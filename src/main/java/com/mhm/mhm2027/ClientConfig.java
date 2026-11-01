package com.mhm.mhm2027;

import net.neoforged.neoforge.common.ModConfigSpec;

// Per-player display settings for the Lens HUD. Client side only.
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("lens");
    }

    public static final ModConfigSpec.BooleanValue GLITCH_EFFECTS = BUILDER
            .comment("Screen glitches when you're hit or attacked. Turn them off if flickering effects bother you")
            .define("glitchEffects", true);

    public static final ModConfigSpec.DoubleValue GLITCH_STRENGTH = BUILDER
            .comment("How strong the glitches are, from 0 (none) to 1")
            .defineInRange("glitchStrength", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue BANNERS = BUILDER
            .comment("Scrolling warning banners for critical moments: a breach, a panic, an overheat, a flash")
            .define("banners", true);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
