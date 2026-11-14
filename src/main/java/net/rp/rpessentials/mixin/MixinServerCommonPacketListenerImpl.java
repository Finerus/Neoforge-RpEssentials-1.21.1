package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsRoleManager;
import net.rp.rpessentials.TabBlur;
import net.rp.rpessentials.TabListCache;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
        if (!(packet instanceof ClientboundPlayerInfoUpdatePacket original)) return packet;

        Object self = this;
        if (!(self instanceof ServerGamePacketListenerImpl gameListener)) return packet;
        ServerPlayer receiver = gameListener.player;
        if (receiver == null) return packet;

        ClientboundPlayerInfoUpdatePacket filtered = VanishManager.filterInfo(receiver, original);
        Packet blurred = rpessentials$blur(receiver, filtered);
        return blurred instanceof ClientboundPlayerInfoUpdatePacket p
                ? VanishManager.markViewers(receiver, p)
                : blurred;
    }

    @Unique
    private Packet rpessentials$blur(ServerPlayer receiver, ClientboundPlayerInfoUpdatePacket infoPacket) {
        TabBlur.Settings s = TabBlur.settings();
        if (s == null || !s.enabled()) return infoPacket;

        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = infoPacket.actions();
        if (!actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)
                && !actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) return infoPacket;

        boolean seesAll = TabBlur.seesAll(receiver, s);
        boolean showRealNames = RpEssentialsRoleManager.has(receiver, RpEssentialsRoleManager.Permission.SEE_NICKNAMES);

        List<ClientboundPlayerInfoUpdatePacket.Entry> originalEntries =
                ((ClientboundPlayerInfoUpdatePacketAccessor) infoPacket).getEntries();
        List<ClientboundPlayerInfoUpdatePacket.Entry> newEntries = new ArrayList<>(originalEntries.size());
        boolean changed = false;

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : originalEntries) {
            if (VanishManager.isHiddenFrom(receiver, entry.profileId())) {
                newEntries.add(entry);
                continue;
            }
            ServerPlayer target = receiver.server.getPlayerList().getPlayer(entry.profileId());
            if (target == null) {
                newEntries.add(entry);
                continue;
            }

            String realName = target.getGameProfile().getName();
            String nickname = NicknameManager.getNickname(target.getUUID());
            String displayed = nickname != null ? nickname : realName;
            String coloredPrefix = net.rp.rpessentials.ColorHelper
                    .translateAlternateColorCodes(RpEssentials.getPlayerPrefix(target));
            Component displayName;

            if (TabBlur.isClear(receiver, seesAll, target, s)) {
                String shown = (nickname != null && showRealNames)
                        ? nickname + " §7§o(" + realName + ")"
                        : displayed;
                displayName = net.rp.rpessentials.ColorHelper.parseColors(coloredPrefix + shown);
            } else {
                displayName = TabBlur.hidden(displayed, coloredPrefix, s);
            }

            newEntries.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                    entry.profileId(), entry.profile(), entry.listed(),
                    entry.latency(), entry.gameMode(), displayName, entry.chatSession()));
            changed = true;
        }

        if (!changed) return infoPacket;
        ClientboundPlayerInfoUpdatePacket newPacket = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
        ((ClientboundPlayerInfoUpdatePacketAccessor) newPacket).setEntries(newEntries);
        return newPacket;
    }
}