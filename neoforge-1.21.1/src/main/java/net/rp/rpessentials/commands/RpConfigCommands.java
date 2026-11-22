package net.rp.rpessentials.commands;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.lang.Msg;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.*;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.moderation.*;
import net.rp.rpessentials.network.HideNametagsPacket;
import net.rp.rpessentials.profession.LicenseManager;
import net.rp.rpessentials.profession.PendingProfileManager;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.List;

import static net.rp.rpessentials.commands.RpChatUi.*;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.core.BackupManager;
import net.rp.rpessentials.core.RpEssentialsDataPaths;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.gameplay.TabBlur;
import net.rp.rpessentials.gameplay.TabListCache;
import net.rp.rpessentials.gameplay.WorldBorderManager;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.ImmersivePresetHelper;
import net.rp.rpessentials.util.RpEssentialsPatternUtils;

public class RpConfigCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var configNode = Commands.literal("config")
                .requires(RpPerms.cmd(RpPerm.CONFIG));

        configNode.then(Commands.literal("reload").executes(RpConfigCommands::reloadConfig));

        configNode.then(Commands.literal("status").executes(RpConfigCommands::showStatus));

        configNode.then(Commands.literal("validate").executes(RpConfigCommands::validateConfig));

        configNode.then(Commands.literal("backup").executes(ctx -> {
            BackupManager.run(RpEssentialsConfig.BACKUP_KEEP.get());
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_BACKUP), true);
            return 1;
        }));

        configNode.then(Commands.literal("exportlang").executes(ctx -> {
            try {
                java.nio.file.Path p = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("rpessentials/en_us.json");
                java.nio.file.Files.createDirectories(p.getParent());
                java.nio.file.Files.writeString(p, ConfigInspector.exportDefaults(MessagesConfig.class, ProfessionConfig.class));
                ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_EXPORT_DONE, "path", p.toString()), false);
                return 1;
            } catch (Exception e) {
                ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_EXPORT_FAIL, "error", String.valueOf(e.getMessage())));
                return 0;
            }
        }));

        // /rpessentials config set [file] <option> <value>: any option of any file.
        configNode.then(ConfigSetCommand.build());

        return configNode;
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================
    static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        // Schedules et caches dérivés
        RpEssentialsRoleManager.clearAll();
        RpEssentialsRoleManager.reload();
        RpEssentialsScheduleManager.reload();
        WarnManager.reload();
        ProfessionRestrictionManager.reloadCache();
        RpEssentialsPatternUtils.clearCache();
        ImmersivePresetHelper.clearCache();
        RpEssentialsPermissions.clearCache();
        TabListCache.reload();
        TabBlur.reload();
        RpEssentials.clearMetaCache();
        WorldBorderManager.reloadConfig();
        RpEssentialsScheduleManager.enforceOnline(ctx.getSource().getServer());
        net.rp.rpessentials.permission.RpPerms.refreshAll(ctx.getSource().getServer());

        // Données persistantes (rechargement depuis fichier)
        NicknameManager.reload();
        LicenseManager.reload();
        MuteManager.reload();
        LastConnectionManager.reload();
        AbsenceManager.reload();
        WarnManager.reload();
        PendingActionManager.reload();
        VanishManager.refreshAll(ctx.getSource().getServer());
        PendingProfileManager.reload();

        // Sync client
        if (Cfg.ready(RpEssentialsConfig.SPEC)) {
            boolean hideNametags = RpEssentialsConfig.HIDE_NAMETAGS.get();
            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player ->
                    PacketDistributor.sendToPlayer(player, new HideNametagsPacket(hideNametags)));
        }

        // Re-sync des restrictions de profession pour tous les joueurs connectés
        try {
            for (net.minecraft.server.level.ServerPlayer player :
                    ctx.getSource().getServer().getPlayerList().getPlayers()) {
                net.rp.rpessentials.profession.ProfessionSyncHelper.syncToPlayer(player);
            }
        } catch (Exception ignored) {}

        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_RELOADED), true);
        String dataFolder = RpEssentialsDataPaths.getDataFolder().getAbsolutePath();
        RpEssentials.LOGGER.info("[RpEssentials] Data layer ready — {} nickname(s), {} license(s), {} warn(s), {} mute(s). Data folder: {}",
                NicknameManager.count(),
                LicenseManager.getAllLicenses().size(),
                WarnManager.getAll().size(),
                MuteManager.getAllMutes().size(),
                dataFolder);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        java.util.function.Function<ModConfigSpec.ConfigValue<String>, String> L = cv -> Msg.get(ctx.getSource(), cv);
        String schedule;
        try {
            schedule = !ScheduleConfig.ENABLE_SCHEDULE.get() ? L.apply(MessagesConfig.STAFF_STATS_DISABLED)
                    : RpEssentialsScheduleManager.isServerOpen() ? L.apply(MessagesConfig.STAFF_STATS_OPEN)
                    : L.apply(MessagesConfig.STAFF_STATS_CLOSED);
        } catch (Exception e) { schedule = L.apply(MessagesConfig.STAFF_STATS_NA); }

        String luckPerms;
        try { net.luckperms.api.LuckPermsProvider.get(); luckPerms = L.apply(MessagesConfig.STAFF_CFG_DETECTED); }
        catch (NoClassDefFoundError | IllegalStateException e) { luckPerms = L.apply(MessagesConfig.STAFF_CFG_ABSENT); }

        StringBuilder sb = new StringBuilder();
        sb.append(L.apply(MessagesConfig.STAFF_CFG_STATUS_TITLE)).append("\n").append(LINE).append("\n");

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_OBFUSCATION)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_BLUR), flag(ctx.getSource(), () -> RpEssentialsConfig.ENABLE_BLUR.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_RANGE), value(ctx.getSource(), () -> RpEssentialsConfig.PROXIMITY_DISTANCE.get()) + L.apply(MessagesConfig.STAFF_CFG_BLOCKS)),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_SNEAK), flag(ctx.getSource(), () -> RpEssentialsConfig.ENABLE_SNEAK_STEALTH.get()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_OPS_SEE_ALL), flag(ctx.getSource(), () -> RpEssentialsConfig.OPS_SEE_ALL.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_HIDE_NAMETAGS), flag(ctx.getSource(), () -> RpEssentialsConfig.HIDE_NAMETAGS.get()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_WHITELIST), value(ctx.getSource(), () -> RpEssentialsConfig.WHITELIST.get().size())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_BLACKLIST), value(ctx.getSource(), () -> RpEssentialsConfig.BLACKLIST.get().size())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_ALWAYS_VISIBLE), value(ctx.getSource(), () -> RpEssentialsConfig.ALWAYS_VISIBLE_LIST.get().size()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_PERMISSIONS)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_LUCKPERMS), luckPerms),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_USE_GROUPS), flag(ctx.getSource(), () -> RpEssentialsConfig.USE_LUCKPERMS_GROUPS.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_OP_LEVEL), value(ctx.getSource(), () -> RpEssentialsConfig.OP_LEVEL_BYPASS.get()))))
                .append(row(kv(L.apply(MessagesConfig.STAFF_CFG_L_ROLES), value(ctx.getSource(), () -> RpEssentialsConfig.ROLES.get().size()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_SCHEDULE)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_SCHEDULE), schedule),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_DEATH_RP), flag(ctx.getSource(), () -> RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_DEATH_HOURS), flag(ctx.getSource(), () -> ScheduleConfig.DEATH_HOURS_ENABLED.get()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_WHITELIST_REMOVAL), flag(ctx.getSource(), () -> RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_HRP_HOURS), flag(ctx.getSource(), () -> ScheduleConfig.ENABLE_HRP_HOURS.get()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_CHAT)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_FORMAT), flag(ctx.getSource(), () -> ChatConfig.ENABLE_CHAT_FORMAT.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_PROXIMITY), flag(ctx.getSource(), () -> ChatConfig.ENABLE_PROXIMITY_CHAT.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_MARKDOWN), flag(ctx.getSource(), () -> ChatConfig.MARKDOWN_ENABLED.get()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_TIMESTAMP), flag(ctx.getSource(), () -> ChatConfig.ENABLE_TIMESTAMP.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_JOIN_LEAVE), flag(ctx.getSource(), () -> ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_COLOR), value(ctx.getSource(), () -> ChatConfig.CHAT_MESSAGE_COLOR.get()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_BORDER)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_ENABLED), flag(ctx.getSource(), () -> RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_DISTANCE), value(ctx.getSource(), () -> RpEssentialsConfig.WORLD_BORDER_DISTANCE.get()) + L.apply(MessagesConfig.STAFF_CFG_BLOCKS)),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_TELEPORT), flag(ctx.getSource(), () -> RpEssentialsConfig.WORLD_BORDER_TELEPORT_ENABLED.get()))))
                .append(row(kv(L.apply(MessagesConfig.STAFF_CFG_L_NAMED_ZONES), value(ctx.getSource(), () -> RpEssentialsConfig.NAMED_ZONES.get().size()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_MODERATION)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_WARNS), flag(ctx.getSource(), () -> ModerationConfig.ENABLE_WARN_SYSTEM.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_MUTES), flag(ctx.getSource(), () -> ModerationConfig.ENABLE_MUTE_SYSTEM.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_LAST_CONNECTION), flag(ctx.getSource(), () -> ModerationConfig.ENABLE_LAST_CONNECTION.get()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_AUTO_UNWHITELIST), flag(ctx.getSource(), () -> ModerationConfig.AUTO_UNWHITELIST_ENABLED.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_SILENT_COMMANDS), flag(ctx.getSource(), () -> ModerationConfig.ENABLE_SILENT_COMMANDS.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_PLATFORMS), flag(ctx.getSource(), () -> ModerationConfig.ENABLE_PLATFORMS.get()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_PROFESSIONS)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_PROFESSIONS), value(ctx.getSource(), () -> ProfessionConfig.PROFESSIONS.get().size())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_BLOCKED_CRAFTS), value(ctx.getSource(), () -> ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get().size())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_BLOCKED_ITEMS), value(ctx.getSource(), () -> ProfessionConfig.GLOBAL_BLOCKED_ITEMS.get().size()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_DICE), flag(ctx.getSource(), () -> RpConfig.ENABLE_DICE_SYSTEM.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_SELF_NICK), flag(ctx.getSource(), () -> RpConfig.ENABLE_SELF_NICK.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_WELCOME), flag(ctx.getSource(), () -> ScheduleConfig.ENABLE_WELCOME.get()))));
        sb.append(LINE);

        String msg = sb.toString();
        RpChatUi.sendFormatted(ctx, msg);
        return 1;
    }

    static int validateConfig(CommandContext<CommandSourceStack> ctx) {
        List<String> problems = ConfigValidator.validate(ctx.getSource().getPlayer());
        if (problems.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_VALID), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder(Msg.get(ctx.getSource(), MessagesConfig.STAFF_CFG_PROBLEMS, "count", String.valueOf(problems.size())) + "\n" + LINE + "\n");
        for (String p : problems) sb.append("§c- §f").append(p).append("\n");
        sb.append(LINE);
        RpChatUi.sendFormatted(ctx, sb.toString());
        return 0;
    }

    // =========================================================================
    // HELPERS (package-private, reused by other command classes)
    // =========================================================================

    /** Name of an option as written in the config file (its last path element). */
    static String optName(ModConfigSpec.ConfigValue<?> cv) {
        java.util.List<String> p = cv.getPath();
        return p.get(p.size() - 1);
    }

    static int updateConfigInt(CommandContext<CommandSourceStack> ctx, ModConfigSpec.IntValue config) {
        String name = optName(config);
        int val = IntegerArgumentType.getInteger(ctx, "value");
        config.set(val);
        config.save();
        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_VALUE_SET, "name", name, "value", String.valueOf(val)), true);
        return 1;
    }

    static int updateConfigBool(CommandContext<CommandSourceStack> ctx, ModConfigSpec.BooleanValue config) {
        String name = optName(config);
        boolean val = BoolArgumentType.getBool(ctx, "value");
        config.set(val);
        config.save();
        if (config == RpEssentialsConfig.HIDE_NAMETAGS) {
            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player ->
                    PacketDistributor.sendToPlayer(player, new HideNametagsPacket(val)));
        }
        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_TOGGLE_SET, "name", name,
                "state", Msg.get(ctx.getSource(), val ? MessagesConfig.STAFF_CFG_ENABLED : MessagesConfig.STAFF_CFG_DISABLED)), true);
        return 1;
    }

    static int updateConfigString(CommandContext<CommandSourceStack> ctx,
                                   ModConfigSpec.ConfigValue<String> configValue) {
        String label = configValue == null ? "" : optName(configValue);
        if (Cfg.ready(MessagesConfig.SPEC)) {
            String value = StringArgumentType.getString(ctx, "value");
            if (configValue == null) {
                ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_UNAVAILABLE)));
                return 0;
            }
            configValue.set(value);
            configValue.save();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_UPDATED, "label", label, "value", value)), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_NOT_BUILT)));
            return 0;
        }
    }

    static int updateConfigDouble(CommandContext<CommandSourceStack> ctx,
                                   ModConfigSpec.DoubleValue configValue) {
        String label = configValue == null ? "" : optName(configValue);
        if (Cfg.ready(MessagesConfig.SPEC)) {
            double value = DoubleArgumentType.getDouble(ctx, "value");
            if (configValue == null) {
                ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_UNAVAILABLE)));
                return 0;
            }
            configValue.set(value);
            configValue.save();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_UPDATED, "label", label, "value", String.valueOf(value))), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_NOT_BUILT)));
            return 0;
        }
    }
}
