package net.rp.rpessentials.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import net.rp.rpessentials.gameplay.ScheduleGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

/** The schedule refuses a player like the whitelist does, after the vanilla checks (ban, whitelist, full server). */
@Mixin(PlayerList.class)
public abstract class MixinPlayerListLogin {

    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true, remap = false)
    private void rpessentials$schedule(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() != null) return;
        Component refusal = ScheduleGate.refusal(((PlayerList) (Object) this).getServer(), profile);
        if (refusal != null) cir.setReturnValue(refusal);
    }
}
