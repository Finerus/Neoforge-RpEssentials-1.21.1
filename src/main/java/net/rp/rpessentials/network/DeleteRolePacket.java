package net.rp.rpessentials.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.RpEssentialsRoleManager;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.ArrayList;
import java.util.List;

public record DeleteRolePacket(String id) implements CustomPacketPayload {

    public static final Type<DeleteRolePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "delete_role"));

    public static final StreamCodec<ByteBuf, DeleteRolePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, DeleteRolePacket::id,
                    DeleteRolePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(DeleteRolePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!RpEssentialsPermissions.isStaff(player)) return;

            String cleanId = packet.id().toLowerCase().trim();
            if (cleanId.isEmpty()) return;

            try {
                List<String> roles = new ArrayList<>(RpEssentialsConfig.ROLES.get());
                roles.removeIf(l -> l.split(";", 2)[0].trim().equalsIgnoreCase(cleanId));
                RpEssentialsConfig.ROLES.set(roles);
                RpEssentialsConfig.SPEC.save();
                RpEssentialsRoleManager.clearAll();
                RpEssentialsRoleManager.reload();
                RpEssentialsPermissions.clearCache();

                player.sendSystemMessage(Component.literal(
                        "§a[RpEssentials] Role §e" + cleanId + " §adeleted."));
                RpEssentials.LOGGER.info("[GUI] Role '{}' deleted by {}",
                        cleanId, player.getGameProfile().getName());
            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal(
                        "§c[RpEssentials] Config not loaded, please try again."));
            }
        });
    }
}