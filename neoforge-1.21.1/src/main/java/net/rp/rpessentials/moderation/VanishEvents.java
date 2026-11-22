package net.rp.rpessentials.moderation;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.rp.rpessentials.RpEssentials;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class VanishEvents {

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer p
                && VanishManager.isVanished(p.getUUID()) && VanishManager.hides(VanishManager.Aspect.MOBS)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer p
                && VanishManager.isVanished(p.getUUID()) && VanishManager.hides(VanishManager.Aspect.PICKUP)) {
            event.setCanPickup(TriState.FALSE);
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
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p
                && VanishManager.isVanished(p.getUUID()) && VanishManager.invulnerable()) {
            event.setCanceled(true);
        }
    }
}