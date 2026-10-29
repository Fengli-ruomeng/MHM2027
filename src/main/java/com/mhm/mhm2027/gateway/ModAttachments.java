package com.mhm.mhm2027.gateway;

import java.util.function.Supplier;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.terminal.KernelSettings;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Mhm2027.MODID);

    // Players start at LV0; any other entity defaults to LV-1, since vanilla mobs have no gateway.
    // Only the owner is sent the data; other players will have to scan for it.
    public static final Supplier<AttachmentType<GatewayData>> GATEWAY = ATTACHMENT_TYPES.register("gateway", () -> AttachmentType
            .builder(holder -> GatewayData.fresh(holder instanceof Player ? GatewayTier.LV0 : GatewayTier.NULL))
            .serialize(GatewayData.CODEC.fieldOf("gateway"))
            .sync((holder, to) -> holder == to, GatewayData.STREAM_CODEC)
            .copyOnDeath()
            .build());

    private static final Codec<KernelSettings> KERNEL_SETTINGS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("schedule").forGetter(KernelSettings::schedule),
            Codec.STRING.fieldOf("gc").forGetter(KernelSettings::gc),
            Codec.STRING.fieldOf("verify").forGetter(KernelSettings::verify),
            Codec.INT.fieldOf("rate_limit").forGetter(KernelSettings::rateLimit)
    ).apply(instance, KernelSettings::new));

    /**
     * A mob's kernel. Mob kernels run natively in Java, so they are settings only, no Lua.
     * Every gateway mob runs Baseline unless something (an admin now, spawn rules later) sets it.
     */
    public static final Supplier<AttachmentType<KernelSettings>> MOB_KERNEL = ATTACHMENT_TYPES.register("mob_kernel", () -> AttachmentType
            .builder(() -> KernelSettings.DEFAULT)
            .serialize(KERNEL_SETTINGS_CODEC.fieldOf("kernel"))
            .build());

    /** Presets written into the gateway. Server only: the panel gets them through its snapshots. */
    public static final Supplier<AttachmentType<InstalledPresets>> PRESETS = ATTACHMENT_TYPES.register("presets", () -> AttachmentType
            .builder(() -> InstalledPresets.EMPTY)
            .serialize(InstalledPresets.CODEC.fieldOf("presets"))
            .copyOnDeath()
            .build());

    /** What sits in the gateway's sockets. Server only, like the presets. */
    public static final Supplier<AttachmentType<Sockets>> SOCKETS = ATTACHMENT_TYPES.register("sockets", () -> AttachmentType
            .builder(() -> Sockets.EMPTY)
            .serialize(Sockets.CODEC.fieldOf("sockets"))
            .copyOnDeath()
            .build());
}
