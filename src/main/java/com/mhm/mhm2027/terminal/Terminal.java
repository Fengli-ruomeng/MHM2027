package com.mhm.mhm2027.terminal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;

import com.mhm.mhm2027.Config;
import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.GatewayTier;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.gateway.Sockets;
import com.mhm.mhm2027.hud.Hud;
import com.mhm.mhm2027.hud.HudBoot;
import com.mhm.mhm2027.hud.HudEvent;
import com.mhm.mhm2027.hud.HudState;
import com.mhm.mhm2027.hud.HudTargets;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * A player's terminal: the kernel, the running programs, and the rules between them and the
 * gateway's hardware. Lives on the server only, for as long as the player is online.
 */
public final class Terminal {
    private static final int LOG_LINES = 50;
    /** How long the target callout remembers an inspection: a minute. */
    private static final int KNOWLEDGE_TICKS = 1200;
    private static final int MAX_TASKS = 8;
    private static final int MAX_TARGETS = 16;
    /** Freed TOPS comes back over 3 seconds. */
    private static final int TOPS_RECOVERY_TICKS = 60;
    private static final int PANIC_LOCK_TICKS = 100;
    private static final int REBOOT_LOCK_TICKS = 60;
    private static final int KERNEL_SWAP_LOCK_TICKS = 200;
    /** Dynamic flaw rules are checked a few times a second, on the same cadence the HUD syncs. */
    private static final int FLAW_INTERVAL_TICKS = 10;
    /** Each program that crashes in a panic leaves this share of its RAM behind as Leak. */
    private static final double PANIC_LEAK_SHARE = 0.25;
    /** Timed {@code me.*} writes. A kernel-swap debuff is not an enhancement, so it is not here. */
    private static final Set<String> BODY_ENHANCEMENTS = Set.of(
            "speed_boost", "jump_boost", "fall_guard", "max_health_boost", "damage_boost", "knockback_resist");
    /** A flash ends by itself if its station stops holding it for this long (unloaded, gone). */
    private static final int FLASH_HOLD_TICKS = 5;

    /** Booting a gateway that was off takes this long. */
    private static final int BOOT_TICKS = 60;

    /** Off, booting (any lock: a boot, a reboot, a panic, a kernel swap) or on. */
    public enum Power {
        OFF, BOOTING, ON
    }

    /** A firmware flash in progress: the Buffer it had, and whether it started out Null. */
    private record Flash(double savedBufferMb, boolean wasNull) {
    }

    private final UUID owner;
    private final MinecraftServer server;
    private Kernel kernel;
    private final List<Program> programs = new ArrayList<>();
    private final Deque<String> log = new ArrayDeque<>();
    private final ProgramOutputs outputs = new ProgramOutputs();
    private final List<int[]> recoveringTops = new ArrayList<>();
    /** TOPS committed by resist(): {amount, ticks left}. Held for the length of the attempt. */
    private final List<int[]> resisting = new ArrayList<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<Identifier, TimedModifier> modifiers = new HashMap<>();

    private long now;
    private long lockedUntil;
    private String lockReason = "";
    private @Nullable String pendingPanic;
    private FlawRules.Profile flaws = FlawRules.Profile.EMPTY;
    private List<FlawRules.Hit> publishedFlaws = List.of();
    /** Set when the body is replaced, so the next tick rechecks before the usual interval. */
    private boolean recheckFlaws;
    private @Nullable Flash flash;
    /** A gateway starts off when you join and after you respawn; G boots it. */
    private boolean poweredOff = true;
    private long flashHeldAt;
    private float flashShare;
    private long kernelReadyAt;
    private long overloadLockedUntil;
    /** How long the current reboot lock lasts in all, for the task list's bar. */
    private int lockTicks;
    /** Access attempts on this gateway, by attempt, while their attackers keep ticking them. */
    private final Map<Object, Incoming> incoming = new HashMap<>();
    /** What this gateway's inspections found, by target, for the target callout. */
    private final Map<UUID, Knowledge> knowledge = new HashMap<>();

    Terminal(ServerPlayer player) {
        this.owner = player.getUUID();
        this.server = player.level().getServer();
        this.kernel = loadSavedKernel(player);
        refreshFlaws(player, false);
    }

    // region Lifecycle

    /**
     * Loads, checks and starts a program from storage: a file name, or {@code preset:<id>} for
     * a preset written into the gateway.
     *
     * @throws InstallException with a player-facing reason if it can't start
     */
    public Program start(String id) throws InstallException {
        ServerPlayer player = requirePlayer();
        GatewayTier tier = data(player).tier();
        if (tier.isNull()) {
            throw new InstallException("A Null gateway can't run programs");
        }
        requireOn();
        if (flash != null) {
            throw new InstallException("The gateway is flashing firmware");
        }
        if (isRebooting()) {
            throw new InstallException("The terminal is rebooting (" + lockReason + ")");
        }

        Program program = load(player, id);
        checkSocket(player, program);
        if (find(program.name()).isPresent()) {
            throw new InstallException(program.name() + " is already running");
        }
        int freeTops = freeTops(player);
        if (freeTops - program.tops() < 1) {
            throw new InstallException("Not enough TOPS: " + program.name() + " reserves " + program.tops() + ", "
                    + freeTops + " free, and 1 must stay free");
        }
        double freeRam = data(player).freeRamMb();
        if (program.ramMb() > freeRam) {
            throw new InstallException("Not enough RAM: " + program.name() + " needs " + GatewayData.formatMb(program.ramMb())
                    + ", " + GatewayData.formatMb(Math.max(0, freeRam)) + " free");
        }

        programs.add(program);
        outputs.started(program.name(), program.id());
        program.begin();
        updateUsage(player);
        refreshFlaws(player, true);
        log(program.name(), "started (" + program.type().id() + ", " + GatewayData.formatMb(program.ramMb()) + ", "
                + program.tops() + " TOPS)");
        return program;
    }

    /** Loads and checks a program from storage without starting it. */
    private Program load(ServerPlayer player, String id) throws InstallException {
        GatewayTier tier = data(player).tier();
        if (id.startsWith(PresetRegistry.PREFIX)) {
            PresetRegistry.Preset preset = installedPreset(player, id, false);
            // Locked code runs at the preset's privilege, so install checks use that level too.
            return new Program(this, id, preset.chunk(), preset.source(), Math.max(tier.level(), preset.privilegeLevel()),
                    tier.tops(), preset.privilegeLevel(), 0);
        }
        return new Program(this, id, id, ScriptStore.read(server, owner, id), tier.level(), tier.tops(), -1,
                Config.OVERLOAD_SCRIPT_REACH.get());
    }

    /**
     * Body-changing programs run only from a standard socket, Overload programs only from an
     * Overload socket. Everything else needs no socket.
     */
    private void checkSocket(ServerPlayer player, Program program) throws InstallException {
        Sockets sockets = sockets(player);
        if (program.isOverload()) {
            if (sockets.overload().isEmpty()) {
                throw new InstallException(program.name() + " is an Overload program; Overload sockets start at LV4");
            }
            if (now < overloadLockedUntil) {
                throw new InstallException("The Overload sockets are locked after overheating ("
                        + overloadLockSecondsLeft() + " s)");
            }
            if (!sockets.holds(program.id(), true)) {
                throw new InstallException(program.name() + " is an Overload program; put it in an Overload socket at a Flash Station");
            }
        } else if (program.needsSlot() && !sockets.holds(program.id(), false)) {
            throw new InstallException(program.name() + " changes your body; put it in a socket at a Flash Station");
        }
    }

    /** Stops a program by name, running its on_stop. Its TOPS come back over 3 seconds. */
    public boolean stop(String name) {
        Optional<Program> program = find(name);
        if (program.isEmpty()) {
            return false;
        }
        programs.remove(program.get());
        program.get().stop(this, instructionsPerTops());
        recoveringTops.add(new int[]{program.get().tops(), TOPS_RECOVERY_TICKS});
        ended(name, "stopped", "");
        withPlayer(player -> {
            updateUsage(player);
            refreshFlaws(player, true);
        });
        return true;
    }

    /** A voluntary reboot: kicks every program out, with a few seconds of mild debuff. */
    public void reboot() {
        // An off gateway has nothing to restart.
        if (poweredOff) {
            return;
        }
        stopAllSilently("stopped", "reboot");
        lock("reboot", REBOOT_LOCK_TICKS);
        withPlayer(player -> {
            applyDowntimeDebuff(player, REBOOT_LOCK_TICKS, false);
            // Programs are gone, so a scheduler flaw opened by load should drop now, not half a second later.
            refreshFlaws(player, false);
        });
        log("kernel", "rebooted");
    }

    /**
     * Swaps the kernel. This reboots the terminal and takes longer than a normal reboot,
     * with a heavier debuff while the new kernel comes up.
     */
    public Kernel installKernel(String id) throws InstallException {
        ServerPlayer player = requirePlayer();
        GatewayTier tier = data(player).tier();
        if (tier.isNull()) {
            throw new InstallException("A Null gateway has no kernel");
        }
        requireOn();
        if (flash != null) {
            throw new InstallException("The gateway is flashing firmware");
        }
        if (isRebooting()) {
            throw new InstallException("The terminal is rebooting (" + lockReason + ")");
        }
        Kernel next = loadKernel(player, id);

        stopAllSilently("stopped", "kernel install");
        kernel = next;
        kernelReadyAt = now + Config.KERNEL_COOLDOWN_SECONDS.get() * 20L;
        data(player).setKernel(next.isBaseline() ? GatewayData.DEFAULT_KERNEL : id);
        // Programs were just cleared, so the static flaws are the ones these settings create.
        refreshFlaws(player, false);
        lock("kernel swap", KERNEL_SWAP_LOCK_TICKS);
        applyDowntimeDebuff(player, KERNEL_SWAP_LOCK_TICKS, true);
        updateUsage(player);
        log("kernel", "installed " + next.name());
        for (String line : FlawRules.installLog(flaws)) {
            log("kernel", line);
        }
        return next;
    }

    /** Throws while the gateway is off: nothing works until it has booted. */
    public void requireOn() throws InstallException {
        if (poweredOff) {
            throw new InstallException("Your gateway is off: press G to boot it");
        }
    }

    // endregion

    // region Power

    public Power power() {
        return poweredOff ? Power.OFF : isRebooting() ? Power.BOOTING : Power.ON;
    }

    public boolean isOff() {
        return poweredOff;
    }

    /** G on an off gateway: it boots, which takes a few seconds, like any reboot. */
    public boolean bootFromOff() {
        if (!poweredOff) {
            return false;
        }
        poweredOff = false;
        lock(HudBoot.STARTUP, BOOT_TICKS);
        log("kernel", "booting");
        return true;
    }

    /**
     * The player closed the boot screen before the boot finished: the boot carries on, but they
     * are blind and heavily slowed until it does.
     */
    public void lookedAway(ServerPlayer player) {
        int ticks = (int) Math.max(0, lockedUntil - now);
        if (ticks > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks + 20, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 3, false, false, true));
        }
    }

    // endregion

    // region Kernel swaps

    /** Kernels can only be swapped so often. Admin commands skip this check. */
    public void checkKernelCooldown() throws InstallException {
        if (now < kernelReadyAt) {
            throw new InstallException("The kernel was changed recently; try again in " + (int) Math.ceil((kernelReadyAt - now) / 20.0) + " s");
        }
    }

    // endregion

    // region Sockets

    /** The gateway's sockets, sized to its level. */
    public Sockets sockets(ServerPlayer player) {
        return player.getData(ModAttachments.SOCKETS).fitted(data(player).tier());
    }

    /**
     * Puts something from storage in the first free socket of the kind it needs. The caller
     * checks that the player is at a Flash Station.
     *
     * @return the program's name
     */
    public String socket(String id) throws InstallException {
        ServerPlayer player = requirePlayer();
        if (data(player).tier().isNull()) {
            throw new InstallException("A Null gateway has no sockets");
        }
        if (flash != null) {
            throw new InstallException("The gateway is flashing firmware");
        }
        Sockets sockets = sockets(player);
        if (sockets.holds(id)) {
            throw new InstallException(id + " is already in a socket");
        }
        // Loading it the way a start would also tells which kind of socket it needs.
        Program program = load(player, id);
        boolean overload = program.isOverload();
        if (!overload && !program.needsSlot()) {
            throw new InstallException(program.name() + " doesn't change your body, so it runs without a socket");
        }
        List<String> kind = overload ? sockets.overload() : sockets.standard();
        if (kind.isEmpty()) {
            throw new InstallException("Your gateway has no Overload sockets yet; they start at LV4");
        }
        int free = SocketRules.firstFree(kind);
        if (free < 0) {
            throw new InstallException("All " + kind.size() + (overload ? " Overload" : " standard") + " sockets are in use; remove something first");
        }
        player.setData(ModAttachments.SOCKETS, sockets.with(overload, free, id));
        log("kernel", "socketed " + program.name() + (overload ? " (Overload)" : ""));
        return program.name();
    }

    /** Takes something out of its socket, stopping it if it runs. The caller checks for a Flash Station. */
    public void unsocket(String id) throws InstallException {
        ServerPlayer player = requirePlayer();
        Sockets sockets = sockets(player);
        if (!sockets.holds(id)) {
            throw new InstallException(id + " isn't in a socket");
        }
        programs.stream().filter(program -> program.id().equals(id)).findFirst().ifPresent(program -> stop(program.name()));
        player.setData(ModAttachments.SOCKETS, sockets.without(id));
        log("kernel", "unsocketed " + id);
    }

    public int overloadLockSecondsLeft() {
        return (int) Math.max(0, Math.ceil((overloadLockedUntil - now) / 20.0));
    }

    /** The level a gateway needs to write and run a preset. */
    public static int requiredLevel(PresetRegistry.Preset preset) {
        return preset.overload() ? SocketRules.presetRequirement(preset.level(), Config.OVERLOAD_PRESET_REACH.get()) : preset.level();
    }

    // endregion

    // region Firmware flash

    /**
     * A Flash Station starts writing firmware: every program stops, the kernel is down and
     * Buffer is held at 0. The gateway can still be hacked; it just can't defend itself.
     */
    public void beginFlash(ServerPlayer player) {
        GatewayData data = data(player);
        flash = new Flash(data.bufferMb(), data.tier().isNull());
        flashHeldAt = now;
        flashShare = 0;
        stopAllSilently("stopped", "flashing");
        // Sessions this gateway holds on others ran on programs that are gone now.
        Sessions.endAllBy(owner, "flashing");
        data.setBufferMb(0);
        updateUsage(player);
        refreshFlaws(player, false);
        log("kernel", "flashing firmware");
    }

    /**
     * A Cyber Potion: Buffer back, up to the maximum. Mid-flash it goes to the Buffer that
     * comes back when the flash ends, so it isn't lost.
     */
    public void restoreBuffer(ServerPlayer player, double mb) {
        GatewayData data = data(player);
        double max = data.tier().bufferMb();
        if (flash != null) {
            flash = new Flash(Math.min(max, flash.savedBufferMb() + mb), flash.wasNull());
            return;
        }
        data.setBufferMb(Math.min(max, data.bufferMb() + mb));
    }

    /** The station calls this every tick it keeps the flash going, with how far it has got. */
    public void holdFlash(float share) {
        flashHeldAt = now;
        flashShare = share;
    }

    public boolean isFlashing() {
        return flash != null;
    }

    /** How far the flash has got, 0 to 1; -1 when there is none. */
    public float flashShare() {
        return flash == null ? -1 : flashShare;
    }

    /**
     * Ends a flash, finished or not. The Buffer comes back (capped at the new maximum); leaving
     * Null starts from the fresh LV0 Buffer instead. The kernel reloads at the gateway's level,
     * which may have changed.
     */
    public void endFlash(ServerPlayer player) {
        if (flash == null) {
            return;
        }
        GatewayData data = data(player);
        if (!flash.wasNull() && !data.tier().isNull()) {
            data.setBufferMb(Math.min(flash.savedBufferMb(), data.tier().bufferMb()));
        }
        flash = null;
        if (data.tier().isNull()) {
            Sessions.endAllOn(owner, "null gateway");
        }
        kernel = loadSavedKernel(player);
        updateUsage(player);
        refreshFlaws(player, false);
        log("kernel", "flash ended; gateway at LV" + data.level() + ", kernel " + kernel.name());
    }

    // endregion

    // region Death and logout

    /** Death: the body is gone, so programs and timed effects end. The kernel carries over. */
    void onDeath() {
        withPlayer(this::endFlash);
        // You respawn with the gateway off.
        poweredOff = true;
        Sessions.endAllOn(owner, "died");
        Sessions.endAllBy(owner, "died");
        stopAllSilently("stopped", "died");
        modifiers.clear();
        cooldowns.clear();
        lockedUntil = 0;
        pendingPanic = null;
        recheckFlaws = true;
    }

    /** Logout: a flash is cancelled, and every timed modifier comes back off the player. */
    void discard(ServerPlayer player) {
        endFlash(player);
        for (TimedModifier modifier : modifiers.values()) {
            AttributeInstance instance = player.getAttribute(modifier.attribute());
            if (instance != null) {
                instance.removeModifier(modifier.id());
            }
        }
        modifiers.clear();
    }

    /** Every program goes at once, with no on_stop; their windows say how. */
    private void stopAllSilently(String how, String reason) {
        programs.forEach(program -> outputs.ended(program.name(), how, reason));
        programs.clear();
        recoveringTops.clear();
        resisting.clear();
    }

    /**
     * Every reboot (voluntary, panic or kernel swap) ends attempts and open sessions on this
     * terminal, and the sessions it holds on others, since its programs are gone.
     */
    private void lock(String reason, int ticks) {
        lockedUntil = now + ticks;
        lockTicks = ticks;
        lockReason = reason;
        Sessions.endAllOn(owner, reason);
        Sessions.endAllBy(owner, reason);
        // The boot screen runs for exactly as long as the gateway is down.
        withPlayer(player -> Hud.boot(player, reason, ticks, bootLog(player, reason)));
    }

    /** The boot log the client shows while the gateway comes back up: this gateway's real state. */
    public List<HudBoot.Line> bootLog(ServerPlayer player, String reason) {
        GatewayData data = data(player);
        GatewayTier tier = data.tier();
        List<HudBoot.Line> lines = new ArrayList<>();
        lines.add(bootLine("gateway", tier.label() + " " + tier.band() + " · " + GatewayData.formatMb(tier.ramMb()) + " RAM · "
                + tier.tops() + " TOPS", HudBoot.Tone.PLAIN));
        lines.add(bootLine("buffer", GatewayData.formatMb(data.bufferMb()) + " / " + GatewayData.formatMb(tier.bufferMb()) + " .... ok",
                HudBoot.Tone.OK));
        lines.add(bootLine("net", "address " + Addresses.of(owner), HudBoot.Tone.PLAIN));
        lines.add(bootLine("storage", ScriptStore.list(server, owner).size() + " files .... ok", HudBoot.Tone.OK));
        List<String> presets = player.getData(ModAttachments.PRESETS).ids().stream().map(Identifier::getPath).toList();
        lines.add(bootLine("presets", presets.isEmpty() ? "none" : String.join(" ", presets), HudBoot.Tone.PLAIN));
        Sockets sockets = sockets(player);
        List<String> socketed = new ArrayList<>();
        for (int i = 0; i < sockets.standard().size(); i++) {
            if (!sockets.standard().get(i).isEmpty()) {
                socketed.add("S" + (i + 1) + " " + socketName(sockets.standard().get(i)));
            }
        }
        for (int i = 0; i < sockets.overload().size(); i++) {
            if (!sockets.overload().get(i).isEmpty()) {
                socketed.add("O" + (i + 1) + " " + socketName(sockets.overload().get(i)));
            }
        }
        lines.add(bootLine("sockets", socketed.isEmpty() ? "empty" : String.join(" · ", socketed), HudBoot.Tone.PLAIN));
        KernelSettings settings = kernel.settings();
        lines.add(bootLine("kernel", kernel.name() + " · " + settings.schedule() + " " + settings.gc() + " verify=" + settings.verify()
                + " rate_limit=" + settings.rateLimit(), HudBoot.Tone.PLAIN));
        for (FlawRules.Hit hit : flaws.active()) {
            if (!hit.dynamic()) {
                lines.add(bootLine("", "flaw " + hit.className() + " " + hit.severity().id(),
                        hit.severity().rank() >= 2 ? HudBoot.Tone.FLAW : HudBoot.Tone.WARN));
            }
        }
        List<String> hooks = kernel.hookNames();
        lines.add(bootLine("hooks", (hooks.isEmpty() ? "defaults" : String.join(" ", hooks)) + " .... ok", HudBoot.Tone.OK));
        if (!reason.equals(HudBoot.STARTUP)) {
            lines.add(bootLine("reboot", reason, HudBoot.Tone.WARN));
        }
        lines.add(bootLine("edge", "starting", HudBoot.Tone.PLAIN));
        return lines;
    }

    private static HudBoot.Line bootLine(String unit, String text, HudBoot.Tone tone) {
        return new HudBoot.Line(unit, text, tone);
    }

    private static String socketName(String id) {
        return PresetRegistry.byTerminalId(id).map(preset -> preset.summary().name()).orElse(id);
    }

    // endregion

    // region Tick

    void tick(ServerPlayer player) {
        now++;
        // An attempt the attacker stopped ticking (it ended, or its program did) leaves the list.
        incoming.values().removeIf(attempt -> now - attempt.seenAt() > 2);
        if (now % 20 == 0) {
            knowledge.values().removeIf(known -> now - known.at() > KNOWLEDGE_TICKS);
        }
        boolean bodyChanged = tickModifiers(player);
        recoveringTops.removeIf(entry -> --entry[1] <= 0);
        resisting.removeIf(entry -> --entry[1] <= 0);

        // First, since a Null gateway writing Restore Firmware is flashing while off.
        if (flash != null) {
            if (now - flashHeldAt > FLASH_HOLD_TICKS) {
                endFlash(player);
            }
            updateUsage(player);
            refreshFlawsIfDue(player, bodyChanged);
            return;
        }
        if (poweredOff) {
            // Off: nothing runs, and you feel it.
            if (now % 20 == 0 && !data(player).tier().isNull()) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0, true, false, true));
            }
            updateUsage(player);
            refreshFlawsIfDue(player, bodyChanged);
            return;
        }
        if (isRebooting()) {
            updateUsage(player);
            refreshFlawsIfDue(player, bodyChanged);
            return;
        }
        if (pendingPanic != null) {
            String reason = pendingPanic;
            pendingPanic = null;
            kernel.callHook(this, Kernel.ON_PANIC, ValueFactory.valueOf(reason));
        }

        if (!programs.isEmpty() && freeTops(player) <= 0) {
            if (!kernel.settings().ranksPrograms()) {
                panic(player, "out of TOPS");
                return;
            }
            starve(player);
        }
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        for (Program program : programs) {
            if (program.crossedLowHealth(health, maxHealth)) {
                program.enqueue(ScriptEvents.ON_LOW_HEALTH, () -> ValueFactory.valueOf(health));
            }
        }
        if (now % 20 == 0 && data(player).isOutOfMemory() && !programs.isEmpty()) {
            handleLowRam(player);
        }

        // Iterate over a copy: programs may start or stop other programs while they run.
        int instructionsPerTops = instructionsPerTops();
        for (Program program : List.copyOf(programs)) {
            if (!programs.contains(program)) {
                continue;
            }
            if (!program.tick(this, now, instructionsPerTops)) {
                programs.remove(program);
                recheckFlaws = true;
            } else if (program.stopRequested()) {
                stop(program.name());
            }
        }
        if (now % 20 == 0) {
            tickOverload(player);
        }
        updateUsage(player);
        refreshFlawsIfDue(player, bodyChanged);
    }

    /**
     * Each running Overload program heats the gateway by 2 plus its level gap a second, and
     * leaks. At full Heat they all crash, each leaving its RAM as Leak, and the Overload
     * sockets lock.
     */
    private void tickOverload(ServerPlayer player) {
        List<Program> overloads = programs.stream().filter(Program::isOverload).toList();
        if (overloads.isEmpty()) {
            return;
        }
        GatewayData data = data(player);
        double heat = 0;
        double leak = 0;
        for (Program program : overloads) {
            heat += SocketRules.heatPerSecond(program.codeLevel(), data.level());
            leak += program.ramMb() * Config.OVERLOAD_LEAK_SHARE.get();
        }
        data.setHeat(data.heat() + heat);
        data.setLeakMb(data.leakMb() + leak);
        if (data.heat() < GatewayData.MAX_HEAT) {
            return;
        }
        for (Program program : overloads) {
            programs.remove(program);
            data.setLeakMb(data.leakMb() + program.ramMb());
            ended(program.name(), "crashed", "overheated");
            announceCrash(program);
        }
        int lockSeconds = Config.OVERLOAD_LOCK_SECONDS.get();
        overloadLockedUntil = now + lockSeconds * 20L;
        recheckFlaws = true;
        Hud.banner(player, HudEvent.Kind.ALERT, Component.translatable("mhm2027.banner.overheat"));
        Hud.event(player, HudEvent.Kind.ALERT, "OVERLOAD // OVERHEAT", Component.translatable("mhm2027.terminal.overheated", lockSeconds));
    }

    /**
     * Kernel panic: every program crashes, each leaving a quarter of its RAM as Leak, and
     * nothing can start for 5 seconds. Afterwards on_panic decides what restarts.
     */
    private void panic(ServerPlayer player, String reason) {
        GatewayData data = data(player);
        double leak = programs.stream().mapToDouble(program -> program.ramMb() * PANIC_LEAK_SHARE).sum();
        data.setLeakMb(data.leakMb() + leak);
        stopAllSilently("crashed", "kernel panic");
        lock("kernel panic", PANIC_LOCK_TICKS);
        pendingPanic = reason;
        applyDowntimeDebuff(player, PANIC_LOCK_TICKS, false);
        updateUsage(player);
        refreshFlaws(player, false);
        log("kernel", "PANIC: " + reason);
        Hud.banner(player, HudEvent.Kind.ALERT, Component.translatable("mhm2027.banner.panic"));
        Hud.event(player, HudEvent.Kind.ALERT, "KERNEL // PANIC", Component.translatable("mhm2027.terminal.panic", reason));
    }

    /**
     * A priority kernel out of TOPS: rather than a panic, the lowest-ranked programs crash (the
     * newest first among equals) until TOPS fit again, each leaving some of its RAM as Leak.
     */
    private void starve(ServerPlayer player) {
        GatewayData data = data(player);
        while (!programs.isEmpty() && freeTops(player) <= 0) {
            Program starved = programs.reversed().stream().min(Comparator.comparingInt(Program::priority)).orElseThrow();
            programs.remove(starved);
            data.setLeakMb(data.leakMb() + starved.ramMb() * PANIC_LEAK_SHARE);
            ended(starved.name(), "crashed", "starved of TOPS");
            announceCrash(starved);
        }
        recheckFlaws = true;
        updateUsage(player);
    }

    /** on_program_crash for every other program still running. */
    private void announceCrash(Program crashed) {
        if (isRebooting() || flash != null) {
            return;
        }
        for (Program other : programs) {
            if (other != crashed) {
                other.enqueue(ScriptEvents.ON_PROGRAM_CRASH, () -> ValueFactory.valueOf(crashed.name()));
            }
        }
    }

    /** Out of memory: on_low_ram decides what to stop; by default the newest program goes. */
    private void handleLowRam(ServerPlayer player) {
        int before = programs.size();
        kernel.callHook(this, Kernel.ON_LOW_RAM, ValueFactory.valueOf(data(player).freeRamMb()));
        if (programs.size() == before && data(player).isOutOfMemory() && !programs.isEmpty()) {
            Program newest = programs.removeLast();
            log(newest.name(), "force-stopped: out of memory");
        }
        if (programs.size() != before) {
            recheckFlaws = true;
        }
    }

    // endregion

    // region Events

    /** Queues an event on every program that handles it. Arguments are built per program. */
    void fire(String event, Supplier<Varargs> args) {
        if (isRebooting()) {
            return;
        }
        for (Program program : programs) {
            program.enqueue(event, args);
        }
    }

    // endregion

    // region Resources

    public int ramInUse() {
        return kernel.ramMb() + programs.stream().mapToInt(Program::ramMb).sum();
    }

    public int topsInUse() {
        int recovering = 0;
        for (int[] entry : recoveringTops) {
            recovering += (int) Math.ceil((double) entry[0] * entry[1] / TOPS_RECOVERY_TICKS);
        }
        int resisted = resisting.stream().mapToInt(entry -> entry[0]).sum();
        return kernel.topsReserve() + programs.stream().mapToInt(Program::tops).sum() + recovering + resisted;
    }

    int freeTops(ServerPlayer player) {
        return data(player).tier().tops() - topsInUse();
    }

    // endregion

    // region Defence

    /**
     * An access attempt on this terminal: programs hear about it (on_access_attempt, which can't
     * decide), then the kernel's on_access_request decides. No hook, or a hook that fails, allows.
     */
    Access.Decision decideAccess(LivingEntity attacker, String method, int tops, boolean escalate) {
        ServerPlayer self = player();
        // Mid-flash the kernel is down, so nothing decides.
        if (self == null || flash != null || poweredOff) {
            return Access.Decision.ALLOW;
        }
        Supplier<Varargs> request = () -> accessRequest(attacker, self, method, tops, escalate);
        fire(ScriptEvents.ON_ACCESS_ATTEMPT, request);
        return Access.parse(kernel.callHook(this, Kernel.ON_ACCESS_REQUEST, request.get()));
    }

    /** req: who is trying, how, and with how much TOPS. */
    private static LuaTable accessRequest(LivingEntity attacker, ServerPlayer self, String method, int tops, boolean escalate) {
        LuaTable request = new LuaTable();
        request.rawset("from", EntityHandles.of(attacker, self));
        request.rawset("method", ValueFactory.valueOf(method));
        request.rawset("tops", ValueFactory.valueOf(tops));
        request.rawset("escalate", ValueFactory.valueOf(escalate));
        return request;
    }

    /** The TOPS this terminal defends an access attempt with: the kernel reserve, or none mid-flash or while off. */
    int defenceTops() {
        return flash != null || poweredOff ? 0 : kernel.topsReserve();
    }

    /** Pays Buffer for a defence (deny, closing a session). False if there isn't enough. */
    boolean spendBuffer(double mb) {
        ServerPlayer self = player();
        if (self == null) {
            return false;
        }
        GatewayData data = data(self);
        if (data.bufferMb() < mb) {
            return false;
        }
        data.setBufferMb(data.bufferMb() - mb);
        return true;
    }

    /**
     * resist(tops): holds TOPS out of what's free (always leaving 1) for {@code ticks}, and
     * runs hot. Returns how many TOPS were actually committed.
     */
    int commitResist(int requested, int ticks) {
        ServerPlayer self = player();
        if (self == null) {
            return 0;
        }
        int committed = Math.clamp(requested, 0, Math.max(0, freeTops(self) - 1));
        if (committed > 0) {
            resisting.add(new int[]{committed, ticks});
            GatewayData data = data(self);
            data.setHeat(data.heat() + committed * AccessRules.HEAT_PER_RESISTED_TOPS);
            updateUsage(self);
            log("kernel", "resisting with " + committed + " TOPS");
        }
        return committed;
    }

    /** Someone got in: the kernel's on_session_opened hook, then the programs' event of the same name. */
    void sessionOpened(LivingEntity attacker) {
        ServerPlayer self = player();
        if (self == null) {
            return;
        }
        String address = Addresses.of(attacker.getUUID());
        log("kernel", "session opened by " + address);
        Hud.event(self, HudEvent.Kind.ALERT, Hud.tag(attacker, "SESSION"), Component.translatable("mhm2027.hud.session_opened"),
                Component.empty(), "access:" + address, 0);
        Hud.banner(self, HudEvent.Kind.ALERT, Component.translatable("mhm2027.banner.breach"));
        if (flash != null || poweredOff) {
            return;
        }
        kernel.callHook(this, Kernel.ON_SESSION_OPENED, EntityHandles.of(attacker, self));
        fire(ScriptEvents.ON_SESSION_OPENED, () -> EntityHandles.of(attacker, self));
    }

    /** s.kill(name) from a root session: the program stops at once, without its on_stop. */
    boolean kill(String name, String by) {
        Optional<Program> program = find(name);
        if (program.isEmpty()) {
            return false;
        }
        programs.remove(program.get());
        log(name, "killed by " + by);
        withPlayer(player -> {
            updateUsage(player);
            refreshFlaws(player, true);
        });
        return true;
    }

    private void updateUsage(ServerPlayer player) {
        data(player).setUsage(ramInUse(), topsInUse());
    }

    private static int instructionsPerTops() {
        return Config.INSTRUCTIONS_PER_TOPS.get();
    }

    // endregion

    // region Helpers for the APIs

    /** Starts a cooldown if the API is ready. Returns false while it's still cooling down. */
    boolean tryCooldown(ApiSpec spec) {
        if (spec.cooldownTicks() <= 0) {
            return true;
        }
        long readyAt = cooldowns.getOrDefault(spec.id(), 0L);
        if (now < readyAt) {
            return false;
        }
        cooldowns.put(spec.id(), now + spec.cooldownTicks());
        return true;
    }

    /** Adds a modifier that the terminal removes again after {@code ticks}. Re-applying refreshes it. */
    void addTimedModifier(ServerPlayer player, Holder<Attribute> attribute, String key, double amount,
                          AttributeModifier.Operation operation, int ticks) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        Identifier id = Identifier.fromNamespaceAndPath(Mhm2027.MODID, "terminal/" + key);
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        modifiers.put(id, new TimedModifier(attribute, id, now + ticks));
        refreshFlaws(player, true);
    }

    /** @return whether an enhancement expired, which can clear a motor flaw before the next interval */
    private boolean tickModifiers(ServerPlayer player) {
        boolean expired = false;
        for (Iterator<TimedModifier> it = modifiers.values().iterator(); it.hasNext(); ) {
            TimedModifier modifier = it.next();
            if (now >= modifier.expiresAt()) {
                AttributeInstance instance = player.getAttribute(modifier.attribute());
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                }
                it.remove();
                expired = true;
            }
        }
        return expired;
    }

    private void applyDowntimeDebuff(ServerPlayer player, int ticks, boolean moderate) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 0));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0));
        if (moderate) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks, 0));
            addTimedModifier(player, Attributes.MAX_HEALTH, "kernel_swap", -4, AttributeModifier.Operation.ADD_VALUE, ticks);
        }
    }

    // endregion

    // region Accessors

    /** Active flaws, as {@code sys.flaws()} returns them. Near-misses are not included. */
    public List<FlawRules.Hit> flaws() {
        return flaws.active();
    }

    /** Active flaws and the ones within a rule's near distance. The panel and {@code /edge flaws} use this. */
    public FlawRules.Profile flawProfile() {
        return flaws;
    }

    private void refreshFlawsIfDue(ServerPlayer player, boolean bodyChanged) {
        if (bodyChanged || recheckFlaws || player.tickCount % FLAW_INTERVAL_TICKS == 0) {
            recheckFlaws = false;
            refreshFlaws(player, true);
        }
    }

    /**
     * Recomputes flaws from the kernel settings and the current body. While rebooting, or when
     * {@code announce} is false, programs are not told: nothing is running to hear it, and the
     * published list still moves so the next real change is not reported twice.
     */
    private void refreshFlaws(ServerPlayer player, boolean announce) {
        FlawRules.Profile next = FlawRules.evaluate(FlawRegistry.classes(), reading(player));
        if (announce && !isRebooting()) {
            for (FlawRules.Change change : FlawRules.changes(publishedFlaws, next.active())) {
                String className = change.className();
                String severity = change.severity();
                fire(ScriptEvents.ON_FLAW_CHANGED, () -> ValueFactory.varargsOf(ValueFactory.valueOf(className), ValueFactory.valueOf(severity)));
            }
        }
        publishedFlaws = next.active();
        flaws = next;
        data(player).setDynamicFlaws(next.active().stream()
                .filter(FlawRules.Hit::dynamic)
                .map(hit -> new GatewayData.FlawMark(hit.className(), hit.severity().id()))
                .toList());
    }

    private FlawRules.Reading reading(ServerPlayer player) {
        GatewayData data = data(player);
        return FlawRules.Reading.of(kernel.settings(), programs.size(), data.heat(), bodyEnhancements(),
                kernel.hookNames().size(), data.tier().hookPoints());
    }

    private int bodyEnhancements() {
        int count = 0;
        for (TimedModifier modifier : modifiers.values()) {
            String path = modifier.id().getPath();
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && BODY_ENHANCEMENTS.contains(path.substring(slash + 1))) {
                count++;
            }
        }
        return count;
    }

    public void log(String source, String message) {
        log.addLast("[" + source + "] " + message);
        while (log.size() > LOG_LINES) {
            log.removeFirst();
        }
        outputs.line(source, clockMinute(), message);
    }

    // region HUD

    /** An access attempt on this gateway, as its attacker last ticked it. */
    private record Incoming(String address, String detail, int total, int left, long seenAt) {
    }

    /** What an inspection by this gateway found, and when. */
    public record Knowledge(List<FlawRules.Hit> flaws, long at) {
    }

    /** Called by an attempt on this gateway every tick it runs, for the task list. */
    void incoming(Object attempt, String address, String detail, int total, int left) {
        incoming.put(attempt, new Incoming(address, detail, total, left, now));
    }

    /** An inspection finished (a program's or the Scanner's): the callout shows its flaws for a minute. */
    public void remember(LivingEntity target) {
        knowledge.put(target.getUUID(), new Knowledge(Gateways.flawsOf(target), now));
    }

    private @Nullable Knowledge knowledge(UUID target) {
        Knowledge known = knowledge.get(target);
        return known == null || now - known.at() > KNOWLEDGE_TICKS ? null : known;
    }

    /** Everything that takes time, for the HUD's task list: what you may need to act on first, at most eight. */
    public List<HudState.Task> tasks(ServerPlayer player) {
        List<HudState.Task> tasks = new ArrayList<>();
        for (Incoming attempt : incoming.values()) {
            tasks.add(new HudState.Task(HudState.Task.Kind.INCOMING, attempt.address(), attempt.detail(), attempt.total(), attempt.left()));
        }
        if (flash != null) {
            tasks.add(new HudState.Task(HudState.Task.Kind.FLASH, "firmware", "flashing · stay near the station", 100,
                    Math.round((1 - Math.max(0, flashShare)) * 100)));
        }
        if (isRebooting()) {
            tasks.add(new HudState.Task(HudState.Task.Kind.LOCKED, "reboot", lockReason, Math.max(1, lockTicks), (int) (lockedUntil - now)));
        }
        if (now < overloadLockedUntil) {
            tasks.add(new HudState.Task(HudState.Task.Kind.LOCKED, "overload", "sockets locked after overheating",
                    Config.OVERLOAD_LOCK_SECONDS.get() * 20, (int) (overloadLockedUntil - now)));
        }
        for (Sessions.Session session : Sessions.heldBy(owner)) {
            tasks.add(new HudState.Task(HudState.Task.Kind.SESSION, Addresses.of(session.target), (session.root ? "root" : "user") + " session",
                    AccessRules.SESSION_TICKS, (int) Math.max(0, session.expiresAt - session.now())));
        }
        GatewayData data = data(player);
        double heatRate = programs.stream().filter(Program::isOverload)
                .mapToInt(program -> SocketRules.heatPerSecond(program.codeLevel(), data.level())).sum() - Config.HEAT_COOLING_PER_SECOND.get();
        for (Program program : programs) {
            LuaSandbox.Awaitable waiting = program.awaiting();
            if (waiting != null && waiting.totalTicks() > 0) {
                String detail = waiting.detail().isEmpty() ? waiting.state() : waiting.state() + " " + waiting.detail();
                tasks.add(new HudState.Task(HudState.Task.Kind.WAITING, program.name(), detail, waiting.totalTicks(),
                        Math.min(waiting.ticksLeft(), waiting.totalTicks())));
            } else if (program.isOverload()) {
                String crash = heatRate > 0 ? " · crash in ~" + (int) Math.ceil((GatewayData.MAX_HEAT - data.heat()) / heatRate) + " s" : "";
                tasks.add(new HudState.Task(HudState.Task.Kind.OVERLOAD, program.name(),
                        "Heat +" + SocketRules.heatPerSecond(program.codeLevel(), data.level()) + "/s" + crash, 100,
                        (int) Math.round(GatewayData.MAX_HEAT - data.heat())));
            } else {
                String state = program.state(now);
                tasks.add(new HudState.Task(state.equals("idle") ? HudState.Task.Kind.IDLE : HudState.Task.Kind.BUSY, program.name(), state, 0, 0));
            }
        }
        for (Map.Entry<String, Long> cooldown : cooldowns.entrySet()) {
            long left = cooldown.getValue() - now;
            if (left > 0) {
                int total = ApiSpec.byId(cooldown.getKey()).map(ApiSpec::cooldownTicks).orElse((int) left);
                tasks.add(new HudState.Task(HudState.Task.Kind.COOLDOWN, cooldown.getKey(), "cooldown", Math.max(total, (int) left), (int) left));
            }
        }
        tasks.sort(Comparator.comparingInt(task -> task.kind().ordinal()));
        return tasks.size() > MAX_TASKS ? List.copyOf(tasks.subList(0, MAX_TASKS)) : tasks;
    }

    /**
     * The gateways in scan range, for the HUD's target callout: what a free scan shows, plus
     * what this gateway knows of each: an inspection in the last minute, its session, an
     * attempt or inspection under way.
     */
    public HudTargets targets(ServerPlayer player) {
        if (poweredOff) {
            return HudTargets.NONE;
        }
        List<HudTargets.Seen> seen = new ArrayList<>();
        for (LivingEntity entity : Gateways.around(player, data(player).tier().scanRange(), MAX_TARGETS)) {
            GatewayData theirs = Gateways.of(entity);
            Knowledge known = knowledge(entity.getUUID());
            List<HudTargets.Flaw> flaws = known == null ? List.of()
                    : known.flaws().stream().map(hit -> new HudTargets.Flaw(hit.className(), hit.severity().id())).toList();
            Sessions.Session session = Sessions.find(owner, entity.getUUID());
            String attempt = "";
            int total = 0;
            int left = 0;
            for (Program program : programs) {
                LuaSandbox.Awaitable waiting = program.awaiting();
                if (waiting != null && waiting.totalTicks() > 0 && entity.getUUID().equals(waiting.target())) {
                    attempt = waiting.state();
                    total = waiting.totalTicks();
                    left = Math.min(waiting.ticksLeft(), total);
                }
            }
            seen.add(new HudTargets.Seen(entity.getId(), entity.getName().getString(), Addresses.of(entity.getUUID()),
                    theirs == null ? -1 : theirs.level(), entity instanceof Player, known == null ? -1 : (int) (now - known.at()), flaws,
                    session == null ? "" : session.root ? "root" : "user", session == null ? 0 : (int) Math.max(0, session.expiresAt - session.now()),
                    attempt, total, left));
        }
        return new HudTargets(List.copyOf(seen));
    }

    // endregion

    /** Logs how a program ended; its window in Edge keeps the output and shows why. */
    void ended(String name, String how, String reason) {
        log(name, reason.isEmpty() ? how : how + ": " + reason);
        outputs.ended(name, how, reason);
    }

    /** What each program printed, and kernel.log, for Edge's windows. */
    public ProgramOutputs outputs() {
        return outputs;
    }

    /** The Overworld's time of day in minutes, as Edge's clock shows it. */
    private int clockMinute() {
        long ofDay = (server.overworld().getOverworldClockTime() + 6000) % 24000;
        return (int) (ofDay * 60 / 1000);
    }

    void notifyCrash(Program program) {
        announceCrash(program);
        withPlayer(player -> Hud.event(player, HudEvent.Kind.WARN, Hud.tag(program.name(), "CRASH"),
                Component.translatable("mhm2027.terminal.crash", program.name())));
    }

    public List<String> log() {
        return List.copyOf(log);
    }

    public List<Program> programs() {
        return List.copyOf(programs);
    }

    public Optional<Program> find(String name) {
        return programs.stream().filter(program -> program.name().equals(name)).findFirst();
    }

    public Kernel kernel() {
        return kernel;
    }

    public UUID owner() {
        return owner;
    }

    public long now() {
        return now;
    }

    public boolean isRebooting() {
        return now < lockedUntil;
    }

    public String lockReason() {
        return lockReason;
    }

    public int lockSecondsLeft() {
        return (int) Math.ceil((lockedUntil - now) / 20.0);
    }

    @Nullable ServerPlayer player() {
        return server.getPlayerList().getPlayer(owner);
    }

    MinecraftServer server() {
        return server;
    }

    private ServerPlayer requirePlayer() throws InstallException {
        ServerPlayer player = player();
        if (player == null) {
            throw new InstallException("The terminal's owner is offline");
        }
        return player;
    }

    private void withPlayer(java.util.function.Consumer<ServerPlayer> action) {
        ServerPlayer player = player();
        if (player != null) {
            action.accept(player);
        }
    }

    static GatewayData data(ServerPlayer player) {
        return player.getData(ModAttachments.GATEWAY);
    }

    private Kernel loadSavedKernel(ServerPlayer player) {
        GatewayData data = data(player);
        String id = data.kernel();
        if (data.tier().isNull()) {
            return Kernel.baseline();
        }
        try {
            return loadKernel(player, id);
        } catch (InstallException e) {
            log("kernel", "couldn't load " + id + " (" + e.getMessage() + "); using " + GatewayData.DEFAULT_KERNEL);
            data.setKernel(GatewayData.DEFAULT_KERNEL);
            return Kernel.baseline();
        }
    }

    /** "Baseline", a file in storage, or {@code preset:<id>} for a preset kernel in the gateway. */
    private Kernel loadKernel(ServerPlayer player, String id) throws InstallException {
        GatewayTier tier = data(player).tier();
        if (id.equals(GatewayData.DEFAULT_KERNEL)) {
            return Kernel.baseline();
        }
        if (id.startsWith(PresetRegistry.PREFIX)) {
            PresetRegistry.Preset preset = installedPreset(player, id, true);
            int privilege = preset.privilegeLevel();
            return Kernel.load(this, preset.chunk(), preset.source(), Math.max(tier.level(), privilege),
                    Math.max(tier.hookPoints(), GatewayTier.of(privilege).hookPoints()), privilege);
        }
        return Kernel.load(this, id, ScriptStore.read(server, owner, id), tier.level(), tier.hookPoints(), -1);
    }

    /** A preset the player has written into their gateway, checked against their level. */
    private PresetRegistry.Preset installedPreset(ServerPlayer player, String id, boolean kernel) throws InstallException {
        PresetRegistry.Preset preset = PresetRegistry.byTerminalId(id)
                .orElseThrow(() -> new InstallException("Unknown preset " + id + " (was its datapack removed?)"));
        if (!player.getData(ModAttachments.PRESETS).contains(preset.id())) {
            throw new InstallException(preset.summary().name() + " isn't in your gateway; write its chip first");
        }
        if (preset.isKernel() != kernel) {
            throw new InstallException(preset.summary().name() + (kernel ? " is a program, not a kernel" : " is a kernel; install it from the Kernel tab"));
        }
        int level = data(player).tier().level();
        int required = requiredLevel(preset);
        if (level < required) {
            throw new InstallException(preset.summary().name() + " requires gateway LV" + required + " (you are LV" + level + ")");
        }
        return preset;
    }

    private record TimedModifier(Holder<Attribute> attribute, Identifier id, long expiresAt) {
    }

    // endregion
}
