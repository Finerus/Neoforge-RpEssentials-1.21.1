package net.rp.rpessentials.commands;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.rp.rpessentials.commands.*;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.identity.RpEssentialsMessagingManager;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.moderation.ManageService;

import java.util.UUID;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.lang.Msg;
import net.rp.rpessentials.permission.RpEssentialsPermissions;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class RpEssentialsCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        java.util.Set<String> ownBefore = CommandToggles.names(dispatcher);

        // =========================================================================
        // ROOT: /rpessentials (requires OP 2)
        // =========================================================================
        // Visible as soon as one sub command is allowed (each one checks its own node).
        final com.mojang.brigadier.tree.CommandNode<CommandSourceStack>[] built = new com.mojang.brigadier.tree.CommandNode[1];
        var root = Commands.literal("rpessentials")
                .requires(src -> built[0] == null || built[0].getChildren().stream().anyMatch(c -> c.canUse(src)));

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
        root.then(RpManageCommands.build());
        root.then(RpModerationCommands.buildAbsence());
        root.then(RpModerationCommands.buildHistory());
        root.then(RpVanishCommands.build());
        root.then(RpZoneCommands.build());
        root.then(RpReportCommands.buildReports());
        root.then(RpDiscordCommands.build());

        built[0] = dispatcher.register(root);

        // =========================================================================
        // STANDALONE ALIASES
        // =========================================================================
        RpScheduleCommands.registerAliases(dispatcher);
        RpStaffCommands.registerAliases(dispatcher);
        RpNicknameCommands.registerAliases(dispatcher);
        RpLicenseCommands.registerAliases(dispatcher);
        RpModerationCommands.registerMyWarn(dispatcher);
        RpVanishCommands.registerAliases(dispatcher);
        RpManageCommands.registerAliases(dispatcher);
        CommandToggles.markOwn(dispatcher, ownBefore);

        // =========================================================================
        // PRIVATE MESSAGING: /msg /tell /w /whisper /r
        // =========================================================================
        for (String alias : new String[]{"msg", "tell", "w", "whisper"}) {
            dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals(alias));
            dispatcher.register(Commands.literal(alias)
                    .requires(RpPerms.cmd(RpPerm.PLAYER_MSG))
                    .then(Commands.argument("target", EntityArgument.player())
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                        String msg = StringArgumentType.getString(ctx, "message");
                                        if (ctx.getSource().getEntity() instanceof ServerPlayer sender) {
                                            RpEssentialsMessagingManager.sendMessage(sender, target, msg);
                                        } else {
                                            target.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(
                                                    Msg.get(target, MessagesConfig.MP_CONSOLE_TO_PLAYER, "msg", msg)));
                                            ctx.getSource().sendSuccess(() -> net.rp.rpessentials.util.ColorHelper.parseSection(
                                                    Msg.get(ctx.getSource(), MessagesConfig.MP_CONSOLE_FROM_SERVER,
                                                            "target", target.getName().getString(), "msg", msg)), false);
                                        }
                                        return 1;
                                    }))));
        }

        dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals("op"));
        dispatcher.register(Commands.literal("op")
                .requires(src -> src.hasPermission(3))
                .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                        .suggests((ctx, builder) -> {
                            PlayerList pl = ctx.getSource().getServer().getPlayerList();
                            return SharedSuggestionProvider.suggest(pl.getPlayers().stream()
                                    .filter(p -> !pl.isOp(p.getGameProfile()))
                                    .map(p -> p.getGameProfile().getName()), builder);
                        })
                        .executes(ctx -> opCommand(ctx, -1))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 4))
                                .executes(ctx -> opCommand(ctx, IntegerArgumentType.getInteger(ctx, "level"))))));

        dispatcher.register(Commands.literal("r")
                .requires(RpPerms.cmd(RpPerm.PLAYER_MSG))
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

    private record OpConfirm(String command, long at) {}

    /** "/op" that lowers the one who types it, waiting to be confirmed. */
    private static final java.util.Map<UUID, OpConfirm> opConfirm = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long OP_CONFIRM_MS = 30_000L;

    private static boolean lowersSelf(CommandSourceStack src, java.util.Collection<GameProfile> targets, int level) {
        // Only who types it: the console, a command block or a function is never asked.
        if (!(src.source instanceof ServerPlayer self) || src.getServer().getProfilePermissions(self.getGameProfile()) <= level) return false;
        for (GameProfile p : targets) if (p.getId().equals(self.getUUID())) return true;
        return false;
    }

    /** True when the same command was already typed (or the button clicked) within 30 seconds; asks for it otherwise. */
    private static boolean confirmedOpSelf(CommandContext<CommandSourceStack> ctx, int level) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer self = RpPerms.actor(src);
        String command = ctx.getInput().startsWith("/") ? ctx.getInput() : "/" + ctx.getInput();
        OpConfirm asked = opConfirm.remove(self.getUUID());
        long now = System.currentTimeMillis();
        if (asked != null && asked.command().equals(command) && now - asked.at() <= OP_CONFIRM_MS) return true;
        opConfirm.put(self.getUUID(), new OpConfirm(command, now));
        int own = src.getServer().getProfilePermissions(self.getGameProfile());
        // A command is 256 characters at most in a click; longer ones are typed again.
        var button = Msg.c(self, MessagesConfig.MANAGE_BTN_CONFIRM).copy();
        if (command.length() <= 256) button.withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, command)));
        self.sendSystemMessage(Msg.c(self, MessagesConfig.STAFF_TOOLSX_OP_LOWER_SELF, "old", String.valueOf(own),
                "level", String.valueOf(level)).copy().append(Component.literal(" ")).append(button));
        return false;
    }

    private static int opCommand(CommandContext<CommandSourceStack> ctx, int requested) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        int level = requested > 0 ? requested : server.getOperatorUserPermissionLevel();
        if (!src.hasPermission(level)) {
            src.sendFailure(Msg.c(src, MessagesConfig.STAFF_TOOLSX_OP_TOO_HIGH));
            return 0;
        }
        int count = 0;
        var targets = GameProfileArgument.getGameProfiles(ctx, "targets");
        if (lowersSelf(src, targets, level) && !confirmedOpSelf(ctx, level)) return 0;
        for (GameProfile profile : targets) {
            PlayerList pl = server.getPlayerList();
            int current = server.getProfilePermissions(profile);
            if (pl.isOp(profile) && current == level) continue;
            if (pl.isOp(profile) && !src.hasPermission(current)) continue;
            ManageService.setOp(server, profile, level);
            count++;
            src.sendSuccess(() -> Msg.c(src, MessagesConfig.STAFF_TOOLSX_OP_SET,
                    "player", profile.getName(), "level", String.valueOf(level)), true);
        }
        if (count == 0) {
            src.sendFailure(Msg.c(src, MessagesConfig.STAFF_TOOLSX_OP_NOTHING));
        }
        return count;
    }
}