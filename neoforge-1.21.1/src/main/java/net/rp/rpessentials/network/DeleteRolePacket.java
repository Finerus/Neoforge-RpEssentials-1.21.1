package net.rp.rpessentials.network;

import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
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
            
            if (!RpEssentialsPermissions.canManageRoles(player)) {
                player.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(ServerLang.get(player, MessagesConfig.GUI_ROLES_NEED_LEVEL)));
                return;
            }

            String cleanId = packet.id().toLowerCase().trim();
            if (cleanId.isEmpty()) return;

            if (Cfg.ready(RpEssentialsConfig.SPEC)) {
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
                net.rp.rpessentials.permission.RpPerms.refreshAll(player.getServer());

                if (removed != null) {
                    String[] o = removed.split(";", 3);
                    net.rp.rpessentials.compat.LuckPermsSync.pushRole(
                            o.length > 1 && !o[1].isBlank() ? o[1].trim() : o[0].trim(), List.of());
                }

                final String removedLine = removed;
                net.rp.rpessentials.util.GuiFeedback.report(player, MessagesConfig.GUI_ROLE_DELETED, new String[]{"id", cleanId}, v -> {
                    List<String> details = new ArrayList<>();
                    if (removedLine != null) {
                        String[] o = removedLine.split(";", 3);
                        details.add(ServerLang.get(v, MessagesConfig.GUI_LP_GROUP, "group", o.length > 1 ? o[1].trim() : ""));
                        if (o.length > 2 && !o[2].isBlank()) details.add(ServerLang.get(v, MessagesConfig.GUI_PERMISSIONS,
                                "permissions", o[2].replace(",", "§7, §f")));
                    }
                    return details;
                }, true);
            } else {
                player.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(ServerLang.get(player, MessagesConfig.GUI_CONFIG_NOT_LOADED)));
            }
        });
    }
}