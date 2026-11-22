package net.rp.rpessentials.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.rp.rpessentials.client.skin.CanvasScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla scissors ignore the pose matrix: while a {@link CanvasScreen} is drawn on its enlarged canvas,
 * list clipping is converted from canvas pixels to real pixels.
 */
@Mixin(GuiGraphics.class)
public abstract class MixinGuiGraphicsCanvas {

    @Shadow
    private void flushIfManaged() {}

    @Inject(method = "applyScissor", at = @At("HEAD"), cancellable = true)
    private void rpessentials$canvasScissor(ScreenRectangle rectangle, CallbackInfo ci) {
        CanvasScreen canvas = CanvasScreen.rendering();
        if (canvas == null || rectangle == null) return;
        flushIfManaged();
        int[] r = canvas.scissorToReal(rectangle);
        RenderSystem.enableScissor(r[0], r[1], r[2], r[3]);
        ci.cancel();
    }
}
