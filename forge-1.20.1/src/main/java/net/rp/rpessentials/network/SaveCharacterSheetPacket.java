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
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.identity.CharacterSheetManager;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.util.ColorHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client -> server: a staff member saves a character sheet (the server answers with the saved sheet). */
public record SaveCharacterSheetPacket(UUID targetUuid, String targetName, Map<String, String> values)
        implements CustomPacketPayload {

    public static final Type<SaveCharacterSheetPacket> TYPE =
            new Type<>(new ResourceLocation(RpEssentials.MODID, "save_character_sheet"));

    public static final StreamCodec<FriendlyByteBuf, SaveCharacterSheetPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SaveCharacterSheetPacket decode(FriendlyByteBuf buf) {
                    UUID id = buf.readUUID();
                    String name = buf.readUtf(64);
                    int n = PacketValidation.readCount(buf);
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int i = 0; i < n; i++) values.put(buf.readUtf(32), buf.readUtf(CharacterSheetPacket.MAX_VALUE));
                    return new SaveCharacterSheetPacket(id, name, values);
                }

                @Override
                public void encode(FriendlyByteBuf buf, SaveCharacterSheetPacket p) {
                    buf.writeUUID(p.targetUuid());
                    buf.writeUtf(p.targetName() == null ? "" : p.targetName(), 64);
                    buf.writeVarInt(p.values().size());
                    for (var e : p.values().entrySet()) {
                        String v = e.getValue() == null ? "" : e.getValue();
                        buf.writeUtf(e.getKey(), 32);
                        buf.writeUtf(v.length() > CharacterSheetPacket.MAX_VALUE
                                ? v.substring(0, CharacterSheetPacket.MAX_VALUE) : v, CharacterSheetPacket.MAX_VALUE);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(SaveCharacterSheetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer staff)) return;
            if (!RpPerms.has(staff, RpPerm.SHEET)) return;
            if (!CharacterSheetManager.enabled()) {
                staff.sendSystemMessage(ColorHelper.parseSection(ServerLang.get(staff, MessagesConfig.SHEET_DISABLED)));
                return;
            }
            String name = packet.targetName().isBlank() ? packet.targetUuid().toString() : packet.targetName();
            List<String> changed = CharacterSheetManager.update(packet.targetUuid(), name, packet.values(),
                    staff.getGameProfile().getName());
            if (changed.isEmpty()) {
                staff.sendSystemMessage(ColorHelper.parseSection(ServerLang.get(staff, MessagesConfig.SHEET_NO_CHANGE, "player", name)));
            } else {
                List<String> labels = new ArrayList<>();
                for (CharacterSheetManager.Field f : CharacterSheetManager.fields()) {
                    if (changed.contains(f.id())) labels.add(CharacterSheetManager.label(f));
                }
                staff.sendSystemMessage(ColorHelper.parseSection(ServerLang.get(staff, MessagesConfig.SHEET_SAVED,
                        "player", name, "fields", String.join(", ", labels))));
            }
            PacketDistributor.sendToPlayer(staff, CharacterSheetPacket.of(packet.targetUuid()));
        });
    }
}
