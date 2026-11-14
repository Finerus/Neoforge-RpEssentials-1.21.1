package net.rp.rpessentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.network.SyncNametagDataPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientNametagCache {

    private static final Map<UUID, NametagData> cache = new ConcurrentHashMap<>();

    public static void update(SyncNametagDataPacket packet) {
        cache.put(packet.targetUUID(), new NametagData(
                packet.displayName(),
                packet.prefix(),
                packet.suffix(),
                packet.profession(),
                packet.isStaff()
        ));
        rendered.remove(packet.targetUUID());
    }

    public static NametagData get(UUID uuid) {
        return cache.get(uuid);
    }

    public static boolean has(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /**
     * Amélioration 10 — Nettoie les entrées des joueurs qui ne sont plus
     * dans la liste du serveur (déconnectés pendant que le client reste connecté).
     * Appelé périodiquement depuis RpClientTickHandler.
     */
    public static void evictDisconnected() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        cache.keySet().removeIf(uuid -> mc.getConnection().getPlayerInfo(uuid) == null);
        rendered.keySet().removeIf(uuid -> !cache.containsKey(uuid));
    }

    /** Appelé à la déconnexion du client. */
    public static void reset() {
        cache.clear();
        rendered.clear();
    }

    public record NametagData(
            String displayName,
            String prefix,
            String suffix,
            String profession,
            boolean isStaff
    ) {}

    private static final Map<UUID, Component> rendered = new ConcurrentHashMap<>();

    public static Component rendered(UUID uuid, String realName) {
        NametagData data = cache.get(uuid);
        if (data == null) return null;
        return rendered.computeIfAbsent(uuid, k -> {
            String prefix = data.prefix() != null ? data.prefix() : "";
            String display = data.displayName() != null && !data.displayName().isEmpty()
                    ? data.displayName() : realName;
            return ColorHelper.parseColors(prefix + display);
        });
    }
}