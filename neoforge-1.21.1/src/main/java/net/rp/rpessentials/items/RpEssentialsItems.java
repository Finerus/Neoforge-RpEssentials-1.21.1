package net.rp.rpessentials.items;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
//#if forge
//? import net.minecraftforge.registries.RegistryObject;
//#else
import net.neoforged.neoforge.registries.DeferredHolder;
//#endif
import net.neoforged.neoforge.registries.DeferredRegister;
import net.rp.rpessentials.items.LicenseItem;
import net.rp.rpessentials.RpEssentials;

public class RpEssentialsItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, RpEssentials.MODID);

    //#if forge
    //? public static final RegistryObject<LicenseItem> LICENSE = ITEMS.register("license",
    //#else
    public static final DeferredHolder<Item, LicenseItem> LICENSE = ITEMS.register("license",
    //#endif
            () -> new LicenseItem(new Item.Properties().stacksTo(1))); // Max 1 par stack
}