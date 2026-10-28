package com.mhm.mhm2027.gateway;

import java.util.function.Supplier;

import com.mhm.mhm2027.Mhm2027;

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
}
