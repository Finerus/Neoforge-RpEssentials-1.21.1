package net.rp.rpessentials.compat;

import net.neoforged.bus.api.IEventBus;
//#if forge
//? import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
//#else
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
//#endif
import net.rp.rpessentials.items.RpEssentialsItems;
//#if forge
//? import top.theillusivec4.curios.api.CuriosApi;
//? import top.theillusivec4.curios.api.type.capability.ICurioItem;
//#else
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
//#endif

public class CuriosCompat {

    public static void register(IEventBus modEventBus) {
        //#if forge
        //? modEventBus.addListener(CuriosCompat::onCommonSetup);
        //#else
        modEventBus.addListener(CuriosCompat::onRegisterCapabilities);
        //#endif
    }

    //#if forge
    //? private static void onCommonSetup(FMLCommonSetupEvent evt) {
        //? // The license can be worn in the curios "license" slot (data/rpessentials/curios). No tick logic needed.
        //? evt.enqueueWork(() -> CuriosApi.registerCurio(RpEssentialsItems.LICENSE.get(), new ICurioItem() {}));
    //#else
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
    //#endif
    }
}
