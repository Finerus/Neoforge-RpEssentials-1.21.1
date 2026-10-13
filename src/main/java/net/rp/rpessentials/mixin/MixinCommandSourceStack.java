package net.rp.rpessentials.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(CommandSourceStack.class)
public abstract class MixinCommandSourceStack {

    @Inject(method = "getOnlinePlayerNames", at = @At("RETURN"), cancellable = true, remap = false)
    private void rpessentials$filterNames(CallbackInfoReturnable<Collection<String>> cir) {
        if (!VanishManager.anyVanished() || !VanishManager.hides(VanishManager.Aspect.SELECTORS)) return;
        CommandSourceStack src = (CommandSourceStack) (Object) this;
        Set<String> hidden = new HashSet<>();
        for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
            if (VanishManager.isHiddenFrom(src, p.getUUID())) hidden.add(p.getGameProfile().getName());
        }
        if (hidden.isEmpty()) return;
        List<String> out = new ArrayList<>(cir.getReturnValue());
        out.removeAll(hidden);
        cir.setReturnValue(out);
    }
}