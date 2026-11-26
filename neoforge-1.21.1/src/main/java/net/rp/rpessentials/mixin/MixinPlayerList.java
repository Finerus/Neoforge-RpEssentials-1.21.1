package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.api.IRpPlayerList;
import net.rp.rpessentials.config.ChatConfig;
import net.rp.rpessentials.identity.RpEssentialsChatFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepte les messages join/leave vanilla et les remplace par les
 * messages personnalisés avec support de toutes les variables de nom.
 *
 * Implémente {@link IRpPlayerList} pour être accessible depuis
 * RpEssentialsEventHandler sans cast direct sur la classe Mixin.
 *
 * 4.1.6 : resolveJoinLeavePlaceholders supporte
 *   {player}, {nickname}   ← rétrocompatibles
 *   {nick}, {real}, {nick_real} ← nouvelles
 */
@Mixin(PlayerList.class)
@org.spongepowered.asm.mixin.Implements(
        @org.spongepowered.asm.mixin.Interface(
                iface = net.rp.rpessentials.api.IRpPlayerList.class,
                prefix = "rpe$",
                unique = true
        )
)
public abstract class MixinPlayerList implements IRpPlayerList {

    @Unique
    private boolean rpessentials$isSendingCustomMessage = false;

    // =========================================================================
    // INTERCEPTION DU BROADCAST VANILLA
    // =========================================================================
    @Inject(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            //#if forge
            //? cancellable = true
            //#else
            cancellable = true,
            remap = false
            //#endif
    )
    public void onBroadcastSystemMessage(Component component, boolean bl, CallbackInfo ci) {
        if (rpessentials$isSendingCustomMessage) return;
        if (!(component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc)) return;

        String key = tc.getKey();
        if (key.startsWith("chat.type.advancement.")) {
            if (net.rp.rpessentials.moderation.VanishManager.isAdvancementHidden()) {
                ci.cancel();
                java.util.UUID id = net.rp.rpessentials.moderation.VanishManager.awardingId();
                for (net.minecraft.server.level.ServerPlayer p : ((PlayerList) (Object) this).getPlayers()) {
                    if (!net.rp.rpessentials.moderation.VanishManager.isHiddenFrom(p, id))
                        p.sendSystemMessage(component.copy().append(net.rp.rpessentials.util.ColorHelper.parseSection(net.rp.rpessentials.lang.ServerLang.get(p,
                                net.rp.rpessentials.config.MessagesConfig.VANISH_ADVANCEMENT_HIDDEN))));
                }
            }
            return;
        }

        boolean isJoin = key.startsWith("multiplayer.player.joined");
        boolean isLeave = key.equals("multiplayer.player.left");
        if (!isJoin && !isLeave) return;

        try {
            if (ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE == null
                    || !ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE.get()) return;
        } catch (Exception e) { return; }

        ci.cancel();
    }

    /** /op, /deop: vanilla sends the command tree, not what this mod depends on. */
    @Inject(method = "sendPlayerPermissionLevel(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("TAIL"), remap = false)
    private void rpessentials$opLevelChanged(net.minecraft.server.level.ServerPlayer player, CallbackInfo ci) {
        net.rp.rpessentials.permission.AccessRefresh.mark(player.getUUID());
    }

    // =========================================================================
    // IMPLÉMENTATION IRpPlayerList
    // =========================================================================
    public void rpe$sendCustomJoinLeaveMessage(
            net.minecraft.server.level.ServerPlayer player, boolean isJoin) {
        try {
            String raw = isJoin ? ChatConfig.JOIN_MESSAGE.get() : ChatConfig.LEAVE_MESSAGE.get();
            if (raw == null || "none".equalsIgnoreCase(raw) || raw.isBlank()) return;

            String resolved = RpEssentialsChatFormatter.resolveJoinLeavePlaceholders(raw, player);
            Component formatted = ColorHelper.parseColors(
                    ColorHelper.translateAlternateColorCodes(resolved));

            rpe$broadcastCustomMessage(formatted);
        } catch (Exception e) {
            RpEssentials.LOGGER.warn("[JoinLeave] Error building {} message: {}",
                    isJoin ? "join" : "leave", e.getMessage());
        }
    }

    public void rpe$broadcastCustomMessage(Component message) {
        rpessentials$isSendingCustomMessage = true;
        try {
            ((PlayerList)(Object)this).broadcastSystemMessage(message, false);
        } finally {
            rpessentials$isSendingCustomMessage = false;
        }
    }
}