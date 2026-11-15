package net.rp.rpessentials.mixin.client;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import net.rp.rpessentials.client.HexColorText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the client draw the mod's hex colors written as {@code §#RRGGBB} in plain text (profession colors,
 * nicknames...), everywhere text is drawn: buttons, screens, chat, tooltips. Vanilla only knows
 * {@code §0}-{@code §f}, so it hid {@code §#} and printed the rest ("FF8800Forgeron").
 * Text without {@code §#} keeps the vanilla code path.
 */
@Mixin(StringDecomposer.class)
public abstract class MixinStringDecomposer {

    @Inject(
            method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void rpessentials$hexColors(String text, int skip, Style current, Style reset,
                                               FormattedCharSink sink, CallbackInfoReturnable<Boolean> cir) {
        if (text.indexOf("§#") < 0) return;
        cir.setReturnValue(HexColorText.iterateFormatted(text, skip, current, reset, sink));
    }
}
