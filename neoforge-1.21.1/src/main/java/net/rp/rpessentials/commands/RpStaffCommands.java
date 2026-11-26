package net.rp.rpessentials.commands;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.Cfg;
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
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.util.TimeHelper;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.moderation.*;
import net.rp.rpessentials.profession.LicenseManager;

import java.util.*;

public class RpStaffCommands {

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> PLATFORM_SUGGESTIONS =
            (ctx, builder) -> {
                if (Cfg.ready(ModerationConfig.SPEC)) {
                    if (ModerationConfig.PLATFORMS != null && ModerationConfig.PLATFORMS.get() != null) {
                        for (String platform : ModerationConfig.PLATFORMS.get()) {
                            String[] parts = platform.split(";");
                            if (parts.length > 0) builder.suggest(parts[0]);
                        }
                    }
                }
                return builder.buildFuture();
            };

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var staffNode = Commands.literal("staff")
                .requires(RpPerms.any(RpPerm.STAFF_GAMEMODE, RpPerm.STAFF_TP, RpPerm.STAFF_EFFECT, RpPerm.STAFF_PLATFORM, RpPerm.STAFF_BROADCAST, RpPerm.STAFF_PROXIMITYSPY));

        staffNode.then(Commands.literal("gamemode")
                .requires(RpPerms.cmd(RpPerm.STAFF_GAMEMODE))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("survival").suggest("creative").suggest("adventure").suggest("spectator");
                            return builder.buildFuture();
                        })
                        .executes(RpStaffCommands::silentGamemodeSelf)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(RpStaffCommands::silentGamemodeTarget))));

        staffNode.then(Commands.literal("tp")
                .requires(RpPerms.cmd(RpPerm.STAFF_TP))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpStaffCommands::silentTeleport)));

        staffNode.then(Commands.literal("effect")
                .requires(RpPerms.cmd(RpPerm.STAFF_EFFECT))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("effect", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> {
                                    BuiltInRegistries.MOB_EFFECT.keySet().forEach(loc -> builder.suggest(loc.toString()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("amplifier", IntegerArgumentType.integer(0))
                                                .executes(RpStaffCommands::silentEffect))))));

        staffNode.then(Commands.literal("platform")
                .requires(RpPerms.cmd(RpPerm.STAFF_PLATFORM))
                .executes(RpStaffCommands::platformSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpStaffCommands::platformTarget)
                        .then(Commands.argument("platform_id", StringArgumentType.word())
                                .suggests(PLATFORM_SUGGESTIONS)
                                .executes(RpStaffCommands::platformTargetSpecific))));

        staffNode.then(Commands.literal("broadcast")
                .requires(RpPerms.cmd(RpPerm.STAFF_BROADCAST))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(RpStaffCommands::staffBroadcast)));

        staffNode.then(Commands.literal("proximityspy")
                .requires(RpPerms.cmd(RpPerm.STAFF_PROXIMITYSPY))
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean newVal = BoolArgumentType.getBool(ctx, "value");
                            if (Cfg.ready(ChatConfig.SPEC)) {
                                ChatConfig.ENABLE_PROXIMITY_SPY.set(newVal);
                                ChatConfig.SPEC.save();
                            } else { return 0; }
                            ctx.getSource().getPlayerOrException().sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(
                                    Msg.get(ctx.getSource(), newVal
                                            ? MessagesConfig.PROXIMITY_CHAT_SPY_ENABLED
                                            : MessagesConfig.PROXIMITY_CHAT_SPY_DISABLED)));
                            return 1;
                        })));

        return staffNode;
    }

    /** Registers /setplatform and /platform as standalone aliases */
    public static void registerAliases(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setplatform")
                .requires(RpPerms.cmd(RpPerm.STAFF_SETPLATFORM))
                .then(Commands.argument("platform_name", StringArgumentType.word())
                        .then(Commands.argument("dimension", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> {
                                    ctx.getSource().getServer().levelKeys().forEach(key ->
                                            builder.suggest(key.location().toString()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .suggests((ctx, builder) -> {
                                            try {
                                                builder.suggest((int) ctx.getSource().getPosition().x);
                                            } catch (Exception ignored) {}
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .suggests((ctx, builder) -> {
                                                    try {
                                                        builder.suggest((int) ctx.getSource().getPosition().y);
                                                    } catch (Exception ignored) {}
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .suggests((ctx, builder) -> {
                                                            try {
                                                                builder.suggest((int) ctx.getSource().getPosition().z);
                                                            } catch (Exception ignored) {}
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(RpStaffCommands::setPlatform)))))));

        dispatcher.register(Commands.literal("platform")
                .requires(RpPerms.cmd(RpPerm.STAFF_PLATFORM))
                .executes(RpStaffCommands::platformSelf)
                .then(Commands.argument("platform_id", StringArgumentType.word())
                        .suggests(PLATFORM_SUGGESTIONS)
                        .executes(RpStaffCommands::platformSpecificSelf)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(RpStaffCommands::platformTargetSpecific))));
    }

    /** /rpessentials stats */
    public static LiteralArgumentBuilder<CommandSourceStack> buildStats() {
        return Commands.literal("stats")
                .requires(RpPerms.cmd(RpPerm.STATS))
                .executes(RpStaffCommands::showStats);
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================

    private static int silentGamemodeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setGamemode(ctx, ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "mode"));
    }

    private static int silentGamemodeTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setGamemode(ctx, EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "mode"));
    }

    private static int setGamemode(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String modeName) {
        if (!ModerationConfig.ENABLE_SILENT_COMMANDS.get()) {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_TOOLS_SILENT_OFF));
            return 0;
        }
        GameType type = parseGameMode(modeName);
        if (type == null) {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_TOOLS_BAD_GAMEMODE));
            return 0;
        }
        target.setGameMode(type);
        String sourceName = ctx.getSource().getPlayer() != null ? ctx.getSource().getPlayer().getName().getString() : net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.UI_CONSOLE);
        logToStaff(ctx.getSource(), sourceName + " set " + target.getName().getString() + " to " + modeName);
        if (ModerationConfig.NOTIFY_TARGET.get() && target != ctx.getSource().getEntity()) {
            target.sendSystemMessage(Msg.c(target, MessagesConfig.STAFF_TOOLS_GAMEMODE_SET, "mode", modeName));
        }
        return 1;
    }

    private static int silentTeleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!ModerationConfig.ENABLE_SILENT_COMMANDS.get()) {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_TOOLS_SILENT_OFF));
            return 0;
        }
        ServerPlayer executor = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        executor.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        logToStaff(ctx.getSource(), executor.getName().getString() + " TP'd to " + target.getName().getString());
        return 1;
    }

    private static int silentEffect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!ModerationConfig.ENABLE_SILENT_COMMANDS.get()) {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_TOOLS_SILENT_OFF));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        ResourceLocation effectId = ResourceLocationArgument.getId(ctx, "effect");
        int duration = IntegerArgumentType.getInteger(ctx, "duration");
        int amplifier = IntegerArgumentType.getInteger(ctx, "amplifier");
        Optional<Holder.Reference<MobEffect>> effect =
                BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(BuiltInRegistries.MOB_EFFECT.key(), effectId));
        if (effect.isPresent()) {
            //#if forge
            //? target.addEffect(new MobEffectInstance(effect.get().value(), duration * 20, amplifier, false, false));
            //#else
            target.addEffect(new MobEffectInstance(effect.get(), duration * 20, amplifier, false, false));
            //#endif
            String sourceName = ctx.getSource().getPlayer() != null ? ctx.getSource().getPlayer().getName().getString() : net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.UI_CONSOLE);
            logToStaff(ctx.getSource(), sourceName + " gave effect " + effectId + " to " + target.getName().getString());
            if (ModerationConfig.NOTIFY_TARGET.get()) {
                target.sendSystemMessage(Msg.c(target, MessagesConfig.STAFF_TOOLS_EFFECT_APPLIED));
            }
        } else {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_TOOLS_BAD_EFFECT));
            return 0;
        }
        return 1;
    }

    private static int platformSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleportToPlatform(ctx.getSource(), ctx.getSource().getPlayerOrException(), null, "platform1");
    }

    private static int platformTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleportToPlatform(ctx.getSource(), ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "target"), "platform1");
    }

    private static int platformSpecificSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleportToPlatform(ctx.getSource(), ctx.getSource().getPlayerOrException(), null, StringArgumentType.getString(ctx, "platform_id"));
    }

    private static int platformTargetSpecific(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleportToPlatform(ctx.getSource(), ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "platform_id"));
    }

    private static int teleportToPlatform(CommandSourceStack source, ServerPlayer executor, ServerPlayer target, String platformId) {
        if (!ModerationConfig.ENABLE_PLATFORMS.get()) {
            source.sendFailure(Msg.c(source, MessagesConfig.STAFF_TOOLS_PLATFORMS_OFF));
            return 0;
        }
        for (String pData : ModerationConfig.PLATFORMS.get()) {
            String[] parts = pData.split(";");
            if (parts.length >= 6 && parts[0].equals(platformId)) {
                ResourceLocation dimRl = ResourceLocation.tryParse(parts[2]);
                ServerLevel level = dimRl == null ? null : source.getServer().getLevel(
                        ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimRl));
                if (level == null) { source.sendFailure(Msg.c(source, MessagesConfig.STAFF_TOOLS_DIM_MISSING, "dim", parts[2])); continue; }
                Vec3 pos;
                try {
                    pos = new Vec3(Double.parseDouble(parts[3]), Double.parseDouble(parts[4]), Double.parseDouble(parts[5]));
                } catch (NumberFormatException e) {
                    source.sendFailure(Msg.c(source, MessagesConfig.STAFF_TOOLS_PLATFORM_MISSING, "platform", platformId));
                    continue;
                }
                if (target != null) {
                    target.teleportTo(level, pos.x, pos.y, pos.z, 0, 0);
                    target.sendSystemMessage(Msg.c(target, MessagesConfig.STAFF_TOOLS_TP_TARGET, "name", parts[1]));
                    logToStaff(source, executor.getName().getString() + " TP'd " + target.getName().getString() + " to " + parts[1]);
                } else {
                    executor.teleportTo(level, pos.x, pos.y, pos.z, 0, 0);
                    executor.sendSystemMessage(Msg.c(executor, MessagesConfig.STAFF_TOOLS_TP_SELF, "name", parts[1]));
                }
                return 1;
            }
        }
        source.sendFailure(Msg.c(source, MessagesConfig.STAFF_TOOLS_PLATFORM_MISSING, "platform", platformId));
        return 0;
    }

    private static int setPlatform(CommandContext<CommandSourceStack> ctx) {
        final String platformName = StringArgumentType.getString(ctx, "platform_name");
        final ResourceLocation dimension = ResourceLocationArgument.getId(ctx, "dimension");
        final int x = IntegerArgumentType.getInteger(ctx, "x");
        final int y = IntegerArgumentType.getInteger(ctx, "y");
        final int z = IntegerArgumentType.getInteger(ctx, "z");
        String platformEntry = platformName + ";" + platformName + ";" + dimension + ";" + x + ";" + y + ";" + z;
        List<String> platforms = new ArrayList<>(ModerationConfig.PLATFORMS.get());
        boolean updated = false;
        for (int i = 0; i < platforms.size(); i++) {
            if (platforms.get(i).startsWith(platformName + ";")) {
                platforms.set(i, platformEntry);
                updated = true;
                break;
            }
        }
        if (!updated) platforms.add(platformEntry);
        ModerationConfig.PLATFORMS.set(platforms);
        ModerationConfig.SPEC.save();
        final boolean wasUpdated = updated;
        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(),
                wasUpdated ? MessagesConfig.STAFF_TOOLS_PLATFORM_UPDATED : MessagesConfig.STAFF_TOOLS_PLATFORM_CREATED,
                "name", platformName, "dim", String.valueOf(dimension),
                "x", String.valueOf(x), "y", String.valueOf(y), "z", String.valueOf(z)), true);
        return 1;
    }

    private static int staffBroadcast(CommandContext<CommandSourceStack> ctx) {
        String message = StringArgumentType.getString(ctx, "message");
        String staffName = ctx.getSource().getPlayer() != null ? ctx.getSource().getPlayer().getName().getString() : Msg.get(ctx.getSource(), MessagesConfig.UI_CONSOLE);
        int count = 0;
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            // Read by the staff and by everyone who can write there.
            if (!RpEssentialsPermissions.isStaff(p) && !RpPerms.has(p, RpPerm.STAFF_BROADCAST)) continue;
            p.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseColors(
                    Msg.get(p, MessagesConfig.STAFF_TOOLS_BROADCAST_FORMAT, "staff", staffName, "message", message)));
            count++;
        }
        RpEssentials.LOGGER.info("[STAFF-BROADCAST] {}: {}", staffName, message);
        final int finalCount = count;
        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_TOOLS_BROADCAST_SENT,
                "count", String.valueOf(finalCount)), false);
        return 1;
    }

    private static int showStats(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        var known = LastConnectionManager.getAllSortedByLogin();

        long totalPlaytime = 0L;
        for (var e : known) totalPlaytime += PlaytimeManager.getTotalPlaytimeMs(e.getKey());

        var licenses = LicenseManager.getAllLicenses();
        int licenseCount = licenses.values().stream().mapToInt(List::size).sum();

        java.time.LocalDate today = TimeHelper.today();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        long expiringSoon = LicenseManager.getAllTempLicenses().stream().filter(e -> {
            try {
                java.time.LocalDate expiry = java.time.LocalDate.parse(e.expiresAt, fmt);
                return !expiry.isBefore(today) && !expiry.isAfter(today.plusDays(7));
            } catch (Exception ex) { return false; }
        }).count();

        var activeWarns = WarnManager.getAll().stream().filter(w -> !w.isExpired()).toList();
        long warnedPlayers = activeWarns.stream().map(w -> w.targetUUID).distinct().count();

        String schedule;
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            schedule = !ScheduleConfig.ENABLE_SCHEDULE.get() ? Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_DISABLED)
                    : RpEssentialsScheduleManager.isServerOpen() ? Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_OPEN)
                    : Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_CLOSED);
        } else { schedule = Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_NA); }

        String deathRp = Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_OFF);
        if (Cfg.ready(RpEssentialsConfig.SPEC)) {
            if (RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED != null && RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get())
                deathRp = Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_ON);
        }

        List<Map.Entry<UUID, LastConnectionManager.ConnectionEntry>> byPlaytime = new ArrayList<>(known);
        byPlaytime.sort((a, b) -> Long.compare(
                PlaytimeManager.getTotalPlaytimeMs(b.getKey()), PlaytimeManager.getTotalPlaytimeMs(a.getKey())));

        long onlineVisible = server.getPlayerList().getPlayers().stream()
                .filter(p -> !VanishManager.canHide(VanishManager.Aspect.LIST, ctx.getSource(), p.getUUID()))
                .count();

        StringBuilder sb = new StringBuilder();
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.STATS_HEADER)).append("\n").append(RpChatUi.LINE).append("\n");

        sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_S_SERVER)).append("\n")
                .append(RpChatUi.row(
                        RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_ONLINE), "§f" + onlineVisible),
                        RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_KNOWN), "§f" + known.size()),
                        RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_PLAYTIME), "§f" + PlaytimeManager.format(ctx.getSource().getPlayer(), totalPlaytime))))
                .append(RpChatUi.row(
                        RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_NICKNAMES), "§f" + NicknameManager.count()),
                        RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_SCHEDULE), schedule)));

        if (net.rp.rpessentials.config.ProfessionConfig.enabled()) {
            sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_S_PROFESSIONS)).append("\n")
                    .append(RpChatUi.row(
                            RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_HOLDERS), Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_V_HOLDERS, "count", String.valueOf(licenses.size()), "licenses", String.valueOf(licenseCount))),
                            RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_EXPIRING), "§f" + expiringSoon)));
        }

        sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_S_MODERATION)).append("\n")
                .append(RpChatUi.row(
                        RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_WARNS), Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_V_WARNS, "count", String.valueOf(activeWarns.size()), "players", String.valueOf(warnedPlayers))),
                        RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_MUTES), "§f" + MuteManager.getAllMutes().size())))
                .append(RpChatUi.row(
                        RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_DEATHRP), deathRp),
                        RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_OVERRIDES), "§f" + DeathRPManager.getAllOverrides().size()),
                        RpChatUi.kv(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_L_DEATHS), "§f" + DeathRPManager.getAllHistory().size())));

        sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_S_PLAYTIME)).append("\n");
        for (int i = 0; i < Math.min(3, byPlaytime.size()); i++) {
            UUID id = byPlaytime.get(i).getKey();
            sb.append("§8").append(i + 1).append(". §f").append(LastConnectionManager.resolveName(server, id))
                    .append(" §8- §7").append(PlaytimeManager.format(ctx.getSource().getPlayer(), PlaytimeManager.getTotalPlaytimeMs(id))).append("\n");
        }

        sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_STATS_S_RECENT)).append("\n");
        for (int i = 0; i < Math.min(5, known.size()); i++) {
            var e = known.get(i);
            boolean online = server.getPlayerList().getPlayer(e.getKey()) != null
                    && !VanishManager.canHide(VanishManager.Aspect.LIST, ctx.getSource(), e.getKey());
            sb.append(online ? "§a● " : "§7○ ").append("§e").append(LastConnectionManager.resolveName(server, e.getKey()))
                    .append(" §8- §7").append(e.getValue().lastLogin != null ? e.getValue().lastLogin : "?").append("\n");
        }
        sb.append(RpChatUi.LINE);

        String msg = sb.toString();
        RpChatUi.sendFormatted(ctx, msg);
        return 1;
    }

    // =========================================================================
    // UTILS
    // =========================================================================

    static void logToStaff(CommandSourceStack source, String msg) {
        if (ModerationConfig.LOG_TO_STAFF.get()) {
            source.getServer().getPlayerList().getPlayers().forEach(p -> {
                if (RpPerms.has(p, RpPerm.NOTIFY))
                    p.sendSystemMessage(Msg.c(p, MessagesConfig.STAFF_TOOLSX_STAFF_LOG, "message", msg));
            });
        }
        if (ModerationConfig.LOG_TO_CONSOLE.get()) RpEssentials.LOGGER.info("[StaffLog] " + msg);
    }

    /** Used by multiple command handlers */
    static void broadcastToStaff(MinecraftServer server, String message) {
        if (message == null || message.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RpPerms.has(p, RpPerm.NOTIFY)) p.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(message));
        }
    }

    private static GameType parseGameMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "survival", "s", "0" -> GameType.SURVIVAL;
            case "creative", "c", "1" -> GameType.CREATIVE;
            case "adventure", "a", "2" -> GameType.ADVENTURE;
            case "spectator", "sp", "3" -> GameType.SPECTATOR;
            default -> null;
        };
    }
}
