package net.rp.rpessentials.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(EntitySelector.class)
public abstract class MixinEntitySelector {

    @Inject(method = "findPlayers", at = @At("RETURN"), cancellable = true, remap = false)
    private void rpessentials$filterPlayers(CommandSourceStack source, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        List<ServerPlayer> result = cir.getReturnValue();
        if (result == null || result.isEmpty() || !VanishManager.anyVanished()
                || !VanishManager.hides(VanishManager.Aspect.SELECTORS)) return;
        List<ServerPlayer> out = new ArrayList<>(result);
        out.removeIf(p -> VanishManager.isHiddenFrom(source, p.getUUID()));
        cir.setReturnValue(out);
    }

    @Inject(method = "findEntities", at = @At("RETURN"), cancellable = true, remap = false)
    private void rpessentials$filterEntities(CommandSourceStack source, CallbackInfoReturnable<List<? extends Entity>> cir) {
        List<? extends Entity> result = cir.getReturnValue();
        if (result == null || result.isEmpty() || !VanishManager.anyVanished()
                || !VanishManager.hides(VanishManager.Aspect.SELECTORS)) return;
        List<Entity> out = new ArrayList<>(result);
        out.removeIf(e -> e instanceof ServerPlayer p && VanishManager.isHiddenFrom(source, p.getUUID()));
        cir.setReturnValue(out);
    }
}