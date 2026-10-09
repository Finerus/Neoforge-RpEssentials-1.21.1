package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.*;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.ArrayList;
import java.util.List;

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
                player.sendSystemMessage(Component.literal(
                        "§c[RpEssentials] Managing roles requires the OP level set in opLevelBypass."));
                return;
            }

            String cleanId = packet.id().toLowerCase().trim().replaceAll("[^a-z0-9_]", "_");
            if (cleanId.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c[RpEssentials] Invalid role ID."));
                return;
            }
            if (packet.isNew() && ProfessionRestrictionManager.getProfessionData(cleanId) != null) {
                player.sendSystemMessage(Component.literal("§c[RpEssentials] This ID is already used by a profession."));
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

                List<String> details = new ArrayList<>();
                if (oldLine == null) {
                    details.add("§7LuckPerms group: §f" + lpGroup);
                    if (newPerms.isEmpty()) details.add("§8No permissions");
                    for (String p : newPerms) details.add("§a+ §7" + p);
                } else {
                    String[] o = oldLine.split(";", 3);
                    String oldLp = o.length > 1 ? o[1].trim() : "";
                    List<String> oldPerms = new ArrayList<>();
                    if (o.length > 2) {
                        for (String s : o[2].split(",")) if (!s.isBlank()) oldPerms.add(s.trim());
                    }
                    if (!oldLp.equals(lpGroup)) details.add("§7LuckPerms group: §c" + oldLp + " §7-> §a" + lpGroup);
                    details.addAll(GuiFeedback.diff(oldPerms, newPerms));
                    if (details.isEmpty()) details.add("§8No change");
                }
                GuiFeedback.report(player, "Role §e" + cleanId + " §f" + (oldLine == null ? "created" : "updated"), details, true);

            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal("§c[RpEssentials] Config not loaded, please try again."));
            }
        });
    }
}