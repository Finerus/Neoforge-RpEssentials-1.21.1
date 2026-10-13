package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.players.PlayerList;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class MixinPlayerListAdvancement {

    @Inject(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void rpessentials$hideAdvancement(Component message, boolean overlay, CallbackInfo ci) {
        if (!VanishManager.isAdvancementHidden()) return;
        if (message.getContents() instanceof TranslatableContents tc
                && tc.getKey().startsWith("chat.type.advancement.")) {
            ci.cancel();
        }
    }
}