package net.rp.rpessentials;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.rp.rpessentials.commands.*;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.identity.RpEssentialsMessagingManager;
import net.rp.rpessentials.moderation.WarnManager;
import net.rp.rpessentials.moderation.LastConnectionManager;

import java.util.UUID;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class RpEssentialsCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // =========================================================================
        // ROOT: /rpessentials (requires OP 2)
        // =========================================================================
        var root = Commands.literal("rpessentials")
                .requires(source -> source.hasPermission(2));

        root.then(RpConfigCommands.build());
        root.then(RpStaffCommands.build());
        root.then(RpBlurTabCommands.build());
        root.then(RpLicenseCommands.build());
        root.then(RpNicknameCommands.buildNick());
        root.then(RpNicknameCommands.buildWhois());
        root.then(RpNicknameCommands.buildHelp());
        root.then(RpLastConnectionCommands.build());
        root.then(RpModerationCommands.buildWarn());
        root.then(RpModerationCommands.buildMute());
        root.then(RpModerationCommands.buildUnmute());
        root.then(RpModerationCommands.buildNote());
        root.then(RpModerationCommands.buildDeathRp());
        root.then(RpModerationCommands.buildInspect());
        root.then(RpScheduleCommands.buildSetRole());
        root.then(RpStaffCommands.buildStats());

        dispatcher.register(root);

        // =========================================================================
        // STANDALONE ALIASES
        // =========================================================================
        RpScheduleCommands.registerAliases(dispatcher);
        RpStaffCommands.registerAliases(dispatcher);
        RpNicknameCommands.registerAliases(dispatcher);
        RpLicenseCommands.registerAliases(dispatcher);
        RpModerationCommands.registerMyWarn(dispatcher);

        // =========================================================================
        // PRIVATE MESSAGING: /msg /tell /w /whisper /r
        // =========================================================================
        for (String alias : new String[]{"msg", "tell", "w", "whisper"}) {
            dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals(alias));
            dispatcher.register(Commands.literal(alias)
                    .then(Commands.argument("target", EntityArgument.player())
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                        String msg = StringArgumentType.getString(ctx, "message");
                                        if (ctx.getSource().getEntity() instanceof ServerPlayer sender) {
                                            RpEssentialsMessagingManager.sendMessage(sender, target, msg);
                                        } else {
                                            target.sendSystemMessage(Component.literal(
                                                    MessagesConfig.get(MessagesConfig.MP_CONSOLE_TO_PLAYER, "msg", msg)));
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    MessagesConfig.get(MessagesConfig.MP_CONSOLE_FROM_SERVER,
                                                            "target", target.getName().getString(), "msg", msg)), false);
                                        }
                                        return 1;
                                    }))));
        }

        dispatcher.register(Commands.literal("r")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            String msg = StringArgumentType.getString(ctx, "message");
                            return RpEssentialsMessagingManager.reply(sender, msg, ctx.getSource().getServer());
                        })));
    }

    // =========================================================================
    // HELPERS (kept here for backward compatibility, used by RpEssentialsRpCommands)
    // =========================================================================

    /** Finds a UUID by player name (online → last connection → profile cache). */
    public static UUID findUUIDByName(net.minecraft.server.MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        UUID fromLastConn = LastConnectionManager.findUUIDByName(name);
        if (fromLastConn != null) return fromLastConn;
        if (server.getProfileCache() != null)
            return server.getProfileCache().get(name).map(p -> p.getId()).orElse(null);
        return null;
    }

    /** Delegates to RpNicknameCommands for backward compatibility. */
    public static int showHelpPublic(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean isStaff) {
        return RpNicknameCommands.showHelpPublic(ctx, isStaff);
    }

    /** Legacy helper for colors — kept for RpEssentialsCommands.getColorsHelp() calls in tests. */
    public static Component getColorsHelp() {
        return net.rp.rpessentials.identity.RpEssentialsChatFormatter.getColorsHelp();
    }
}