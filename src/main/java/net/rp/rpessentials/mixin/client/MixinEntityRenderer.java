package net.rp.rpessentials.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.rp.rpessentials.ColorHelper;
import net.rp.rpessentials.client.ClientNametagCache;
import net.rp.rpessentials.client.ClientNametagConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(EntityRenderer.class)
@OnlyIn(Dist.CLIENT)
public abstract class MixinEntityRenderer<T extends Entity> {

    @Unique
    private Entity rpessentials$currentEntity = null;

    @Inject(
            method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void rpessentials$hideNametag(T entity, Component displayName, PoseStack poseStack,
                                          MultiBufferSource bufferSource, int packedLight,
                                          float partialTick, CallbackInfo ci) {
        if (ClientNametagConfig.shouldHideNametags()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"),
            remap = false
    )
    private void rpessentials$captureEntity(T entity, Component displayName, PoseStack poseStack,
                                            MultiBufferSource bufferSource, int packedLight,
                                            float partialTick, CallbackInfo ci) {
        rpessentials$currentEntity = entity;
    }

    @Inject(
            method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("RETURN"),
            remap = false
    )
    private void rpessentials$clearEntity(T entity, Component displayName, PoseStack poseStack,
                                          MultiBufferSource bufferSource, int packedLight,
                                          float partialTick, CallbackInfo ci) {
        rpessentials$currentEntity = null;
    }

    @ModifyArg(
            method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
                    remap = false,
                    ordinal = 0
            ),
            index = 0,
            remap = false
    )
    private Component rpessentials$injectNicknameBackground(Component original) {
        Component resolved = rpessentials$resolveNickname(original);
        return resolved != null ? resolved : original;
    }

    @ModifyArg(
            method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
                    remap = false,
                    ordinal = 1
            ),
            index = 0,
            remap = false
    )
    private Component rpessentials$injectNicknameText(Component original) {
        Component resolved = rpessentials$resolveNickname(original);
        return resolved != null ? resolved : original;
    }

    @Unique
    private Component rpessentials$resolveNickname(Component original) {
        Entity entity = rpessentials$currentEntity;
        if (!(entity instanceof Player player)) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        if (player.getUUID().equals(mc.player.getUUID())) return null;

        String realName = player.getGameProfile().getName();
        UUID uuid = player.getUUID();

        ClientNametagCache.NametagData data = ClientNametagCache.get(uuid);
        if (data == null) {
            return Component.literal(realName);
        }

        String prefix  = data.prefix() != null ? data.prefix() : "";
        String display = data.displayName() != null && !data.displayName().isEmpty()
                ? data.displayName()
                : realName;

        return ColorHelper.parseColors(prefix + display);
    }
}