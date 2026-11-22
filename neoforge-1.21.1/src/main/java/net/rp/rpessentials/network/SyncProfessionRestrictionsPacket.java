package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.rp.rpessentials.RpEssentials;

import java.util.HashSet;
import java.util.Set;

/**
 * Packet pour synchroniser les items/armures interdits du serveur vers le client
 */
public record SyncProfessionRestrictionsPacket(Set<String> blockedCrafts, Set<String> blockedEquipment) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncProfessionRestrictionsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "sync_profession_restrictions"));

    public static final StreamCodec<FriendlyByteBuf, SyncProfessionRestrictionsPacket> STREAM_CODEC = StreamCodec.of(
            SyncProfessionRestrictionsPacket::encode,
            SyncProfessionRestrictionsPacket::decode
    );

    private static void encode(FriendlyByteBuf buf, SyncProfessionRestrictionsPacket packet) {
        // Écrire les crafts bloqués
        buf.writeInt(packet.blockedCrafts.size());
        for (String item : packet.blockedCrafts) {
            buf.writeUtf(item);
        }

        // Écrire les équipements bloqués
        buf.writeInt(packet.blockedEquipment.size());
        for (String item : packet.blockedEquipment) {
            buf.writeUtf(item);
        }
    }

    private static SyncProfessionRestrictionsPacket decode(FriendlyByteBuf buf) {
        // Lire les crafts bloqués
        int craftCount = buf.readInt();
        Set<String> blockedCrafts = new HashSet<>();
        for (int i = 0; i < craftCount; i++) {
            blockedCrafts.add(buf.readUtf());
        }

        // Lire les équipements bloqués
        int equipCount = buf.readInt();
        Set<String> blockedEquipment = new HashSet<>();
        for (int i = 0; i < equipCount; i++) {
            blockedEquipment.add(buf.readUtf());
        }

        return new SyncProfessionRestrictionsPacket(blockedCrafts, blockedEquipment);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}