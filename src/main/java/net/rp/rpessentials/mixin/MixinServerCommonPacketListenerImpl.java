package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
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
        try {
            if (!RpEssentialsConfig.ENABLE_BLUR.get()) {
                return packet;
            }
        } catch (IllegalStateException e) {
            return packet;
        }

        Object self = this;
        if (!(self instanceof ServerGamePacketListenerImpl)) return packet;

        ServerGamePacketListenerImpl gameListener = (ServerGamePacketListenerImpl) self;
        ServerPlayer receiver = gameListener.player;
        if (receiver == null) return packet;

        if (!(packet instanceof ClientboundPlayerInfoUpdatePacket)) return packet;

        ClientboundPlayerInfoUpdatePacket infoPacket = (ClientboundPlayerInfoUpdatePacket) packet;

        boolean isWhitelisted = RpEssentialsConfig.WHITELIST.get().contains(receiver.getGameProfile().getName());

        boolean isOpExempt;
        try {
            int opLevel = RpEssentialsConfig.OP_LEVEL_BYPASS.get();
            isOpExempt = RpEssentialsConfig.OPS_SEE_ALL.get()
                    && opLevel > 0
                    && receiver.hasPermissions(opLevel);
        } catch (IllegalStateException e) {
            isOpExempt = false;
        }

        boolean debugMode = RpEssentialsConfig.DEBUG_SELF_BLUR.get();
        boolean isAdmin = !debugMode && (isWhitelisted || isOpExempt);

        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = infoPacket.actions();

        boolean shouldProcess = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME) ||
                actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);

        if (!shouldProcess) return packet;

        List<ClientboundPlayerInfoUpdatePacket.Entry> originalEntries =
                ((ClientboundPlayerInfoUpdatePacketAccessor) infoPacket).getEntries();
        List<ClientboundPlayerInfoUpdatePacket.Entry> newEntries = new ArrayList<>();
        double maxDistSq = Math.pow(RpEssentialsConfig.PROXIMITY_DISTANCE.get(), 2);
        double sneakMaxDistSq = Math.pow(RpEssentialsConfig.SNEAK_PROXIMITY_DISTANCE.get(), 2);
        boolean sneakStealthEnabled = RpEssentialsConfig.ENABLE_SNEAK_STEALTH.get();

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : originalEntries) {
            ServerPlayer targetPlayer = receiver.server.getPlayerList().getPlayer(entry.profileId());
            Component displayName;

            if (targetPlayer != null) {
                String prefix = net.rp.rpessentials.RpEssentials.getPlayerPrefix(targetPlayer);
                String realName = targetPlayer.getGameProfile().getName();
                boolean hasNickname = NicknameManager.hasNickname(targetPlayer.getUUID());
                String nickname = hasNickname ? NicknameManager.getNickname(targetPlayer.getUUID()) : null;

                boolean isAlwaysVisible = false;
                try {
                    if (RpEssentialsConfig.ALWAYS_VISIBLE_LIST != null)
                        isAlwaysVisible = RpEssentialsConfig.ALWAYS_VISIBLE_LIST.get().contains(realName);
                } catch (Exception ignored) {}

                boolean isBlacklisted = RpEssentialsConfig.BLACKLIST.get().contains(realName);
                boolean isTargetSpectator = targetPlayer.isSpectator();
                boolean shouldBlurSpectators = false;
                try {
                    if (RpEssentialsConfig.BLUR_SPECTATORS != null)
                        shouldBlurSpectators = RpEssentialsConfig.BLUR_SPECTATORS.get();
                } catch (Exception ignored) {}

                if (isAlwaysVisible) {
                    displayName = Component.literal(prefix + (hasNickname && nickname != null ? nickname : realName));
                } else if (isAdmin && !isBlacklisted) {
                    displayName = hasNickname && nickname != null
                            ? Component.literal(prefix + nickname + " §7§o(" + realName + ")")
                            : Component.literal(prefix + realName);
                } else {
                    double distSq = receiver.distanceToSqr(targetPlayer);
                    double effectiveMaxDistSq = (sneakStealthEnabled && targetPlayer.isCrouching())
                            ? sneakMaxDistSq : maxDistSq;

                    boolean shouldBlur = isBlacklisted || debugMode || (distSq > effectiveMaxDistSq)
                            || (isTargetSpectator && shouldBlurSpectators && !isAlwaysVisible);

                    String displayedName = (hasNickname && nickname != null) ? nickname : realName;

                    if (shouldBlur) {
                        String cleanName = displayedName.replaceAll("§.", "");
                        int obfLength = Math.min(cleanName.length(), RpEssentialsConfig.OBFUSCATED_NAME_LENGTH.get());
                        displayName = Component.literal("§k" + "?".repeat(obfLength));
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

        ClientboundPlayerInfoUpdatePacket newPacket = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
        ((ClientboundPlayerInfoUpdatePacketAccessor) newPacket).setEntries(newEntries);
        return newPacket;
    }
}