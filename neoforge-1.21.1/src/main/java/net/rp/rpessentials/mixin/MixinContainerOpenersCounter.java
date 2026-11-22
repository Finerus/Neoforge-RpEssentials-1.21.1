package net.rp.rpessentials.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerOpenersCounter.class)
public abstract class MixinContainerOpenersCounter {

    @Inject(method = "incrementOpeners", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void rpessentials$silentOpen(Player player, Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp && VanishManager.silentOpen(sp)) ci.cancel();
    }

    @Inject(method = "decrementOpeners", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void rpessentials$silentClose(Player player, Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp && VanishManager.silentClose(sp)) ci.cancel();
    }
}