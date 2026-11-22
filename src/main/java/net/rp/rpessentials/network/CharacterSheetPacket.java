package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.rp.rpessentials.network.compat.StreamCodec;
import net.rp.rpessentials.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.rp.rpessentials.network.compat.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.RpConfig;
import net.rp.rpessentials.identity.CharacterSheetManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server -> client: the fields and values of a character sheet, for the staff GUI. */
public record CharacterSheetPacket(UUID targetUuid, boolean enabled, int maxTextLength,
                                   List<CharacterSheetManager.Field> fields,
                                   Map<String, String> values) implements CustomPacketPayload {

    public static final int MAX_VALUE = 20000;

    public static final Type<CharacterSheetPacket> TYPE =
            new Type<>(new ResourceLocation(RpEssentials.MODID, "character_sheet"));

    public static final StreamCodec<FriendlyByteBuf, CharacterSheetPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public CharacterSheetPacket decode(FriendlyByteBuf buf) {
                    UUID id = buf.readUUID();
                    boolean enabled = buf.readBoolean();
                    int max = buf.readVarInt();
                    int n = PacketValidation.readCount(buf);
                    List<CharacterSheetManager.Field> fields = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        fields.add(new CharacterSheetManager.Field(buf.readUtf(32), buf.readUtf(64), buf.readBoolean()));
                    }
                    int m = PacketValidation.readCount(buf);
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int i = 0; i < m; i++) values.put(buf.readUtf(32), buf.readUtf(MAX_VALUE));
                    return new CharacterSheetPacket(id, enabled, max, fields, values);
                }

                @Override
                public void encode(FriendlyByteBuf buf, CharacterSheetPacket p) {
                    buf.writeUUID(p.targetUuid());
                    buf.writeBoolean(p.enabled());
                    buf.writeVarInt(p.maxTextLength());
                    buf.writeVarInt(p.fields().size());
                    for (CharacterSheetManager.Field f : p.fields()) {
                        buf.writeUtf(f.id(), 32);
                        buf.writeUtf(f.label().length() > 64 ? f.label().substring(0, 64) : f.label(), 64);
                        buf.writeBoolean(f.multiline());
                    }
                    buf.writeVarInt(p.values().size());
                    for (var e : p.values().entrySet()) {
                        String v = e.getValue();
                        buf.writeUtf(e.getKey(), 32);
                        buf.writeUtf(v.length() > MAX_VALUE ? v.substring(0, MAX_VALUE) : v, MAX_VALUE);
                    }
                }
            };

    /** The sheet of {@code target} with the fields currently enabled. */
    public static CharacterSheetPacket of(UUID target) {
        List<CharacterSheetManager.Field> fields = CharacterSheetManager.fields();
        Map<String, String> stored = CharacterSheetManager.get(target);
        Map<String, String> values = new LinkedHashMap<>();
        for (CharacterSheetManager.Field f : fields) {
            String v = stored.get(f.id());
            if (v != null) values.put(f.id(), v);
        }
        return new CharacterSheetPacket(target, CharacterSheetManager.enabled(),
                Cfg.get(RpConfig.SHEET_MAX_TEXT_LENGTH, 4000), fields, values);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(CharacterSheetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientGuiOpener.receiveCharacterSheet(packet);
        });
    }
}
