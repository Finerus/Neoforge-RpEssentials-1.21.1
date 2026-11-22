package net.rp.rpessentials.mixin;

import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.rp.rpessentials.moderation.VanishManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class MixinPlayerListSound {

    @Inject(
            method = "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void rpessentials$vanishNoise(@Nullable Player except, double x, double y, double z, double radius,
                                          ResourceKey<Level> dimension, Packet<?> packet, CallbackInfo ci) {
        if (!(except instanceof ServerPlayer source)) return;
        if (!VanishManager.isVanished(source.getUUID()) || !VanishManager.hides(VanishManager.Aspect.NOISE)) return;
        for (ServerPlayer p : ((PlayerList) (Object) this).getPlayers()) {
            if (p == except || p.level().dimension() != dimension) continue;
            if (VanishManager.isHiddenFrom(p, source.getUUID())) continue;
            double dx = x - p.getX(), dy = y - p.getY(), dz = z - p.getZ();
            if (dx * dx + dy * dy + dz * dz < radius * radius) p.connection.send(packet);
        }
        ci.cancel();
    }
}