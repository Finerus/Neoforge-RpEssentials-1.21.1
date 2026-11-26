package net.rp.rpessentials.mixin;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
//#if forge
//#else
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
//#endif
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.gameplay.TabBlur;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

//#if forge
//? @Mixin(ServerGamePacketListenerImpl.class)
//#else
@Mixin(ServerCommonPacketListenerImpl.class)
//#endif
public abstract class MixinServerCommonPacketListenerImpl {

    @ModifyVariable(
            //#if forge
            //? method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            //#else
            method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            //#endif
            at = @At("HEAD"),
            //#if forge
            //? argsOnly = true)
            //#else
            argsOnly = true,
            remap = false
    )
            //#endif
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
        boolean showRealNames = RpPerms.has(receiver, RpPerm.SEE_NICKNAMES);

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

            Component displayName = TabBlur.displayName(
                    target, TabBlur.isClear(receiver, seesAll, target, s), showRealNames, s);

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