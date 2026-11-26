package net.rp.rpessentials.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//#if forge
//? import net.minecraftforge.eventbus.api.Event;
//#else
import net.neoforged.neoforge.common.util.TriState;
//#endif
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.rp.rpessentials.RpEssentials;

/**
 * Client-side mirror of the item use restriction: cancels the right click locally so the
 * item animation does not play. The action is still sent to the server, which shows the message.
 */
@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public final class ClientRestrictionHandler {

    private ClientRestrictionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() || event.getEntity().isCreative()) return;
        if (ClientTooltipRestrictions.isUseBlocked(event.getItemStack().getItem())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getEntity().isCreative()) return;
        if (event.getItemStack().isEmpty()) return;
        //#if forge
        //? if (ClientTooltipRestrictions.isUseBlocked(event.getItemStack().getItem())) event.setUseItem(Event.Result.DENY);
        //#else
        if (ClientTooltipRestrictions.isUseBlocked(event.getItemStack().getItem())) event.setUseItem(TriState.FALSE);
        //#endif
    }
}
