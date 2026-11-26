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
        ServerStatus filtered = VanishManager.filterStatus((MinecraftServer) (Object) this, status);
        // Schedule: lines above the player names.
        java.util.List<com.mojang.authlib.GameProfile> lines = net.rp.rpessentials.gameplay.RpEssentialsScheduleManager.serverListLines();
        if (!lines.isEmpty() && filtered.players().isPresent()) {
            ServerStatus.Players p = filtered.players().get();
            java.util.List<com.mojang.authlib.GameProfile> sample = new java.util.ArrayList<>(lines);
            sample.addAll(p.sample());
            filtered = new ServerStatus(filtered.description(),
                    java.util.Optional.of(new ServerStatus.Players(p.max(), p.online(), sample)),
                    //#if forge
                    //? filtered.version(), filtered.favicon(), filtered.enforcesSecureChat(), filtered.forgeData());
                    //#else
                    filtered.version(), filtered.favicon(), filtered.enforcesSecureChat(), filtered.isModded());
                    //#endif
        }
        cir.setReturnValue(filtered);
    }
}