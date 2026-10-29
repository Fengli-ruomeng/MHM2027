package com.mhm.mhm2027.terminal;

import java.util.ArrayDeque;
import java.util.ArrayList;
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

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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

    /** A firmware flash in progress: the Buffer it had, and whether it started out Null. */
    private record Flash(double savedBufferMb, boolean wasNull) {
    }

    private final UUID owner;
    private final MinecraftServer server;
    private Kernel kernel;
    private final List<Program> programs = new ArrayList<>();
    private final Deque<String> log = new ArrayDeque<>();
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
    private long flashHeldAt;
    private long kernelReadyAt;
    private long overloadLockedUntil;

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
        log(name, "stopped");
        withPlayer(player -> {
            updateUsage(player);
            refreshFlaws(player, true);
        });
        return true;
    }

    /** A voluntary reboot: kicks every program out, with a few seconds of mild debuff. */
    public void reboot() {
        stopAllSilently();
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
        if (flash != null) {
            throw new InstallException("The gateway is flashing firmware");
        }
        if (isRebooting()) {
            throw new InstallException("The terminal is rebooting (" + lockReason + ")");
        }
        Kernel next = loadKernel(player, id);

        stopAllSilently();
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
        stopAllSilently();
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

    /** The station calls this every tick it keeps the flash going. */
    public void holdFlash() {
        flashHeldAt = now;
    }

    public boolean isFlashing() {
        return flash != null;
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
        Sessions.endAllOn(owner, "died");
        Sessions.endAllBy(owner, "died");
        stopAllSilently();
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

    private void stopAllSilently() {
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
        lockReason = reason;
        Sessions.endAllOn(owner, reason);
        Sessions.endAllBy(owner, reason);
    }

    // endregion

    // region Tick

    void tick(ServerPlayer player) {
        now++;
        boolean bodyChanged = tickModifiers(player);
        recoveringTops.removeIf(entry -> --entry[1] <= 0);
        resisting.removeIf(entry -> --entry[1] <= 0);

        if (flash != null) {
            if (now - flashHeldAt > FLASH_HOLD_TICKS) {
                endFlash(player);
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
            panic(player, "out of TOPS");
            return;
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
            log(program.name(), "crashed: overheated");
        }
        int lockSeconds = Config.OVERLOAD_LOCK_SECONDS.get();
        overloadLockedUntil = now + lockSeconds * 20L;
        recheckFlaws = true;
        player.sendOverlayMessage(Component.translatable("mhm2027.terminal.overheated", lockSeconds).withStyle(ChatFormatting.RED));
    }

    /**
     * Kernel panic: every program crashes, each leaving a quarter of its RAM as Leak, and
     * nothing can start for 5 seconds. Afterwards on_panic decides what restarts.
     */
    private void panic(ServerPlayer player, String reason) {
        GatewayData data = data(player);
        double leak = programs.stream().mapToDouble(program -> program.ramMb() * PANIC_LEAK_SHARE).sum();
        data.setLeakMb(data.leakMb() + leak);
        stopAllSilently();
        lock("kernel panic", PANIC_LOCK_TICKS);
        pendingPanic = reason;
        applyDowntimeDebuff(player, PANIC_LOCK_TICKS, false);
        updateUsage(player);
        refreshFlaws(player, false);
        log("kernel", "PANIC: " + reason);
        player.sendSystemMessage(Component.translatable("mhm2027.terminal.panic", reason).withStyle(ChatFormatting.RED));
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
        if (self == null || flash != null) {
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

    /** The TOPS this terminal defends an access attempt with: the kernel reserve, or none mid-flash. */
    int defenceTops() {
        return flash != null ? 0 : kernel.topsReserve();
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
        self.sendOverlayMessage(Component.translatable("mhm2027.access.session_opened", address).withStyle(ChatFormatting.RED));
        if (flash != null) {
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
    }

    void notifyCrash(Program program) {
        withPlayer(player -> player.sendOverlayMessage(
                Component.translatable("mhm2027.terminal.crash", program.name()).withStyle(ChatFormatting.RED)));
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
