package net.rp.rpessentials.commands;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.Cfg;
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
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.moderation.MuteManager;

import java.util.List;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.gameplay.DiceManager;
import net.rp.rpessentials.gameplay.TeleportWarmupManager;
import net.rp.rpessentials.lang.Msg;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.util.RpCooldownManager;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class RpEssentialsRpCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Supprime le /me vanilla et le remplace
        dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals("me"));

        // ── /me <action> — alias direct de /rp action ──────────────────────
        dispatcher.register(Commands.literal("me")
                .requires(RpPerms.cmd(RpPerm.PLAYER_RP))
                .then(Commands.argument("action", StringArgumentType.greedyString())
                        .executes(ctx -> executeAction(ctx,
                                StringArgumentType.getString(ctx, "action")))));

        // ── /rp ────────────────────────────────────────────────────────────
        var rpRoot = Commands.literal("rp");

        // /rp spawn
        rpRoot.then(Commands.literal("spawn")
                .requires(src -> spawnEnabled() && RpPerms.has(src, RpPerm.PLAYER_SPAWN))
                .executes(RpEssentialsRpCommands::executeSpawn));

        // /rp afk
        rpRoot.then(Commands.literal("afk")
                .requires(RpPerms.cmd(RpPerm.PLAYER_RP))
                .executes(RpEssentialsRpCommands::executeAfk));

        rpRoot.then(Commands.literal("help")
                .executes(ctx -> {
                    boolean isStaff = RpEssentialsPermissions.isStaff(ctx.getSource().getPlayer());
                    return RpEssentialsCommands.showHelpPublic(ctx, isStaff);
                }));

        // /rp commerce <message>
        rpRoot.then(Commands.literal("commerce")
                .requires(RpPerms.cmd(RpPerm.PLAYER_RP))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> executeCommerce(ctx,
                                StringArgumentType.getString(ctx, "message")))));

        // /rp incognito <message>
        rpRoot.then(Commands.literal("incognito")
                .requires(RpPerms.cmd(RpPerm.PLAYER_RP))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> executeIncognito(ctx,
                                StringArgumentType.getString(ctx, "message")))));

        // /rp action <action>
        rpRoot.then(Commands.literal("action")
                .requires(RpPerms.cmd(RpPerm.PLAYER_RP))
                .then(Commands.argument("action", StringArgumentType.greedyString())
                        .executes(ctx -> executeAction(ctx,
                                StringArgumentType.getString(ctx, "action")))));

        // /rp selfnick [nickname]
        rpRoot.then(Commands.literal("selfnick")
                .requires(src -> src.getEntity() instanceof ServerPlayer && isSelfNickEnabled() && RpPerms.has(src, RpPerm.PLAYER_SELFNICK))
                .executes(ctx -> executeSelfNick(ctx, ""))
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(ctx -> executeSelfNick(ctx,
                                StringArgumentType.getString(ctx, "nickname")))));

        // /rp report <player> <reason>
        rpRoot.then(RpReportCommands.buildReport());

        // /rp annonce <title|chat> <message>
        rpRoot.then(Commands.literal("annonce")
                .requires(RpPerms.cmd(RpPerm.CHAT_ANNOUNCE))
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
                .requires(src -> src.getEntity() instanceof ServerPlayer && RpPerms.has(src, RpPerm.PLAYER_ROLL))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (Cfg.ready(RpConfig.SPEC)) {
                        if (!RpConfig.ENABLE_DICE_SYSTEM.get()) {
                            ctx.getSource().sendFailure(Component.literal(ServerLang.get(player, MessagesConfig.DICE_DISABLED)));
                            return 0;
                        }
                    } else { return 0; }
                    List<DiceManager.DiceType> dice = DiceManager.getAvailableDice();
                    if (dice.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal(ServerLang.get(player, MessagesConfig.DICE_NONE)));
                        return 0;
                    }
                    return diceError(player, DiceManager.rollInput(player, dice.get(0).name(), false), dice.get(0).name());
                })
                .then(Commands.argument("dice", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            DiceManager.getAvailableDice().forEach(d -> builder.suggest(d.name()));
                            builder.suggest("2d6").suggest("d20+2").suggest("adv d20").suggest("dis d20").suggest("secret d20");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String diceName = StringArgumentType.getString(ctx, "dice");
                            if (Cfg.ready(RpConfig.SPEC)) {
                                if (!RpConfig.ENABLE_DICE_SYSTEM.get()) {
                                    ctx.getSource().sendFailure(Component.literal(ServerLang.get(player, MessagesConfig.DICE_DISABLED)));
                                    return 0;
                                }
                            } else { return 0; }
                            return diceError(player, DiceManager.rollInput(player, diceName, false), diceName);
                        })));

        dispatcher.register(rpRoot);
    }

    /** Sends the error of a refused /roll to the player. */
    private static int diceError(ServerPlayer player, DiceManager.Status s, String input) {
        switch (s) {
            case OK, COOLDOWN, CANCELLED -> { return s == DiceManager.Status.OK ? 1 : 0; }
            case SECRET_DISABLED -> player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.DICE_SECRET_DISABLED)));
            case EXPRESSIONS_DISABLED -> player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.DICE_EXPRESSIONS_DISABLED)));
            case TOO_MANY -> {
                int count = 20, sides = 1000;
                if (Cfg.ready(RpConfig.SPEC)) { count = RpConfig.DICE_MAX_COUNT.get(); sides = RpConfig.DICE_MAX_SIDES.get(); }
                player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.DICE_TOO_MANY,
                        "count", String.valueOf(count), "sides", String.valueOf(sides))));
            }
            default -> player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.DICE_UNKNOWN, "dice", input)));
        }
        return 0;
    }

    // =========================================================================
    // /rp afk
    // =========================================================================
    private static int executeAfk(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();

        if (Cfg.ready(RpConfig.SPEC)) {
            int combat = RpConfig.AFK_COMBAT_BLOCK_SECONDS.get();
            int hurt = player.getLastHurtByMobTimestamp();
            if (combat > 0 && hurt > 0 && player.tickCount - hurt < combat * 20) {
                player.displayClientMessage(Component.literal(ServerLang.get(player, MessagesConfig.AFK_COMBAT)), true);
                return 0;
            }
        }

        if (RpCooldownManager.isOnCooldown(player.getUUID(), "afk")) {
            long remaining = RpCooldownManager.getRemainingSeconds(player.getUUID(), "afk");
            player.displayClientMessage(ColorHelper.parseColors(
                    ServerLang.get(player, MessagesConfig.RP_COOLDOWN_MESSAGE,
                            "command", "rp afk", "seconds", String.valueOf(remaining))), true);
            return 0;
        }

        String dimId = "minecraft:overworld";
        double x = 0, y = 64, z = 0;
        float yaw = 0, pitch = 0;

        if (Cfg.ready(RpConfig.SPEC)) {
            dimId = RpConfig.AFK_DIMENSION.get();
            x     = RpConfig.AFK_X.get();
            y     = RpConfig.AFK_Y.get();
            z     = RpConfig.AFK_Z.get();
            yaw   = RpConfig.AFK_YAW.get().floatValue();
            pitch = RpConfig.AFK_PITCH.get().floatValue();
        }

        ResourceLocation dimRl = ResourceLocation.tryParse(dimId);
        ServerLevel level = dimRl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimRl));

        if (level == null) {
            RpEssentials.LOGGER.warn("[RP-AFK] Dimension '{}' not found.", dimId);
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_RP_AFK_DIM, "dim", dimId));
            return 0;
        }

        TeleportWarmupManager.start(player, "afk", level, x, y, z, yaw, pitch,
                TeleportWarmupManager.configuredSeconds("afk"), p -> {
                    p.displayClientMessage(ColorHelper.parseColors(ServerLang.get(p, MessagesConfig.RP_AFK_TELEPORT)), false);
                    RpCooldownManager.setCooldown(p.getUUID(), "afk");
                    PlayerCommands.runAs(p, PlayerCommands.get(RpConfig.AFK_COMMANDS));
                });
        return 1;
    }

    // =========================================================================
    // /rp spawn
    // =========================================================================
    private static boolean spawnEnabled() {
        return Cfg.get(RpConfig.ENABLE_SPAWN_COMMAND, false);
    }

    private static int executeSpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        if (!spawnEnabled()) return 0;

        if (Cfg.ready(RpConfig.SPEC)) {
            int combat = RpConfig.SPAWN_COMBAT_BLOCK_SECONDS.get();
            int hurt = player.getLastHurtByMobTimestamp();
            if (combat > 0 && hurt > 0 && player.tickCount - hurt < combat * 20) {
                player.displayClientMessage(ColorHelper.parseColors(ServerLang.get(player, MessagesConfig.AFK_COMBAT)), true);
                return 0;
            }
        }

        if (RpCooldownManager.isOnCooldown(player.getUUID(), "spawn")) {
            long remaining = RpCooldownManager.getRemainingSeconds(player.getUUID(), "spawn");
            player.displayClientMessage(ColorHelper.parseColors(
                    ServerLang.get(player, MessagesConfig.RP_COOLDOWN_MESSAGE,
                            "command", "rp spawn", "seconds", String.valueOf(remaining))), true);
            return 0;
        }

        ServerLevel level;
        double x, y, z;
        float yaw, pitch;
        boolean worldSpawn = true;
        worldSpawn = Cfg.get(RpConfig.SPAWN_USE_WORLD_SPAWN, worldSpawn);

        if (worldSpawn) {
            level = server.overworld();
            net.minecraft.core.BlockPos pos = level.getSharedSpawnPos();
            x = pos.getX() + 0.5;
            y = pos.getY();
            z = pos.getZ() + 0.5;
            yaw = level.getSharedSpawnAngle();
            pitch = 0;
        } else {
            String dimId = "minecraft:overworld";
            x = 0; y = 64; z = 0; yaw = 0; pitch = 0;
            if (Cfg.ready(RpConfig.SPEC)) {
                dimId = RpConfig.SPAWN_DIMENSION.get();
                x     = RpConfig.SPAWN_X.get();
                y     = RpConfig.SPAWN_Y.get();
                z     = RpConfig.SPAWN_Z.get();
                yaw   = RpConfig.SPAWN_YAW.get().floatValue();
                pitch = RpConfig.SPAWN_PITCH.get().floatValue();
            }
            ResourceLocation dim = ResourceLocation.tryParse(dimId);
            level = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (level == null) {
                RpEssentials.LOGGER.warn("[RP-SPAWN] Dimension '{}' not found.", dimId);
                ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_RP_SPAWN_DIM, "dim", dimId));
                return 0;
            }
        }

        TeleportWarmupManager.start(player, "spawn", level, x, y, z, yaw, pitch,
                TeleportWarmupManager.configuredSeconds("spawn"), p -> {
                    p.displayClientMessage(ColorHelper.parseColors(ServerLang.get(p, MessagesConfig.RP_SPAWN_TELEPORT)), false);
                    RpCooldownManager.setCooldown(p.getUUID(), "spawn");
                    PlayerCommands.runAs(p, PlayerCommands.get(RpConfig.SPAWN_COMMANDS));
                });
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
                    ServerLang.get(player, MessagesConfig.RP_COOLDOWN_MESSAGE,
                            "command", "rp commerce", "seconds", String.valueOf(remaining))), true);
            return 0;
        }
        RpCooldownManager.setCooldown(player.getUUID(), "commerce");

        MinecraftServer server = ctx.getSource().getServer();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(ColorHelper.parseColors(RpEssentialsChatFormatter.resolveRpPlaceholders(
                    Msg.get(p, MessagesConfig.RP_COMMERCE_FORMAT, "message", message), player)), false);
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
                    ServerLang.get(player, MessagesConfig.RP_COOLDOWN_MESSAGE,
                            "command", "rp incognito", "seconds", String.valueOf(remaining))), true);
            return 0;
        }
        RpCooldownManager.setCooldown(player.getUUID(), "incognito");

        MinecraftServer server = ctx.getSource().getServer();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(ColorHelper.parseColors(
                    Msg.get(p, MessagesConfig.RP_INCOGNITO_FORMAT, "message", message)), false);
        }

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!RpPerms.has(p, RpPerm.CHAT_SPY)) continue;
            p.displayClientMessage(ColorHelper.parseColors(RpEssentialsChatFormatter.resolveRpPlaceholders(
                    Msg.get(p, MessagesConfig.RP_INCOGNITO_LOG, "message", message), player)), false);
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
                    ServerLang.get(player, MessagesConfig.RP_COOLDOWN_MESSAGE,
                            "command", "rp action", "seconds", String.valueOf(remaining))), true);
            return 0;
        }
        RpCooldownManager.setCooldown(player.getUUID(), "action");

        MinecraftServer server = ctx.getSource().getServer();

        int distance = 32;
        distance = Cfg.get(RpConfig.ACTION_DISTANCE, distance);

        final int    finalDist = distance;

        java.util.List<ServerPlayer> inRange = new java.util.ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != player.level()) continue;
            if (p.getUUID().equals(player.getUUID()) || p.distanceTo(player) <= finalDist) {
                inRange.add(p);
                p.displayClientMessage(ColorHelper.parseColors(RpEssentialsChatFormatter.resolveRpPlaceholders(
                        Msg.get(p, MessagesConfig.RP_ACTION_FORMAT, "action", action), player)), false);
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!RpPerms.has(p, RpPerm.CHAT_SPY)) continue;
            if (inRange.stream().anyMatch(r -> r.getUUID().equals(p.getUUID()))) continue;
            p.displayClientMessage(ColorHelper.parseColors(RpEssentialsChatFormatter.resolveRpPlaceholders(
                    Msg.get(p, MessagesConfig.RP_ACTION_SPY, "action", action), player)), false);
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

        if (Cfg.ready(RpConfig.SPEC)) {
            if (!RpConfig.ENABLE_SELF_NICK.get()) {
                player.displayClientMessage(
                        ColorHelper.parseColors(Msg.get(ctx.getSource(), MessagesConfig.RP_SELFNICK_DISABLED)), false);
                return 0;
            }
        } else { return 0; }

        if (RpCooldownManager.isOnCooldown(player.getUUID(), "selfnick")) {
            long remaining = RpCooldownManager.getRemainingSeconds(player.getUUID(), "selfnick");
            player.displayClientMessage(ColorHelper.parseColors(
                    ServerLang.get(player, MessagesConfig.RP_COOLDOWN_MESSAGE,
                            "command", "rp selfnick", "seconds", String.valueOf(remaining))), true);
            return 0;
        }

        if (nickname.isBlank()) {
            NicknameManager.removeNickname(player.getUUID(), player.getGameProfile().getName());
            player.displayClientMessage(
                    ColorHelper.parseColors(Msg.get(ctx.getSource(), MessagesConfig.RP_SELFNICK_RESET)), false);
            RpCooldownManager.setCooldown(player.getUUID(), "selfnick");
            return 1;
        }

        boolean allowColors = false;
        allowColors = Cfg.get(RpConfig.SELF_NICK_ALLOW_COLORS, allowColors);

        if (!allowColors && (nickname.contains("§") || nickname.contains("&"))) {
            player.displayClientMessage(
                    ColorHelper.parseColors(Msg.get(ctx.getSource(), MessagesConfig.RP_SELFNICK_COLORS_DISABLED)), false);
            return 0;
        }

        String finalNick = allowColors ? nickname.replace("&", "§") : nickname;

        int maxLen = 24;
        maxLen = Cfg.get(RpConfig.SELF_NICK_MAX_LENGTH, maxLen);
        String stripped = ColorHelper.stripAll(ColorHelper.translateAlternateColorCodes(finalNick)).trim();
        if (stripped.isEmpty()) return 0;
        if (stripped.length() > maxLen) {
            final int max = maxLen;
            player.displayClientMessage(
                    ColorHelper.parseColors(ServerLang.get(player, MessagesConfig.RP_SELFNICK_TOO_LONG,
                            "max", String.valueOf(max))), false);
            return 0;
        }

        String err = NicknameManager.rejection(player.getUUID(), finalNick, false, player);
        if (err != null) {
            player.displayClientMessage(ColorHelper.parseColors(err), false);
            return 0;
        }

        NicknameManager.setNickname(player.getUUID(), finalNick, player.getGameProfile().getName());
        RpCooldownManager.setCooldown(player.getUUID(), "selfnick");

        String displayNick = finalNick;
        player.displayClientMessage(
                ColorHelper.parseColors(ServerLang.get(player, MessagesConfig.RP_SELFNICK_SET,
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

        if (!RpPerms.has(player, RpPerm.CHAT_ANNOUNCE)) {
            player.displayClientMessage(
                    ColorHelper.parseColors(Msg.get(ctx.getSource(), MessagesConfig.RP_PERMISSION_DENIED)), false);
            return 0;
        }

        boolean playSound = true;
        String soundId = "minecraft:entity.arrow.hit_player";
        if (Cfg.ready(RpConfig.SPEC)) {
            playSound = RpConfig.ANNONCE_PLAY_SOUND.get();
            soundId   = RpConfig.ANNONCE_SOUND.get();
        }

        final String finalSoundId  = soundId;
        final boolean finalPlaySnd = playSound;

        switch (type.toLowerCase()) {
            case "title" -> {
                // Title and subtitle can both name the staff member ({nick}, {real}, {nick_real}, {player}).
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    Component title = ColorHelper.parseColors(RpEssentialsChatFormatter.resolveRpPlaceholders(
                            Msg.get(p, MessagesConfig.RP_ANNONCE_TITLE), player));
                    Component subtitle = ColorHelper.parseColors(RpEssentialsChatFormatter.resolveRpPlaceholders(
                            Msg.get(p, MessagesConfig.RP_ANNONCE_SUBTITLE, "message", message), player));
                    p.connection.send(new ClientboundSetTitleTextPacket(title));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                    if (finalPlaySnd) playAnnonceSound(p, finalSoundId);
                }
            }
            case "chat" -> {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.displayClientMessage(ColorHelper.parseColors(RpEssentialsChatFormatter.resolveRpPlaceholders(
                            Msg.get(p, MessagesConfig.RP_ANNONCE_CHAT_FORMAT, "message", message), player)), false);
                    if (finalPlaySnd) playAnnonceSound(p, finalSoundId);
                }
            }
            default -> {
                ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_RP_ANNONCE_TYPE, "type", type));
                return 0;
            }
        }

        player.displayClientMessage(
                ColorHelper.parseColors(Msg.get(ctx.getSource(), MessagesConfig.RP_ANNONCE_SENT)), false);
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
        return Cfg.get(RpConfig.ENABLE_SELF_NICK, false);
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