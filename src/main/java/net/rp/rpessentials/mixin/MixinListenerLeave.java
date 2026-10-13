package net.rp.rpessentials.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinListenerLeave {

    @Shadow public ServerPlayer player;

    @WrapOperation(
            method = "onDisconnect",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"),
            remap = false,
            require = 0
    )
    private void rpessentials$hideLeave(PlayerList list, Component message, boolean overlay, Operation<Void> original) {
        if (VanishManager.isVanished(player.getUUID()) && VanishManager.hides(VanishManager.Aspect.JOIN_VANILLA)) return;
        original.call(list, message, overlay);
    }
}