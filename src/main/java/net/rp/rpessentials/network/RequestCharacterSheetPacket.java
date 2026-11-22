package net.rp.rpessentials.network;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.minecraft.network.FriendlyByteBuf;
import net.rp.rpessentials.network.compat.StreamCodec;
import net.rp.rpessentials.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.network.compat.PacketDistributor;
import net.rp.rpessentials.network.compat.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;

import java.util.UUID;

/** Client -> server: the staff GUI asks for a character sheet. */
public record RequestCharacterSheetPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final Type<RequestCharacterSheetPacket> TYPE =
            new Type<>(new ResourceLocation(RpEssentials.MODID, "request_character_sheet"));

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
            if (!RpPerms.has(staff, RpPerm.SHEET)) return;
            PacketDistributor.sendToPlayer(staff, CharacterSheetPacket.of(packet.targetUuid()));
        });
    }
}
