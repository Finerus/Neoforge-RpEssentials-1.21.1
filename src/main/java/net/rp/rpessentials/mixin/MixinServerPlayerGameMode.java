package net.rp.rpessentials.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ServerPlayerGameMode.class)
public class MixinServerPlayerGameMode {

    private static final Map<UUID, Long> lastContainerMessage = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000L;

    @Inject(
            method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void rpessentials$blockContainerOpen(ServerPlayer player, Level level, ItemStack stack,
                                                 InteractionHand hand, BlockHitResult hitResult,
                                                 CallbackInfoReturnable<InteractionResult> cir) {
        if (player.isCreative()) return;

        BlockPos pos = hitResult.getBlockPos();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
                level.getBlockState(pos).getBlock());

        if (!ProfessionRestrictionManager.canOpenContainer(player, blockId)) {
            ProfessionRestrictionManager.notifyBlocked(player, "container",
                    () -> ProfessionRestrictionManager.getContainerOpenBlockedMessage(player, blockId));
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}