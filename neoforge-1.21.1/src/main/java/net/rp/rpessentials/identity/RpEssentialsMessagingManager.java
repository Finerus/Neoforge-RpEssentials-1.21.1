package net.rp.rpessentials.identity;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.config.ChatConfig;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.moderation.MuteManager;
import net.rp.rpessentials.moderation.VanishManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RpEssentialsMessagingManager {

    private static final Map<UUID, UUID> lastMessaged = new HashMap<>();

    public static int sendMessage(ServerPlayer sender, ServerPlayer target, String message) {
        if (MuteManager.denyIfMuted(sender)) return 0;

        UUID senderUuid = sender.getUUID();
        UUID targetUuid = target.getUUID();

        lastMessaged.put(targetUuid, senderUuid);
        lastMessaged.put(senderUuid, targetUuid);

        String senderNick = NicknameManager.getNickname(senderUuid);
        String targetNick = NicknameManager.getNickname(targetUuid);
        String senderDisplay = (senderNick != null) ? senderNick : sender.getName().getString();
        String targetDisplay = (targetNick != null) ? targetNick : target.getName().getString();

        String hoverToSender = ServerLang.get(sender, MessagesConfig.MP_HOVER_REPLY);
        String hoverToTarget = ServerLang.get(target, MessagesConfig.MP_HOVER_REPLY);

        MutableComponent targetNameComponent = ColorHelper.parseSection(targetDisplay).copy()
                .withStyle(style -> style
                        .withBold(true)
                        .withItalic(true)
                        .withColor(0x999999)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverToSender)))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/msg " + target.getName().getString() + " ")));

        MutableComponent senderNameComponent = ColorHelper.parseSection(senderDisplay).copy()
                .withStyle(style -> style
                        .withBold(true)
                        .withItalic(true)
                        .withColor(0x999999)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverToTarget)))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/msg " + sender.getName().getString() + " ")));

        String toSenderPrefix = ServerLang.get(sender, MessagesConfig.MP_TO_SENDER, "target", targetDisplay);
        String fromTargetPrefix = ServerLang.get(target, MessagesConfig.MP_FROM_TARGET, "sender", senderDisplay);

        MutableComponent toSender = ColorHelper.parseSection(toSenderPrefix).copy()
                .withStyle(style -> style.withColor(0x999999).withItalic(true))
                .append(targetNameComponent)
                .append(Component.literal(" : " + message)
                        .withStyle(style -> style.withColor(0x999999).withItalic(true)));

        MutableComponent toTarget = ColorHelper.parseSection(fromTargetPrefix).copy()
                .withStyle(style -> style.withColor(0x999999).withItalic(true))
                .append(senderNameComponent)
                .append(Component.literal(" : " + message)
                        .withStyle(style -> style.withColor(0x999999).withItalic(true)));

        sender.sendSystemMessage(toSender);
        target.sendSystemMessage(toTarget);

        if (Cfg.ready(ChatConfig.SPEC)) {
            if (ChatConfig.ENABLE_PM_SPY.get()) {
                for (ServerPlayer p : sender.getServer().getPlayerList().getPlayers()) {
                    if (p == sender || p == target) continue;
                    if (RpPerms.has(p, RpPerm.CHAT_SPY)) {
                        p.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseColors(
                                net.rp.rpessentials.lang.ServerLang.get(p, MessagesConfig.MP_SPY_FORMAT,
                                        "sender", sender.getName().getString(),
                                        "target", target.getName().getString(),
                                        "msg", message)));
                    }
                }
            }
        }

        try {
            if (ChatConfig.LOG_PRIVATE_MESSAGES.get()) {
                RpEssentials.LOGGER.info("[PM] {} -> {}: {}",
                        sender.getName().getString(),
                        target.getName().getString(),
                        message);
            }
        } catch (Exception ignored) {}

        return 1;
    }

    public static int reply(ServerPlayer sender, String message, MinecraftServer server) {
        UUID lastId = lastMessaged.get(sender.getUUID());
        if (lastId == null) {
            sender.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(ServerLang.get(sender, MessagesConfig.MP_NO_ONE_TO_REPLY))
                    .withStyle(style -> style.withColor(0xFF5555).withItalic(true)));
            return 0;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(lastId);
        if (target == null
                || VanishManager.canHide(VanishManager.Aspect.SELECTORS, sender, lastId)) {
            sender.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(ServerLang.get(sender, MessagesConfig.MP_TARGET_OFFLINE))
                    .withStyle(style -> style.withColor(0xFF5555).withItalic(true)));
            return 0;
        }
        return sendMessage(sender, target, message);
    }

    public static void clearCache(UUID playerId) {
        lastMessaged.remove(playerId);
    }
}