package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.RpEssentialsRoleManager;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.config.RpEssentialsConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class MixinServerCommonPacketListenerImpl {

    @ModifyVariable(
            method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"),
            argsOnly = true,
            remap = false
    )
    private Packet modifyPacket(Packet packet) {
        if (!(packet instanceof ClientboundPlayerInfoUpdatePacket infoPacket)) return packet;

        try {
            if (!RpEssentialsConfig.ENABLE_BLUR.get()) return packet;
        } catch (IllegalStateException e) {
            return packet;
        }

        Object self = this;
        if (!(self instanceof ServerGamePacketListenerImpl gameListener)) return packet;

        ServerPlayer receiver = gameListener.player;
        if (receiver == null) return packet;

        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = infoPacket.actions();
        boolean shouldProcess =
                actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME) ||
                        actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);
        if (!shouldProcess) return packet;

        // Permissions du joueur qui reçoit le packet
        boolean seeAll       = RpEssentialsRoleManager.has(receiver, RpEssentialsRoleManager.Permission.SEE_ALL);
        boolean seeNicknames = RpEssentialsRoleManager.has(receiver, RpEssentialsRoleManager.Permission.SEE_NICKNAMES);
        boolean opsSeeAll    = false;
        boolean debugMode    = false;
        try {
            opsSeeAll = RpEssentialsConfig.OPS_SEE_ALL.get();
            debugMode = RpEssentialsConfig.DEBUG_SELF_BLUR.get();
        } catch (IllegalStateException ignored) {}

        // opsSeeAll reste un fallback global indépendant des rôles
        boolean isAdminViewer = !debugMode && (seeAll || (opsSeeAll && RpEssentialsPermissions.isStaff(receiver)));

        double maxDistSq    = 0;
        double sneakDistSq  = 0;
        boolean sneakStealth = false;
        try {
            maxDistSq   = Math.pow(RpEssentialsConfig.PROXIMITY_DISTANCE.get(), 2);
            sneakDistSq = Math.pow(RpEssentialsConfig.SNEAK_PROXIMITY_DISTANCE.get(), 2);
            sneakStealth = RpEssentialsConfig.ENABLE_SNEAK_STEALTH.get();
        } catch (IllegalStateException ignored) {}

        List<ClientboundPlayerInfoUpdatePacket.Entry> originalEntries =
                ((ClientboundPlayerInfoUpdatePacketAccessor) infoPacket).getEntries();
        List<ClientboundPlayerInfoUpdatePacket.Entry> newEntries = new ArrayList<>();

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : originalEntries) {
            ServerPlayer target = receiver.server.getPlayerList().getPlayer(entry.profileId());
            Component displayName;

            if (target != null) {
                String prefix   = net.rp.rpessentials.RpEssentials.getPlayerPrefix(target);
                String realName = target.getGameProfile().getName();
                boolean hasNick = NicknameManager.hasNickname(target.getUUID());
                String nickname = hasNick ? NicknameManager.getNickname(target.getUUID()) : null;

                // tabWhitelist : toujours visible clairement
                boolean targetAlwaysVisible = false;
                try {
                    if (RpEssentialsConfig.ALWAYS_VISIBLE_LIST != null)
                        targetAlwaysVisible = RpEssentialsConfig.ALWAYS_VISIBLE_LIST.get().contains(realName);
                } catch (Exception ignored) {}
                targetAlwaysVisible = targetAlwaysVisible
                        || RpEssentialsRoleManager.has(target, RpEssentialsRoleManager.Permission.TAB_WHITELIST);

                boolean isBlacklisted = false;
                try { isBlacklisted = RpEssentialsConfig.BLACKLIST.get().contains(realName); }
                catch (Exception ignored) {}

                boolean isTargetSpectator = target.isSpectator();
                boolean blurSpectators = false;
                try {
                    if (RpEssentialsConfig.BLUR_SPECTATORS != null)
                        blurSpectators = RpEssentialsConfig.BLUR_SPECTATORS.get();
                } catch (Exception ignored) {}

                String displayedName = (hasNick && nickname != null) ? nickname : realName;

                if (targetAlwaysVisible && !isBlacklisted) {
                    displayName = Component.literal(prefix + displayedName);
                } else if (isAdminViewer && !isBlacklisted) {
                    // seeNicknames : format "Nickname (RealName)" si différents
                    if (seeNicknames && hasNick && nickname != null) {
                        displayName = Component.literal(prefix + nickname + " §7§o(" + realName + ")");
                    } else if (opsSeeAll && RpEssentialsPermissions.isStaff(receiver) && hasNick && nickname != null) {
                        displayName = Component.literal(prefix + nickname + " §7§o(" + realName + ")");
                    } else {
                        displayName = Component.literal(prefix + displayedName);
                    }
                } else {
                    double distSq = receiver.distanceToSqr(target);
                    double effectiveMaxDistSq = (sneakStealth && target.isCrouching())
                            ? sneakDistSq : maxDistSq;

                    boolean shouldBlur = isBlacklisted || debugMode
                            || distSq > effectiveMaxDistSq
                            || (isTargetSpectator && blurSpectators);

                    if (shouldBlur) {
                        String cleanName = displayedName.replaceAll("§.", "");
                        int obfLen;
                        try { obfLen = RpEssentialsConfig.OBFUSCATED_NAME_LENGTH.get(); }
                        catch (IllegalStateException e) { obfLen = 5; }
                        obfLen = Math.min(cleanName.length(), obfLen);
                        displayName = Component.literal("§k" + "?".repeat(obfLen));
                    } else {
                        displayName = Component.literal(prefix + displayedName);
                    }
                }
            } else {
                displayName = entry.displayName();
            }

            newEntries.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                    entry.profileId(), entry.profile(), entry.listed(),
                    entry.latency(), entry.gameMode(), displayName, entry.chatSession()));
        }

        ClientboundPlayerInfoUpdatePacket newPacket =
                new ClientboundPlayerInfoUpdatePacket(actions, List.of());
        ((ClientboundPlayerInfoUpdatePacketAccessor) newPacket).setEntries(newEntries);
        return newPacket;
    }
}