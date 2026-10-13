package net.rp.rpessentials.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.moderation.ManageService;

import java.util.UUID;

public record ManagePlayerPacket(UUID target, ManageService.Action action, String text, int number)
        implements CustomPacketPayload {

    public static final Type<ManagePlayerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "manage_player"));

    public static final StreamCodec<FriendlyByteBuf, ManagePlayerPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ManagePlayerPacket decode(FriendlyByteBuf buf) {
                    UUID target = buf.readUUID();
                    int i = buf.readByte();
                    ManageService.Action[] values = ManageService.Action.values();
                    if (i < 0 || i >= values.length) throw new DecoderException("Invalid manage action");
                    return new ManagePlayerPacket(target, values[i], buf.readUtf(256), buf.readVarInt());
                }
                @Override
                public void encode(FriendlyByteBuf buf, ManagePlayerPacket p) {
                    buf.writeUUID(p.target());
                    buf.writeByte(p.action().ordinal());
                    buf.writeUtf(p.text());
                    buf.writeVarInt(p.number());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(ManagePlayerPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer admin)) return;
            if (!RpEssentialsPermissions.isStaff(admin)) return;
            ManageService.Result r = ManageService.execute(admin.getServer(), admin,
                    packet.target(), packet.action(), packet.text(), packet.number());
            admin.sendSystemMessage(Component.literal(r.message()));
        });
    }
}