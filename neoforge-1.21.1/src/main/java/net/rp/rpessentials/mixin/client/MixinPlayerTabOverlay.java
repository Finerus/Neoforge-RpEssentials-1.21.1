package net.rp.rpessentials.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(PlayerTabOverlay.class)
@OnlyIn(Dist.CLIENT)
public class MixinPlayerTabOverlay {

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/PlayerFaceRenderer;draw(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;IIIZZ)V"
            )
    )
    private void rpessentials$skipFaceIfObfuscated(
            GuiGraphics guiGraphics, ResourceLocation texture,
            int x, int y, int size, boolean drawHat, boolean upsideDown,
            Operation<Void> original,
            @Local PlayerInfo playerinfo1) {

        Component displayName = playerinfo1.getTabListDisplayName();
        if (displayName != null && rpessentials$obfuscated(displayName)) return;
        original.call(guiGraphics, texture, x, y, size, drawHat, upsideDown);
    }

    @Unique
    private static boolean rpessentials$obfuscated(Component c) {
        String ins = c.getStyle().getInsertion();
        if (ins != null && ins.startsWith("rpe_blur")) return true;
        return c.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents.LiteralContents lit
                && lit.text().contains("§k");
    }
}