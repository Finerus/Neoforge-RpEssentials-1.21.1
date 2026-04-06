package net.rp.rpessentials.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.ColorHelper;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.moderation.*;
import net.rp.rpessentials.profession.LicenseManager;

import java.util.*;

public class RpModerationCommands {

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> SUGGEST_WARNED_PLAYERS =
            (ctx, builder) -> {
                WarnManager.getAll().stream().map(w -> w.targetName).distinct().sorted().forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> SUGGEST_WARN_IDS =
            (ctx, builder) -> {
                String name;
                try { name = StringArgumentType.getString(ctx, "player"); } catch (Exception e) { return builder.buildFuture(); }
                MinecraftServer server = ctx.getSource().getServer();
                ServerPlayer online = server.getPlayerList().getPlayerByName(name);
                UUID uuid = online != null ? online.getUUID() : LastConnectionManager.findUUIDByName(name);
                if (uuid == null) return builder.buildFuture();
                WarnManager.getWarns(uuid).forEach(w -> builder.suggest(w.id, Component.literal("#" + w.id + " — " + w.reason)));
                return builder.buildFuture();
            };

    // =========================================================================
    // WARN
    // =========================================================================

    public static LiteralArgumentBuilder<CommandSourceStack> buildWarn() {
        var warnNode = Commands.literal("warn")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()));

        warnNode.then(Commands.literal("add")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(RpModerationCommands::warnAdd))));

        warnNode.then(Commands.literal("temp")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(RpModerationCommands::warnTemp)))));

        warnNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(SUGGEST_WARNED_PLAYERS)
                        .then(Commands.argument("warnId", StringArgumentType.word())
                                .suggests(SUGGEST_WARN_IDS)
                                .executes(RpModerationCommands::warnRemove))));

        warnNode.then(Commands.literal("list")
                .executes(RpModerationCommands::warnListAll)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpModerationCommands::warnListPlayer)));

        warnNode.then(Commands.literal("info")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(SUGGEST_WARNED_PLAYERS)
                        .executes(ctx -> {
                            if (!warnSystemCheck(ctx)) return 0;
                            String name = StringArgumentType.getString(ctx, "player");
                            MinecraftServer srv = ctx.getSource().getServer();
                            ServerPlayer op = srv.getPlayerList().getPlayerByName(name);
                            UUID uuid = op != null ? op.getUUID() : LastConnectionManager.findUUIDByName(name);
                            if (uuid == null) {
                                ctx.getSource().sendFailure(Component.literal("§c[RPE] Player not found: " + name));
                                return 0;
                            }
                            return displayWarnList(ctx, uuid, name, true);
                        })
                        .then(Commands.argument("warnId", StringArgumentType.word())
                                .suggests(SUGGEST_WARN_IDS)
                                .executes(RpModerationCommands::warnInfo))));

        warnNode.then(Commands.literal("clear")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpModerationCommands::warnClear)));

        warnNode.then(Commands.literal("purge").executes(RpModerationCommands::warnPurge));

        return warnNode;
    }

    public static void registerMyWarn(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mywarn")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(RpModerationCommands::myWarn));
    }

    // =========================================================================
    // MUTE
    // =========================================================================

    public static LiteralArgumentBuilder<CommandSourceStack> buildMute() {
        var muteNode = Commands.literal("mute")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()));

        muteNode.then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> executeMute(ctx, -1, "No reason specified"))
                .then(Commands.argument("minutes", IntegerArgumentType.integer(0))
                        .executes(ctx -> executeMute(ctx, IntegerArgumentType.getInteger(ctx, "minutes"), "No reason specified"))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeMute(ctx, IntegerArgumentType.getInteger(ctx, "minutes"),
                                        StringArgumentType.getString(ctx, "reason"))))));

        return muteNode;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildUnmute() {
        return Commands.literal("unmute")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            MuteManager.unmute(target.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    MessagesConfig.get(MessagesConfig.MUTE_STAFF_REMOVED, "player", target.getName().getString())), true);
                            target.sendSystemMessage(Component.literal(MessagesConfig.get(MessagesConfig.MUTE_EXPIRED)));
                            return 1;
                        }));
    }

    // =========================================================================
    // NOTE
    // =========================================================================

    public static LiteralArgumentBuilder<CommandSourceStack> buildNote() {
        var noteNode = Commands.literal("note")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()));

        noteNode.then(Commands.literal("add")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    String text = StringArgumentType.getString(ctx, "text");
                                    ServerPlayer author = ctx.getSource().getPlayer();
                                    String authorName = author != null ? author.getName().getString() : "Console";
                                    String authorUUID = author != null ? author.getUUID().toString() : "console";
                                    int id = NoteManager.addNote(target.getUUID(), authorName, authorUUID, text);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            MessagesConfig.get(MessagesConfig.NOTE_ADDED,
                                                    "player", target.getName().getString(), "id", String.valueOf(id))), false);
                                    return 1;
                                }))));

        noteNode.then(Commands.literal("list")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            List<NoteManager.NoteEntry> notes = NoteManager.getNotes(target.getUUID());
                            if (notes.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        MessagesConfig.get(MessagesConfig.NOTE_NONE, "player", target.getName().getString())), false);
                                return 1;
                            }
                            StringBuilder sb = new StringBuilder();
                            sb.append(MessagesConfig.get(MessagesConfig.NOTE_LIST_HEADER, "player", target.getName().getString())).append("\n");
                            for (NoteManager.NoteEntry n : notes) {
                                sb.append("§6║ §e#").append(n.id).append(" §8[").append(n.timestamp).append("§8] §7by §f").append(n.authorName)
                                        .append("\n§6║  §f").append(n.text).append("\n");
                            }
                            sb.append("§6╚═══════════════════════════════════╝");
                            String msg = sb.toString();
                            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                            return 1;
                        })));

        noteNode.then(Commands.literal("remove")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                    boolean removed = NoteManager.removeNote(target.getUUID(), id);
                                    if (!removed) {
                                        ctx.getSource().sendFailure(Component.literal("§c[NOTE] Note #" + id + " not found."));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            MessagesConfig.get(MessagesConfig.NOTE_REMOVED,
                                                    "player", target.getName().getString(), "id", String.valueOf(id))), false);
                                    return 1;
                                }))));

        noteNode.then(Commands.literal("clear")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            NoteManager.clearNotes(target.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§a[NOTE] All notes cleared for §e" + target.getName().getString()), false);
                            return 1;
                        })));

        return noteNode;
    }

    // =========================================================================
    // DEATH RP
    // =========================================================================

    public static LiteralArgumentBuilder<CommandSourceStack> buildDeathRp() {
        var deathRpNode = Commands.literal("deathrp")
                .requires(source -> source.hasPermission(2));

        deathRpNode.then(Commands.literal("history")
                .executes(ctx -> showDeathHistory(ctx, null))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> showDeathHistory(ctx, EntityArgument.getPlayer(ctx, "player")))));

        deathRpNode.then(Commands.literal("enable")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(RpModerationCommands::deathRpSetGlobal)));

        deathRpNode.then(Commands.literal("player")
                .then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.literal("enable")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(RpModerationCommands::deathRpSetPlayer)))
                        .then(Commands.literal("reset")
                                .executes(RpModerationCommands::deathRpResetPlayer))));

        deathRpNode.then(Commands.literal("status").executes(RpModerationCommands::deathRpStatus));

        return deathRpNode;
    }

    // =========================================================================
    // INSPECT
    // =========================================================================

    public static LiteralArgumentBuilder<CommandSourceStack> buildInspect() {
        return Commands.literal("inspect")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            ctx.getSource().getServer().getPlayerList().getPlayers()
                                    .forEach(p -> builder.suggest(p.getName().getString()));
                            LastConnectionManager.getAllSortedByLogin().stream()
                                    .map(e -> e.getValue().mcName).filter(Objects::nonNull).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(RpModerationCommands::inspectPlayer));
    }

    // =========================================================================
    // WARN HANDLERS
    // =========================================================================

    private static int warnAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String reason = StringArgumentType.getString(ctx, "reason");
        MinecraftServer server = ctx.getSource().getServer();
        UUID issuerUUID = null;
        String issuerName = "Console";
        if (ctx.getSource().getEntity() instanceof ServerPlayer issuer) {
            issuerUUID = issuer.getUUID();
            issuerName = issuer.getName().getString();
        }
        String warnId = WarnManager.addWarn(target.getUUID(), target.getName().getString(),
                issuerUUID, issuerName, reason, null);
        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.WARN_RECEIVED_PERM, "id", warnId, "reason", reason)));
        try {
            String fmt = ModerationConfig.WARN_ADDED_BROADCAST_FORMAT.get();
            if (!fmt.isEmpty()) {
                RpStaffCommands.broadcastToStaff(server, fmt.replace("{id}", warnId).replace("{staff}", issuerName)
                        .replace("{player}", target.getName().getString()).replace("{reason}", reason).replace("{expiry}", "Permanent"));
            }
        } catch (IllegalStateException ignored) {}
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[RpEssentials] Warn §e#" + warnId + "§a added for §e" + target.getName().getString() + "§a."), false);
        autoMuteIfNeeded(target, server);
        return 1;
    }

    private static void autoMuteIfNeeded(ServerPlayer target, MinecraftServer server) {
        try {
            if (ModerationConfig.ENABLE_MUTE_SYSTEM.get() && ModerationConfig.MUTE_AUTO_FROM_WARNS.get()) {
                int threshold = ModerationConfig.MUTE_AUTO_WARN_COUNT.get();
                int activeCount = WarnManager.getActiveWarns(target.getUUID()).size();
                if (activeCount >= threshold && !MuteManager.isMuted(target.getUUID())) {
                    int duration = ModerationConfig.MUTE_AUTO_DURATION_MINUTES.get();
                    MuteManager.mute(target.getUUID(), target.getName().getString(),
                            null, "System", "Auto-mute (" + activeCount + " active warns)", duration);
                    String durationStr = duration == 0 ? "Permanent" : MessagesConfig.formatDuration(duration);
                    Component autoNotify = ColorHelper.parseColors(
                            MessagesConfig.get(MessagesConfig.MUTE_AUTO_NOTIFY,
                                    "player", target.getName().getString(),
                                    "count", String.valueOf(activeCount), "duration", durationStr));
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (RpEssentialsPermissions.isStaff(p)) p.sendSystemMessage(autoNotify);
                    }
                    target.sendSystemMessage(ColorHelper.parseColors(
                            MessagesConfig.get(MessagesConfig.MUTE_RECEIVED,
                                    "reason", "Auto-mute (" + activeCount + " active warns)", "duration", durationStr)));
                }
            }
        } catch (IllegalStateException ignored) {}
    }

    private static int warnTemp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        String reason = StringArgumentType.getString(ctx, "reason");
        MinecraftServer server = ctx.getSource().getServer();
        try {
            int maxDays = ModerationConfig.WARN_MAX_TEMP_DAYS.get();
            if (maxDays > 0 && minutes > maxDays * 1440) {
                ctx.getSource().sendFailure(Component.literal(
                        MessagesConfig.get(MessagesConfig.WARN_MAX_DURATION_EXCEEDED,
                                "maxDays", String.valueOf(maxDays), "maxMinutes", String.valueOf(maxDays * 1440))));
                return 0;
            }
        } catch (IllegalStateException ignored) {}
        UUID issuerUUID = null;
        String issuerName = "Console";
        if (ctx.getSource().getEntity() instanceof ServerPlayer issuer) {
            issuerUUID = issuer.getUUID();
            issuerName = issuer.getName().getString();
        }
        long expiresAt = System.currentTimeMillis() + (long) minutes * 60_000;
        String warnId = WarnManager.addWarn(target.getUUID(), target.getName().getString(),
                issuerUUID, issuerName, reason, expiresAt);
        String durationStr = MessagesConfig.formatDuration(minutes);
        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.WARN_RECEIVED_TEMP, "id", warnId, "reason", reason, "duration", durationStr)));
        try {
            String fmt = ModerationConfig.WARN_ADDED_BROADCAST_FORMAT.get();
            if (!fmt.isEmpty()) RpStaffCommands.broadcastToStaff(server, fmt.replace("{id}", warnId)
                    .replace("{staff}", issuerName).replace("{player}", target.getName().getString())
                    .replace("{reason}", reason).replace("{expiry}", durationStr));
        } catch (IllegalStateException ignored) {}
        String finalDurationStr = durationStr;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[RpEssentials] Temp warn §e#" + warnId + "§a added for §e" + target.getName().getString() +
                        "§a (" + finalDurationStr + "). Reason: §f" + reason), false);
        return 1;
    }

    private static int warnRemove(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;
        String warnId = StringArgumentType.getString(ctx, "warnId");
        MinecraftServer server = ctx.getSource().getServer();
        Optional<WarnManager.WarnEntry> optEntry = WarnManager.getWarnById(warnId);
        if (optEntry.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.WARN_NOT_FOUND, "id", warnId)));
            return 0;
        }
        WarnManager.WarnEntry entry = optEntry.get();
        if (!WarnManager.removeWarn(warnId)) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.WARN_REMOVE_FAILED, "id", warnId)));
            return 0;
        }
        String staffName = ctx.getSource().getEntity() instanceof ServerPlayer issuer ? issuer.getName().getString() : "Console";
        ServerPlayer targetOnline = server.getPlayerList().getPlayer(UUID.fromString(entry.targetUUID));
        if (targetOnline != null) targetOnline.sendSystemMessage(Component.literal(MessagesConfig.get(MessagesConfig.WARN_REMOVED_PLAYER, "id", warnId)));
        try {
            String fmt = ModerationConfig.WARN_REMOVED_BROADCAST_FORMAT.get();
            if (!fmt.isEmpty()) RpStaffCommands.broadcastToStaff(server, fmt.replace("{id}", warnId).replace("{staff}", staffName));
        } catch (IllegalStateException ignored) {}
        ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] Warn §e#" + warnId + "§a removed."), false);
        return 1;
    }

    private static int warnListPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        return displayWarnList(ctx, target.getUUID(), target.getName().getString(), true);
    }

    private static int warnListAll(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;
        var all = WarnManager.getAll();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7[RpEssentials] No registered warn."), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═ All warns (").append(all.size()).append(") ══════════╗\n");
        for (WarnManager.WarnEntry w : all) {
            String status = w.isExpired() ? "§8[EXP]" : (w.isPermanent() ? "§c[PERM]" : "§e[TEMP]");
            sb.append("§6║ ").append(status).append(" §e#").append(w.id)
                    .append(" §7→ §f").append(w.targetName).append(" §7— ").append(w.reason).append("\n");
        }
        sb.append("§6╚═══════════════════════════════════╝");
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int warnInfo(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;
        String warnId = StringArgumentType.getString(ctx, "warnId");
        Optional<WarnManager.WarnEntry> optW = WarnManager.getWarnById(warnId);
        if (optW.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.WARN_NOT_FOUND, "id", warnId)));
            return 0;
        }
        WarnManager.WarnEntry w = optW.get();
        String typeStr = w.isPermanent() ? MessagesConfig.get(MessagesConfig.WARN_TYPE_PERMANENT)
                : (w.isExpired() ? MessagesConfig.get(MessagesConfig.WARN_TYPE_EXPIRED) : MessagesConfig.get(MessagesConfig.WARN_TYPE_TEMPORARY));
        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.WARN_INFO_HEADER, "id", w.id) + "\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_PLAYER_LABEL) + "§e" + w.targetName + " §8(" + w.targetUUID + ")\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_STAFF_LABEL) + "§e" + w.issuerName + "\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_REASON_LABEL) + "§f" + w.reason + "\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_DATE_LABEL) + "§f" + w.getFormattedDate() + "\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_TYPE_LABEL) + typeStr + "\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_EXPIRY_LABEL) + "§f" + w.getFormattedExpiry() + "\n" +
                        "§6╚════════════════════════════════════╝"), false);
        return 1;
    }

    private static int warnClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        int removed = WarnManager.clearWarns(target.getUUID());
        if (removed == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(MessagesConfig.get(MessagesConfig.WARN_LIST_NONE_STAFF, "player", target.getName().getString())), false);
            return 1;
        }
        target.sendSystemMessage(Component.literal(MessagesConfig.get(MessagesConfig.WARN_CLEARED_PLAYER)));
        int finalRemoved = removed;
        ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] §e" + finalRemoved + "§a warn(s) removed for §e" + target.getName().getString() + "§a."), false);
        return 1;
    }

    private static int warnPurge(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;
        int purged = WarnManager.purgeExpiredWarns();
        ctx.getSource().sendSuccess(() -> Component.literal(MessagesConfig.get(MessagesConfig.WARN_PURGE_DONE, "count", String.valueOf(purged))), false);
        return 1;
    }

    private static int myWarn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.COMMAND_PLAYER_ONLY)));
            return 0;
        }
        try {
            if (!ModerationConfig.ENABLE_WARN_SYSTEM.get()) {
                player.sendSystemMessage(Component.literal(MessagesConfig.get(MessagesConfig.WARN_SYSTEM_DISABLED)));
                return 0;
            }
        } catch (IllegalStateException e) { return 0; }
        return displayWarnList(ctx, player.getUUID(), player.getName().getString(), false);
    }

    static int displayWarnList(CommandContext<CommandSourceStack> ctx, UUID uuid, String name, boolean showAll) {
        List<WarnManager.WarnEntry> list = showAll ? WarnManager.getWarns(uuid) : WarnManager.getActiveWarns(uuid);
        if (list.isEmpty()) {
            String msg = showAll ? MessagesConfig.get(MessagesConfig.WARN_LIST_NONE, "player", name)
                    : MessagesConfig.get(MessagesConfig.WARN_LIST_NONE_SELF);
            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
            return 1;
        }
        long activeCount = list.stream().filter(w -> !w.isExpired()).count();
        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.WARN_LIST_HEADER, "player", name, "count", String.valueOf(activeCount))).append("\n");
        for (WarnManager.WarnEntry w : list) {
            String tag = w.isExpired() ? MessagesConfig.get(MessagesConfig.WARN_STATUS_EXPIRED_TAG)
                    : (w.isPermanent() ? MessagesConfig.get(MessagesConfig.WARN_STATUS_PERM_TAG) : MessagesConfig.get(MessagesConfig.WARN_STATUS_TEMP_TAG));
            sb.append("§6║ ").append(tag).append(" §7#").append(w.id)
                    .append(" §8(").append(w.getFormattedDate()).append(")")
                    .append(" §7by §f").append(w.issuerName).append("\n")
                    .append("§6║   §7→ §f").append(w.reason).append(" §8| ").append(w.getFormattedExpiry()).append("\n");
        }
        sb.append("§6╚═══════════════════════════════════╝");
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    // =========================================================================
    // MUTE HANDLER
    // =========================================================================

    private static int executeMute(CommandContext<CommandSourceStack> ctx, int minutes, String reason)
            throws CommandSyntaxException {
        try {
            if (!ModerationConfig.ENABLE_MUTE_SYSTEM.get()) {
                ctx.getSource().sendFailure(Component.literal("§c[MUTE] Mute system is disabled."));
                return 0;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal("§c[MUTE] Config not loaded."));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ServerPlayer staff = ctx.getSource().getPlayer();
        UUID staffUUID = staff != null ? staff.getUUID() : null;
        String staffName = staff != null ? staff.getName().getString() : "Console";
        int duration = minutes < 0 ? 0 : minutes;
        MuteManager.mute(target.getUUID(), target.getName().getString(), staffUUID, staffName, reason, duration);
        String durationStr = duration == 0 ? "Permanent" : MessagesConfig.formatDuration(duration);
        target.sendSystemMessage(ColorHelper.parseColors(
                MessagesConfig.get(MessagesConfig.MUTE_RECEIVED, "reason", reason, "duration", durationStr)));
        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.MUTE_STAFF_ADDED, "player", target.getName().getString(),
                        "duration", durationStr, "reason", reason)), true);
        return 1;
    }

    // =========================================================================
    // DEATH RP HANDLERS
    // =========================================================================

    private static int deathRpSetGlobal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        boolean enabled = BoolArgumentType.getBool(ctx, "value");
        String staffName;
        try { staffName = ctx.getSource().getPlayerOrException().getName().getString(); }
        catch (CommandSyntaxException e) { staffName = "Console"; }
        try {
            if (RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED != null) {
                RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.set(enabled);
                RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.save();
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.DEATHRP_CONFIG_UNAVAILABLE)));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        if (server != null) DeathRPManager.broadcastGlobalToggle(staffName, enabled, server);
        ctx.getSource().sendSuccess(() -> Component.literal(
                enabled ? MessagesConfig.get(MessagesConfig.DEATHRP_GLOBAL_ENABLED) : MessagesConfig.get(MessagesConfig.DEATHRP_GLOBAL_DISABLED)), true);
        return 1;
    }

    private static int deathRpSetPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
        boolean enabled = BoolArgumentType.getBool(ctx, "value");
        DeathRPManager.setOverride(target.getUUID(), enabled);
        DeathRPManager.notifyPlayerToggle(target, enabled);
        ctx.getSource().sendSuccess(() -> Component.literal(
                enabled ? MessagesConfig.get(MessagesConfig.DEATHRP_PLAYER_ENABLED, "player", target.getName().getString())
                        : MessagesConfig.get(MessagesConfig.DEATHRP_PLAYER_DISABLED, "player", target.getName().getString())), true);
        return 1;
    }

    private static int deathRpResetPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
        DeathRPManager.removeOverride(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.DEATHRP_OVERRIDE_RESET, "player", target.getName().getString())), true);
        return 1;
    }

    private static int deathRpStatus(CommandContext<CommandSourceStack> ctx) {
        boolean globalEnabled;
        try { globalEnabled = RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED != null && RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get(); }
        catch (IllegalStateException e) { globalEnabled = false; }
        boolean whitelistRemove;
        try { whitelistRemove = RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE != null && RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE.get(); }
        catch (IllegalStateException e) { whitelistRemove = false; }

        String active = MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_ACTIVE);
        String inactive = MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_INACTIVE);
        String yes = MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_YES);
        String no = MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_NO);
        String unknown = MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_UNKNOWN);

        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_HEADER)).append("\n");
        sb.append("§6╠═══════════════════════════════\n");
        sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_GLOBAL, "value", globalEnabled ? active : inactive)).append("\n");
        sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_WHITELIST, "value", whitelistRemove ? yes : no)).append("\n");
        sb.append("§6╠═══════════════════════════════\n");
        sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_OVERRIDES)).append("\n");

        Map<UUID, Boolean> overrides = DeathRPManager.getAllOverrides();
        MinecraftServer server = ctx.getSource().getServer();
        if (overrides.isEmpty()) {
            sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_NO_OVERRIDES)).append("\n");
        } else {
            for (Map.Entry<UUID, Boolean> entry : overrides.entrySet()) {
                String name = unknown;
                if (server != null) {
                    ServerPlayer onlineP = server.getPlayerList().getPlayer(entry.getKey());
                    if (onlineP != null) name = onlineP.getName().getString();
                    else if (server.getProfileCache() != null) name = server.getProfileCache().get(entry.getKey())
                            .map(com.mojang.authlib.GameProfile::getName).orElse(entry.getKey().toString());
                }
                sb.append("§6║  §e").append(name).append(" §7→ ").append(entry.getValue() ? active : inactive).append("\n");
            }
        }
        sb.append("§6═══════════════════════════════");
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int showDeathHistory(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        List<DeathRPManager.DeathHistoryEntry> history = target != null
                ? DeathRPManager.getHistory(target.getUUID()) : DeathRPManager.getAllHistory();
        String playerLabel = target != null ? target.getName().getString() : "All players";

        if (history.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.DEATHRP_HISTORY_NONE, "player", playerLabel)), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_HISTORY_HEADER, "player", playerLabel)).append("\n");
        for (int i = 0; i < history.size(); i++) {
            DeathRPManager.DeathHistoryEntry e = history.get(i);
            if (target == null) {
                sb.append("§6║ §e#").append(i + 1).append(" §f").append(e.playerName)
                        .append(" §8— ").append(e.timestamp).append(" §7— ").append(e.damageCause).append("\n");
            } else {
                sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_HISTORY_ENTRY,
                        "index", String.valueOf(i + 1), "date", e.timestamp, "cause", e.damageCause)).append("\n");
            }
        }
        sb.append("§6╚═══════════════════════════════════╝");
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // =========================================================================
    // INSPECT HANDLER
    // =========================================================================

    private static int inspectPlayer(CommandContext<CommandSourceStack> ctx) {
        String targetName = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayer online = server.getPlayerList().getPlayerByName(targetName);
        UUID uuid = online != null ? online.getUUID() : LastConnectionManager.findUUIDByName(targetName);
        if (uuid == null) {
            ctx.getSource().sendFailure(Component.literal("§c[Inspect] Player not found: " + targetName));
            return 0;
        }

        List<String> licenses = LicenseManager.getLicenses(uuid);
        List<WarnManager.WarnEntry> activeWarns = WarnManager.getActiveWarns(uuid);
        MuteManager.MuteEntry mute = MuteManager.getEntry(uuid);
        String nickname = net.rp.rpessentials.identity.NicknameManager.getNickname(uuid);
        LastConnectionManager.ConnectionEntry conn = LastConnectionManager.getEntry(uuid);
        List<NoteManager.NoteEntry> notes = NoteManager.getNotes(uuid);
        boolean isMuted = MuteManager.isMuted(uuid);
        boolean isOnline = online != null;

        String role = "§8—";
        if (online != null) {
            try {
                for (String entry : RpEssentialsConfig.ROLES.get()) {
                    String roleId = entry.split(";", 2)[0].trim();
                    if (online.getTags().contains(roleId)) { role = "§f" + roleId; break; }
                }
            } catch (IllegalStateException ignored) {}
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═════ §eInspect: §f").append(targetName).append(" §6═════╗\n");
        sb.append("§6║ §7Status   : ").append(isOnline ? "§a● Online" : "§7○ Offline").append("\n");
        sb.append("§6║ §7Nickname : ").append(nickname != null ? nickname : "§8—").append("\n");
        sb.append("§6║ §7Role     : ").append(role).append("\n");
        sb.append("§6║ §7UUID     : §8").append(uuid).append("\n");
        sb.append("§6╠═══════════════════════════════╣\n");
        sb.append("§6║ §eLicenses (").append(licenses.size()).append(")§7: ");
        if (licenses.isEmpty()) sb.append("§8None");
        else {
            for (int i = 0; i < licenses.size(); i++) {
                String expiry = LicenseManager.getTempExpirationDate(uuid, licenses.get(i));
                sb.append("§f").append(licenses.get(i));
                if (expiry != null) sb.append("§8(RP:").append(expiry).append(")");
                if (i < licenses.size() - 1) sb.append("§7, ");
            }
        }
        sb.append("\n");
        sb.append("§6║ §eWarns (").append(activeWarns.size()).append(" active)§7: ");
        if (activeWarns.isEmpty()) sb.append("§aNone");
        else activeWarns.forEach(w -> sb.append("\n§6║   §c#").append(w.id).append(" §7").append(w.reason));
        sb.append("\n");
        sb.append("§6║ §eMute     : ");
        if (isMuted && mute != null) sb.append("§c").append(mute.getFormattedExpiry()).append(" — §f").append(mute.reason);
        else sb.append("§aNone");
        sb.append("\n");
        sb.append("§6║ §eLast seen : §f")
                .append(conn != null && conn.lastLogin != null ? conn.lastLogin : "§8Unknown").append("\n");
        if (!notes.isEmpty()) {
            sb.append("§6╠═══════════════════════════════╣\n");
            sb.append("§6║ §eNotes (").append(notes.size()).append("):\n");
            notes.forEach(n -> sb.append("§6║  §8#").append(n.id).append(" §7by §f").append(n.authorName)
                    .append("§7: §f").append(n.text).append("\n"));
        }
        sb.append("§6╚═══════════════════════════════╝");
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // =========================================================================
    // UTILS
    // =========================================================================

    private static boolean warnSystemCheck(CommandContext<CommandSourceStack> ctx) {
        try {
            if (!ModerationConfig.ENABLE_WARN_SYSTEM.get()) {
                ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.WARN_SYSTEM_DISABLED_CONFIG)));
                return false;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return false;
        }
        return true;
    }
}
