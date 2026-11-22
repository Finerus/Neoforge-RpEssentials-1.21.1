package net.rp.rpessentials.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerList.class)
public abstract class MixinPlayerListJoin {

    @WrapOperation(
            method = "placeNewPlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"),
            remap = false,
            require = 0
    )
    private void rpessentials$hideJoin(PlayerList list, Component message, boolean overlay,
                                       Operation<Void> original, @Local(argsOnly = true) ServerPlayer player) {
        if (VanishManager.isVanished(player.getUUID()) && VanishManager.hides(VanishManager.Aspect.JOIN_VANILLA)) return;
        original.call(list, message, overlay);
    }
}