package net.rp.rpessentials.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.rp.rpessentials.api.VanishTrackable;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class MixinTrackedEntity implements VanishTrackable {

    @Shadow @Final Entity entity;

    @Shadow public abstract void removePlayer(ServerPlayer player);
    @Shadow public abstract void updatePlayers(List<ServerPlayer> players);

    @Override
    public void rpessentials$refresh(List<ServerPlayer> players) {
        updatePlayers(players);
    }

    @Inject(
            method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void rpessentials$hideVanished(ServerPlayer viewer, CallbackInfo ci) {
        if (!VanishManager.anyVanished()) return;
        if (entity instanceof ServerPlayer target
                && VanishManager.canHide(VanishManager.Aspect.WORLD, viewer, target.getUUID())) {
            removePlayer(viewer);
            ci.cancel();
        }
    }
}