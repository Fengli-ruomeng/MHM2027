package com.mhm.mhm2027;

import java.util.List;

import com.mhm.mhm2027.terminal.SocketRules;

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
        BUILDER.pop().push("mobs");
    }

    public static final ModConfigSpec.BooleanValue WIRED_MOBS = BUILDER
            .comment("Whether zombies and skeletons can spawn with a gateway (wired mobs)")
            .define("wiredMobs", true);

    public static final ModConfigSpec.IntValue WIRED_START_DAY = BUILDER
            .comment("The first in-game day on which wired mobs appear (day 1 is the first day)")
            .defineInRange("wiredStartDay", 2, 1, 100000);

    public static final ModConfigSpec.IntValue WIRED_FULL_DAY = BUILDER
            .comment("The day on which the chance of a wired mob reaches its maximum")
            .defineInRange("wiredFullDay", 30, 1, 100000);

    public static final ModConfigSpec.DoubleValue WIRED_MAX_CHANCE = BUILDER
            .comment("The highest chance that a naturally spawned zombie or skeleton is wired")
            .defineInRange("wiredMaxChance", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue RANDOM_MOB_KERNELS = BUILDER
            .comment("Whether wired mobs roll a random native kernel, and so random flaws. Off gives them all the default kernel")
            .define("randomMobKernels", true);

    static {
        BUILDER.pop().push("station");
    }

    public static final ModConfigSpec.IntValue KERNEL_COOLDOWN_SECONDS = BUILDER
            .comment("Seconds a player must wait between kernel installs. Kernels are installed at a Flash Station")
            .defineInRange("kernelCooldownSeconds", 60, 0, 86400);

    static {
        BUILDER.pop().push("overload");
    }

    public static final ModConfigSpec.IntValue OVERLOAD_PRESET_REACH = BUILDER
            .comment("How many levels above the gateway an Overload preset's code may reach")
            .defineInRange("presetReach", 6, 0, 11);

    public static final ModConfigSpec.IntValue OVERLOAD_SCRIPT_REACH = BUILDER
            .comment("How many levels above the gateway a player's own Overload script may reach")
            .defineInRange("scriptReach", SocketRules.DEFAULT_SCRIPT_REACH, 0, 11);

    public static final ModConfigSpec.DoubleValue OVERLOAD_LEAK_SHARE = BUILDER
            .comment("Share of an Overload program's RAM added to Leak each second it runs")
            .defineInRange("leakShare", 0.1, 0.0, 10.0);

    public static final ModConfigSpec.IntValue OVERLOAD_LOCK_SECONDS = BUILDER
            .comment("Seconds the Overload sockets stay locked after overheating")
            .defineInRange("lockSeconds", 60, 0, 3600);

    static {
        BUILDER.pop().push("potions");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> BUFFER_RESTORE_SHARES = BUILDER
            .comment("Share of max Buffer a Cyber Potion restores at each level, I to V")
            .defineList("bufferRestoreShares", List.of(0.1, 0.2, 0.35, 0.5, 0.75), () -> 0.1,
                    value -> value instanceof Double share && share >= 0 && share <= 1);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
