package net.rp.rpessentials.network;

import net.rp.rpessentials.config.Cfg;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.gameplay.DiceManager;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.RpConfig;
import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

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

            if (Cfg.ready(RpConfig.SPEC)) {
                if (!RpConfig.ENABLE_DICE_SYSTEM.get()) return;
            } else { return; }
            // Same rule as /roll, and a pause between two rolls even when diceCooldownSeconds is 0.
            if (!RpPerms.has(player, RpPerm.PLAYER_ROLL)) return;
            if (!PacketGuard.every(player, "dice", 250L)) return;

            boolean success = DiceManager.roll(player, packet.diceName());
            if (!success) {
                player.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(
                        ServerLang.get(player,
                                MessagesConfig.DICE_UNKNOWN, "dice", packet.diceName().length() > DiceManager.MAX_INPUT
                                        ? packet.diceName().substring(0, DiceManager.MAX_INPUT) : packet.diceName())));
            }
        });
    }
}