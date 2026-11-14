package net.rp.rpessentials.mixin;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.config.ChatConfig;
import net.rp.rpessentials.identity.NicknameManager;
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
        CommandSourceStack src = (CommandSourceStack) (Object) this;
        boolean vanish = VanishManager.anyVanished() && VanishManager.hides(VanishManager.Aspect.SELECTORS);
        boolean hideReal = false;
        if (Cfg.ready(ChatConfig.SPEC)) { hideReal = ChatConfig.HIDE_REAL_NAMES.get() && !RpEssentialsPermissions.isStaffSource(src); }
        if (!vanish && !hideReal) return;

        Set<String> hidden = new HashSet<>();
        for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
            if (vanish && VanishManager.isHiddenFrom(src, p.getUUID())) {
                hidden.add(p.getGameProfile().getName());
            } else if (hideReal && src.getEntity() != p && NicknameManager.hasNickname(p.getUUID())) {
                hidden.add(p.getGameProfile().getName());
            }
        }
        if (hidden.isEmpty()) return;
        List<String> out = new ArrayList<>(cir.getReturnValue());
        out.removeAll(hidden);
        cir.setReturnValue(out);
    }
}