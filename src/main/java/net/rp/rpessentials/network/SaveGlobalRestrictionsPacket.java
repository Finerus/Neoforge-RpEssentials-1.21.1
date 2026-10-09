package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.GuiFeedback;
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
                    return PacketValidation.readList(buf);
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
                List<String> details = new ArrayList<>();
                applyCategory(details, "Blocked crafts", ProfessionConfig.GLOBAL_BLOCKED_CRAFTS,
                        PacketValidation.cleanItems(packet.blockedCrafts()));
                applyCategory(details, "Unbreakable blocks", ProfessionConfig.GLOBAL_UNBREAKABLE_BLOCKS,
                        PacketValidation.cleanItems(packet.unbreakableBlocks()));
                applyCategory(details, "Blocked items", ProfessionConfig.GLOBAL_BLOCKED_ITEMS,
                        PacketValidation.cleanItems(packet.blockedItems()));
                applyCategory(details, "Blocked equipment", ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT,
                        PacketValidation.cleanItems(packet.blockedEquipment()));
                applyCategory(details, "Container restrictions", ProfessionConfig.CONTAINER_OPEN_RESTRICTIONS,
                        PacketValidation.cleanContainers(packet.containerRestrictions()));

                if (details.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§7[RpEssentials] Global restrictions: no change."));
                    return;
                }

                ProfessionConfig.SPEC.save();
                ProfessionRestrictionManager.reloadCache();
                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers())
                    ProfessionSyncHelper.syncToPlayer(p);

                GuiFeedback.report(player, "Global restrictions updated", details, true);

            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal("§c[RpEssentials] Config not loaded, please try again."));
            }
        });
    }

    private static void applyCategory(List<String> details, String label,
                                      ModConfigSpec.ConfigValue<List<? extends String>> cfg,
                                      List<String> next) {
        List<String> before = new ArrayList<>(cfg.get());
        List<String> d = GuiFeedback.diff(before, next);
        if (d.isEmpty()) return;
        cfg.set(next);
        details.add("§e" + label + "§7:");
        for (String line : d) details.add("    " + line);
    }
}