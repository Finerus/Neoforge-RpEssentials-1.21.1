package net.rp.rpessentials.compat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.rp.rpessentials.RpEssentialsItems;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;

public class CuriosCompat {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CuriosCompat::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent evt) {
        evt.registerItem(
                CuriosCapability.ITEM,
                (stack, context) -> new ICurio() {
                    @Override
                    public net.minecraft.world.item.ItemStack getStack() {
                        return stack;
                    }

                    @Override
                    public void curioTick(SlotContext slotContext) {
                        // pas de logique de tick nécessaire pour une license
                    }
                },
                RpEssentialsItems.LICENSE.get()
        );
    }
}