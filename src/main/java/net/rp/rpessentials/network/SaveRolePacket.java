package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.RpEssentialsRoleManager;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.ArrayList;
import java.util.List;

public record SaveRolePacket(
        String id,
        String lpGroup,
        List<String> permissions,
        boolean isNew
) implements CustomPacketPayload {

    public static final Type<SaveRolePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "save_role"));

    public static final StreamCodec<FriendlyByteBuf, SaveRolePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SaveRolePacket decode(FriendlyByteBuf buf) {
                    String id      = buf.readUtf();
                    String lp      = buf.readUtf();
                    int count      = buf.readVarInt();
                    List<String> p = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) p.add(buf.readUtf());
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

            String cleanId = packet.id().toLowerCase().trim().replaceAll("[^a-z0-9_]", "_");
            if (cleanId.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c[RPEssentials] Invalid role ID."));
                return;
            }

            try {
                List<? extends String> current = RpEssentialsConfig.ROLES.get();
                List<String> updated = new ArrayList<>();
                boolean found = false;

                String permStr = String.join(",", packet.permissions());
                String newLine = cleanId + ";" + packet.lpGroup().trim() + ";" + permStr;

                for (String line : current) {
                    String[] parts = line.split(";", 2);
                    if (parts[0].trim().equalsIgnoreCase(cleanId)) {
                        updated.add(newLine);
                        found = true;
                    } else {
                        updated.add(line);
                    }
                }
                if (!found) updated.add(newLine);

                RpEssentialsConfig.ROLES.set(updated);
                RpEssentialsConfig.SPEC.save();
                RpEssentialsRoleManager.clearAll();
                RpEssentialsRoleManager.reload();

                String verb = found ? "updated" : "created";
                player.sendSystemMessage(Component.literal(
                        "§a[RPEssentials] Role §e" + cleanId + " §a" + verb + "."));
                RpEssentials.LOGGER.info("[GUI] Role '{}' {} by {}",
                        cleanId, verb, player.getGameProfile().getName());

            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPEssentials] Config not loaded, please try again."));
            }
        });
    }
}