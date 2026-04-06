package net.rp.rpessentials.commands;

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
import net.rp.rpessentials.RpEssentialsScheduleManager;
import net.rp.rpessentials.config.*;

import java.util.List;

public class RpScheduleCommands {

    /** Registers /schedule and /horaires standalone aliases */
    public static void registerAliases(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("schedule").executes(RpScheduleCommands::showSchedule));
        dispatcher.register(Commands.literal("horaires").executes(RpScheduleCommands::showSchedule));
    }

    /** Registers /rpessentials setrole */
    public static LiteralArgumentBuilder<CommandSourceStack> buildSetRole() {
        return Commands.literal("setrole")
                .requires(source -> source.hasPermission(3))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        RpEssentialsConfig.ROLES.get().stream()
                                                .map(s -> s.split(";", 2)[0].trim())
                                                .forEach(builder::suggest);
                                    } catch (IllegalStateException ignored) {}
                                    return builder.buildFuture();
                                })
                                .executes(RpScheduleCommands::setRole)));
    }

    /** Called from RpConfigCommands to add schedule-related "set" nodes */
    public static void registerSetNodes(LiteralArgumentBuilder<CommandSourceStack> set) {
        set.then(Commands.literal("scheduleDay")
                .then(Commands.argument("day", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            java.util.Arrays.asList("MONDAY","TUESDAY","WEDNESDAY",
                                            "THURSDAY","FRIDAY","SATURDAY","SUNDAY")
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(Commands.literal("open")
                                .then(Commands.argument("time", StringArgumentType.word())
                                        .executes(ctx -> setDayTime(ctx, "open"))))
                        .then(Commands.literal("close")
                                .then(Commands.argument("time", StringArgumentType.word())
                                        .executes(ctx -> setDayTime(ctx, "close"))))
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(RpScheduleCommands::setDayEnabled)))));

        set.then(Commands.literal("deathHoursEnabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> RpConfigCommands.updateConfigBool(ctx, ScheduleConfig.DEATH_HOURS_ENABLED, "Death Hours"))));

        set.then(Commands.literal("deathHoursSlots")
                .then(Commands.argument("slots", StringArgumentType.greedyString())
                        .executes(RpScheduleCommands::setDeathHoursSlots)));

        set.then(Commands.literal("enableHrpHours")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> RpConfigCommands.updateConfigBool(ctx, ScheduleConfig.ENABLE_HRP_HOURS, "HRP Hours"))));

        set.then(Commands.literal("hrpToleratedSlots")
                .then(Commands.argument("slots", StringArgumentType.greedyString())
                        .executes(RpScheduleCommands::setHrpToleratedSlots)));

        set.then(Commands.literal("hrpAllowedSlots")
                .then(Commands.argument("slots", StringArgumentType.greedyString())
                        .executes(RpScheduleCommands::setHrpAllowedSlots)));

        set.then(Commands.literal("hrpToleratedMessage")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> RpConfigCommands.updateConfigString(ctx, ScheduleConfig.HRP_TOLERATED_MESSAGE, "HRP Tolerated Message"))));

        set.then(Commands.literal("hrpAllowedMessage")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> RpConfigCommands.updateConfigString(ctx, ScheduleConfig.HRP_ALLOWED_MESSAGE, "HRP Allowed Message"))));

        set.then(Commands.literal("hrpMessageMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("CHAT").suggest("ACTION_BAR").suggest("TITLE").suggest("IMMERSIVE");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> RpConfigCommands.updateConfigString(ctx, ScheduleConfig.HRP_MESSAGE_MODE, "HRP Message Mode"))));
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================

    static int showSchedule(CommandContext<CommandSourceStack> ctx) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        java.time.DayOfWeek today = java.time.LocalDate.now().getDayOfWeek();

        boolean scheduleEnabled;
        try { scheduleEnabled = ScheduleConfig.ENABLE_SCHEDULE.get(); }
        catch (IllegalStateException e) { scheduleEnabled = false; }

        boolean isOpen = RpEssentialsScheduleManager.isServerOpen();
        String timeInfo = scheduleEnabled
                ? RpEssentialsScheduleManager.getTimeUntilNextEvent()
                : MessagesConfig.get(MessagesConfig.SCHEDULE_STATUS_OPEN) + " (schedule disabled)";

        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_HEADER)).append("\n");
        sb.append(" §6§lSERVER SCHEDULE\n");
        sb.append(" §7Status: ")
                .append(isOpen ? MessagesConfig.get(MessagesConfig.SCHEDULE_STATUS_OPEN)
                        : MessagesConfig.get(MessagesConfig.SCHEDULE_STATUS_CLOSED))
                .append("\n\n");

        if (scheduleEnabled) {
            for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
                RpEssentialsScheduleManager.DaySchedule s = RpEssentialsScheduleManager.getSchedules().get(day);
                boolean isToday = day == today;
                String prefix = isToday
                        ? MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_TODAY_PREFIX)
                        : MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_OTHER_PREFIX);
                String dayName = day.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);

                if (s == null) {
                    sb.append(prefix).append(MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_CLOSED_FORMAT, "day", dayName)).append("\n");
                } else {
                    sb.append(prefix).append(MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_OPEN_FORMAT,
                            "day", dayName, "open", s.open().format(fmt), "close", s.close().format(fmt))).append("\n");
                }
            }
        }

        try {
            if (ScheduleConfig.DEATH_HOURS_ENABLED.get()) {
                sb.append("\n ").append(MessagesConfig.get(MessagesConfig.SCHEDULE_DEATH_HOURS_LABEL)).append(" — ");
                if (RpEssentialsScheduleManager.isDeathHour()) {
                    sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_DEATH_HOURS_ACTIVE));
                } else {
                    String slots = String.join(", ", ScheduleConfig.DEATH_HOURS_SLOTS.get());
                    sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_DEATH_HOURS_INACTIVE, "slots", slots));
                }
                sb.append("\n");
            }
        } catch (IllegalStateException ignored) {}

        try {
            if (ScheduleConfig.ENABLE_HRP_HOURS.get()) {
                sb.append(" ").append(MessagesConfig.get(MessagesConfig.SCHEDULE_HRP_LABEL)).append(" — ");
                if (RpEssentialsScheduleManager.isHrpAllowed()) {
                    sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_HRP_ALLOWED));
                } else if (RpEssentialsScheduleManager.isHrpTolerated()) {
                    sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_HRP_TOLERATED));
                } else {
                    String tolerated = String.join(", ", ScheduleConfig.HRP_TOLERATED_SLOTS.get());
                    String allowed = String.join(", ", ScheduleConfig.HRP_ALLOWED_SLOTS.get());
                    sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_HRP_INACTIVE,
                            "slots", "Tolerated: " + tolerated + " / Allowed: " + allowed));
                }
                sb.append("\n");
            }
        } catch (IllegalStateException ignored) {}

        if (scheduleEnabled) sb.append("\n §f").append(timeInfo).append("\n");
        sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_FOOTER));

        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int setRole(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String roleId = StringArgumentType.getString(ctx, "role").toLowerCase();
        MinecraftServer server = ctx.getSource().getServer();

        List<? extends String> rolesConfig;
        try { rolesConfig = RpEssentialsConfig.ROLES.get(); }
        catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }

        String lpGroup = null;
        for (String entry : rolesConfig) {
            String[] parts = entry.split(";", 2);
            if (parts[0].trim().equalsIgnoreCase(roleId)) {
                lpGroup = parts.length > 1 ? parts[1].trim() : roleId;
                break;
            }
        }
        if (lpGroup == null) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.SETROLE_UNKNOWN, "role", roleId.toUpperCase())));
            return 0;
        }

        final String finalLpGroup = lpGroup;
        final String name = target.getName().getString();
        final String roleDisplay = roleId.toUpperCase();
        CommandSourceStack silent = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);

        for (String entry : rolesConfig) {
            String oldTag = entry.split(";", 2)[0].trim();
            server.getCommands().performPrefixedCommand(silent, "tag " + name + " remove " + oldTag);
        }
        server.getCommands().performPrefixedCommand(silent, "tag " + name + " add " + roleId);
        server.getCommands().performPrefixedCommand(silent, "lp user " + name + " parent set " + finalLpGroup);

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.SETROLE_SUCCESS_STAFF, "role", roleDisplay, "player", name)), true);
        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.SETROLE_SUCCESS_PLAYER, "role", roleDisplay)));
        return 1;
    }

    private static int setDayTime(CommandContext<CommandSourceStack> ctx, String type) {
        String day = StringArgumentType.getString(ctx, "day").toUpperCase();
        String time = StringArgumentType.getString(ctx, "time");
        if (!time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SCHEDULE_TIME_INVALID)));
            return 0;
        }
        try {
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> cfg = switch (day + "_" + type.toUpperCase()) {
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
            if (cfg == null) {
                ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_INVALID, "day", day)));
                return 0;
            }
            cfg.set(time);
            ScheduleConfig.SPEC.save();
            RpEssentialsScheduleManager.reload();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_UPDATED, "day", day, "type", type, "value", time)), true);
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }
    }

    private static int setDayEnabled(CommandContext<CommandSourceStack> ctx) {
        String day = StringArgumentType.getString(ctx, "day").toUpperCase();
        boolean value = BoolArgumentType.getBool(ctx, "value");
        try {
            net.neoforged.neoforge.common.ModConfigSpec.BooleanValue cfg = switch (day) {
                case "MONDAY"    -> ScheduleConfig.MONDAY_ENABLED;
                case "TUESDAY"   -> ScheduleConfig.TUESDAY_ENABLED;
                case "WEDNESDAY" -> ScheduleConfig.WEDNESDAY_ENABLED;
                case "THURSDAY"  -> ScheduleConfig.THURSDAY_ENABLED;
                case "FRIDAY"    -> ScheduleConfig.FRIDAY_ENABLED;
                case "SATURDAY"  -> ScheduleConfig.SATURDAY_ENABLED;
                case "SUNDAY"    -> ScheduleConfig.SUNDAY_ENABLED;
                default -> null;
            };
            if (cfg == null) {
                ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_INVALID, "day", day)));
                return 0;
            }
            cfg.set(value);
            ScheduleConfig.SPEC.save();
            RpEssentialsScheduleManager.reload();
            String state = value ? "enabled" : "disabled";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_ENABLED, "day", day, "state", state)), true);
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }
    }

    private static int setDeathHoursSlots(CommandContext<CommandSourceStack> ctx) {
        return setSlotsConfig(ctx, ScheduleConfig.DEATH_HOURS_SLOTS, "Death Hours Slots");
    }

    private static int setHrpToleratedSlots(CommandContext<CommandSourceStack> ctx) {
        return setSlotsConfig(ctx, ScheduleConfig.HRP_TOLERATED_SLOTS, "HRP Tolerated Slots");
    }

    private static int setHrpAllowedSlots(CommandContext<CommandSourceStack> ctx) {
        return setSlotsConfig(ctx, ScheduleConfig.HRP_ALLOWED_SLOTS, "HRP Allowed Slots");
    }

    private static int setSlotsConfig(CommandContext<CommandSourceStack> ctx,
                                       net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<java.util.List<? extends String>> config,
                                       String label) {
        String raw = StringArgumentType.getString(ctx, "slots");
        java.util.List<String> slots = java.util.Arrays.stream(raw.split(","))
                .map(String::trim).collect(java.util.stream.Collectors.toList());
        try {
            config.set(slots);
            ScheduleConfig.SPEC.save();
            RpEssentialsScheduleManager.reload();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UPDATED,
                            "label", label, "value", String.join(", ", slots))), true);
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
        }
        return 1;
    }
}
