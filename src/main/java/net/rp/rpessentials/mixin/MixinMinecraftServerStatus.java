package net.rp.rpessentials.mixin;

import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServerStatus {

    @Inject(method = "buildServerStatus", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void rpessentials$filterStatus(CallbackInfoReturnable<ServerStatus> cir) {
        ServerStatus status = cir.getReturnValue();
        if (status == null) return;
        cir.setReturnValue(VanishManager.filterStatus((MinecraftServer) (Object) this, status));
    }
}