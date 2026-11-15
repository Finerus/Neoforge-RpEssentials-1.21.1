package net.rp.rpessentials.identity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.ChatConfig;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.moderation.MuteManager;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.ColorHelper;

/**
 * Nickname and chat through the official NeoForge events (they replace two mixins):
 * <ul>
 *   <li>{@link PlayerEvent.NameFormat}: the nickname becomes the player's display name, so Minecraft
 *       still adds the team formatting, the hover card and the click-to-/tell around it.</li>
 *   <li>{@link ServerChatEvent}: muted players are stopped first (before any other mod relays the
 *       message, whatever the chat format option), then the RP format and proximity chat are applied
 *       last, after the other mods saw the message.</li>
 * </ul>
 */
@EventBusSubscriber(modid = RpEssentials.MODID)
public final class RpChatEvents {

    private RpChatEvents() {}

    // =========================================================================
    // NICKNAME
    // =========================================================================
    /** Cached by Minecraft: {@link NicknameManager} calls refreshDisplayName() when a nickname changes. */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        // Server side only: on the client the nametags come from the synced data (ClientNametagCache).
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String nickname = NicknameManager.getNickname(player.getUUID());
        if (nickname != null && !nickname.isEmpty()) {
            event.setDisplayname(ColorHelper.parseSection(nickname));
        }
    }

    // =========================================================================
    // CHAT
    // =========================================================================
    /** Muted players: the message is dropped before any other listener (Discord bridges...) sees it. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChatMuted(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!MuteManager.isMuted(player.getUUID())) return;
        event.setCanceled(true);
        MuteManager.MuteEntry entry = MuteManager.getEntry(player.getUUID());
        if (entry != null) {
            player.sendSystemMessage(ColorHelper.parseColors(
                    ServerLang.get(player, MessagesConfig.MUTE_BLOCKED_MESSAGE,
                            "expiry", entry.getFormattedExpiry(player))));
        }
    }

    /** RP format and proximity chat, last: the vanilla broadcast is replaced by ours. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChatFormat(ServerChatEvent event) {
        if (!Cfg.get(ChatConfig.ENABLE_CHAT_FORMAT, true)) return;
        ServerPlayer player = event.getPlayer();
        String message = event.getRawText();
        if (message.startsWith("/")) return;

        event.setCanceled(true);

        boolean proximityEnabled = Cfg.get(ChatConfig.ENABLE_PROXIMITY_CHAT, false);
        boolean isGlobal = !proximityEnabled;
        String actualMessage = message;
        if (proximityEnabled) {
            String bypass = Cfg.get(ChatConfig.PROXIMITY_CHAT_BYPASS_PREFIX, "!");
            if (!bypass.isEmpty() && message.startsWith(bypass)) {
                actualMessage = message.substring(bypass.length());
                isGlobal = true;
            }
        }

        Component formatted = RpEssentialsChatFormatter.formatChatMessage(player, actualMessage, isGlobal);
        if (player.getServer() == null) return;

        if (isGlobal) {
            player.getServer().getPlayerList().broadcastSystemMessage(formatted, false);
            return;
        }

        int distance = Cfg.get(ChatConfig.PROXIMITY_CHAT_DISTANCE, 32);
        double distSq = (double) distance * distance;
        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            if (p.getUUID().equals(player.getUUID())
                    || (p.level() == player.level() && p.distanceToSqr(player) <= distSq)) {
                p.sendSystemMessage(formatted);
            }
        }

        if (!Cfg.get(ChatConfig.ENABLE_PROXIMITY_SPY, false)) return;
        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            if (!RpEssentialsRoleManager.has(p, RpEssentialsRoleManager.Permission.SPY_MESSAGES)) continue;
            if (p.getUUID().equals(player.getUUID())) continue;
            if (p.level() == player.level() && p.distanceToSqr(player) <= distSq) continue;
            p.sendSystemMessage(ColorHelper.parseColors(RpEssentialsChatFormatter.resolveRpPlaceholders(
                    ServerLang.get(p, MessagesConfig.PROXIMITY_CHAT_SPY_FORMAT,
                            "msg", actualMessage, "distance", String.valueOf(distance)), player)));
        }
    }
}
