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
import net.rp.rpessentials.config.ProfessionConfig;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import net.rp.rpessentials.profession.ProfessionSyncHelper;

import java.util.ArrayList;
import java.util.List;

public record SaveGlobalRestrictionsPacket(
        List<String> blockedCrafts,
        List<String> unbreakableBlocks,
        List<String> blockedItems,
        List<String> blockedEquipment,
        List<String> containerRestrictions
) implements CustomPacketPayload {

    public static final Type<SaveGlobalRestrictionsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "save_global_restrictions"));

    public static final StreamCodec<FriendlyByteBuf, SaveGlobalRestrictionsPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SaveGlobalRestrictionsPacket decode(FriendlyByteBuf buf) {
                    return new SaveGlobalRestrictionsPacket(
                            readList(buf), readList(buf), readList(buf),
                            readList(buf), readList(buf));
                }

                @Override
                public void encode(FriendlyByteBuf buf, SaveGlobalRestrictionsPacket p) {
                    writeList(buf, p.blockedCrafts());
                    writeList(buf, p.unbreakableBlocks());
                    writeList(buf, p.blockedItems());
                    writeList(buf, p.blockedEquipment());
                    writeList(buf, p.containerRestrictions());
                }

                private List<String> readList(FriendlyByteBuf buf) {
                    int n = buf.readVarInt();
                    List<String> out = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) out.add(buf.readUtf());
                    return out;
                }

                private void writeList(FriendlyByteBuf buf, List<String> list) {
                    buf.writeVarInt(list.size());
                    for (String s : list) buf.writeUtf(s);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(SaveGlobalRestrictionsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!RpEssentialsPermissions.isStaff(player)) return;

            try {
                ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.set(packet.blockedCrafts());
                ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.save();

                ProfessionConfig.GLOBAL_UNBREAKABLE_BLOCKS.set(packet.unbreakableBlocks());
                ProfessionConfig.GLOBAL_UNBREAKABLE_BLOCKS.save();

                ProfessionConfig.GLOBAL_BLOCKED_ITEMS.set(packet.blockedItems());
                ProfessionConfig.GLOBAL_BLOCKED_ITEMS.save();

                ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT.set(packet.blockedEquipment());
                ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT.save();

                ProfessionConfig.CONTAINER_OPEN_RESTRICTIONS.set(packet.containerRestrictions());
                ProfessionConfig.CONTAINER_OPEN_RESTRICTIONS.save();

                ProfessionRestrictionManager.reloadCache();

                // Re-sync restrictions pour tous les joueurs connectes
                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers())
                    ProfessionSyncHelper.syncToPlayer(p);

                player.sendSystemMessage(Component.literal(
                        "§a[RPEssentials] Global restrictions saved."));
                RpEssentials.LOGGER.info("[GUI] Global restrictions updated by {}",
                        player.getGameProfile().getName());

            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPEssentials] Config not loaded, please try again."));
            }
        });
    }
}