package com.mhm.mhm2027.item;

import java.util.List;
import java.util.function.Supplier;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.terminal.ScriptSummary;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredRegister;

/** What a chip holds. A chip has either code or a preset id, plus a summary for its tooltip. */
public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Mhm2027.MODID);

    private static final Codec<ScriptSummary> SUMMARY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(ScriptSummary::name),
            Codec.STRING.fieldOf("kind").forGetter(ScriptSummary::kind),
            Codec.INT.fieldOf("tops").forGetter(ScriptSummary::tops),
            Codec.INT.fieldOf("ram").forGetter(ScriptSummary::ramMb),
            Codec.INT.fieldOf("level").forGetter(ScriptSummary::level),
            Codec.STRING.listOf().optionalFieldOf("details", List.of()).forGetter(ScriptSummary::details)
    ).apply(instance, ScriptSummary::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, ScriptSummary> SUMMARY_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ScriptSummary::name,
            ByteBufCodecs.STRING_UTF8, ScriptSummary::kind,
            ByteBufCodecs.VAR_INT, ScriptSummary::tops,
            ByteBufCodecs.VAR_INT, ScriptSummary::ramMb,
            ByteBufCodecs.VAR_INT, ScriptSummary::level,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ScriptSummary::details,
            ScriptSummary::new);

    /** A player's own code. */
    public static final Supplier<DataComponentType<ScriptCode>> SCRIPT_CODE = COMPONENTS.registerComponentType("script_code",
            builder -> builder.persistent(ScriptCode.CODEC).networkSynchronized(ScriptCode.STREAM_CODEC));

    /** A preset's id. The code stays in the datapack. */
    public static final Supplier<DataComponentType<Identifier>> PRESET = COMPONENTS.registerComponentType("preset",
            builder -> builder.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));

    /** What the tooltip shows. Filled in on the server, since only it can read code and presets. */
    public static final Supplier<DataComponentType<ScriptSummary>> SUMMARY = COMPONENTS.registerComponentType("script_summary",
            builder -> builder.persistent(SUMMARY_CODEC).networkSynchronized(SUMMARY_STREAM_CODEC));

    private ModComponents() {
    }
}
