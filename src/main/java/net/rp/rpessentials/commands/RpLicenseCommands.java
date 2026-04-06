package net.rp.rpessentials.commands;

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
import net.rp.rpessentials.RpEssentialsItems;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.profession.*;

import java.util.ArrayList;
import java.util.List;

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

        licenseNode.then(Commands.literal("check")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        LicenseManager.getLicenses(target.getUUID()).forEach(builder::suggest);
                                    } catch (Exception ignored) {}
                                    return builder.buildFuture();
                                })
                                .executes(RpLicenseCommands::checkLicense))));

        licenseNode.then(Commands.literal("giverp")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    ProfessionRestrictionManager.getAllProfessions().forEach(p -> builder.suggest(p.id));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("days_duration", IntegerArgumentType.integer(1, 365))
                                        .executes(RpLicenseCommands::giveRPLicense)))));

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
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_UNKNOWN_PROFESSION, "profession", professionId)));
            return 0;
        }

        LicenseManager.addLicense(target.getUUID(), professionId);
        ServerPlayer staff = ctx.getSource().getPlayer();
        LicenseManager.logAction("GIVE", staff, target, professionId, null);
        LicenseHelper.giveLicenseItem(server, staff, target, professionId);

        String displayName = NicknameManager.getDisplayName(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_GIVE_STAFF, "profession", profData.getFormattedName(), "player", displayName)), true);
        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_GIVE_PLAYER, "profession", profData.getFormattedName())));
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

        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "tag " + target.getName().getString() + " remove " + profession);

        ProfessionRestrictionManager.ProfessionData profData = ProfessionRestrictionManager.getProfessionData(profession);
        String profDisplayName = profData != null ? profData.getFormattedName() : profession;

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_REVOKE_STAFF, "profession", profDisplayName, "player", target.getName().getString())), true);
        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_REVOKE_PLAYER, "profession", profDisplayName)));
        return 1;
    }

    private static int listLicenses(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        List<String> licenses = LicenseManager.getLicenses(target.getUUID());

        if (licenses.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.LICENSE_LIST_NONE, "player", target.getName().getString())), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═══════════════════════════════════╗\n");
        sb.append(MessagesConfig.get(MessagesConfig.LICENSE_LIST_HEADER, "player", target.getName().getString())).append("\n");
        sb.append("§6╠═══════════════════════════════════╣\n");
        for (String profession : licenses) {
            String expiry = LicenseManager.getTempExpirationDate(target.getUUID(), profession);
            sb.append("§6║ §f").append(profession);
            if (expiry != null) sb.append(MessagesConfig.get(MessagesConfig.LICENSE_LIST_RP_EXPIRY, "date", expiry));
            sb.append("\n");
        }
        sb.append("§6╚═══════════════════════════════════╝");
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int listAllLicenses(CommandContext<CommandSourceStack> ctx) {
        var allLicenses = LicenseManager.getAllLicenses();
        if (allLicenses.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_LIST_ALL_NONE)), false);
            return 1;
        }

        var server = ctx.getSource().getServer();
        StringBuilder result = new StringBuilder("§6╔═══════════════════════════════════╗\n");
        result.append(MessagesConfig.get(MessagesConfig.LICENSE_LIST_ALL_HEADER)).append("\n");
        result.append("§6╠═══════════════════════════════════╣\n");

        for (var entry : allLicenses.entrySet()) {
            java.util.UUID uuid = entry.getKey();
            List<String> licenses = entry.getValue();
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(uuid);
            String playerName = (onlinePlayer != null) ? onlinePlayer.getName().getString()
                    : server.getProfileCache().get(uuid).map(p -> p.getName()).orElse(uuid.toString());

            String noneStr = MessagesConfig.get(MessagesConfig.LICENSE_LIST_ALL_NONE_FOR_PLAYER);
            StringBuilder licLine = new StringBuilder();
            for (String lic : licenses) {
                if (licLine.length() > 0) licLine.append("§7, ");
                String expiry = LicenseManager.getTempExpirationDate(uuid, lic);
                licLine.append("§f").append(lic);
                if (expiry != null) licLine.append(MessagesConfig.get(MessagesConfig.LICENSE_LIST_RP_EXPIRY, "date", expiry));
            }
            result.append("§6║ §f").append(playerName).append("§7: ")
                    .append(licLine.length() > 0 ? licLine : noneStr).append("\n");
        }
        result.append("§6╚═══════════════════════════════════╝");
        ctx.getSource().sendSuccess(() -> Component.literal(result.toString()), false);
        return 1;
    }

    private static int checkLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String profession = StringArgumentType.getString(ctx, "profession");
        boolean has = LicenseManager.hasLicense(target.getUUID(), profession);
        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(has ? MessagesConfig.PROFESSION_HAS_LICENSE : MessagesConfig.PROFESSION_NO_LICENSE,
                        "player", target.getName().getString(), "profession", profession)), false);
        return 1;
    }

    private static int giveRPLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String professionId = StringArgumentType.getString(ctx, "profession");
        int days = IntegerArgumentType.getInteger(ctx, "days_duration");

        ProfessionRestrictionManager.ProfessionData profData = ProfessionRestrictionManager.getProfessionData(professionId);
        if (profData == null) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_UNKNOWN_PROFESSION, "profession", professionId)));
            return 0;
        }

        String displayName = NicknameManager.getDisplayName(target);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String issued = java.time.LocalDate.now().format(fmt);
        String expires = java.time.LocalDate.now().plusDays(days).format(fmt);

        ItemStack license = new ItemStack(RpEssentialsItems.LICENSE.get());
        license.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(profData.colorCode + MessagesConfig.get(MessagesConfig.LICENSE_ITEM_NAME) + profData.displayName));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_LORE_ISSUED_TO, "player", displayName)));
        lore.add(Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_LORE_ISSUED_DATE, "date", issued)));
        lore.add(Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_LORE_VALID_UNTIL, "date", expires)));
        license.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        if (!target.getInventory().add(license)) target.drop(license, false);

        LicenseManager.addLicense(target.getUUID(), professionId);
        ServerPlayer staff = ctx.getSource().getPlayer();
        LicenseManager.addTempLicense(staff, target, professionId, days, issued, expires);
        LicenseManager.logAction("GIVE_RP", staff, target, professionId, days + " days, expires " + expires);
        ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_GIVE_RP_STAFF, "profession", profData.getFormattedName(),
                        "player", displayName, "days", String.valueOf(days), "date", expires)), true);
        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_GIVE_RP_PLAYER, "profession", profData.getFormattedName(), "date", expires)));
        return 1;
    }

    private static int reissueLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String professionId = StringArgumentType.getString(ctx, "profession");
        MinecraftServer server = ctx.getSource().getServer();

        if (!LicenseManager.hasLicense(target.getUUID(), professionId)) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.LICENSE_REISSUE_NOT_FOUND,
                            "player", target.getName().getString(), "profession", professionId)));
            return 0;
        }

        ProfessionRestrictionManager.ProfessionData profData = ProfessionRestrictionManager.getProfessionData(professionId);
        if (profData == null) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_UNKNOWN_PROFESSION, "profession", professionId)));
            return 0;
        }

        ServerPlayer staff = ctx.getSource().getPlayer();
        boolean gave = LicenseHelper.giveLicenseItem(server, staff, target, professionId);
        if (!gave) {
            ctx.getSource().sendFailure(Component.literal("§c[RPEssentials] Could not create item for: " + professionId));
            return 0;
        }

        LicenseManager.logActionSystem("REISSUE", target.getName().getString(), target.getUUID().toString(),
                professionId, "Reissued by " + (staff != null ? staff.getName().getString() : "Console"));

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_REISSUE_STAFF, "profession", profData.getFormattedName(),
                        "player", target.getName().getString())), true);
        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_REISSUE_PLAYER, "profession", profData.getFormattedName())));
        return 1;
    }

    private static int myProfession(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<String> licenses = LicenseManager.getLicenses(player.getUUID());

        if (licenses.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.LICENSE_LIST_NONE, "player", player.getName().getString())), false);
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═══════════════════════════════════╗\n");
        sb.append(MessagesConfig.get(MessagesConfig.LICENSE_LIST_HEADER, "player", player.getName().getString())).append("\n");
        sb.append("§6╠═══════════════════════════════════╣\n");

        for (String licenseId : licenses) {
            ProfessionRestrictionManager.ProfessionData data = ProfessionRestrictionManager.getProfessionData(licenseId);
            String expiry = LicenseManager.getTempExpirationDate(player.getUUID(), licenseId);
            if (data != null) sb.append("§6║ ").append(data.getFormattedName());
            else sb.append("§6║ §f").append(licenseId);
            if (expiry != null) sb.append(MessagesConfig.get(MessagesConfig.LICENSE_LIST_RP_EXPIRY, "date", expiry));
            sb.append("\n");
        }
        sb.append("§6╚═══════════════════════════════════╝");
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
