package net.rp.rpessentials.mixin;

import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemCombinerMenu.class)
public abstract class MixinItemCombinerMenuBase {

    @Shadow
    protected ResultContainer resultSlots;
}