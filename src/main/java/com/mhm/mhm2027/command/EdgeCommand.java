package com.mhm.mhm2027.command;

import static com.mhm.mhm2027.gateway.GatewayData.formatMb;

import java.util.List;
import java.util.Locale;
import java.util.function.ObjDoubleConsumer;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.GatewayTier;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.terminal.FlawRules;
import com.mhm.mhm2027.terminal.InstallException;
import com.mhm.mhm2027.terminal.KernelSettings;
import com.mhm.mhm2027.terminal.Kernel;
import com.mhm.mhm2027.terminal.Program;
import com.mhm.mhm2027.terminal.ScriptStore;
import com.mhm.mhm2027.terminal.Terminal;
import com.mhm.mhm2027.terminal.Terminals;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Admin and testing commands, all op-only. Players manage their terminal from the control
 * panel (G by default); these act on the op's own terminal unless a player is named.
 *
 * /edge                              your gateway status
 * /edge status [player]
 * /edge scripts                      scripts in your storage folder
 * /edge run|stop <name>              start or stop a program
 * /edge ps                           running programs and what they use
 * /edge log                          the last lines of the terminal log
 * /edge kernel [install <name>]      show or swap the kernel ("Baseline" is built in)
 * /edge flaws                        static and dynamic flaws, including ones close to firing
 * /edge reboot                       stop everything, with a short debuff
 * /edge set level|buffer|leak|heat <players> <value>   (op, for testing)
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public class EdgeCommand {
    private static final int LOG_LINES_SHOWN = 12;

    private static final SuggestionProvider<CommandSourceStack> SCRIPTS = (ctx, builder) -> {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            return SharedSuggestionProvider.suggest(ScriptStore.list(ctx.getSource().getServer(), player.getUUID()).stream()
                    .map(ScriptStore.Entry::name), builder);
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> RUNNING = (ctx, builder) -> {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            return SharedSuggestionProvider.suggest(Terminals.get(player).programs().stream().map(Program::name), builder);
        }
        return builder.buildFuture();
    };

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Players use the control panel; the whole command tree is for admins and testing.
        dispatcher.register(Commands.literal("edge")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> showStatus(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(ctx -> showStatus(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showStatus(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("scripts").executes(ctx -> listScripts(ctx.getSource())))
                .then(Commands.literal("run")
                        .then(Commands.argument("script", StringArgumentType.word()).suggests(SCRIPTS)
                                .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "script")))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("program", StringArgumentType.word()).suggests(RUNNING)
                                .executes(ctx -> stop(ctx.getSource(), StringArgumentType.getString(ctx, "program")))))
                .then(Commands.literal("ps").executes(ctx -> showPrograms(ctx.getSource())))
                .then(Commands.literal("log").executes(ctx -> showLog(ctx.getSource())))
                .then(Commands.literal("kernel")
                        .executes(ctx -> showKernel(ctx.getSource()))
                        .then(Commands.literal("install")
                                .then(Commands.argument("script", StringArgumentType.word()).suggests(SCRIPTS)
                                        .executes(ctx -> installKernel(ctx.getSource(), StringArgumentType.getString(ctx, "script"))))))
                .then(Commands.literal("flaws").executes(ctx -> showFlaws(ctx.getSource())))
                .then(Commands.literal("reboot").executes(ctx -> reboot(ctx.getSource())))
                .then(Commands.literal("set")
                        .then(setter("level", IntegerArgumentType.integer(GatewayTier.MIN_LEVEL, GatewayTier.MAX_LEVEL),
                                (data, value) -> data.setLevel((int) value)))
                        .then(setter("buffer", DoubleArgumentType.doubleArg(0), GatewayData::setBufferMb))
                        .then(setter("leak", DoubleArgumentType.doubleArg(0), GatewayData::setLeakMb))
                        .then(setter("heat", DoubleArgumentType.doubleArg(0, GatewayData.MAX_HEAT), GatewayData::setHeat)))
                .then(mobKernel()));
    }

    // region Status

    private static int showStatus(CommandSourceStack source, ServerPlayer player) {
        GatewayData data = player.getData(ModAttachments.GATEWAY);
        GatewayTier tier = data.tier();

        MutableComponent out = Component.translatable("mhm2027.terminal.header", tier.label(), tier.band())
                .withStyle(ChatFormatting.AQUA);
        if (tier.isNull()) {
            out.append("\n").append(Component.translatable("mhm2027.terminal.null").withStyle(ChatFormatting.GRAY));
            source.sendSuccess(() -> out, false);
            return 1;
        }

        Terminal terminal = Terminals.get(player);
        out.append("\n").append(Component.translatable("mhm2027.terminal.ram",
                formatMb(data.freeRamMb()), formatMb(tier.ramMb()), formatMb(data.programRamMb()), formatMb(data.leakMb()))
                .withStyle(data.isOutOfMemory() ? ChatFormatting.RED : ChatFormatting.WHITE));
        out.append("\n").append(Component.translatable("mhm2027.terminal.tops", data.freeTops(), tier.tops()));
        out.append("\n").append(Component.translatable("mhm2027.terminal.buffer",
                formatMb(data.bufferMb()), formatMb(tier.bufferMb())));
        out.append("\n").append(Component.translatable("mhm2027.terminal.heat", formatNumber(data.heat())));
        out.append("\n").append(Component.translatable("mhm2027.terminal.kernel_line",
                terminal.kernel().name(), terminal.programs().size()));
        out.append("\n").append(Component.translatable("mhm2027.terminal.slots",
                tier.standardSlots(), tier.overloadSlots(), tier.hookPoints()).withStyle(ChatFormatting.GRAY));
        if (terminal.isRebooting()) {
            out.append("\n").append(Component.translatable("mhm2027.terminal.rebooting", terminal.lockReason(), terminal.lockSecondsLeft())
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (data.isOutOfMemory()) {
            out.append("\n").append(Component.translatable("mhm2027.terminal.oom").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        source.sendSuccess(() -> out, false);
        return 1;
    }

    // endregion

    // region Programs and kernel

    private static int listScripts(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<String> scripts = ScriptStore.list(source.getServer(), player.getUUID()).stream()
                .map(entry -> entry.name() + (entry.kind() == ScriptStore.Kind.KERNEL ? " (kernel)" : ""))
                .toList();
        MutableComponent out = Component.translatable("mhm2027.terminal.scripts_header",
                ScriptStore.folder(source.getServer(), player.getUUID()).toString()).withStyle(ChatFormatting.AQUA);
        out.append("\n").append(scripts.isEmpty()
                ? Component.translatable("mhm2027.terminal.none").withStyle(ChatFormatting.GRAY)
                : Component.literal(String.join(", ", scripts)));
        source.sendSuccess(() -> out, false);
        return scripts.size();
    }

    private static int run(CommandSourceStack source, String script) throws CommandSyntaxException {
        Terminal terminal = Terminals.get(source.getPlayerOrException());
        try {
            Program program = terminal.start(script);
            source.sendSuccess(() -> Component.translatable("mhm2027.terminal.started", program.name(), program.type().id(),
                    formatMb(program.ramMb()), program.tops()), false);
            return 1;
        } catch (InstallException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int stop(CommandSourceStack source, String name) throws CommandSyntaxException {
        Terminal terminal = Terminals.get(source.getPlayerOrException());
        if (!terminal.stop(name)) {
            source.sendFailure(Component.translatable("mhm2027.terminal.not_running", name));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("mhm2027.terminal.stopped", name), false);
        return 1;
    }

    private static int showPrograms(CommandSourceStack source) throws CommandSyntaxException {
        Terminal terminal = Terminals.get(source.getPlayerOrException());
        Kernel kernel = terminal.kernel();
        MutableComponent out = Component.translatable("mhm2027.terminal.ps_header", terminal.programs().size()).withStyle(ChatFormatting.AQUA);
        out.append("\n").append(Component.translatable("mhm2027.terminal.ps_kernel", kernel.name(),
                formatMb(kernel.ramMb()), kernel.topsReserve()).withStyle(ChatFormatting.GRAY));
        for (Program program : terminal.programs()) {
            out.append("\n").append(Component.translatable("mhm2027.terminal.ps_line", program.name(), program.type().id(),
                    formatMb(program.ramMb()), program.tops(), formatNumber(program.averageTops()), formatNumber(program.peakTops()),
                    program.state(terminal.now())));
        }
        source.sendSuccess(() -> out, false);
        return terminal.programs().size();
    }

    private static int showLog(CommandSourceStack source) throws CommandSyntaxException {
        List<String> log = Terminals.get(source.getPlayerOrException()).log();
        MutableComponent out = Component.translatable("mhm2027.terminal.log_header").withStyle(ChatFormatting.AQUA);
        if (log.isEmpty()) {
            out.append("\n").append(Component.translatable("mhm2027.terminal.none").withStyle(ChatFormatting.GRAY));
        }
        for (String line : log.subList(Math.max(0, log.size() - LOG_LINES_SHOWN), log.size())) {
            out.append("\n").append(Component.literal(line));
        }
        source.sendSuccess(() -> out, false);
        return log.size();
    }

    private static int showKernel(CommandSourceStack source) throws CommandSyntaxException {
        Kernel kernel = Terminals.get(source.getPlayerOrException()).kernel();
        var settings = kernel.settings();
        MutableComponent out = Component.translatable("mhm2027.terminal.kernel_header", kernel.name()).withStyle(ChatFormatting.AQUA);
        out.append("\n").append(Component.translatable("mhm2027.terminal.kernel_settings",
                settings.schedule(), settings.gc(), settings.verify(), settings.rateLimit()));
        out.append("\n").append(Component.translatable("mhm2027.terminal.kernel_hooks",
                kernel.hookNames().isEmpty() ? "-" : String.join(", ", kernel.hookNames())));
        out.append("\n").append(Component.translatable("mhm2027.terminal.kernel_cost", formatMb(kernel.ramMb()), kernel.topsReserve())
                .withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    private static int installKernel(CommandSourceStack source, String script) throws CommandSyntaxException {
        Terminal terminal = Terminals.get(source.getPlayerOrException());
        try {
            Kernel kernel = terminal.installKernel(script);
            source.sendSuccess(() -> Component.translatable("mhm2027.terminal.kernel_installed", kernel.name()), false);
            return 1;
        } catch (InstallException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int showFlaws(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.getData(ModAttachments.GATEWAY).tier().isNull()) {
            source.sendFailure(Component.translatable("mhm2027.terminal.null"));
            return 0;
        }
        FlawRules.Profile profile = Terminals.get(player).flawProfile();
        MutableComponent out = Component.translatable("mhm2027.terminal.flaws_header").withStyle(ChatFormatting.AQUA);
        if (profile.active().isEmpty() && profile.approaching().isEmpty()) {
            out.append("\n").append(Component.translatable("mhm2027.terminal.none").withStyle(ChatFormatting.GRAY));
        }
        for (FlawRules.Hit hit : profile.active()) {
            out.append("\n").append(Component.translatable("mhm2027.terminal.flaw_line", hit.className(),
                    Component.translatable("mhm2027.flaw." + hit.severity().id()), hit.cause()).withStyle(flawColor(hit.severity().id())));
        }
        for (FlawRules.Approach approach : profile.approaching()) {
            out.append("\n").append(Component.translatable("mhm2027.terminal.flaw_close", approach.className(),
                    Component.translatable("mhm2027.flaw." + approach.severity().id()), approach.cause()).withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> out, false);
        return profile.active().size();
    }

    private static ChatFormatting flawColor(String severity) {
        return switch (severity) {
            case "critical" -> ChatFormatting.RED;
            case "major" -> ChatFormatting.GOLD;
            case "minor" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GRAY;
        };
    }

    private static int reboot(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.getData(ModAttachments.GATEWAY).tier().isNull()) {
            source.sendFailure(Component.translatable("mhm2027.terminal.null"));
            return 0;
        }
        Terminals.get(player).reboot();
        source.sendSuccess(() -> Component.translatable("mhm2027.terminal.rebooted"), false);
        return 1;
    }

    // endregion

    // region Testing

    /** Works on players and mobs, so admins can give a mob a gateway to test against. */
    private static LiteralArgumentBuilder<CommandSourceStack> setter(String name, ArgumentType<? extends Number> type, ObjDoubleConsumer<GatewayData> apply) {
        return Commands.literal(name)
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("value", type)
                                .executes(ctx -> set(ctx, name, apply))));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, String name, ObjDoubleConsumer<GatewayData> apply) throws CommandSyntaxException {
        List<LivingEntity> targets = livingTargets(ctx);
        double value = ctx.getArgument("value", Number.class).doubleValue();
        for (LivingEntity target : targets) {
            GatewayData data = target.getData(ModAttachments.GATEWAY);
            apply.accept(data, value);
            data.consumeDirty();
            if (target instanceof ServerPlayer player) {
                player.syncData(ModAttachments.GATEWAY);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("mhm2027.command.set", name, formatNumber(value), targets.size()), true);
        return targets.size();
    }

    /** /edge mobkernel: a mob's native kernel settings, which decide its static flaws. */
    private static LiteralArgumentBuilder<CommandSourceStack> mobKernel() {
        return Commands.literal("mobkernel")
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("schedule", StringArgumentType.word()).suggests(choices(KernelSettings.SCHEDULES))
                                .then(Commands.argument("gc", StringArgumentType.word()).suggests(choices(KernelSettings.GC_MODES))
                                        .then(Commands.argument("verify", StringArgumentType.word()).suggests(choices(KernelSettings.VERIFY_MODES))
                                                .then(Commands.argument("rate_limit", IntegerArgumentType.integer(
                                                                KernelSettings.MIN_RATE_LIMIT, KernelSettings.MAX_RATE_LIMIT))
                                                        .executes(EdgeCommand::setMobKernel))))));
    }

    private static int setMobKernel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        KernelSettings settings;
        try {
            settings = KernelSettings.of(StringArgumentType.getString(ctx, "schedule"), StringArgumentType.getString(ctx, "gc"),
                    StringArgumentType.getString(ctx, "verify"), IntegerArgumentType.getInteger(ctx, "rate_limit"));
        } catch (InstallException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        List<LivingEntity> mobs = livingTargets(ctx).stream().filter(entity -> !(entity instanceof Player)).toList();
        for (LivingEntity mob : mobs) {
            mob.setData(ModAttachments.MOB_KERNEL, settings);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("mhm2027.command.mob_kernel", mobs.size()), true);
        return mobs.size();
    }

    private static SuggestionProvider<CommandSourceStack> choices(List<String> options) {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(options, builder);
    }

    private static List<LivingEntity> livingTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EntityArgument.getEntities(ctx, "targets").stream()
                .filter(entity -> entity instanceof LivingEntity)
                .map(entity -> (LivingEntity) entity)
                .toList();
    }

    // endregion

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
