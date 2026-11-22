package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;

import java.util.ArrayList;
import java.util.List;

public record OpenRolesGuiPacket(List<RoleEntry> roles) implements CustomPacketPayload {

    public record RoleEntry(String id, String lpGroup, List<String> permissions) {}

    public static final Type<OpenRolesGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "open_roles_gui"));

    public static final StreamCodec<FriendlyByteBuf, OpenRolesGuiPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenRolesGuiPacket decode(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<RoleEntry> roles = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String id     = buf.readUtf();
                        String lp     = buf.readUtf();
                        int permCount = buf.readVarInt();
                        List<String> perms = new ArrayList<>(permCount);
                        for (int j = 0; j < permCount; j++) perms.add(buf.readUtf());
                        roles.add(new RoleEntry(id, lp, perms));
                    }
                    return new OpenRolesGuiPacket(roles);
                }

                @Override
                public void encode(FriendlyByteBuf buf, OpenRolesGuiPacket packet) {
                    buf.writeVarInt(packet.roles().size());
                    for (RoleEntry r : packet.roles()) {
                        buf.writeUtf(r.id());
                        buf.writeUtf(r.lpGroup());
                        buf.writeVarInt(r.permissions().size());
                        for (String p : r.permissions()) buf.writeUtf(p);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(OpenRolesGuiPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientGuiOpener.openRolesGui(packet.roles());
        });
    }
}