package com.mhm.mhm2027.gateway;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The live gateway state of one entity. Capacities come from {@link GatewayTier};
 * this holds only what changes during play.
 *
 * Units: RAM, Leak and Buffer are in MB. Heat runs from 0 to {@link #MAX_HEAT}.
 * Program RAM and reserved TOPS are owned by the server-side terminal; they are synced
 * to the client for the HUD but never saved, since programs don't survive a logout.
 */
public class GatewayData {
    public static final double MAX_HEAT = 100.0;
    public static final String DEFAULT_KERNEL = "Baseline";

    public static final Codec<GatewayData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(GatewayData::level),
            Codec.DOUBLE.optionalFieldOf("buffer", 0.0).forGetter(GatewayData::bufferMb),
            Codec.DOUBLE.optionalFieldOf("leak", 0.0).forGetter(GatewayData::leakMb),
            Codec.DOUBLE.optionalFieldOf("heat", 0.0).forGetter(GatewayData::heat),
            Codec.STRING.optionalFieldOf("kernel", DEFAULT_KERNEL).forGetter(GatewayData::kernel)
    ).apply(instance, (level, buffer, leak, heat, kernel) -> new GatewayData(level, buffer, leak, heat, kernel, 0, 0)));

    public static final StreamCodec<RegistryFriendlyByteBuf, GatewayData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GatewayData::level,
            ByteBufCodecs.DOUBLE, GatewayData::bufferMb,
            ByteBufCodecs.DOUBLE, GatewayData::leakMb,
            ByteBufCodecs.DOUBLE, GatewayData::heat,
            ByteBufCodecs.STRING_UTF8, GatewayData::kernel,
            ByteBufCodecs.VAR_INT, GatewayData::programRamMb,
            ByteBufCodecs.VAR_INT, GatewayData::topsInUse,
            GatewayData::new);

    private int level;
    private double bufferMb;
    private double leakMb;
    private double heat;
    private String kernel;
    private int programRamMb;
    private int topsInUse;
    private boolean dirty;

    private GatewayData(int level, double bufferMb, double leakMb, double heat, String kernel, int programRamMb, int topsInUse) {
        this.level = Math.clamp(level, GatewayTier.MIN_LEVEL, GatewayTier.MAX_LEVEL);
        this.bufferMb = Math.clamp(bufferMb, 0, tier().bufferMb());
        this.leakMb = Math.max(0, leakMb);
        this.heat = Math.clamp(heat, 0, MAX_HEAT);
        this.kernel = kernel;
        this.programRamMb = programRamMb;
        this.topsInUse = topsInUse;
    }

    /** A fresh gateway at the given level, with a full Buffer and the Baseline kernel. */
    public static GatewayData fresh(GatewayTier tier) {
        return new GatewayData(tier.level(), tier.bufferMb(), 0, 0, DEFAULT_KERNEL, 0, 0);
    }

    public GatewayTier tier() {
        return GatewayTier.of(level);
    }

    public int level() {
        return level;
    }

    public double bufferMb() {
        return bufferMb;
    }

    public double leakMb() {
        return leakMb;
    }

    public double heat() {
        return heat;
    }

    /** Name of the installed kernel script, or {@link #DEFAULT_KERNEL} for the built-in one. */
    public String kernel() {
        return kernel;
    }

    /** RAM held by the kernel and running programs. */
    public int programRamMb() {
        return programRamMb;
    }

    /** TOPS reserved by the kernel and programs, plus TOPS still recovering from stopped ones. */
    public int topsInUse() {
        return topsInUse;
    }

    /** Free RAM = capacity - kernel and programs - Leak. */
    public double freeRamMb() {
        return tier().ramMb() - programRamMb - leakMb;
    }

    public int freeTops() {
        return tier().tops() - topsInUse;
    }

    public boolean isOutOfMemory() {
        return !tier().isNull() && freeRamMb() < 0;
    }

    /**
     * Changes the gateway level. Buffer keeps its current amount but is capped at the
     * new maximum. Moving to or from Null wipes the cyber state, as the design requires.
     */
    public void setLevel(int newLevel) {
        int clamped = Math.clamp(newLevel, GatewayTier.MIN_LEVEL, GatewayTier.MAX_LEVEL);
        if (clamped == level) {
            return;
        }
        boolean nullChanged = GatewayTier.of(clamped).isNull() != tier().isNull();
        level = clamped;
        if (nullChanged) {
            leakMb = 0;
            heat = 0;
            bufferMb = tier().bufferMb();
        } else {
            bufferMb = Math.min(bufferMb, tier().bufferMb());
        }
        markDirty();
    }

    public void setBufferMb(double value) {
        bufferMb = Math.clamp(value, 0, tier().bufferMb());
        markDirty();
    }

    public void setLeakMb(double value) {
        leakMb = tier().isNull() ? 0 : Math.max(0, value);
        markDirty();
    }

    public void setHeat(double value) {
        heat = tier().isNull() ? 0 : Math.clamp(value, 0, MAX_HEAT);
        markDirty();
    }

    public void setKernel(String name) {
        kernel = name;
        markDirty();
    }

    /** Called by the terminal every tick; only marks the data dirty when something changed. */
    public void setUsage(int ramMb, int tops) {
        if (ramMb != programRamMb || tops != topsInUse) {
            programRamMb = ramMb;
            topsInUse = tops;
            markDirty();
        }
    }

    /** Clears the per-body state on respawn. Level, Buffer and kernel carry over. */
    public void resetBody() {
        leakMb = 0;
        heat = 0;
        markDirty();
    }

    /** Shows MB below 1 GB and GB above it, using 1,024 MB per GB. */
    public static String formatMb(double mb) {
        if (Math.abs(mb) < 1024) {
            return String.format(Locale.ROOT, "%.0f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024);
    }

    public void markDirty() {
        dirty = true;
    }

    /** Returns whether the state changed since the last call, and clears the flag. */
    public boolean consumeDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }
}
