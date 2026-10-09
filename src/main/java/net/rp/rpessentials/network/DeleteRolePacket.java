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
import net.rp.rpessentials.RpEssentialsScheduleManager;
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

            if (!RpEssentialsPermissions.canManageRoles(player)) {
                player.sendSystemMessage(Component.literal("§c[RpEssentials] Managing roles requires the OP level set in opLevelBypass."));
                return;
            }

            String cleanId = packet.id().toLowerCase().trim();
            if (cleanId.isEmpty()) return;

            try {
                List<String> roles = new ArrayList<>(RpEssentialsConfig.ROLES.get());
                String removed = null;
                for (String l : roles) {
                    if (l.split(";", 2)[0].trim().equalsIgnoreCase(cleanId)) { removed = l; break; }
                }
                roles.removeIf(l -> l.split(";", 2)[0].trim().equalsIgnoreCase(cleanId));
                RpEssentialsConfig.ROLES.set(roles);
                RpEssentialsConfig.SPEC.save();
                RpEssentialsRoleManager.clearAll();
                RpEssentialsRoleManager.reload();
                RpEssentialsPermissions.clearCache();
                RpEssentialsScheduleManager.enforceOnlineDelayed(player.getServer());

                List<String> details = new ArrayList<>();
                if (removed != null) {
                    String[] o = removed.split(";", 3);
                    details.add("§7LuckPerms group: §f" + (o.length > 1 ? o[1].trim() : ""));
                    if (o.length > 2 && !o[2].isBlank()) details.add("§7Permissions: §f" + o[2].replace(",", "§7, §f"));
                }
                net.rp.rpessentials.GuiFeedback.report(player, "Role §e" + cleanId + " §fdeleted", details, true);
            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal(
                        "§c[RpEssentials] Config not loaded, please try again."));
            }
        });
    }
}