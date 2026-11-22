package net.rp.rpessentials.compat;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.rp.rpessentials.items.RpEssentialsItems;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class CuriosCompat {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CuriosCompat::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent evt) {
        // The license can be worn in the curios "license" slot (data/rpessentials/curios). No tick logic needed.
        evt.enqueueWork(() -> CuriosApi.registerCurio(RpEssentialsItems.LICENSE.get(), new ICurioItem() {}));
    }
}
