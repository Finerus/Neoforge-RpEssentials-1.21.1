package net.rp.rpessentials.items;

import net.rp.rpessentials.config.Cfg;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.rp.rpessentials.config.MessagesConfig;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

public class LicenseItem extends Item {

    public LicenseItem(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                                   net.minecraft.client.player.LocalPlayer player,
                                                   net.minecraft.world.entity.HumanoidArm arm,
                                                   net.minecraft.world.item.ItemStack stack,
                                                   float partialTick, float equipProgress, float swingProgress) {
                net.rp.rpessentials.client.LicenseCardRenderer.applyCardTransform(
                        poseStack, arm, equipProgress, swingProgress);
                return true;
            }

            /** Third person: held like in first person (both arms forward when the other hand is empty). */
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity,
                                                                              net.minecraft.world.InteractionHand hand,
                                                                              net.minecraft.world.item.ItemStack stack) {
                return net.rp.rpessentials.client.LicenseArmPose.POSE.getValue();
            }
        });
    }

    /**
     * Tooltip lines are drawn on the client, so they follow the game language.
     * A value changed by the server admin in the config is still used as is.
     */
    private static net.minecraft.network.chat.MutableComponent text(
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> cv, String key) {
        String v = Cfg.get(cv, cv.getDefault());
        if (!v.equals(cv.getDefault())) return Component.literal(v);
        return Component.translatable(key);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            net.minecraft.nbt.CompoundTag tag = stack.get(
                    net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();
            if (tag.getBoolean("revoked")) {
                tooltip.add(text(MessagesConfig.LICENSE_REVOKED_TITLE, "rpessentials.license.tooltip.revoked_title")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                tooltip.add(text(MessagesConfig.LICENSE_REVOKED_BODY, "rpessentials.license.tooltip.revoked_body")
                        .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            }
        }
        if (!net.rp.rpessentials.client.ClientFeatures.professionsEnabled()) {
            tooltip.add(Component.translatable("rpessentials.professions.disabled.license_tooltip")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        tooltip.add(text(MessagesConfig.LICENSE_TOOLTIP_OFFICIAL, "rpessentials.license.tooltip.official")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(text(MessagesConfig.LICENSE_TOOLTIP_NONTRANSFERABLE, "rpessentials.license.tooltip.non_transferable")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}