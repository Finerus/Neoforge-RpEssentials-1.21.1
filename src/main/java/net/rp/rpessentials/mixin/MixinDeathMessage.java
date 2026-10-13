package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.rp.rpessentials.moderation.DeathRPManager;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public abstract class MixinDeathMessage {

    @Redirect(
            method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
            ),
            remap = false
    )
    private void onDeathMessageBroadcast(PlayerList playerList, Component message, boolean overlay, DamageSource damageSource) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        boolean rpDeath = DeathRPManager.isDeathRPEnabled(self.getUUID());
        boolean hiddenByVanish = VanishManager.isVanished(self.getUUID())
                && VanishManager.hides(VanishManager.Aspect.DEATH);
        if (!rpDeath && !hiddenByVanish) {
            playerList.broadcastSystemMessage(message, overlay);
        }
    }
}