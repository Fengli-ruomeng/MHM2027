package com.mhm.mhm2027;

import net.neoforged.neoforge.common.ModConfigSpec;

// Balance values for the gateway layer. Defaults are the starting numbers from Design Draft v2.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("memory");
    }

    public static final ModConfigSpec.DoubleValue GC_RATE_FULL_SATURATION = BUILDER
            .comment("Share of RAM capacity that garbage collection clears from Leak each second at full saturation")
            .defineInRange("gcRateFullSaturation", 0.02, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue GC_RATE_ZERO_SATURATION = BUILDER
            .comment("Share of RAM capacity that garbage collection clears from Leak each second at zero saturation")
            .defineInRange("gcRateZeroSaturation", 0.005, 0.0, 1.0);

    public static final ModConfigSpec.IntValue GC_MIN_FOOD_LEVEL = BUILDER
            .comment("Garbage collection stops while the hunger bar is at or below this value")
            .defineInRange("gcMinFoodLevel", 6, 0, 20);

    static {
        BUILDER.pop().push("heat");
    }

    public static final ModConfigSpec.DoubleValue HEAT_COOLING_PER_SECOND = BUILDER
            .comment("Heat lost each second")
            .defineInRange("heatCoolingPerSecond", 2.0, 0.0, 100.0);

    static {
        BUILDER.pop().push("runtime");
    }

    public static final ModConfigSpec.IntValue INSTRUCTIONS_PER_TOPS = BUILDER
            .comment("Lua instructions a program may run each tick per reserved TOPS. Lower this to reduce server load")
            .defineInRange("instructionsPerTops", 20, 1, 1000);

    static {
        BUILDER.pop().push("players");
    }

    public static final ModConfigSpec.BooleanValue PLAYER_TARGETS = BUILDER
            .comment("Whether players' programs can scan, inspect and access other players. Mobs are always targets")
            .define("playerTargets", true);

    static {
        BUILDER.pop().push("defence");
    }

    public static final ModConfigSpec.DoubleValue DENY_BUFFER_MB = BUILDER
            .comment("Buffer (MB) a kernel pays to deny an access attempt")
            .defineInRange("denyBufferMb", 256.0, 0.0, 1048576.0);

    public static final ModConfigSpec.DoubleValue END_SESSION_BUFFER_MB = BUILDER
            .comment("Buffer (MB) it costs to close a session someone opened on you")
            .defineInRange("endSessionBufferMb", 512.0, 0.0, 1048576.0);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
