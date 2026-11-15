package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;

import java.util.UUID;

/** Client -> server: the staff GUI asks for a character sheet. */
public record RequestCharacterSheetPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final Type<RequestCharacterSheetPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "request_character_sheet"));

    public static final StreamCodec<FriendlyByteBuf, RequestCharacterSheetPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestCharacterSheetPacket decode(FriendlyByteBuf buf) {
                    return new RequestCharacterSheetPacket(buf.readUUID());
                }

                @Override
                public void encode(FriendlyByteBuf buf, RequestCharacterSheetPacket p) {
                    buf.writeUUID(p.targetUuid());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(RequestCharacterSheetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer staff)) return;
            if (!RpEssentialsPermissions.isStaff(staff)) return;
            PacketDistributor.sendToPlayer(staff, CharacterSheetPacket.of(packet.targetUuid()));
        });
    }
}
