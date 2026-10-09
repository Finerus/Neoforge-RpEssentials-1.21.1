package net.rp.rpessentials.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.config.ProfessionConfig;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.ArrayList;
import java.util.List;

public record DeleteProfessionPacket(String id) implements CustomPacketPayload {

    public static final Type<DeleteProfessionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "delete_profession"));

    public static final StreamCodec<ByteBuf, DeleteProfessionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, DeleteProfessionPacket::id,
                    DeleteProfessionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(DeleteProfessionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!RpEssentialsPermissions.isStaff(player)) return;

            String cleanId = packet.id().toLowerCase().trim();
            if (cleanId.isEmpty()) return;

            try {
                // Suppression dans PROFESSIONS
                List<String> professions = new ArrayList<>(ProfessionConfig.PROFESSIONS.get());
                professions.removeIf(l -> l.split(";", 2)[0].trim().equalsIgnoreCase(cleanId));
                ProfessionConfig.PROFESSIONS.set(professions);

                // Suppression dans toutes les listes d'overrides
                removeFromOverride(ProfessionConfig.PROFESSION_ALLOWED_CRAFTS,    cleanId);
                removeFromOverride(ProfessionConfig.PROFESSION_ALLOWED_BLOCKS,    cleanId);
                removeFromOverride(ProfessionConfig.PROFESSION_ALLOWED_ITEMS,     cleanId);
                removeFromOverride(ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT, cleanId);

                ProfessionRestrictionManager.reloadCache();
                ProfessionConfig.SPEC.save();

                player.sendSystemMessage(Component.literal(
                        "§a[RpEssentials] Profession §e" + cleanId + " §adeleted."));
                RpEssentials.LOGGER.info("[GUI] Profession '{}' deleted by {}",
                        cleanId, player.getGameProfile().getName());
            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal(
                        "§c[RpEssentials] Config not loaded, please try again."));
            }
        });
    }

    private static void removeFromOverride(
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<List<? extends String>> config,
            String profId) {
        List<String> list = new ArrayList<>(config.get());
        list.removeIf(l -> l.split(";", 2)[0].trim().equalsIgnoreCase(profId));
        config.set(list);
    }
}