package net.rp.rpessentials;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.core.registries.Registries;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.RpConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.identity.RpEssentialsChatFormatter;
import net.rp.rpessentials.RpEssentialsRoleManager;
import net.rp.rpessentials.moderation.MuteManager;

import java.util.List;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class RpEssentialsRpCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Supprime le /me vanilla et le remplace
        dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals("me"));

        // ── /me <action> — alias direct de /rp action ──────────────────────
        dispatcher.register(Commands.literal("me")
                .then(Commands.argument("action", StringArgumentType.greedyString())
                        .executes(ctx -> executeAction(ctx,
                                StringArgumentType.getString(ctx, "action")))));

        // ── /rp ────────────────────────────────────────────────────────────
        var rpRoot = Commands.literal("rp");

        // /rp afk
        rpRoot.then(Commands.literal("afk")
                .executes(RpEssentialsRpCommands::executeAfk));

        rpRoot.then(Commands.literal("help")
                .executes(ctx -> {
                    boolean isStaff = RpEssentialsPermissions.isStaff(ctx.getSource().getPlayer());
                    return RpEssentialsCommands.showHelpPublic(ctx, isStaff);
                }));

        // /rp commerce <message>
        rpRoot.then(Commands.literal("commerce")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> executeCommerce(ctx,
                                StringArgumentType.getString(ctx, "message")))));

        // /rp incognito <message>
        rpRoot.then(Commands.literal("incognito")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> executeIncognito(ctx,
                                StringArgumentType.getString(ctx, "message")))));

        // /rp action <action>
        rpRoot.then(Commands.literal("action")
                .then(Commands.argument("action", StringArgumentType.greedyString())
                        .executes(ctx -> executeAction(ctx,
                                StringArgumentType.getString(ctx, "action")))));

        // /rp selfnick [nickname]
        rpRoot.then(Commands.literal("selfnick")
                .requires(src -> src.getEntity() instanceof ServerPlayer && isSelfNickEnabled())
                .executes(ctx -> executeSelfNick(ctx, ""))
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(ctx -> executeSelfNick(ctx,
                                StringArgumentType.getString(ctx, "nickname")))));

        // /rp annonce <title|chat> <message>
        rpRoot.then(Commands.literal("annonce")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()))
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("title");
                            builder.suggest("chat");
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> executeAnnonce(ctx,
                                        StringArgumentType.getString(ctx, "type"),
                                        StringArgumentType.getString(ctx, "message"))))));

        // /roll [dice]
        dispatcher.register(Commands.literal("roll")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    try {
                        if (!RpConfig.ENABLE_DICE_SYSTEM.get()) {
                            ctx.getSource().sendFailure(Component.literal("§c[DICE] Dice system is disabled."));
                            return 0;
                        }
                    } catch (IllegalStateException ignored) { return 0; }
                    List<DiceManager.DiceType> dice = DiceManager.getAvailableDice();
                    if (dice.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("§c[DICE] No dice configured."));
                        return 0;
                    }
                    DiceManager.roll(player, dice.get(0).name());
                    return 1;
                })
                .then(Commands.argument("dice", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            DiceManager.getAvailableDice().forEach(d -> builder.suggest(d.name()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String diceName = StringArgumentType.getString(ctx, "dice");
                            try {
                                if (!RpConfig.ENABLE_DICE_SYSTEM.get()) {
                                    ctx.getSource().sendFailure(Component.literal("§c[DICE] Dice system is disabled."));
                                    return 0;
                                }
                            } catch (IllegalStateException ignored) { return 0; }
                            boolean success = DiceManager.roll(player, diceName);
                            if (!success) {
                                ctx.getSource().sendFailure(Component.literal("§c[DICE] Unknown dice type: §f" + diceName));
                                return 0;
                            }
                            return 1;
                        })));

        dispatcher.register(rpRoot);
    }

    // =========================================================================
    // /rp afk
    // =========================================================================

    private static int executeAfk(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();

        String dimId = "minecraft:overworld";
        double x = 0, y = 64, z = 0;
        float yaw = 0, pitch = 0;

        try {
            dimId = RpConfig.AFK_DIMENSION.get();
            x     = RpConfig.AFK_X.get();
            y     = RpConfig.AFK_Y.get();
            z     = RpConfig.AFK_Z.get();
            yaw   = RpConfig.AFK_YAW.get().floatValue();
            pitch = RpConfig.AFK_PITCH.get().floatValue();
        } catch (IllegalStateException ignored) {}

        ResourceKey<Level> dimKey = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(dimId));
        ServerLevel level = server.getLevel(dimKey);

        if (level == null) {
            RpEssentials.LOGGER.warn("[RP-AFK] Dimension '{}' not found.", dimId);
            ctx.getSource().sendFailure(Component.literal("§c[ERROR] AFK dimension not found: " + dimId));
            return 0;
        }

        player.teleportTo(level, x, y, z, java.util.Set.of(), yaw, pitch);
        player.displayClientMessage(
                ColorHelper.parseColors(MessagesConfig.get(MessagesConfig.RP_AFK_TELEPORT)), false);
        return 1;
    }

    // =========================================================================
    // /rp commerce <message>
    // =========================================================================
    private static int executeCommerce(CommandContext<CommandSourceStack> ctx, String message)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (MuteManager.denyIfMuted(player)) return 0;

        if (RpCooldownManager.isOnCooldown(player.getUUID(), "commerce")) {
            long remaining = RpCooldownManager.getRemainingSeconds(player.getUUID(), "commerce");
            player.displayClientMessage(ColorHelper.parseColors(
                    MessagesConfig.get(MessagesConfig.RP_COOLDOWN_MESSAGE,
                            "command", "rp commerce", "seconds", String.valueOf(remaining))), true);
            return 0;
        }
        RpCooldownManager.setCooldown(player.getUUID(), "commerce");

        MinecraftServer server = ctx.getSource().getServer();

        String rawFormat = MessagesConfig.get(MessagesConfig.RP_COMMERCE_FORMAT, "message", message);
        Component component = ColorHelper.parseColors(
                RpEssentialsChatFormatter.resolveRpPlaceholders(rawFormat, player));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(component, false);
        }

        RpEssentials.LOGGER.info("[COMMERCE] {} ({}): {}",
                NicknameManager.getDisplayName(player),
                player.getName().getString(),
                message);
        return 1;
    }

    // =========================================================================
    // /rp incognito <message>
    // =========================================================================
    private static int executeIncognito(CommandContext<CommandSourceStack> ctx, String message)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (MuteManager.denyIfMuted(player)) return 0;

        if (RpCooldownManager.isOnCooldown(player.getUUID(), "incognito")) {
            long remaining = RpCooldownManager.getRemainingSeconds(player.getUUID(), "incognito");
            player.displayClientMessage(ColorHelper.parseColors(
                    MessagesConfig.get(MessagesConfig.RP_COOLDOWN_MESSAGE,
                            "command", "rp incognito", "seconds", String.valueOf(remaining))), true);
            return 0;
        }
        RpCooldownManager.setCooldown(player.getUUID(), "incognito");

        MinecraftServer server = ctx.getSource().getServer();

        String rawPublic = MessagesConfig.get(MessagesConfig.RP_INCOGNITO_FORMAT, "message", message);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(ColorHelper.parseColors(rawPublic), false);
        }

        String rawLog = MessagesConfig.get(MessagesConfig.RP_INCOGNITO_LOG, "message", message);
        rawLog = RpEssentialsChatFormatter.resolveRpPlaceholders(rawLog, player);
        String finalLogResolved = RpEssentialsChatFormatter.resolveRpPlaceholders(
                MessagesConfig.get(MessagesConfig.RP_INCOGNITO_LOG, "message", message), player);
        Component spyComponent = ColorHelper.parseColors(finalLogResolved);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!RpEssentialsRoleManager.has(p, RpEssentialsRoleManager.Permission.SPY_MESSAGES)) continue;
            p.displayClientMessage(spyComponent, false);
        }

        RpEssentials.LOGGER.info("[INCOGNITO] {} ({}): {}",
                NicknameManager.getDisplayName(player),
                player.getName().getString(),
                message);
        return 1;
    }

    // =========================================================================
    // /rp action <action> + /me <action>
    // =========================================================================
    private static int executeAction(CommandContext<CommandSourceStack> ctx, String action)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (MuteManager.denyIfMuted(player)) return 0;

        if (RpCooldownManager.isOnCooldown(player.getUUID(), "action")) {
            long remaining = RpCooldownManager.getRemainingSeconds(player.getUUID(), "action");
            player.displayClientMessage(ColorHelper.parseColors(
                    MessagesConfig.get(MessagesConfig.RP_COOLDOWN_MESSAGE,
                            "command", "rp action", "seconds", String.valueOf(remaining))), true);
            return 0;
        }
        RpCooldownManager.setCooldown(player.getUUID(), "action");

        MinecraftServer server = ctx.getSource().getServer();

        int distance = 32;
        try { distance = RpConfig.ACTION_DISTANCE.get(); }
        catch (IllegalStateException ignored) {}

        String rawAction = MessagesConfig.get(MessagesConfig.RP_ACTION_FORMAT, "action", action);
        rawAction = RpEssentialsChatFormatter.resolveRpPlaceholders(rawAction, player);

        String rawSpy = MessagesConfig.get(MessagesConfig.RP_ACTION_SPY, "action", action);
        rawSpy = RpEssentialsChatFormatter.resolveRpPlaceholders(rawSpy, player);

        final String actionMsg = rawAction;
        final String spyMsg    = rawSpy;
        final int    finalDist = distance;

        java.util.List<ServerPlayer> inRange = new java.util.ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != player.level()) continue;
            if (p.getUUID().equals(player.getUUID()) || p.distanceTo(player) <= finalDist) {
                inRange.add(p);
                p.displayClientMessage(ColorHelper.parseColors(actionMsg), false);
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!RpEssentialsRoleManager.has(p, RpEssentialsRoleManager.Permission.SPY_MESSAGES)) continue;
            if (inRange.stream().anyMatch(r -> r.getUUID().equals(p.getUUID()))) continue;
            p.displayClientMessage(ColorHelper.parseColors(spyMsg), false);
        }

        RpEssentials.LOGGER.info("[ACTION-RP] {} ({}): {}",
                NicknameManager.getDisplayName(player),
                player.getName().getString(),
                action);
        return 1;
    }

    private static int executeSelfNick(CommandContext<CommandSourceStack> ctx, String nickname)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        try {
            if (!RpConfig.ENABLE_SELF_NICK.get()) {
                player.displayClientMessage(
                        ColorHelper.parseColors(MessagesConfig.get(MessagesConfig.RP_SELFNICK_DISABLED)), false);
                return 0;
            }
        } catch (IllegalStateException ignored) { return 0; }

        if (RpCooldownManager.isOnCooldown(player.getUUID(), "selfnick")) {
            long remaining = RpCooldownManager.getRemainingSeconds(player.getUUID(), "selfnick");
            player.displayClientMessage(ColorHelper.parseColors(
                    MessagesConfig.get(MessagesConfig.RP_COOLDOWN_MESSAGE,
                            "command", "rp selfnick", "seconds", String.valueOf(remaining))), true);
            return 0;
        }

        if (nickname.isBlank()) {
            NicknameManager.removeNickname(player.getUUID());
            player.displayClientMessage(
                    ColorHelper.parseColors(MessagesConfig.get(MessagesConfig.RP_SELFNICK_RESET)), false);
            RpCooldownManager.setCooldown(player.getUUID(), "selfnick");
            return 1;
        }

        boolean allowColors = false;
        try { allowColors = RpConfig.SELF_NICK_ALLOW_COLORS.get(); } catch (IllegalStateException ignored) {}

        if (!allowColors && (nickname.contains("§") || nickname.contains("&"))) {
            player.displayClientMessage(
                    ColorHelper.parseColors(MessagesConfig.get(MessagesConfig.RP_SELFNICK_COLORS_DISABLED)), false);
            return 0;
        }

        String finalNick = allowColors ? nickname.replace("&", "§") : nickname;

        int maxLen = 24;
        try { maxLen = RpConfig.SELF_NICK_MAX_LENGTH.get(); } catch (IllegalStateException ignored) {}
        String stripped = finalNick.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        if (stripped.isEmpty()) return 0;
        if (stripped.length() > maxLen) {
            final int max = maxLen;
            player.displayClientMessage(
                    ColorHelper.parseColors(MessagesConfig.get(MessagesConfig.RP_SELFNICK_TOO_LONG,
                            "max", String.valueOf(max))), false);
            return 0;
        }

        NicknameManager.setNickname(player.getUUID(), finalNick);
        RpCooldownManager.setCooldown(player.getUUID(), "selfnick");

        String displayNick = finalNick;
        player.displayClientMessage(
                ColorHelper.parseColors(MessagesConfig.get(MessagesConfig.RP_SELFNICK_SET,
                        "nick", displayNick)), false);
        RpEssentials.LOGGER.info("[SELFNICK] {} set nickname to '{}'",
                player.getName().getString(), finalNick);
        return 1;
    }

    // =========================================================================
    // /rp annonce <title|chat> <message>
    // =========================================================================

    private static int executeAnnonce(CommandContext<CommandSourceStack> ctx,
                                      String type, String message)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();

        if (!RpEssentialsPermissions.isStaff(player)) {
            player.displayClientMessage(
                    ColorHelper.parseColors(MessagesConfig.get(MessagesConfig.RP_PERMISSION_DENIED)), false);
            return 0;
        }

        boolean playSound = true;
        String soundId = "minecraft:entity.arrow.hit_player";
        try {
            playSound = RpConfig.ANNONCE_PLAY_SOUND.get();
            soundId   = RpConfig.ANNONCE_SOUND.get();
        } catch (IllegalStateException ignored) {}

        final String finalSoundId  = soundId;
        final boolean finalPlaySnd = playSound;

        switch (type.toLowerCase()) {
            case "title" -> {
                // Le titre lui-même n'affiche pas de nom de joueur,
                // mais le sous-titre peut contenir {player} etc.
                Component title    = ColorHelper.parseColors(
                        MessagesConfig.get(MessagesConfig.RP_ANNONCE_TITLE));
                String rawSubtitle = MessagesConfig.get(MessagesConfig.RP_ANNONCE_SUBTITLE,
                        "message", message);
                rawSubtitle = RpEssentialsChatFormatter.resolveRpPlaceholders(rawSubtitle, player);
                Component subtitle = ColorHelper.parseColors(rawSubtitle);

                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.connection.send(new ClientboundSetTitleTextPacket(title));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                    if (finalPlaySnd) playAnnonceSound(p, finalSoundId);
                }
            }
            case "chat" -> {
                String rawChat = MessagesConfig.get(MessagesConfig.RP_ANNONCE_CHAT_FORMAT,
                        "message", message);
                rawChat = RpEssentialsChatFormatter.resolveRpPlaceholders(rawChat, player);
                String finalChat = rawChat;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.displayClientMessage(ColorHelper.parseColors(finalChat), false);
                    if (finalPlaySnd) playAnnonceSound(p, finalSoundId);
                }
            }
            default -> {
                ctx.getSource().sendFailure(Component.literal(
                        "§c[ERROR] Unknown type: " + type + ". Use 'title' or 'chat'."));
                return 0;
            }
        }

        player.displayClientMessage(
                ColorHelper.parseColors(MessagesConfig.get(MessagesConfig.RP_ANNONCE_SENT)), false);
        RpEssentials.LOGGER.info("[ANNONCE][{}] {} ({}): {}",
                type.toUpperCase(),
                NicknameManager.getDisplayName(player),
                player.getName().getString(),
                message);
        return 1;
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================
    private static boolean isSelfNickEnabled() {
        try { return RpConfig.ENABLE_SELF_NICK.get(); }
        catch (IllegalStateException e) { return false; }
    }

    private static void playAnnonceSound(ServerPlayer player, String soundId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(soundId);
            net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> holder =
                    player.level().registryAccess()
                            .registryOrThrow(Registries.SOUND_EVENT)
                            .getHolder(loc)
                            .orElse(null);
            if (holder != null) {
                player.playNotifySound(holder.value(), SoundSource.MASTER, 1.0f, 1.0f);
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.warn("[RP] Could not play sound '{}': {}", soundId, e.getMessage());
        }
    }
}