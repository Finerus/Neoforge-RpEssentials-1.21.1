package net.rp.rpessentials.commands;

import net.rp.rpessentials.moderation.HistoryManager;
import net.rp.rpessentials.lang.Msg;
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
import net.rp.rpessentials.commands.RpEssentialsCommands;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.moderation.*;
import net.rp.rpessentials.profession.LicenseManager;

import java.util.*;

/**
 * Commandes de modération : warn, mute, note, deathrp, inspect.
 */
public class RpModerationCommands {

    // ── Suggest helpers ─────────────────────────────────────────────────────────
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
                .requires(RpEssentialsPermissions::isStaffSource);

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
                            ServerPlayer op = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                            UUID uuid = op != null ? op.getUUID() : LastConnectionManager.findUUIDByName(name);
                            if (uuid == null) { ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_WARN_PLAYER_NOT_FOUND, "player", name)); return 0; }
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
                .requires(RpEssentialsPermissions::isStaffSource);
        muteNode.then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> executeMute(ctx, -1, ServerLang.get(null, MessagesConfig.REASON_NONE)))
                .then(Commands.argument("minutes", IntegerArgumentType.integer(0))
                        .executes(ctx -> executeMute(ctx, IntegerArgumentType.getInteger(ctx, "minutes"), ServerLang.get(null, MessagesConfig.REASON_NONE)))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeMute(ctx, IntegerArgumentType.getInteger(ctx, "minutes"),
                                        StringArgumentType.getString(ctx, "reason"))))));
        return muteNode;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildUnmute() {
        return Commands.literal("unmute")
                .requires(RpEssentialsPermissions::isStaffSource)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            MuteManager.unmute(target.getUUID());
                            HistoryManager.record(target.getUUID(), target.getName().getString(), HistoryManager.UNMUTE, actorName(ctx), "");
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    Msg.get(ctx.getSource(), MessagesConfig.MUTE_STAFF_REMOVED, "player", target.getName().getString())), true);
                            target.sendSystemMessage(Component.literal(ServerLang.get(target, MessagesConfig.MUTE_EXPIRED)));
                            return 1;
                        }));
    }

    // =========================================================================
    // NOTE
    // =========================================================================
    public static LiteralArgumentBuilder<CommandSourceStack> buildNote() {
        var noteNode = Commands.literal("note")
                .requires(RpEssentialsPermissions::isStaffSource);

        noteNode.then(Commands.literal("add")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    String text = StringArgumentType.getString(ctx, "text");
                                    ServerPlayer author = ctx.getSource().getPlayer();
                                    String authorName = author != null ? author.getName().getString() : net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.UI_CONSOLE);
                                    String authorUUID = author != null ? author.getUUID().toString() : "console";
                                    int id = NoteManager.addNote(target.getUUID(), target.getName().getString(), authorName, authorUUID, text);
                                    if (id < 0) {
                                        ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_NOTE_LIMIT,
                                                "player", target.getName().getString()));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            Msg.get(ctx.getSource(), MessagesConfig.NOTE_ADDED,
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
                                        Msg.get(ctx.getSource(), MessagesConfig.NOTE_NONE, "player", target.getName().getString())), false);
                                return 1;
                            }
                            StringBuilder sb = new StringBuilder();
                            sb.append(Msg.get(ctx.getSource(), MessagesConfig.NOTE_LIST_HEADER, "player", target.getName().getString())).append("\n")
                                    .append(RpChatUi.LINE).append("\n");
                            for (NoteManager.NoteEntry n : notes) {
                                sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_NOTE_ENTRY, "id", String.valueOf(n.id),
                                        "date", String.valueOf(n.timestamp), "author", n.authorName, "text", n.text)).append("\n");
                            }
                            sb.append(RpChatUi.LINE);
                            String msg = sb.toString();
                            RpChatUi.sendFormatted(ctx, msg);
                            return 1;
                        })));

        noteNode.then(Commands.literal("remove")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                    boolean removed = NoteManager.removeNote(target.getUUID(), id);
                                    if (!removed) { ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_NOTE_NOT_FOUND, "id", String.valueOf(id))); return 0; }
                                    HistoryManager.record(target.getUUID(), target.getName().getString(), HistoryManager.NOTE_REMOVE, actorName(ctx), "#" + id);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            Msg.get(ctx.getSource(), MessagesConfig.NOTE_REMOVED,
                                                    "player", target.getName().getString(), "id", String.valueOf(id))), false);
                                    return 1;
                                }))));

        noteNode.then(Commands.literal("clear")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            NoteManager.clearNotes(target.getUUID());
                            HistoryManager.record(target.getUUID(), target.getName().getString(), HistoryManager.NOTE_REMOVE, actorName(ctx),
                                    ServerLang.get(null, MessagesConfig.HISTORY_ALL_NOTES));
                            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_NOTE_CLEARED, "player", target.getName().getString()), false);
                            return 1;
                        })));

        return noteNode;
    }

    // =========================================================================
    // DEATH RP
    // =========================================================================
    public static LiteralArgumentBuilder<CommandSourceStack> buildDeathRp() {
        var node = Commands.literal("deathrp").requires(src -> src.hasPermission(2));

        node.then(Commands.literal("history")
                .executes(ctx -> showDeathHistory(ctx, null))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> showDeathHistory(ctx, EntityArgument.getPlayer(ctx, "player")))));

        node.then(Commands.literal("enable")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(RpModerationCommands::deathRpSetGlobal)));

        node.then(Commands.literal("player")
                .then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.literal("enable")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(RpModerationCommands::deathRpSetPlayer)))
                        .then(Commands.literal("reset")
                                .executes(RpModerationCommands::deathRpResetPlayer))));

        node.then(Commands.literal("revive")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(RpManageCommands.SUGGEST_KNOWN)
                        .executes(RpModerationCommands::deathRpRevive)));

        node.then(Commands.literal("status").executes(RpModerationCommands::deathRpStatus));
        return node;
    }

    // =========================================================================
    // INSPECT
    // =========================================================================
    public static LiteralArgumentBuilder<CommandSourceStack> buildInspect() {
        return Commands.literal("inspect")
                .requires(RpEssentialsPermissions::isStaffSource)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Set<String> names = new LinkedHashSet<>();
                            VanishManager.visibleTo(ctx.getSource())
                                    .forEach(p -> names.add(p.getName().getString()));
                            LastConnectionManager.getAllSortedByLogin().stream()
                                    .filter(e -> !VanishManager.canHide(
                                            VanishManager.Aspect.SELECTORS, ctx.getSource(), e.getKey()))
                                    .map(e -> e.getValue().mcName).filter(Objects::nonNull).forEach(names::add);
                            return net.minecraft.commands.SharedSuggestionProvider.suggest(names, builder);
                        })
                        .executes(RpModerationCommands::inspectPlayer));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildAbsence() {
        var node = Commands.literal("absence").requires(RpEssentialsPermissions::isStaffSource);

        node.then(Commands.literal("add")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(RpManageCommands.SUGGEST_KNOWN)
                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
                                .executes(ctx -> absenceAdd(ctx, ""))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> absenceAdd(ctx, StringArgumentType.getString(ctx, "reason")))))));

        node.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            AbsenceManager.getAll().forEach(e -> builder.suggest(e.mcName));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> absenceRemove(ctx))));

        node.then(Commands.literal("list").executes(RpModerationCommands::absenceList));
        return node;
    }

    // =========================================================================
    // ABSENCE
    // =========================================================================
    private static int absenceAdd(CommandContext<CommandSourceStack> ctx, String reason) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        UUID uuid = RpEssentialsCommands.findUUIDByName(server, name);
        if (uuid == null) { ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_ABS_NOT_FOUND, "player", name)); return 0; }
        ServerPlayer staff = ctx.getSource().getPlayer();
        ManageService.Result r = ManageService.execute(server, staff, uuid, ManageService.Action.ABSENCE_ADD,
                reason, IntegerArgumentType.getInteger(ctx, "days"));
        if (r.ok()) ctx.getSource().sendSuccess(() -> Component.literal(r.message()), false);
        else ctx.getSource().sendFailure(Component.literal(r.message()));
        return r.ok() ? 1 : 0;
    }

    private static int absenceRemove(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        UUID uuid = RpEssentialsCommands.findUUIDByName(server, name);
        if (uuid == null) { ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_ABS_NOT_FOUND, "player", name)); return 0; }
        ManageService.Result r = ManageService.execute(server, ctx.getSource().getPlayer(), uuid,
                ManageService.Action.ABSENCE_REMOVE, "", 0);
        if (r.ok()) ctx.getSource().sendSuccess(() -> Component.literal(r.message()), false);
        else ctx.getSource().sendFailure(Component.literal(r.message()));
        return r.ok() ? 1 : 0;
    }

    private static int absenceList(CommandContext<CommandSourceStack> ctx) {
        List<AbsenceManager.AbsenceEntry> all = AbsenceManager.getAll();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_ABS_NONE), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder(Msg.get(ctx.getSource(), MessagesConfig.STAFF_ABS_HEADER,
                "count", String.valueOf(all.size())) + "\n" + RpChatUi.LINE + "\n");
        for (AbsenceManager.AbsenceEntry e : all) {
            String reason = e.reason != null && !e.reason.isEmpty()
                    ? Msg.get(ctx.getSource(), MessagesConfig.STAFF_ABS_REASON, "reason", e.reason) : "";
            sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_ABS_ENTRY,
                    "tag", Msg.get(ctx.getSource(), e.isExpired() ? MessagesConfig.STAFF_ABS_TAG_ENDED : MessagesConfig.STAFF_ABS_TAG_ACTIVE),
                    "player", e.mcName, "end", e.formatEnd(), "reason", reason)).append("\n");
        }
        sb.append(RpChatUi.LINE);
        RpChatUi.sendFormatted(ctx, sb.toString());
        return 1;
    }

    // =========================================================================
    // WARN HANDLERS
    // =========================================================================
    private static int warnAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String reason = StringArgumentType.getString(ctx, "reason");
        MinecraftServer server = ctx.getSource().getServer();
        UUID issuerUUID = null; String issuerName = ServerLang.get(null, MessagesConfig.UI_CONSOLE);
        if (ctx.getSource().getEntity() instanceof ServerPlayer issuer) { issuerUUID = issuer.getUUID(); issuerName = issuer.getName().getString(); }
        String warnId = WarnManager.addWarn(target.getUUID(), target.getName().getString(), issuerUUID, issuerName, reason, null);
        target.sendSystemMessage(Component.literal(ServerLang.get(target, MessagesConfig.WARN_RECEIVED_PERM, "id", warnId, "reason", reason)));
        try {
            String fmt = ModerationConfig.WARN_ADDED_BROADCAST_FORMAT.get();
            if (!fmt.isEmpty()) RpStaffCommands.broadcastToStaff(server, fmt.replace("{id}", warnId).replace("{staff}", issuerName)
                    .replace("{player}", target.getName().getString()).replace("{reason}", reason).replace("{expiry}", Msg.get(ctx.getSource(), MessagesConfig.TIME_PERMANENT)));
        } catch (IllegalStateException ignored) {}
        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_WARN_ADDED, "id", warnId, "player", target.getName().getString()), false);
        autoMuteIfNeeded(target, server);
        return 1;
    }

    private static void autoMuteIfNeeded(ServerPlayer target, MinecraftServer server) {
        try {
            if (ModerationConfig.ENABLE_MUTE_SYSTEM.get() && ModerationConfig.MUTE_AUTO_FROM_WARNS.get()) {
                int threshold = ModerationConfig.MUTE_AUTO_WARN_COUNT.get();
                int count     = WarnManager.getActiveWarns(target.getUUID()).size();
                if (count >= threshold && !MuteManager.isMuted(target.getUUID())) {
                    int duration = ModerationConfig.MUTE_AUTO_DURATION_MINUTES.get();
                    String autoReason = ServerLang.get(null, MessagesConfig.MUTE_AUTO_REASON, "count", String.valueOf(count));
                    MuteManager.mute(target.getUUID(), target.getName().getString(), null, "System", autoReason, duration);
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (!RpEssentialsPermissions.isStaff(p)) continue;
                        String d = duration == 0 ? ServerLang.get(p, MessagesConfig.TIME_PERMANENT) : MessagesConfig.formatDuration(p, duration);
                        p.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseColors(ServerLang.get(p, MessagesConfig.MUTE_AUTO_NOTIFY,
                                "player", target.getName().getString(), "count", String.valueOf(count), "duration", d)));
                    }
                    String td = duration == 0 ? ServerLang.get(target, MessagesConfig.TIME_PERMANENT) : MessagesConfig.formatDuration(target, duration);
                    target.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseColors(
                            ServerLang.get(target, MessagesConfig.MUTE_RECEIVED, "reason", autoReason, "duration", td)));
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
                ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.WARN_MAX_DURATION_EXCEEDED,
                        "maxDays", String.valueOf(maxDays), "maxMinutes", String.valueOf(maxDays * 1440))));
                return 0;
            }
        } catch (IllegalStateException ignored) {}
        UUID issuerUUID = null; String issuerName = ServerLang.get(null, MessagesConfig.UI_CONSOLE);
        if (ctx.getSource().getEntity() instanceof ServerPlayer i) { issuerUUID = i.getUUID(); issuerName = i.getName().getString(); }
        long expiresAt = System.currentTimeMillis() + (long) minutes * 60_000;
        String warnId = WarnManager.addWarn(target.getUUID(), target.getName().getString(), issuerUUID, issuerName, reason, expiresAt);
        String dur = MessagesConfig.formatDuration(ctx.getSource().getPlayer(), minutes);
        target.sendSystemMessage(Component.literal(ServerLang.get(target, MessagesConfig.WARN_RECEIVED_TEMP, "id", warnId, "reason", reason, "duration", dur)));
        try {
            String fmt = ModerationConfig.WARN_ADDED_BROADCAST_FORMAT.get();
            if (!fmt.isEmpty()) RpStaffCommands.broadcastToStaff(server, fmt.replace("{id}", warnId)
                    .replace("{staff}", issuerName).replace("{player}", target.getName().getString()).replace("{reason}", reason).replace("{expiry}", dur));
        } catch (IllegalStateException ignored) {}
        String finalDur = dur;
        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_WARN_TEMP_ADDED, "id", warnId, "player", target.getName().getString(), "duration", finalDur), false);
        return 1;
    }

    private static int warnRemove(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;
        String warnId = StringArgumentType.getString(ctx, "warnId");
        MinecraftServer server = ctx.getSource().getServer();
        Optional<WarnManager.WarnEntry> opt = WarnManager.getWarnById(warnId);
        if (opt.isEmpty()) { ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.WARN_NOT_FOUND, "id", warnId))); return 0; }
        WarnManager.WarnEntry entry = opt.get();
        if (!WarnManager.removeWarn(warnId)) { ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.WARN_REMOVE_FAILED, "id", warnId))); return 0; }
        String staffName = actorName(ctx);
        try {
            HistoryManager.record(UUID.fromString(entry.targetUUID), entry.targetName, HistoryManager.WARN_REMOVE, staffName, "#" + warnId + " " + entry.reason);
        } catch (IllegalArgumentException ignored) {}
        ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(entry.targetUUID));
        if (online != null) online.sendSystemMessage(Component.literal(ServerLang.get(online, MessagesConfig.WARN_REMOVED_PLAYER, "id", warnId)));
        try {
            String fmt = ModerationConfig.WARN_REMOVED_BROADCAST_FORMAT.get();
            if (!fmt.isEmpty()) RpStaffCommands.broadcastToStaff(server, fmt.replace("{id}", warnId).replace("{staff}", staffName));
        } catch (IllegalStateException ignored) {}
        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_WARN_REMOVED, "id", warnId), false);
        return 1;
    }

    private static int warnListPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;
        ServerPlayer t = EntityArgument.getPlayer(ctx, "target");
        return displayWarnList(ctx, t.getUUID(), t.getName().getString(), true);
    }

    private static int warnListAll(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;
        var all = WarnManager.getAll();
        if (all.isEmpty()) { ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_WARN_NONE), false); return 1; }
        StringBuilder sb = new StringBuilder(Msg.get(ctx.getSource(), MessagesConfig.STAFF_WARN_ALL_HEADER,
                "count", String.valueOf(all.size())) + "\n" + RpChatUi.LINE + "\n");
        for (WarnManager.WarnEntry w : all) {
            String tag = Msg.get(ctx.getSource(), w.isExpired() ? MessagesConfig.STAFF_WARN_TAG_EXPIRED
                    : (w.isPermanent() ? MessagesConfig.STAFF_WARN_TAG_PERM : MessagesConfig.STAFF_WARN_TAG_TEMP));
            sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_WARN_ALL_ENTRY,
                    "tag", tag, "id", w.id, "player", w.targetName, "reason", w.reason)).append("\n");
        }
        sb.append(RpChatUi.LINE);
        String msg = sb.toString();
        RpChatUi.sendFormatted(ctx, msg);
        return 1;
    }

    private static int warnInfo(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;
        String warnId = StringArgumentType.getString(ctx, "warnId");
        Optional<WarnManager.WarnEntry> opt = WarnManager.getWarnById(warnId);
        if (opt.isEmpty()) { ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.WARN_NOT_FOUND, "id", warnId))); return 0; }
        WarnManager.WarnEntry w = opt.get();
        String typeStr = w.isPermanent() ? Msg.get(ctx.getSource(), MessagesConfig.WARN_TYPE_PERMANENT)
                : (w.isExpired() ? Msg.get(ctx.getSource(), MessagesConfig.WARN_TYPE_EXPIRED) : Msg.get(ctx.getSource(), MessagesConfig.WARN_TYPE_TEMPORARY));
        ctx.getSource().sendSuccess(() -> Component.literal(
                Msg.get(ctx.getSource(), MessagesConfig.WARN_INFO_HEADER, "id", w.id) + "\n"
                        + RpChatUi.LINE + "\n"
                        + Msg.get(ctx.getSource(), MessagesConfig.WARN_INFO_PLAYER_LABEL) + "§e" + w.targetName + "\n"
                        + Msg.get(ctx.getSource(), MessagesConfig.WARN_INFO_STAFF_LABEL)  + "§e" + w.issuerName + "\n"
                        + Msg.get(ctx.getSource(), MessagesConfig.WARN_INFO_REASON_LABEL) + "§f" + w.reason + "\n"
                        + Msg.get(ctx.getSource(), MessagesConfig.WARN_INFO_DATE_LABEL)   + "§f" + w.getFormattedDate() + "\n"
                        + Msg.get(ctx.getSource(), MessagesConfig.WARN_INFO_TYPE_LABEL)   + typeStr + "\n"
                        + Msg.get(ctx.getSource(), MessagesConfig.WARN_INFO_EXPIRY_LABEL) + "§f" + w.getFormattedExpiry(ctx.getSource().getPlayer()) + "\n"
                        + RpChatUi.LINE), false);
        return 1;
    }

    private static int warnClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        int removed = WarnManager.clearWarns(target.getUUID());
        if (removed > 0) HistoryManager.record(target.getUUID(), target.getName().getString(), HistoryManager.WARN_CLEAR, actorName(ctx), String.valueOf(removed));
        if (removed == 0) { ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.WARN_LIST_NONE_STAFF, "player", target.getName().getString())), false); return 1; }
        target.sendSystemMessage(Component.literal(ServerLang.get(target, MessagesConfig.WARN_CLEARED_PLAYER)));
        int finalR = removed;
        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_WARN_CLEARED, "count", String.valueOf(finalR), "player", target.getName().getString()), false);
        return 1;
    }

    private static int warnPurge(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;
        int p = WarnManager.purgeExpiredWarns();
        ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.WARN_PURGE_DONE, "count", String.valueOf(p))), false);
        return 1;
    }

    private static int myWarn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.COMMAND_PLAYER_ONLY)));
            return 0;
        }
        try { if (!ModerationConfig.ENABLE_WARN_SYSTEM.get()) { player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.WARN_SYSTEM_DISABLED))); return 0; } }
        catch (IllegalStateException e) { return 0; }
        return displayWarnList(ctx, player.getUUID(), player.getName().getString(), false);
    }

    static int displayWarnList(CommandContext<CommandSourceStack> ctx, UUID uuid, String name, boolean showAll) {
        List<WarnManager.WarnEntry> list = showAll ? WarnManager.getWarns(uuid) : WarnManager.getActiveWarns(uuid);
        if (list.isEmpty()) {
            String msg = showAll ? Msg.get(ctx.getSource(), MessagesConfig.WARN_LIST_NONE, "player", name) : Msg.get(ctx.getSource(), MessagesConfig.WARN_LIST_NONE_SELF);
            RpChatUi.sendFormatted(ctx, msg);
            return 1;
        }
        long active = list.stream().filter(w -> !w.isExpired()).count();
        StringBuilder sb = new StringBuilder();
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.WARN_LIST_HEADER, "player", name, "count", String.valueOf(active))).append("\n")
                .append(RpChatUi.LINE).append("\n");
        for (WarnManager.WarnEntry w : list) {
            String tag = w.isExpired() ? Msg.get(ctx.getSource(), MessagesConfig.WARN_STATUS_EXPIRED_TAG)
                    : (w.isPermanent() ? Msg.get(ctx.getSource(), MessagesConfig.WARN_STATUS_PERM_TAG) : Msg.get(ctx.getSource(), MessagesConfig.WARN_STATUS_TEMP_TAG));
            sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_WARN_LIST_ENTRY, "tag", tag, "id", w.id,
                    "date", w.getFormattedDate(), "staff", w.issuerName, "reason", w.reason,
                    "expiry", w.getFormattedExpiry(ctx.getSource().getPlayer()))).append("\n");
        }
        sb.append(RpChatUi.LINE);
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    // =========================================================================
    // MUTE HANDLER
    // =========================================================================
    private static int executeMute(CommandContext<CommandSourceStack> ctx, int minutes, String reason) throws CommandSyntaxException {
        try { if (!ModerationConfig.ENABLE_MUTE_SYSTEM.get()) { ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_MUTE_DISABLED)); return 0; } }
        catch (IllegalStateException e) { ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_MUTE_CONFIG)); return 0; }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ServerPlayer staff = ctx.getSource().getPlayer();
        int duration = minutes < 0 ? 0 : minutes;
        MuteManager.mute(target.getUUID(), target.getName().getString(),
                staff != null ? staff.getUUID() : null, staff != null ? staff.getName().getString() : net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.UI_CONSOLE), reason, duration);
        String d = duration == 0 ? Msg.get(ctx.getSource(), MessagesConfig.TIME_PERMANENT) : MessagesConfig.formatDuration(ctx.getSource().getPlayer(), duration);
        target.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseColors(ServerLang.get(target, MessagesConfig.MUTE_RECEIVED, "reason", reason, "duration", d)));
        ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.MUTE_STAFF_ADDED, "player", target.getName().getString(), "duration", d, "reason", reason)), true);
        return 1;
    }

    // =========================================================================
    // DEATH RP HANDLERS
    // =========================================================================
    private static int deathRpSetGlobal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        boolean enabled = BoolArgumentType.getBool(ctx, "value");
        String staff;
        try { staff = ctx.getSource().getPlayerOrException().getName().getString(); } catch (CommandSyntaxException e) { staff = net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.UI_CONSOLE); }
        try { if (RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED != null) { RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.set(enabled); RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.save(); } }
        catch (IllegalStateException e) { ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_CONFIG_UNAVAILABLE))); return 0; }
        DeathRPManager.broadcastGlobalToggle(staff, enabled, ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(enabled ? Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_GLOBAL_ENABLED) : Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_GLOBAL_DISABLED)), true);
        return 1;
    }

    private static int deathRpSetPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
        boolean enabled = BoolArgumentType.getBool(ctx, "value");
        DeathRPManager.setOverride(target.getUUID(), enabled);
        DeathRPManager.notifyPlayerToggle(target, enabled);
        ctx.getSource().sendSuccess(() -> Component.literal(enabled
                ? Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_PLAYER_ENABLED, "player", target.getName().getString())
                : Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_PLAYER_DISABLED, "player", target.getName().getString())), true);
        return 1;
    }

    private static int deathRpResetPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
        DeathRPManager.removeOverride(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_OVERRIDE_RESET, "player", target.getName().getString())), true);
        return 1;
    }

    private static int deathRpRevive(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        UUID uuid = RpEssentialsCommands.findUUIDByName(server, name);
        if (uuid == null) {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_DRP_NOT_FOUND, "player", name));
            return 0;
        }
        ManageService.Result r = ManageService.execute(server, ctx.getSource().getPlayer(), uuid,
                ManageService.Action.WHITELIST_ADD, "", 0);
        if (r.ok()) ctx.getSource().sendSuccess(() -> Component.literal(r.message()), false);
        else ctx.getSource().sendFailure(Component.literal(r.message()));
        return r.ok() ? 1 : 0;
    }

    private static int deathRpStatus(CommandContext<CommandSourceStack> ctx) {
        boolean global = false;
        try { global = RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED != null && RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get(); } catch (IllegalStateException ignored) {}
        boolean wlRemove = false;
        try { wlRemove = RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE != null && RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE.get(); } catch (IllegalStateException ignored) {}
        String active = Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_STATUS_ACTIVE);
        String inactive = Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_STATUS_INACTIVE);
        String yes = Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_STATUS_YES);
        String no = Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_STATUS_NO);
        StringBuilder sb = new StringBuilder();
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_STATUS_HEADER)).append("\n").append(RpChatUi.LINE).append("\n");
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_STATUS_GLOBAL, "value", global ? active : inactive)).append("\n");
        try {
            boolean hoursOn = ScheduleConfig.DEATH_HOURS_ENABLED.get();
            boolean hourNow = hoursOn && RpEssentialsScheduleManager.isDeathHour();
            String hours = !hoursOn ? Msg.get(ctx.getSource(), MessagesConfig.STAFF_DRP_HOURS_DISABLED) : hourNow ? active : inactive;
            if (hoursOn) hours += Msg.get(ctx.getSource(), MessagesConfig.STAFF_DRP_HOURS_SLOTS,
                    "slots", String.join(", ", ScheduleConfig.DEATH_HOURS_SLOTS.get()));
            sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_DRP_HOURS, "value", hours)).append("\n");
            sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_DRP_EFFECTIVE, "value", global || hourNow ? active : inactive)).append("\n");
        } catch (IllegalStateException ignored) {}
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_STATUS_WHITELIST, "value", wlRemove ? yes : no)).append("\n").append(RpChatUi.LINE).append("\n");
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_STATUS_OVERRIDES)).append("\n");
        Map<UUID, Boolean> overrides = DeathRPManager.getAllOverrides();
        MinecraftServer server = ctx.getSource().getServer();
        if (overrides.isEmpty()) { sb.append(Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_STATUS_NO_OVERRIDES)).append("\n"); }
        else {
            for (Map.Entry<UUID, Boolean> e : overrides.entrySet()) {
                String name = Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_STATUS_UNKNOWN);
                if (server != null) {
                    ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                    if (p != null) name = p.getName().getString();
                    else if (server.getProfileCache() != null) name = server.getProfileCache().get(e.getKey())
                            .map(com.mojang.authlib.GameProfile::getName).orElse(name);
                }
                sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_DRP_OVERRIDE_ENTRY, "player", name, "value", e.getValue() ? active : inactive)).append("\n");
            }
        }
        sb.append(RpChatUi.LINE);
        String msg = sb.toString();
        RpChatUi.sendFormatted(ctx, msg);
        return 1;
    }

    private static int showDeathHistory(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        List<DeathRPManager.DeathHistoryEntry> h = target != null ? DeathRPManager.getHistory(target.getUUID()) : DeathRPManager.getAllHistory();
        String label = target != null ? target.getName().getString() : Msg.get(ctx.getSource(), MessagesConfig.STAFF_DRP_ALL_PLAYERS);
        if (h.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_HISTORY_NONE, "player", label)), false); return 1; }
        StringBuilder sb = new StringBuilder();
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_HISTORY_HEADER, "player", label)).append("\n")
                .append(RpChatUi.LINE).append("\n");
        for (int i = 0; i < h.size(); i++) {
            DeathRPManager.DeathHistoryEntry e = h.get(i);
            if (target == null) sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_DRP_HISTORY_ALL_ENTRY, "index", String.valueOf(i + 1),
                    "player", e.playerName, "date", String.valueOf(e.timestamp), "cause", String.valueOf(e.damageCause))).append("\n");
            else sb.append(Msg.get(ctx.getSource(), MessagesConfig.DEATHRP_HISTORY_ENTRY, "index", String.valueOf(i+1), "date", e.timestamp, "cause", e.damageCause)).append("\n");
        }
        sb.append(RpChatUi.LINE);
        String msg = sb.toString();
        RpChatUi.sendFormatted(ctx, msg);
        return 1;
    }

    // =========================================================================
    // INSPECT
    // =========================================================================
    private static int inspectPlayer(CommandContext<CommandSourceStack> ctx) {
        String targetName = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayer online = server.getPlayerList().getPlayerByName(targetName);
        UUID uuid = online != null ? online.getUUID() : LastConnectionManager.findUUIDByName(targetName);
        if (online != null && VanishManager.canHide(VanishManager.Aspect.LIST, ctx.getSource(), online.getUUID())) {
            online = null;
        }
        if (uuid == null) { ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_INSPECT_NOT_FOUND, "player", targetName)); return 0; }
        CommandSourceStack src = ctx.getSource();

        List<String> licenses = LicenseManager.getLicenses(uuid);
        List<WarnManager.WarnEntry> warns = WarnManager.getActiveWarns(uuid);
        MuteManager.MuteEntry mute = MuteManager.getEntry(uuid);
        String nick = net.rp.rpessentials.identity.NicknameManager.getNickname(uuid);
        LastConnectionManager.ConnectionEntry conn = LastConnectionManager.getEntry(uuid);
        List<NoteManager.NoteEntry> notes = NoteManager.getNotes(uuid);
        boolean isMuted = MuteManager.isMuted(uuid);
        boolean isOnline = online != null;

        long totalPlaytimeMs   = PlaytimeManager.getTotalPlaytimeMs(uuid);
        long sessionMs         = isOnline ? PlaytimeManager.getCurrentSessionMs(uuid) : 0L;
        String totalPlaytimeStr = PlaytimeManager.format(ctx.getSource().getPlayer(), totalPlaytimeMs);
        String sessionStr       = PlaytimeManager.format(ctx.getSource().getPlayer(), sessionMs);

        String role = Msg.get(src, MessagesConfig.STAFF_INSPECT_EMPTY);
        if (online != null) {
            try {
                for (String entry : net.rp.rpessentials.config.RpEssentialsConfig.ROLES.get()) {
                    String rid = entry.split(";", 2)[0].trim();
                    if (online.getTags().contains(rid)) { role = "§f" + rid; break; }
                }
            } catch (IllegalStateException ignored) {}
        }

        StringBuilder header = new StringBuilder();
        header.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_HEADER, "player", targetName)).append("\n").append(RpChatUi.LINE).append("\n");
        header.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_STATUS, "status",
                Msg.get(src, isOnline ? MessagesConfig.STAFF_INSPECT_ONLINE : MessagesConfig.STAFF_INSPECT_OFFLINE))).append("\n");
        header.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_NICK, "nickname",
                nick != null ? nick : Msg.get(src, MessagesConfig.STAFF_INSPECT_EMPTY))).append("\n");
        header.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_ROLE, "role", role));
        RpChatUi.sendFormatted(ctx, header.toString());

        final UUID finalUuid = uuid;
        if (ctx.getSource().getEntity() instanceof ServerPlayer viewer) {
            viewer.sendSystemMessage(Msg.c(viewer, MessagesConfig.STAFF_INSPECT_UUID, "uuid", finalUuid.toString()).copy()
                    .withStyle(style -> style
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                    net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                    Msg.c(viewer, MessagesConfig.STAFF_INSPECT_UUID_HOVER)))
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                    net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,
                                    "https://namemc.com/profile/" + finalUuid))));
        } else {
            ctx.getSource().sendSuccess(() -> Msg.c(src, MessagesConfig.STAFF_INSPECT_UUID, "uuid", finalUuid.toString()), false);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(RpChatUi.LINE).append("\n");

        StringBuilder lic = new StringBuilder();
        if (licenses.isEmpty()) lic.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_NONE_GREY));
        else {
            for (int i = 0; i < licenses.size(); i++) {
                String expiry = LicenseManager.getTempExpirationDate(uuid, licenses.get(i));
                lic.append("§f").append(licenses.get(i));
                if (expiry != null) lic.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_LICENSE_TEMP, "date", expiry));
                if (i < licenses.size() - 1) lic.append("§7, ");
            }
        }
        sb.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_LICENSES, "count", String.valueOf(licenses.size()), "list", lic.toString())).append("\n");

        StringBuilder warnList = new StringBuilder();
        if (warns.isEmpty()) warnList.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_NONE_GREEN));
        else warns.forEach(w -> warnList.append("\n ").append(Msg.get(src, MessagesConfig.STAFF_INSPECT_WARN_ENTRY, "id", w.id, "reason", w.reason)));
        sb.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_WARNS, "count", String.valueOf(warns.size()), "list", warnList.toString())).append("\n");

        String muteValue = isMuted && mute != null
                ? Msg.get(src, MessagesConfig.STAFF_INSPECT_MUTE_ACTIVE, "expiry", mute.getFormattedExpiry(src.getPlayer()), "reason", mute.reason)
                : Msg.get(src, MessagesConfig.STAFF_INSPECT_NONE_GREEN);
        sb.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_MUTE, "value", muteValue)).append("\n");

        sb.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_LAST_SEEN, "date",
                conn != null && conn.lastLogin != null ? String.valueOf(conn.lastLogin) : Msg.get(src, MessagesConfig.STAFF_INSPECT_UNKNOWN))).append("\n");

        String session = isOnline && sessionMs > 60_000L
                ? Msg.get(src, MessagesConfig.STAFF_INSPECT_SESSION, "session", sessionStr) : "";
        sb.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_PLAYTIME, "total", totalPlaytimeStr, "session", session)).append("\n");

        if (!notes.isEmpty()) {
            sb.append(RpChatUi.LINE).append("\n");
            sb.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_NOTES, "count", String.valueOf(notes.size()))).append("\n");
            notes.forEach(n -> sb.append(Msg.get(src, MessagesConfig.STAFF_INSPECT_NOTE_ENTRY,
                    "id", String.valueOf(n.id), "author", n.authorName, "text", n.text)).append("\n"));
        }

        sb.append(RpChatUi.LINE);
        RpChatUi.sendFormatted(ctx, sb.toString());
        return 1;
    }

    // =========================================================================
    // UTILS
    // =========================================================================

    /** Name of the staff member (or the console) running the command, for stored data. */
    static String actorName(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() instanceof ServerPlayer p ? p.getName().getString()
                : ServerLang.get(null, MessagesConfig.UI_CONSOLE);
    }

    // =========================================================================
    // /rpessentials history <player> [page] | purge
    // =========================================================================
    public static LiteralArgumentBuilder<CommandSourceStack> buildHistory() {
        var node = Commands.literal("history");
        node.then(Commands.literal("purge").executes(ctx -> {
            if (!HistoryManager.enabled()) { ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.HISTORY_DISABLED)); return 0; }
            int n = HistoryManager.purge();
            HistoryManager.tick();
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.HISTORY_PURGED, "count", String.valueOf(n)), true);
            return 1;
        }));
        var player = Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, b) -> {
                    ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> b.suggest(p.getName().getString()));
                    return b.buildFuture();
                })
                .executes(ctx -> showHistory(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> showHistory(ctx, IntegerArgumentType.getInteger(ctx, "page"))));
        node.then(player);
        return node;
    }

    private static int showHistory(CommandContext<CommandSourceStack> ctx, int page) {
        if (!HistoryManager.enabled()) { ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.HISTORY_DISABLED)); return 0; }
        String name = StringArgumentType.getString(ctx, "player");
        UUID uuid = net.rp.rpessentials.commands.RpEssentialsCommands.findUUIDByName(ctx.getSource().getServer(), name);
        if (uuid == null) uuid = HistoryManager.findByName(name);
        if (uuid == null) { ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.HISTORY_UNKNOWN_PLAYER, "player", name)); return 0; }
        String shown = HistoryManager.nameOf(uuid) != null ? HistoryManager.nameOf(uuid) : name;
        List<HistoryManager.Entry> all = HistoryManager.get(uuid, ctx.getSource().getPlayer());
        if (all.isEmpty()) { ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.HISTORY_EMPTY, "player", shown), false); return 1; }

        int pages = RpChatUi.pageCount(all.size());
        int current = Math.min(page, pages);
        ServerPlayer viewer = ctx.getSource().getPlayer();
        net.minecraft.network.chat.MutableComponent out = Component.literal(
                Msg.get(ctx.getSource(), MessagesConfig.HISTORY_HEADER, "player", shown, "count", String.valueOf(all.size()))
                        + "\n" + RpChatUi.LINE);
        int from = (current - 1) * RpChatUi.PAGE_SIZE;
        for (int i = from; i < Math.min(all.size(), from + RpChatUi.PAGE_SIZE); i++) {
            out.append(Component.literal("\n")).append(net.rp.rpessentials.util.ColorHelper.parseSection(HistoryManager.format(viewer, all.get(i))));
        }
        if (pages > 1) {
            out.append(Component.literal("\n")).append(RpChatUi.pager(ctx.getSource(), current, pages, "/rpessentials history " + shown));
        }
        ctx.getSource().sendSuccess(() -> out, false);
        return 1;
    }

    private static boolean warnSystemCheck(CommandContext<CommandSourceStack> ctx) {
        try {
            if (!ModerationConfig.ENABLE_WARN_SYSTEM.get()) {
                ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.WARN_SYSTEM_DISABLED_CONFIG)));
                return false;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return false;
        }
        return true;
    }
}