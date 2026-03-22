package net.rp.rpessentials.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public interface ArmorSlotAccessor {

    @Accessor(value = "owner", remap = false)
    LivingEntity getOwner();
}