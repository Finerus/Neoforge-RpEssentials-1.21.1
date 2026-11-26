package net.rp.rpessentials.commands;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.moderation.ReportManager;
import net.rp.rpessentials.moderation.ReportService;


/**
 * /rp report &lt;player&gt; &lt;reason&gt;  (players)
 * /rpessentials reports list|info|claim|close|reopen|delete|tp  (staff)
 */
public final class RpReportCommands {

    private RpReportCommands() {}

    /** Player side, added under /rp. */
    public static LiteralArgumentBuilder<CommandSourceStack> buildReport() {
        return Commands.literal("report")
                .requires(src -> src.getEntity() instanceof ServerPlayer && RpPerms.has(src, RpPerm.PLAYER_REPORT))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(RpReportCommands::report)));
    }

    /** Staff side, added under /rpessentials (which already requires staff). */
    public static LiteralArgumentBuilder<CommandSourceStack> buildReports() {
        return Commands.literal("reports")
                .requires(RpPerms.cmd(RpPerm.REPORTS))
                .then(Commands.literal("list").executes(ctx -> {
                    for (Component line : ReportService.listLines(viewer(ctx))) ctx.getSource().sendSystemMessage(line);
                    return 1;
                }))
                .then(Commands.literal("info").then(id().executes(ctx -> {
                    ReportManager.Report r = ReportManager.get(IntegerArgumentType.getInteger(ctx, "id"));
                    if (r == null) {
                        ReportService.reportNotFound(ctx.getSource().getServer(), viewer(ctx),
                                IntegerArgumentType.getInteger(ctx, "id"));
                        return 0;
                    }
                    for (Component line : ReportService.infoLines(viewer(ctx), r)) ctx.getSource().sendSystemMessage(line);
                    return 1;
                })))
                .then(Commands.literal("claim").then(id().executes(ctx ->
                        ReportService.claim(ctx.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)))
                .then(Commands.literal("close").then(id()
                        .executes(ctx -> close(ctx, null))
                        .then(Commands.argument("note", StringArgumentType.greedyString())
                                .executes(ctx -> close(ctx, StringArgumentType.getString(ctx, "note"))))))
                .then(Commands.literal("reopen").then(id().executes(ctx ->
                        ReportService.reopen(ctx.getSource().getServer(), viewer(ctx),
                                IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)))
                .then(Commands.literal("delete").then(id().executes(ctx ->
                        ReportService.delete(ctx.getSource().getServer(), viewer(ctx),
                                IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)))
                .then(Commands.literal("tp").then(id().executes(ctx ->
                        ReportService.teleport(ctx.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> id() {
        return Commands.argument("id", IntegerArgumentType.integer(1));
    }

    private static int report(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer reporter = ctx.getSource().getPlayerOrException();
        GameProfile target = KnownPlayers.get(ctx, "player");
        return ReportService.submit(reporter, target.getId(), target.getName(),
                StringArgumentType.getString(ctx, "reason"));
    }

    private static int close(CommandContext<CommandSourceStack> ctx, String note) {
        return ReportService.close(ctx.getSource().getServer(), viewer(ctx),
                IntegerArgumentType.getInteger(ctx, "id"), note) ? 1 : 0;
    }

    /** The staff member running the command, or null for the console. */
    private static ServerPlayer viewer(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() instanceof ServerPlayer p ? p : null;
    }
}
