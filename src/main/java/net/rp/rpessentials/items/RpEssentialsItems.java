package net.rp.rpessentials.items;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.rp.rpessentials.items.LicenseItem;
import net.rp.rpessentials.RpEssentials;

public class RpEssentialsItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, RpEssentials.MODID);

    public static final RegistryObject<LicenseItem> LICENSE = ITEMS.register("license",
            () -> new LicenseItem(new Item.Properties().stacksTo(1))); // Max 1 par stack
}