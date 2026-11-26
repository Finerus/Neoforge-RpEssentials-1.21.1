package net.rp.rpessentials.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.rp.rpessentials.client.ServerListBadge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Open / closed badge of the servers with a schedule, in the multiplayer list. */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class MixinOnlineServerEntry {

    @Shadow @Final private JoinMultiplayerScreen screen;
    @Shadow @Final private ServerData serverData;
    //#if forge
    //#else
    /** Copy of the lines made when the ping ends: null or empty before (offline server, ping not done...). */
    @Shadow private List<Component> onlinePlayersTooltip;
    //#endif

    @Unique private final ServerListBadge rpessentials$badge = new ServerListBadge();
    @Unique private static boolean rpessentials$failed;

    // require = 0: only a decoration, never a reason not to start.
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void rpessentials$drawBadge(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                        int mouseX, int mouseY, boolean hovering, float partialTick, CallbackInfo ci) {
        // A decoration: whatever happens, the list keeps working (logged once).
        try {
            List<Component> tooltip = rpessentials$badge.draw(graphics, serverData, top, left, width, mouseX, mouseY);
            //#if forge
            //? if (tooltip != null) screen.setToolTip(tooltip);
            //#else
            List<Component> lines = rpessentials$badge.lines();
            if (lines != null && onlinePlayersTooltip != null && !onlinePlayersTooltip.isEmpty()) onlinePlayersTooltip = lines;
            if (tooltip != null) screen.setTooltipForNextRenderPass(tooltip.stream().map(Component::getVisualOrderText).toList());
            //#endif
        } catch (RuntimeException e) {
            if (!rpessentials$failed) net.rp.rpessentials.RpEssentials.LOGGER.error("[ServerList] The open / closed badge could not be drawn", e);
            rpessentials$failed = true;
        }
    }
}
