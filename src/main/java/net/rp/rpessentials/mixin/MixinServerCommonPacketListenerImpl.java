package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.RpEssentialsRoleManager;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.TabListCache;
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
        if (!actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)
                && !actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) return packet;

        boolean opsSeeAll = false;
        boolean debugMode = false;
        boolean blurSpectators = false;
        boolean sneakStealth = false;
        double maxDistSq = 0;
        double sneakDistSq = 0;
        int obfLen = 5;
        List<? extends String> whitelist = List.of();
        try {
            opsSeeAll = RpEssentialsConfig.OPS_SEE_ALL.get();
            debugMode = RpEssentialsConfig.DEBUG_SELF_BLUR.get();
            blurSpectators = RpEssentialsConfig.BLUR_SPECTATORS.get();
            sneakStealth = RpEssentialsConfig.ENABLE_SNEAK_STEALTH.get();
            maxDistSq = Math.pow(RpEssentialsConfig.PROXIMITY_DISTANCE.get(), 2);
            sneakDistSq = Math.pow(RpEssentialsConfig.SNEAK_PROXIMITY_DISTANCE.get(), 2);
            obfLen = RpEssentialsConfig.OBFUSCATED_NAME_LENGTH.get();
            whitelist = RpEssentialsConfig.WHITELIST.get();
        } catch (IllegalStateException ignored) {}

        // Observateur : role seeAll, liste whitelist de /rpessentials blurtab, ou staff avec opsSeeAll
        boolean isStaffViewer = RpEssentialsPermissions.isStaff(receiver);
        boolean seesAll = !debugMode
                && (RpEssentialsRoleManager.has(receiver, RpEssentialsRoleManager.Permission.SEE_ALL)
                || whitelist.contains(receiver.getGameProfile().getName())
                || (opsSeeAll && isStaffViewer));
        boolean showRealNames = RpEssentialsRoleManager.has(receiver, RpEssentialsRoleManager.Permission.SEE_NICKNAMES)
                || (opsSeeAll && isStaffViewer);

        List<ClientboundPlayerInfoUpdatePacket.Entry> originalEntries =
                ((ClientboundPlayerInfoUpdatePacketAccessor) infoPacket).getEntries();
        List<ClientboundPlayerInfoUpdatePacket.Entry> newEntries = new ArrayList<>();

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : originalEntries) {
            ServerPlayer target = receiver.server.getPlayerList().getPlayer(entry.profileId());
            Component displayName;

            if (target == null) {
                displayName = entry.displayName();
            } else {
                String realName = target.getGameProfile().getName();
                String prefix = RpEssentials.getPlayerPrefix(target);
                String nickname = NicknameManager.getNickname(target.getUUID());
                String displayed = nickname != null ? nickname : realName;
                String shown = (nickname != null && showRealNames)
                        ? nickname + " §7§o(" + realName + ")"
                        : displayed;

                boolean blacklisted = TabListCache.isBlacklisted(realName);
                boolean alwaysVisible = TabListCache.isAlwaysVisible(realName)
                        || RpEssentialsRoleManager.has(target, RpEssentialsRoleManager.Permission.TAB_WHITELIST);

                boolean clear;
                if (blacklisted) {
                    clear = false;
                } else if (alwaysVisible || seesAll) {
                    clear = true;
                } else {
                    double effectiveMaxDistSq = (sneakStealth && target.isCrouching()) ? sneakDistSq : maxDistSq;
                    boolean blur = debugMode
                            || receiver.distanceToSqr(target) > effectiveMaxDistSq
                            || (target.isSpectator() && blurSpectators);
                    clear = !blur;
                }

                if (clear) {
                    displayName = Component.literal(prefix + shown);
                } else {
                    String cleanName = displayed.replaceAll("§.", "");
                    int len = Math.min(cleanName.length(), obfLen);
                    displayName = Component.literal("§k" + "?".repeat(len));
                }
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