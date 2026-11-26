package net.rp.rpessentials.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TagCommand;
import net.minecraft.world.entity.Entity;
import net.rp.rpessentials.permission.RoleTagGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * /tag add and remove, whoever runs them (players, command blocks, signs, /execute, functions): the command event
 * of the loader does not see the last ones.
 */
@Mixin(TagCommand.class)
public abstract class MixinTagCommand {

    @Inject(method = {"addTag", "removeTag"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void rpessentials$protectRoles(CommandSourceStack source, Collection<? extends Entity> targets, String tag,
                                                  CallbackInfoReturnable<Integer> cir) {
        if (RoleTagGuard.refuses(source, targets, tag)) cir.setReturnValue(0);
    }
}
