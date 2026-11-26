package net.rp.rpessentials.moderation;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//#if forge
//#else
import net.neoforged.neoforge.common.util.TriState;
//#endif
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
//#if forge
//? import net.minecraftforge.event.entity.living.LivingAttackEvent;
//? import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
//#else
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
//#endif
import net.rp.rpessentials.RpEssentials;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class VanishEvents {

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        //#if forge
        //? if (event.getNewTarget() instanceof ServerPlayer p
        //#else
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer p
        //#endif
                && VanishManager.isVanished(p.getUUID()) && VanishManager.hides(VanishManager.Aspect.MOBS)) {
            //#if forge
            //? event.setNewTarget(null);
            //#else
            event.setNewAboutToBeSetTarget(null);
            //#endif
        }
    }

    @SubscribeEvent
    //#if forge
    //? public static void onPickup(EntityItemPickupEvent event) {
        //? if (event.getEntity() instanceof ServerPlayer p
    //#else
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer p
    //#endif
                && VanishManager.isVanished(p.getUUID()) && VanishManager.hides(VanishManager.Aspect.PICKUP)) {
            //#if forge
            //? event.setCanceled(true);
            //#else
            event.setCanPickup(TriState.FALSE);
            //#endif
        }
    }

    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (event.getCause() instanceof ServerPlayer p
                && VanishManager.isVanished(p.getUUID()) && VanishManager.hides(VanishManager.Aspect.GAME_EVENTS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    //#if forge
    //? public static void onDamage(LivingAttackEvent event) {
    //#else
    public static void onDamage(LivingIncomingDamageEvent event) {
    //#endif
        if (event.getEntity() instanceof ServerPlayer p
                && VanishManager.isVanished(p.getUUID()) && VanishManager.invulnerable()) {
            event.setCanceled(true);
        }
    }
}