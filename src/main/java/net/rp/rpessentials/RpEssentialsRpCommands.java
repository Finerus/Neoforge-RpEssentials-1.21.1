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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        double x = -2408.5, y = 127.0, z = 588.5;
        float yaw = 138.29f, pitch = 1.05f;

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
            ctx.getSource().sendFailure(Component.literal(
                    "§c[ERROR] AFK dimension not found: " + dimId));
            return 0;
        }

        player.teleportTo(level, x, y, z, Set.of(), yaw, pitch);
        player.displayClientMessage(
                ColorHelper.parseColors(MessagesConfig.get(MessagesConfig.RP_AFK_TELEPORT)),
                false);
        return 1;
    }

    // =========================================================================
    // /rp commerce <message>
    // =========================================================================

    private static int executeCommerce(CommandContext<CommandSourceStack> ctx, String message)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();

        String nickname = NicknameManager.hasNickname(player.getUUID())
                ? NicknameManager.getNickname(player.getUUID())
                : player.getName().getString();

        String formatted = MessagesConfig.get(MessagesConfig.RP_COMMERCE_FORMAT,
                "player", nickname, "message", message);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(ColorHelper.parseColors(formatted), false);
        }

        RpEssentials.LOGGER.info("[COMMERCE] {}: {}", nickname, message);
        return 1;
    }

    // =========================================================================
    // /rp incognito <message>
    // =========================================================================

    private static int executeIncognito(CommandContext<CommandSourceStack> ctx, String message)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();

        String nickname = NicknameManager.hasNickname(player.getUUID())
                ? NicknameManager.getNickname(player.getUUID())
                : player.getName().getString();

        // Broadcast public — anonyme
        String publicMsg = MessagesConfig.get(MessagesConfig.RP_INCOGNITO_FORMAT,
                "message", message);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(ColorHelper.parseColors(publicMsg), false);
        }

        // Log staff — révèle l'expéditeur
        String staffLog = MessagesConfig.get(MessagesConfig.RP_INCOGNITO_LOG,
                "player", nickname, "message", message);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RpEssentialsPermissions.isStaff(p)) {
                p.displayClientMessage(ColorHelper.parseColors(staffLog), false);
            }
        }

        RpEssentials.LOGGER.info("[INCOGNITO] {}: {}", nickname, message);
        return 1;
    }

    // =========================================================================
    // /rp action <action> + /me <action>
    // =========================================================================

    private static int executeAction(CommandContext<CommandSourceStack> ctx, String action)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();

        String nickname = NicknameManager.hasNickname(player.getUUID())
                ? NicknameManager.getNickname(player.getUUID())
                : player.getName().getString();

        int distance = 32;
        try { distance = RpConfig.ACTION_DISTANCE.get(); }
        catch (IllegalStateException ignored) {}

        String actionMsg = MessagesConfig.get(MessagesConfig.RP_ACTION_FORMAT,
                "player", nickname, "action", action);
        String spyMsg = MessagesConfig.get(MessagesConfig.RP_ACTION_SPY,
                "player", nickname, "action", action);

        List<ServerPlayer> inRange = new ArrayList<>();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != player.level()) continue;
            if (p.getUUID().equals(player.getUUID())
                    || p.distanceTo(player) <= distance) {
                inRange.add(p);
                p.displayClientMessage(ColorHelper.parseColors(actionMsg), false);
            }
        }

        // Espionnage staff hors portée
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!RpEssentialsPermissions.isStaff(p)) continue;
            boolean alreadySaw = inRange.stream()
                    .anyMatch(r -> r.getUUID().equals(p.getUUID()));
            if (!alreadySaw) {
                p.displayClientMessage(ColorHelper.parseColors(spyMsg), false);
            }
        }

        RpEssentials.LOGGER.info("[ACTION-RP] {}: {}", nickname, action);
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
                    ColorHelper.parseColors(
                            MessagesConfig.get(MessagesConfig.RP_PERMISSION_DENIED)),
                    false);
            return 0;
        }

        boolean playSound = true;
        String soundId = "minecraft:entity.arrow.hit_player";
        try {
            playSound = RpConfig.ANNONCE_PLAY_SOUND.get();
            soundId   = RpConfig.ANNONCE_SOUND.get();
        } catch (IllegalStateException ignored) {}

        final String finalSoundId = soundId;
        final boolean finalPlaySound = playSound;

        switch (type.toLowerCase()) {

            case "title" -> {
                Component title    = ColorHelper.parseColors(
                        MessagesConfig.get(MessagesConfig.RP_ANNONCE_TITLE));
                Component subtitle = ColorHelper.parseColors(
                        MessagesConfig.get(MessagesConfig.RP_ANNONCE_SUBTITLE,
                                "message", message));

                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.connection.send(new ClientboundSetTitleTextPacket(title));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                    if (finalPlaySound) playAnnonceSound(p, finalSoundId);
                }
            }

            case "chat" -> {
                String chatMsg = MessagesConfig.get(MessagesConfig.RP_ANNONCE_CHAT_FORMAT,
                        "message", message);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.displayClientMessage(ColorHelper.parseColors(chatMsg), false);
                    if (finalPlaySound) playAnnonceSound(p, finalSoundId);
                }
            }

            default -> {
                ctx.getSource().sendFailure(Component.literal(
                        "§c[ERROR] Unknown type: " + type + ". Use 'title' or 'chat'."));
                return 0;
            }
        }

        player.displayClientMessage(
                ColorHelper.parseColors(MessagesConfig.get(MessagesConfig.RP_ANNONCE_SENT)),
                false);
        RpEssentials.LOGGER.info("[ANNONCE][{}] {}: {}", type.toUpperCase(),
                player.getName().getString(), message);
        return 1;
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================

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
