package net.rp.rpessentials.commands;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.lang.Msg;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.*;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.profession.ProfessionSyncHelper;

import java.util.List;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.network.SyncNametagDataPacket;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;

/**
 * Commandes schedule et setrole.
 */
public class RpScheduleCommands {

    public static void registerAliases(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : new String[]{"schedule", "calendar", "horaires"}) {
            dispatcher.register(scheduleNode(name));
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> scheduleNode(String name) {
        return Commands.literal(name)
                .executes(RpScheduleCommands::showSchedule)
                .then(Commands.literal("forceopen")
                        .requires(RpPerms.cmd(RpPerm.SCHEDULE_FORCE))
                        .executes(RpScheduleCommands::forceOpen))
                .then(Commands.literal("forceclose")
                        .requires(RpPerms.cmd(RpPerm.SCHEDULE_FORCE))
                        .executes(RpScheduleCommands::forceClose))
                .then(Commands.literal("resetforcestate")
                        .requires(RpPerms.cmd(RpPerm.SCHEDULE_FORCE))
                        .executes(RpScheduleCommands::forceReset))
                .then(Commands.literal("whitelist")
                        .requires(RpPerms.cmd(RpPerm.SCHEDULE_WHITELIST))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            net.rp.rpessentials.moderation.VanishManager.visibleTo(ctx.getSource())
                                                    .forEach(p -> builder.suggest(p.getName().getString()));
                                            return builder.buildFuture();
                                        })
                                        .executes(RpScheduleCommands::scheduleWhitelistAdd)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            if (Cfg.ready(ScheduleConfig.SPEC)) { ScheduleConfig.SCHEDULE_WHITELIST.get().forEach(builder::suggest); }
                                            return builder.buildFuture();
                                        })
                                        .executes(RpScheduleCommands::scheduleWhitelistRemove)))
                        .then(Commands.literal("list")
                                .executes(RpScheduleCommands::scheduleWhitelistList)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildSetRole() {
        return Commands.literal("setrole")
                .requires(RpPerms.cmd(RpPerm.ROLES_SET))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    if (Cfg.ready(RpEssentialsConfig.SPEC)) {
                                        RpEssentialsConfig.ROLES.get().stream()
                                                .map(s -> s.split(";", 2)[0].trim())
                                                .forEach(builder::suggest);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(RpScheduleCommands::setRole)));
    }

    public static void registerSetNodes(LiteralArgumentBuilder<CommandSourceStack> set) {
        set.then(Commands.literal("scheduleDay")
                .then(Commands.argument("day", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            java.util.Arrays.asList("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY")
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(Commands.literal("open").then(Commands.argument("time", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("08:00").suggest("18:00").suggest("19:00")
                                            .suggest("20:00").suggest("22:00").suggest("23:59");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> setDayTime(ctx, "open"))))
                        .then(Commands.literal("close").then(Commands.argument("time", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("22:00").suggest("23:00").suggest("23:59")
                                            .suggest("01:00").suggest("02:00");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> setDayTime(ctx, "close"))))
                        .then(Commands.literal("enabled").then(Commands.argument("value", BoolArgumentType.bool()).executes(RpScheduleCommands::setDayEnabled)))));

        set.then(Commands.literal("deathHoursEnabled").then(Commands.argument("value", BoolArgumentType.bool())
                .executes(ctx -> RpConfigCommands.updateConfigBool(ctx, ScheduleConfig.DEATH_HOURS_ENABLED))));
        set.then(Commands.literal("enableHrpHours").then(Commands.argument("value", BoolArgumentType.bool())
                .executes(ctx -> RpConfigCommands.updateConfigBool(ctx, ScheduleConfig.ENABLE_HRP_HOURS))));
        set.then(Commands.literal("hrpToleratedMessage").then(Commands.argument("value", StringArgumentType.greedyString())
                .executes(ctx -> RpConfigCommands.updateConfigString(ctx, ScheduleConfig.HRP_TOLERATED_MESSAGE))));
        set.then(Commands.literal("hrpAllowedMessage").then(Commands.argument("value", StringArgumentType.greedyString())
                .executes(ctx -> RpConfigCommands.updateConfigString(ctx, ScheduleConfig.HRP_ALLOWED_MESSAGE))));
        set.then(Commands.literal("hrpMessageMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, b) -> { b.suggest("CHAT").suggest("ACTION_BAR").suggest("TITLE").suggest("IMMERSIVE").suggest("IMMERSIVE:hrp"); return b.buildFuture(); })
                        .executes(ctx -> RpConfigCommands.updateConfigString(ctx, ScheduleConfig.HRP_MESSAGE_MODE))));
        set.then(Commands.literal("deathHoursSlots").then(Commands.argument("slots", StringArgumentType.greedyString()).executes(RpScheduleCommands::setDeathHoursSlots)));
        set.then(Commands.literal("hrpToleratedSlots").then(Commands.argument("slots", StringArgumentType.greedyString()).executes(RpScheduleCommands::setHrpToleratedSlots)));
        set.then(Commands.literal("hrpAllowedSlots").then(Commands.argument("slots", StringArgumentType.greedyString()).executes(RpScheduleCommands::setHrpAllowedSlots)));
    }

    // =========================================================================
    // /schedule
    // =========================================================================
    static int showSchedule(CommandContext<CommandSourceStack> ctx) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        java.time.DayOfWeek today = RpEssentialsScheduleManager.today().getDayOfWeek();

        boolean schedEnabled;
        if (Cfg.ready(ScheduleConfig.SPEC)) { schedEnabled = ScheduleConfig.ENABLE_SCHEDULE.get(); } else { schedEnabled = false; }
        boolean forceClosed = false;
        boolean forceOpen = false;
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            String state = ScheduleConfig.FORCE_STATE.get();
            forceClosed = "FORCE_CLOSED".equals(state);
            forceOpen = "FORCE_OPEN".equals(state);
        }

        String status;
        if (forceClosed) status = Msg.get(ctx.getSource(), MessagesConfig.STAFF_SCHED_FORCED_CLOSED);
        else if (forceOpen) status = Msg.get(ctx.getSource(), MessagesConfig.STAFF_SCHED_FORCED_OPEN);
        else status = RpEssentialsScheduleManager.isServerOpen()
                    ? Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_STATUS_OPEN)
                    : Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_STATUS_CLOSED);

        StringBuilder sb = new StringBuilder();
        sb.append(RpChatUi.header(Msg.get(ctx.getSource(), MessagesConfig.STAFF_SCHED_TITLE)));
        sb.append(RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_SCHED_LABEL_STATUS), status)).append("\n");

        if (schedEnabled) {
            sb.append(RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_SCHED_LABEL_NEXT), "§f" + RpEssentialsScheduleManager.getTimeUntilNextEvent(ctx.getSource().getPlayer()))).append("\n");
            sb.append(RpChatUi.section(Msg.get(ctx.getSource(), MessagesConfig.STAFF_SCHED_SECTION_OPENING)));
            for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
                RpEssentialsScheduleManager.DaySchedule s = RpEssentialsScheduleManager.getSchedules().get(day);
                String prefix = day == today
                        ? Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_DAY_TODAY_PREFIX)
                        : Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_DAY_OTHER_PREFIX);
                String dayName = RpEssentialsScheduleManager.getDayName(ctx.getSource().getPlayer(), day);
                sb.append(prefix);
                if (s == null) sb.append(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_DAY_CLOSED_FORMAT, "day", dayName));
                else sb.append(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_DAY_OPEN_FORMAT,
                        "day", dayName, "open", s.open().format(fmt), "close", s.close().format(fmt)));
                sb.append("\n");
            }
        var upcoming = RpEssentialsScheduleManager.getUpcomingExceptions();
            if (!upcoming.isEmpty()) {
                sb.append(RpChatUi.section(Msg.get(ctx.getSource(), MessagesConfig.SCHED_SECTION_EXCEPTIONS)));
                java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                int shown = 0;
                for (var e : upcoming.entrySet()) {
                    if (shown++ >= 5) break;
                    String date = e.getKey().format(df);
                    String dayName = RpEssentialsScheduleManager.getDayName(ctx.getSource().getPlayer(), e.getKey().getDayOfWeek());
                    var s = e.getValue();
                    sb.append(s == null
                            ? Msg.get(ctx.getSource(), MessagesConfig.SCHED_EXCEPTION_CLOSED, "day", dayName, "date", date)
                            : Msg.get(ctx.getSource(), MessagesConfig.SCHED_EXCEPTION_OPEN, "day", dayName, "date", date,
                                    "open", s.open().format(fmt), "close", s.close().format(fmt))).append("\n");
                }
            }
        } else {
            sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_SCHED_DISABLED)).append("\n");
        }

        StringBuilder special = new StringBuilder();
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if (ScheduleConfig.DEATH_HOURS_ENABLED.get()) {
                special.append(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_DEATH_HOURS_LABEL)).append("§7: ");
                if (RpEssentialsScheduleManager.isDeathHour())
                    special.append(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_DEATH_HOURS_ACTIVE));
                else
                    special.append(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_DEATH_HOURS_INACTIVE,
                            "slots", String.join(", ", ScheduleConfig.DEATH_HOURS_SLOTS.get())));
                special.append("\n");
            }
        }
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if (ScheduleConfig.ENABLE_HRP_HOURS.get()) {
                special.append(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_HRP_LABEL)).append("§7: ");
                if (RpEssentialsScheduleManager.isHrpAllowed())
                    special.append(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_HRP_ALLOWED));
                else if (RpEssentialsScheduleManager.isHrpTolerated())
                    special.append(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_HRP_TOLERATED));
                else
                    special.append(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_HRP_INACTIVE,
                            "slots", Msg.get(ctx.getSource(), MessagesConfig.STAFF_SCHED_TOLERATED,
                                    "slots", String.join(", ", ScheduleConfig.HRP_TOLERATED_SLOTS.get()))));
                special.append("\n");
            }
        }
        if (special.length() > 0) sb.append(RpChatUi.section(Msg.get(ctx.getSource(), MessagesConfig.STAFF_SCHED_SECTION_SPECIAL))).append(special);

        sb.append(RpChatUi.LINE);
        RpChatUi.sendFormatted(ctx, sb.toString());
        return 1;
    }

    // =========================================================================
    // /setrole
    // =========================================================================
    private static int setRole(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String roleId = StringArgumentType.getString(ctx, "role").toLowerCase();
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayer staffPlayer = ctx.getSource().getPlayer();
        String staffName = staffPlayer != null ? staffPlayer.getName().getString() : net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.UI_CONSOLE);

        List<? extends String> rolesConfig;
        if (Cfg.ready(RpEssentialsConfig.SPEC)) { rolesConfig = RpEssentialsConfig.ROLES.get(); } else {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }

        String lpGroup = null;
        for (String entry : rolesConfig) {
            String[] parts = entry.split(";", 3);
            if (parts[0].trim().equalsIgnoreCase(roleId)) {
                lpGroup = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : roleId;
                break;
            }
        }
        if (lpGroup == null) {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SETROLE_UNKNOWN, "role", roleId.toUpperCase())));
            return 0;
        }

        final String finalLpGroup = lpGroup;
        final String name = target.getName().getString();
        final String roleDisplay = roleId.toUpperCase();
        CommandSourceStack silent = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);

        for (String entry : rolesConfig) {
            String old = entry.split(";", 2)[0].trim();
            server.getCommands().performPrefixedCommand(silent, "tag " + name + " remove " + old);
        }
        server.getCommands().performPrefixedCommand(silent, "tag " + name + " add " + roleId);
        server.getCommands().performPrefixedCommand(silent, "lp user " + name + " parent set " + finalLpGroup);

        net.rp.rpessentials.permission.RpPerms.refresh(target);
        ProfessionSyncHelper.syncToPlayer(target);
        SyncNametagDataPacket.broadcastDelayed(target);
        RpEssentialsScheduleManager.enforceOnlineDelayed(server);

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (RpPerms.has(sp, RpPerm.NOTIFY) && !sp.getUUID().equals(target.getUUID())) {
                sp.sendSystemMessage(Msg.c(sp, MessagesConfig.STAFF_SCHED_SETROLE_ALERT,
                        "staff", staffName, "player", name, "role", roleDisplay, "group", String.valueOf(finalLpGroup)));
            }
        }
        RpEssentials.LOGGER.info("[SETROLE] {} assigned role '{}' (LP: {}) to {}", staffName, roleDisplay, finalLpGroup, name);

        ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SETROLE_SUCCESS_STAFF, "role", roleDisplay, "player", name)), true);
        target.sendSystemMessage(Component.literal(ServerLang.get(target, MessagesConfig.SETROLE_SUCCESS_PLAYER, "role", roleDisplay)));
        return 1;
    }

    // =========================================================================
    // Setters schedule
    // =========================================================================

    private static int setDayTime(CommandContext<CommandSourceStack> ctx, String type) {
        String day  = StringArgumentType.getString(ctx, "day").toUpperCase();
        String time = StringArgumentType.getString(ctx, "time");
        if (!time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_TIME_INVALID)));
            return 0;
        }
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> cfg = switch (day + "_" + type.toUpperCase()) {
                case "MONDAY_OPEN"     -> ScheduleConfig.MONDAY_OPEN;
                case "MONDAY_CLOSE"    -> ScheduleConfig.MONDAY_CLOSE;
                case "TUESDAY_OPEN"    -> ScheduleConfig.TUESDAY_OPEN;
                case "TUESDAY_CLOSE"   -> ScheduleConfig.TUESDAY_CLOSE;
                case "WEDNESDAY_OPEN"  -> ScheduleConfig.WEDNESDAY_OPEN;
                case "WEDNESDAY_CLOSE" -> ScheduleConfig.WEDNESDAY_CLOSE;
                case "THURSDAY_OPEN"   -> ScheduleConfig.THURSDAY_OPEN;
                case "THURSDAY_CLOSE"  -> ScheduleConfig.THURSDAY_CLOSE;
                case "FRIDAY_OPEN"     -> ScheduleConfig.FRIDAY_OPEN;
                case "FRIDAY_CLOSE"    -> ScheduleConfig.FRIDAY_CLOSE;
                case "SATURDAY_OPEN"   -> ScheduleConfig.SATURDAY_OPEN;
                case "SATURDAY_CLOSE"  -> ScheduleConfig.SATURDAY_CLOSE;
                case "SUNDAY_OPEN"     -> ScheduleConfig.SUNDAY_OPEN;
                case "SUNDAY_CLOSE"    -> ScheduleConfig.SUNDAY_CLOSE;
                default -> null;
            };
            if (cfg == null) { ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_DAY_INVALID, "day", day))); return 0; }
            cfg.set(time); ScheduleConfig.SPEC.save(); RpEssentialsScheduleManager.reload();
            RpEssentialsScheduleManager.enforceOnline(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_DAY_UPDATED, "day", day, "type", type, "value", time)), true);
            return 1;
        } else { ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_NOT_LOADED))); return 0; }
    }

    private static int setDayEnabled(CommandContext<CommandSourceStack> ctx) {
        String day  = StringArgumentType.getString(ctx, "day").toUpperCase();
        boolean val = BoolArgumentType.getBool(ctx, "value");
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            net.minecraftforge.common.ForgeConfigSpec.BooleanValue cfg = switch (day) {
                case "MONDAY"    -> ScheduleConfig.MONDAY_ENABLED;
                case "TUESDAY"   -> ScheduleConfig.TUESDAY_ENABLED;
                case "WEDNESDAY" -> ScheduleConfig.WEDNESDAY_ENABLED;
                case "THURSDAY"  -> ScheduleConfig.THURSDAY_ENABLED;
                case "FRIDAY"    -> ScheduleConfig.FRIDAY_ENABLED;
                case "SATURDAY"  -> ScheduleConfig.SATURDAY_ENABLED;
                case "SUNDAY"    -> ScheduleConfig.SUNDAY_ENABLED;
                default -> null;
            };
            if (cfg == null) { ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_DAY_INVALID, "day", day))); return 0; }
            cfg.set(val); ScheduleConfig.SPEC.save(); RpEssentialsScheduleManager.reload();
            RpEssentialsScheduleManager.enforceOnline(ctx.getSource().getServer());
            String state = Msg.get(ctx.getSource(), val ? MessagesConfig.STAFF_SCHED_STATE_ENABLED : MessagesConfig.STAFF_SCHED_STATE_DISABLED);
            ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SCHEDULE_DAY_ENABLED, "day", day, "state", state)), true);
            return 1;
        } else { ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_NOT_LOADED))); return 0; }
    }

    private static int setDeathHoursSlots(CommandContext<CommandSourceStack> ctx) { return setSlotsConfig(ctx, ScheduleConfig.DEATH_HOURS_SLOTS); }
    private static int setHrpToleratedSlots(CommandContext<CommandSourceStack> ctx) { return setSlotsConfig(ctx, ScheduleConfig.HRP_TOLERATED_SLOTS); }
    private static int setHrpAllowedSlots(CommandContext<CommandSourceStack> ctx) { return setSlotsConfig(ctx, ScheduleConfig.HRP_ALLOWED_SLOTS); }

    private static int setSlotsConfig(CommandContext<CommandSourceStack> ctx,
                                      net.minecraftforge.common.ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> config) {
        String label = RpConfigCommands.optName(config);
        String raw = StringArgumentType.getString(ctx, "slots");
        java.util.List<String> slots = java.util.Arrays.stream(raw.split(",")).map(String::trim).collect(java.util.stream.Collectors.toList());
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            config.set(slots); ScheduleConfig.SPEC.save(); RpEssentialsScheduleManager.reload();
            ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_UPDATED, "label", label, "value", String.join(", ", slots))), true);
        } else {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
        }
        return 1;
    }

    private static int forceReset(CommandContext<CommandSourceStack> ctx) {
        RpEssentialsScheduleManager.resetForceState(ctx.getSource().getServer(), ctx.getSource().getTextName(),
                net.rp.rpessentials.api.event.ServerScheduleEvent.Cause.COMMAND);
        return 1;
    }

    private static int forceOpen(CommandContext<CommandSourceStack> ctx) {
        RpEssentialsScheduleManager.forceOpen(ctx.getSource().getServer(), ctx.getSource().getTextName(),
                net.rp.rpessentials.api.event.ServerScheduleEvent.Cause.COMMAND);
        return 1;
    }

    private static int forceClose(CommandContext<CommandSourceStack> ctx) {
        RpEssentialsScheduleManager.forceClose(ctx.getSource().getServer(), ctx.getSource().getTextName(),
                net.rp.rpessentials.api.event.ServerScheduleEvent.Cause.COMMAND);
        return 1;
    }

    private static int scheduleWhitelistAdd(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            java.util.List<String> list = new java.util.ArrayList<>(ScheduleConfig.SCHEDULE_WHITELIST.get());
            if (list.contains(name)) {
                ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_SCHED_WL_ALREADY, "player", name));
                return 0;
            }
            list.add(name);
            ScheduleConfig.SCHEDULE_WHITELIST.set(list);
            ScheduleConfig.SPEC.save();
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_SCHED_WL_ADDED, "player", name), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_SCHED_CONFIG));
            return 0;
        }
    }

    private static int scheduleWhitelistRemove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            java.util.List<String> list = new java.util.ArrayList<>(ScheduleConfig.SCHEDULE_WHITELIST.get());
            if (!list.remove(name)) {
                ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_SCHED_WL_NOT_IN, "player", name));
                return 0;
            }
            ScheduleConfig.SCHEDULE_WHITELIST.set(list);
            ScheduleConfig.SPEC.save();
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_SCHED_WL_REMOVED, "player", name), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_SCHED_CONFIG));
            return 0;
        }
    }

    private static int scheduleWhitelistList(CommandContext<CommandSourceStack> ctx) {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            java.util.List<? extends String> list = ScheduleConfig.SCHEDULE_WHITELIST.get();
            if (list.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_SCHED_WL_EMPTY), false);
                return 1;
            }
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_SCHED_WL_LIST,
                    "count", String.valueOf(list.size()), "list", String.join("§7, §f", list)), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_SCHED_CONFIG));
            return 0;
        }
    }
}