package net.rp.rpessentials.network;

import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.util.GuiFeedback;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
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

            if (Cfg.ready(ProfessionConfig.SPEC)) {
                List<Object[]> details = new ArrayList<>();
                applyCategory(details, MessagesConfig.GUI_CAT_BLOCKED_CRAFTS, ProfessionConfig.GLOBAL_BLOCKED_CRAFTS,
                        PacketValidation.cleanItems(packet.blockedCrafts()));
                applyCategory(details, MessagesConfig.GUI_CAT_UNBREAKABLE, ProfessionConfig.GLOBAL_UNBREAKABLE_BLOCKS,
                        PacketValidation.cleanItems(packet.unbreakableBlocks()));
                applyCategory(details, MessagesConfig.GUI_CAT_BLOCKED_ITEMS, ProfessionConfig.GLOBAL_BLOCKED_ITEMS,
                        PacketValidation.cleanItems(packet.blockedItems()));
                applyCategory(details, MessagesConfig.GUI_CAT_BLOCKED_EQUIPMENT, ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT,
                        PacketValidation.cleanItems(packet.blockedEquipment()));
                applyCategory(details, MessagesConfig.GUI_CAT_CONTAINERS, ProfessionConfig.CONTAINER_OPEN_RESTRICTIONS,
                        PacketValidation.cleanContainers(packet.containerRestrictions()));

                if (details.isEmpty()) {
                    player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.GUI_GLOBAL_NO_CHANGE)));
                    return;
                }

                ProfessionConfig.SPEC.save();
                ProfessionRestrictionManager.reloadCache();
                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers())
                    ProfessionSyncHelper.syncToPlayer(p);

                GuiFeedback.report(player, MessagesConfig.GUI_GLOBAL_UPDATED, new String[0], v -> {
                    List<String> out = new ArrayList<>();
                    for (Object[] cat : details) {
                        @SuppressWarnings("unchecked")
                        ModConfigSpec.ConfigValue<String> label = (ModConfigSpec.ConfigValue<String>) cat[0];
                        out.add("§e" + ServerLang.get(v, label) + "§7:");
                        @SuppressWarnings("unchecked")
                        List<String> lines = (List<String>) cat[1];
                        for (String line : lines) out.add("    " + line);
                    }
                    return out;
                }, true);

            } else {
                player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.GUI_CONFIG_NOT_LOADED)));
            }
        });
    }

    private static void applyCategory(List<Object[]> details, ModConfigSpec.ConfigValue<String> label,
                                      ModConfigSpec.ConfigValue<List<? extends String>> cfg,
                                      List<String> next) {
        List<String> before = new ArrayList<>(cfg.get());
        List<String> d = GuiFeedback.diff(before, next);
        if (d.isEmpty()) return;
        cfg.set(next);
        details.add(new Object[]{label, d});
    }
}