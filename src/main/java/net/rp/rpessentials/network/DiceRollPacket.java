package net.rp.rpessentials.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.DiceManager;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.config.RpConfig;

public record DiceRollPacket(String diceName) implements CustomPacketPayload {

    public static final Type<DiceRollPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "dice_roll"));

    public static final StreamCodec<ByteBuf, DiceRollPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DiceRollPacket::diceName,
            DiceRollPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(DiceRollPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            try {
                if (!RpConfig.ENABLE_DICE_SYSTEM.get()) return;
            } catch (IllegalStateException ignored) { return; }

            boolean success = DiceManager.roll(player, packet.diceName());
            if (!success) {
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§c[DICE] Unknown dice type: §f" + packet.diceName()));
            }
        });
    }
}