package net.rp.rpessentials.commands;

import net.rp.rpessentials.lang.Msg;
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
import net.minecraft.world.item.ItemStack;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.items.RpEssentialsItems;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.util.TimeHelper;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.profession.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RpLicenseCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var licenseNode = Commands.literal("license")
                .requires(source -> source.hasPermission(2));

        licenseNode.then(Commands.literal("give")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        List<String> current = LicenseManager.getLicenses(target.getUUID());
                                        ProfessionRestrictionManager.getAllProfessions().stream()
                                                .filter(p -> !current.contains(p.id))
                                                .forEach(p -> builder.suggest(p.id));
                                    } catch (Exception e) {
                                        ProfessionRestrictionManager.getAllProfessions().forEach(p -> builder.suggest(p.id));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(RpLicenseCommands::giveLicense))));

        licenseNode.then(Commands.literal("revoke")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        LicenseManager.getLicenses(target.getUUID()).forEach(builder::suggest);
                                    } catch (Exception e) {
                                        builder.suggest("chasseur").suggest("mineur");
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(RpLicenseCommands::revokeLicense))));

        licenseNode.then(Commands.literal("list")
                .executes(RpLicenseCommands::listAllLicenses)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(RpLicenseCommands::listLicenses)));

        licenseNode.then(Commands.literal("tempgive")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        List<String> current = LicenseManager.getLicenses(target.getUUID());
                                        ProfessionRestrictionManager.getAllProfessions().stream()
                                                .filter(p -> !current.contains(p.id))
                                                .forEach(p -> builder.suggest(p.id));
                                    } catch (Exception e) {
                                        ProfessionRestrictionManager.getAllProfessions().forEach(p -> builder.suggest(p.id));
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("days_duration", IntegerArgumentType.integer(1, 365))
                                        .executes(RpLicenseCommands::giveTempLicense)))));

        licenseNode.then(Commands.literal("reissue")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        LicenseManager.getLicenses(target.getUUID()).forEach(builder::suggest);
                                    } catch (Exception e) {
                                        ProfessionRestrictionManager.getAllProfessions().forEach(p -> builder.suggest(p.id));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(RpLicenseCommands::reissueLicense))));

        licenseNode.then(Commands.literal("audit")
                .executes(ctx -> licenseAudit(ctx, null, 20))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            LicenseManager.getAuditLog().stream()
                                    .map(e -> e.targetName).distinct().sorted().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> licenseAudit(ctx, StringArgumentType.getString(ctx, "player"), 20))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> licenseAudit(ctx,
                                        StringArgumentType.getString(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "count"))))));

        return licenseNode;
    }

    /** Registers /myprofession and /myjob standalone commands */
    public static void registerAliases(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("myprofession")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(RpLicenseCommands::myProfession));
        dispatcher.register(Commands.literal("myjob")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(RpLicenseCommands::myProfession));
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================

    private static int giveLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String professionId = StringArgumentType.getString(ctx, "profession");
        MinecraftServer server = ctx.getSource().getServer();

        ProfessionRestrictionManager.ProfessionData profData = ProfessionRestrictionManager.getProfessionData(professionId);
        if (profData == null) {
            ctx.getSource().sendFailure(ColorHelper.parseSection(Msg.get(ctx.getSource(), MessagesConfig.LICENSE_UNKNOWN_PROFESSION, "profession", professionId)));
            return 0;
        }
        if (LicenseManager.hasLicense(target.getUUID(), professionId)) {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_LIC_ALREADY,
                    "player", target.getName().getString(), "profession", professionId));
            return 0;
        }

        LicenseManager.addLicense(target.getUUID(), professionId);
        ServerPlayer staff = ctx.getSource().getPlayer();
        LicenseManager.logAction("GIVE", staff, target, professionId, null);
        LicenseHelper.giveLicenseItem(server, staff, target, professionId);

        String displayName = NicknameManager.getDisplayName(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                Msg.get(ctx.getSource(), MessagesConfig.LICENSE_GIVE_STAFF, "profession", profData.getFormattedName(), "player", displayName)), true);
        target.sendSystemMessage(Component.literal(
                ServerLang.get(target, MessagesConfig.LICENSE_GIVE_PLAYER, "profession", profData.getFormattedName())));
        return 1;
    }

    private static int revokeLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String profession = StringArgumentType.getString(ctx, "profession");
        MinecraftServer server = ctx.getSource().getServer();

        LicenseManager.removeLicense(target.getUUID(), profession);
        ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);
        ServerPlayer staff = ctx.getSource().getPlayer();
        LicenseManager.logAction("REVOKE", staff, target, profession, null);
        TempLicenseExpirationManager.markRevokedLicenseItems(target);

        if (!ProfessionRestrictionManager.isReservedTag(profession)) {
            target.removeTag(profession);
        }
        ProfessionRestrictionManager.ProfessionData profData = ProfessionRestrictionManager.getProfessionData(profession);
        String profDisplayName = profData != null ? profData.getFormattedName() : profession;

        ctx.getSource().sendSuccess(() -> Component.literal(
                Msg.get(ctx.getSource(), MessagesConfig.LICENSE_REVOKE_STAFF, "profession", profDisplayName, "player", target.getName().getString())), true);
        target.sendSystemMessage(Component.literal(
                ServerLang.get(target, MessagesConfig.LICENSE_REVOKE_PLAYER, "profession", profDisplayName)));
        return 1;
    }

    private static int sendLicenseList(CommandContext<CommandSourceStack> ctx, UUID uuid, String name) {
        List<String> licenses = LicenseManager.getLicenses(uuid);
        if (licenses.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    Msg.get(ctx.getSource(), MessagesConfig.LICENSE_LIST_NONE, "player", name)), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.LICENSE_LIST_HEADER, "player", name)).append("\n")
                .append(RpChatUi.LINE).append("\n");
        for (String profession : licenses) {
            ProfessionRestrictionManager.ProfessionData data = ProfessionRestrictionManager.getProfessionData(profession);
            String expiry = LicenseManager.getTempExpirationDate(uuid, profession);
            sb.append(data != null ? data.getFormattedName() : "§f" + profession);
            if (expiry != null) sb.append(Msg.get(ctx.getSource(), MessagesConfig.LICENSE_LIST_RP_EXPIRY, "date", expiry));
            sb.append("\n");
        }
        sb.append(RpChatUi.LINE);
        String msg = sb.toString();
        RpChatUi.sendFormatted(ctx, msg);
        return 1;
    }

    private static int listLicenses(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        return sendLicenseList(ctx, target.getUUID(), target.getName().getString());
    }

    private static int myProfession(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return sendLicenseList(ctx, player.getUUID(), player.getName().getString());
    }

    private static int listAllLicenses(CommandContext<CommandSourceStack> ctx) {
        var allLicenses = LicenseManager.getAllLicenses();
        if (allLicenses.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.LICENSE_LIST_ALL_NONE)), false);
            return 1;
        }

        var server = ctx.getSource().getServer();
        StringBuilder sb = new StringBuilder();
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.LICENSE_LIST_ALL_HEADER)).append("\n")
                .append(RpChatUi.LINE).append("\n");

        for (var entry : allLicenses.entrySet()) {
            UUID uuid = entry.getKey();
            StringBuilder licLine = new StringBuilder();
            for (String lic : entry.getValue()) {
                if (licLine.length() > 0) licLine.append("§7, ");
                ProfessionRestrictionManager.ProfessionData data = ProfessionRestrictionManager.getProfessionData(lic);
                licLine.append(data != null ? data.getFormattedName() : "§f" + lic);
                String expiry = LicenseManager.getTempExpirationDate(uuid, lic);
                if (expiry != null) licLine.append(Msg.get(ctx.getSource(), MessagesConfig.LICENSE_LIST_RP_EXPIRY, "date", expiry));
            }
            sb.append("§f").append(LastConnectionManager.resolveName(server, uuid)).append("§7: ").append(licLine).append("\n");
        }
        sb.append(RpChatUi.LINE);
        String msg = sb.toString();
        RpChatUi.sendFormatted(ctx, msg);
        return 1;
    }

    private static int giveTempLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String professionId = StringArgumentType.getString(ctx, "profession");
        int days = IntegerArgumentType.getInteger(ctx, "days_duration");
        MinecraftServer server = ctx.getSource().getServer();

        ProfessionRestrictionManager.ProfessionData profData = ProfessionRestrictionManager.getProfessionData(professionId);
        if (profData == null) {
            ctx.getSource().sendFailure(ColorHelper.parseSection(Msg.get(ctx.getSource(), MessagesConfig.LICENSE_UNKNOWN_PROFESSION, "profession", professionId)));
            return 0;
        }
        if (LicenseManager.hasLicense(target.getUUID(), professionId)) {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_LIC_ALREADY_TEMP,
                    "player", target.getName().getString(), "profession", professionId));
            return 0;
        }

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String issued = TimeHelper.today().format(fmt);
        String expires = TimeHelper.today().plusDays(days).format(fmt);
        String displayName = NicknameManager.getDisplayName(target);
        ServerPlayer staff = ctx.getSource().getPlayer();

        LicenseManager.addLicense(target.getUUID(), professionId);
        LicenseManager.addTempLicense(staff, target, professionId, days, issued, expires);
        LicenseManager.logAction("TEMPGIVE", staff, target, professionId, net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.AUDIT_TEMP, "days", String.valueOf(days), "date", String.valueOf(expires)));
        LicenseHelper.giveLicenseItem(server, staff, target, professionId, expires);

        ctx.getSource().sendSuccess(() -> Component.literal(
                Msg.get(ctx.getSource(), MessagesConfig.LICENSE_GIVE_RP_STAFF, "profession", profData.getFormattedName(),
                        "player", displayName, "days", String.valueOf(days), "date", expires)), true);
        target.sendSystemMessage(Component.literal(
                ServerLang.get(target, MessagesConfig.LICENSE_GIVE_RP_PLAYER, "profession", profData.getFormattedName(), "date", expires)));
        return 1;
    }

    private static int reissueLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String professionId = StringArgumentType.getString(ctx, "profession");
        MinecraftServer server = ctx.getSource().getServer();

        if (!LicenseManager.hasLicense(target.getUUID(), professionId)) {
            ctx.getSource().sendFailure(Component.literal(
                    Msg.get(ctx.getSource(), MessagesConfig.LICENSE_REISSUE_NOT_FOUND,
                            "player", target.getName().getString(), "profession", professionId)));
            return 0;
        }

        ProfessionRestrictionManager.ProfessionData profData = ProfessionRestrictionManager.getProfessionData(professionId);
        if (profData == null) {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.LICENSE_UNKNOWN_PROFESSION, "profession", professionId)));
            return 0;
        }

        ServerPlayer staff = ctx.getSource().getPlayer();
        boolean gave = LicenseHelper.giveLicenseItem(server, staff, target, professionId,
                LicenseManager.getTempExpirationDate(target.getUUID(), professionId));
        if (!gave) {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_LIC_CREATE_FAILED, "profession", professionId));
            return 0;
        }

        LicenseManager.logActionSystem("REISSUE", target.getName().getString(), target.getUUID().toString(),
                professionId, net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.AUDIT_REISSUED, "staff", staff != null ? staff.getName().getString() : net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.UI_CONSOLE)));

        ctx.getSource().sendSuccess(() -> Component.literal(
                Msg.get(ctx.getSource(), MessagesConfig.LICENSE_REISSUE_STAFF, "profession", profData.getFormattedName(),
                        "player", target.getName().getString())), true);
        target.sendSystemMessage(Component.literal(
                ServerLang.get(target, MessagesConfig.LICENSE_REISSUE_PLAYER, "profession", profData.getFormattedName())));
        return 1;
    }

    private static int licenseAudit(CommandContext<CommandSourceStack> ctx, String playerFilter, int count) {
        List<LicenseManager.AuditEntry> log = LicenseManager.getAuditLog();

        if (playerFilter != null) {
            String filter = playerFilter.toLowerCase();
            log = log.stream()
                    .filter(e -> e.targetName != null && e.targetName.toLowerCase().contains(filter))
                    .collect(java.util.stream.Collectors.toList());
        }

        if (log.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_LIC_AUDIT_NONE), false);
            return 1;
        }

        List<LicenseManager.AuditEntry> recent = log.subList(Math.max(0, log.size() - count), log.size());
        StringBuilder sb = new StringBuilder();
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.LIC_AUDIT_HEADER, "shown", String.valueOf(recent.size()), "total", String.valueOf(log.size()))).append("\n")
                .append(RpChatUi.LINE).append("\n");
        for (LicenseManager.AuditEntry e : recent) {
            String actionColor = switch (e.action) {
                case "GIVE", "TEMPGIVE", "GIVE_RP" -> "§a";
                case "REVOKE", "TEMPEXPIRE", "EXPIRE_RP" -> "§c";
                default -> "§7";
            };
            sb.append(Msg.get(ctx.getSource(), MessagesConfig.LIC_AUDIT_LINE, "color", actionColor, "action", e.action,
                    "player", String.valueOf(e.targetName), "staff", String.valueOf(e.staffName),
                    "profession", String.valueOf(e.profession), "date", String.valueOf(e.timestamp))).append("\n");
        }
        sb.append(RpChatUi.LINE);
        String msg = sb.toString();
        RpChatUi.sendFormatted(ctx, msg);
        return 1;
    }

}
