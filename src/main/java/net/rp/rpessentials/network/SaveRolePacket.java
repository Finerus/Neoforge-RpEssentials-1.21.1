package net.rp.rpessentials.network;

import net.rp.rpessentials.config.MessagesConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.*;
import net.rp.rpessentials.compat.LuckPermsSync;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.ArrayList;
import java.util.List;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.GuiFeedback;

public record SaveRolePacket(
        String id,
        String lpGroup,
        List<String> permissions,
        boolean isNew
) implements CustomPacketPayload {

    private static final java.util.Set<String> KNOWN_PERMS = java.util.Set.of(
            "isStaff", "tabWhitelist", "scheduleWhitelist", "professionWhitelist", "seeNicknames",
            "seeAll", "bypassWorldBorder", "spyMessages", "bypassAutoUnwhitelist", "bypassDeathRpWhitelist");

    public static final Type<SaveRolePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "save_role"));

    public static final StreamCodec<FriendlyByteBuf, SaveRolePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SaveRolePacket decode(FriendlyByteBuf buf) {
                    String id      = buf.readUtf();
                    String lp      = buf.readUtf();
                    List<String> p = PacketValidation.readList(buf);
                    boolean isNew  = buf.readBoolean();
                    return new SaveRolePacket(id, lp, p, isNew);
                }

                @Override
                public void encode(FriendlyByteBuf buf, SaveRolePacket packet) {
                    buf.writeUtf(packet.id());
                    buf.writeUtf(packet.lpGroup());
                    buf.writeVarInt(packet.permissions().size());
                    for (String p : packet.permissions()) buf.writeUtf(p);
                    buf.writeBoolean(packet.isNew());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(SaveRolePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!RpEssentialsPermissions.isStaff(player)) return;
            if (!RpEssentialsPermissions.canManageRoles(player)) {
                player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.GUI_ROLES_NEED_LEVEL)));
                return;
            }

            String cleanId = packet.id().toLowerCase().trim().replaceAll("[^a-z0-9_]", "_");
            if (cleanId.isEmpty()) {
                player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.GUI_ROLE_INVALID)));
                return;
            }
            if (packet.isNew() && ProfessionRestrictionManager.getProfessionData(cleanId) != null) {
                player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.GUI_ROLE_ID_TAKEN)));
                return;
            }

            try {
                String lpGroup = packet.lpGroup().trim().replaceAll("[^A-Za-z0-9_.-]", "");
                List<String> newPerms = packet.permissions().stream().filter(KNOWN_PERMS::contains).distinct().toList();
                String newLine = cleanId + ";" + lpGroup + ";" + String.join(",", newPerms);

                List<String> updated = new ArrayList<>();
                String oldLine = null;
                for (String line : RpEssentialsConfig.ROLES.get()) {
                    if (line.split(";", 2)[0].trim().equalsIgnoreCase(cleanId)) {
                        oldLine = line;
                        updated.add(newLine);
                    } else {
                        updated.add(line);
                    }
                }
                if (oldLine == null) updated.add(newLine);

                RpEssentialsConfig.ROLES.set(updated);
                RpEssentialsConfig.SPEC.save();
                RpEssentialsRoleManager.clearAll();
                RpEssentialsRoleManager.reload();
                RpEssentialsPermissions.clearCache();
                RpEssentialsScheduleManager.enforceOnlineDelayed(player.getServer());
                LuckPermsSync.pushRole(lpGroup.isBlank() ? cleanId : lpGroup, newPerms);

                final String previous = oldLine;
                final String group = lpGroup;
                GuiFeedback.report(player, previous == null ? MessagesConfig.GUI_ROLE_CREATED : MessagesConfig.GUI_ROLE_UPDATED,
                        new String[]{"id", cleanId}, v -> {
                    List<String> details = new ArrayList<>();
                    if (previous == null) {
                        details.add(ServerLang.get(v, MessagesConfig.GUI_LP_GROUP, "group", group));
                        if (newPerms.isEmpty()) details.add(ServerLang.get(v, MessagesConfig.GUI_NO_PERMISSIONS));
                        for (String p : newPerms) details.add("§a+ §7" + p);
                    } else {
                        String[] o = previous.split(";", 3);
                        String oldLp = o.length > 1 ? o[1].trim() : "";
                        List<String> oldPerms = new ArrayList<>();
                        if (o.length > 2) {
                            for (String s : o[2].split(",")) if (!s.isBlank()) oldPerms.add(s.trim());
                        }
                        if (!oldLp.equals(group)) details.add(ServerLang.get(v, MessagesConfig.GUI_LP_GROUP_CHANGED,
                                "old", oldLp, "new", group));
                        details.addAll(GuiFeedback.diff(oldPerms, newPerms));
                        if (details.isEmpty()) details.add(ServerLang.get(v, MessagesConfig.GUI_NO_CHANGE));
                    }
                    return details;
                }, true);

            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.GUI_CONFIG_NOT_LOADED)));
            }
        });
    }
}