package com.mhm.mhm2027.command;

import java.util.Collection;
import java.util.Locale;
import java.util.function.ObjDoubleConsumer;

import com.mhm.mhm2027.Mhm2027;
import static com.mhm.mhm2027.gateway.GatewayData.formatMb;

import com.mhm.mhm2027.gateway.GatewayData;
import com.mhm.mhm2027.gateway.GatewayTier;
import com.mhm.mhm2027.gateway.ModAttachments;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The LV0 terminal: read your own gateway diagnostics. A real terminal screen comes later;
 * until then this command is the terminal.
 *
 * /edge                          your own status
 * /edge status [player]          (op for other players)
 * /edge set level|buffer|leak|heat <players> <value>   (op, for testing)
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
public class EdgeCommand {
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("edge")
                .executes(ctx -> showStatus(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(ctx -> showStatus(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> showStatus(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(setter("level", IntegerArgumentType.integer(GatewayTier.MIN_LEVEL, GatewayTier.MAX_LEVEL),
                                (data, value) -> data.setLevel((int) value)))
                        .then(setter("buffer", DoubleArgumentType.doubleArg(0), GatewayData::setBufferMb))
                        .then(setter("leak", DoubleArgumentType.doubleArg(0), GatewayData::setLeakMb))
                        .then(setter("heat", DoubleArgumentType.doubleArg(0, GatewayData.MAX_HEAT), GatewayData::setHeat))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> setter(String name, ArgumentType<? extends Number> type, ObjDoubleConsumer<GatewayData> apply) {
        return Commands.literal(name)
                .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("value", type)
                                .executes(ctx -> set(ctx, name, apply))));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, String name, ObjDoubleConsumer<GatewayData> apply) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        double value = ctx.getArgument("value", Number.class).doubleValue();
        for (ServerPlayer player : players) {
            GatewayData data = player.getData(ModAttachments.GATEWAY);
            apply.accept(data, value);
            data.consumeDirty();
            player.syncData(ModAttachments.GATEWAY);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("mhm2027.command.set", name, formatNumber(value), players.size()), true);
        return players.size();
    }

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

        out.append("\n").append(Component.translatable("mhm2027.terminal.ram",
                formatMb(data.freeRamMb()), formatMb(tier.ramMb()), formatMb(data.leakMb()))
                .withStyle(data.isOutOfMemory() ? ChatFormatting.RED : ChatFormatting.WHITE));
        out.append("\n").append(Component.translatable("mhm2027.terminal.tops", tier.tops()));
        out.append("\n").append(Component.translatable("mhm2027.terminal.buffer",
                formatMb(data.bufferMb()), formatMb(tier.bufferMb())));
        out.append("\n").append(Component.translatable("mhm2027.terminal.heat", formatNumber(data.heat())));
        out.append("\n").append(Component.translatable("mhm2027.terminal.slots",
                tier.standardSlots(), tier.overloadSlots(), tier.hookPoints()).withStyle(ChatFormatting.GRAY));
        if (data.isOutOfMemory()) {
            out.append("\n").append(Component.translatable("mhm2027.terminal.oom").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        source.sendSuccess(() -> out, false);
        return 1;
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
