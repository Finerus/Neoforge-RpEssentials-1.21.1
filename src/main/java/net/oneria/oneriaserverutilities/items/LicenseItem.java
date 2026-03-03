package net.oneria.oneriaserverutilities.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LicenseItem extends Item {

    public LicenseItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            net.minecraft.nbt.CompoundTag tag = stack.get(
                    net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();
            if (tag.getBoolean("revoked")) {
                tooltip.add(Component.literal("§c§l✖ PERMIS RÉVOQUÉ")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                tooltip.add(Component.literal("§cCe permis n'est plus valide.")
                        .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            }
        }
        tooltip.add(Component.literal("§7Document officiel").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("§8Non transférable").withStyle(ChatFormatting.DARK_GRAY));
    }
}