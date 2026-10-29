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
        if (isRebooting()) {
            throw new InstallException("The terminal is rebooting (" + lockReason + ")");
        }

        Program program;
        if (id.startsWith(PresetRegistry.PREFIX)) {
            PresetRegistry.Preset preset = installedPreset(player, id, false);
            // Locked code runs at the preset's privilege, so install checks use that level too.
            program = new Program(this, id, preset.chunk(), preset.source(), Math.max(tier.level(), preset.privilegeLevel()),
                    tier.tops(), preset.privilegeLevel());
        } else {
            program = new Program(this, id, id, ScriptStore.read(server, owner, id), tier.level(), tier.tops(), -1);
        }
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
        if (program.needsSlot() && slotsInUse() >= tier.standardSlots()) {
            throw new InstallException(program.name() + " changes your body and needs a standard slot; all "
                    + tier.standardSlots() + " are in use");
        }

        programs.add(program);
        program.begin();
        updateUsage(player);
        refreshFlaws(player, true);
        log(program.name(), "started (" + program.type().id() + ", " + GatewayData.formatMb(program.ramMb()) + ", "
                + program.tops() + " TOPS)");
        return program;
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
        if (isRebooting()) {
            throw new InstallException("The terminal is rebooting (" + lockReason + ")");
        }
        Kernel next = loadKernel(player, id);

        stopAllSilently();
        kernel = next;
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

    /** Death: the body is gone, so programs and timed effects end. The kernel carries over. */
    void onDeath() {
        Sessions.endAllOn(owner, "died");
        Sessions.endAllBy(owner, "died");
        stopAllSilently();
        modifiers.clear();
        cooldowns.clear();
        lockedUntil = 0;
        pendingPanic = null;
        recheckFlaws = true;
    }

    /** Logout: take every timed modifier back off the player. */
    void discard(ServerPlayer player) {
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
        updateUsage(player);
        refreshFlawsIfDue(player, bodyChanged);
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
        if (self == null) {
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

    private int slotsInUse() {
        return (int) programs.stream().filter(Program::needsSlot).count();
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
        if (level < preset.level()) {
            throw new InstallException(preset.summary().name() + " requires gateway LV" + preset.level() + " (you are LV" + level + ")");
        }
        return preset;
    }

    private record TimedModifier(Holder<Attribute> attribute, Identifier id, long expiresAt) {
    }

    // endregion
}
