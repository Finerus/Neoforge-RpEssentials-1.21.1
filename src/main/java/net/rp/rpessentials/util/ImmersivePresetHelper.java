package net.rp.rpessentials.util;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.config.RpConfig;

import java.util.HashMap;
import java.util.Map;
import net.rp.rpessentials.RpEssentials;

/**
 * Gestionnaire des presets ImmersiveMessages.
 *
 * Chaque preset est défini dans rpessentials-rp.toml sous [Immersive Presets] :
 *   presets = ["death;5.0;0.5;0.5", "zone;3.0;0.3;0.3", "alert;4.0;0.2;1.0"]
 *   Format : name;duration;fadeIn;fadeOut
 *
 * Utilisation dans les modes d'affichage :
 *   "IMMERSIVE"        → utilise le preset "default" (duration=3, fadeIn=0.5, fadeOut=0.5)
 *   "IMMERSIVE:death"  → utilise le preset nommé "death"
 *   "IMMERSIVE:zone"   → utilise le preset nommé "zone"
 *
 * Fallback : si ImmersiveMessages n'est pas installé côté client,
 * ou si le preset est introuvable, le message est envoyé en ACTION_BAR.
 */
public class ImmersivePresetHelper {

    public record Preset(String name, float duration, float fadeIn, float fadeOut) {
        public static final Preset DEFAULT = new Preset("default", 3.0f, 0.5f, 0.5f);
    }

    /** Cache des presets parsés depuis la config. Vidé au reload. */
    private static final Map<String, Preset> cache = new HashMap<>();
    private static boolean cacheBuilt = false;

    // =========================================================================
    // PARSING
    // =========================================================================

    public static void clearCache() {
        cache.clear();
        cacheBuilt = false;
    }

    private static void ensureCache() {
        if (cacheBuilt) return;
        cache.clear();
        if (Cfg.ready(RpConfig.SPEC)) {
            for (String entry : RpConfig.IMMERSIVE_PRESETS.get()) {
                String[] parts = entry.split(";");
                if (parts.length < 4) continue;
                try {
                    String name    = parts[0].trim();
                    float duration = Float.parseFloat(parts[1].trim());
                    float fadeIn   = Float.parseFloat(parts[2].trim());
                    float fadeOut  = Float.parseFloat(parts[3].trim());
                    cache.put(name.toLowerCase(), new Preset(name, duration, fadeIn, fadeOut));
                } catch (NumberFormatException ignored) {
                    RpEssentials.LOGGER.warn("[ImmersivePreset] Invalid entry: {}", entry);
                }
            }
        }
        cacheBuilt = true;
    }

    /**
     * Résout un preset depuis un nom.
     * Retourne {@link Preset#DEFAULT} si introuvable.
     */
    public static Preset resolve(String name) {
        ensureCache();
        if (name == null || name.isBlank()) return Preset.DEFAULT;
        Preset p = cache.get(name.toLowerCase());
        return p != null ? p : Preset.DEFAULT;
    }

    // =========================================================================
    // ENVOI
    // =========================================================================

    /**
     * Envoie un message à un joueur en utilisant le mode et un preset optionnel.
     *
     * @param player   le joueur destinataire
     * @param raw      le message brut (supporte § et &)
     * @param mode     le mode : "CHAT", "ACTION_BAR", "TITLE",
     *                           "IMMERSIVE" ou "IMMERSIVE:presetName"
     */
    public static void send(ServerPlayer player, String raw, String mode) {
        net.minecraft.network.chat.Component component = ColorHelper.parseColors(raw);
        String upperMode = mode.toUpperCase();

        if (upperMode.startsWith("IMMERSIVE")) {
            // Extraire le nom du preset si fourni : "IMMERSIVE:death" → "death"
            String presetName = upperMode.contains(":") ? mode.substring(mode.indexOf(':') + 1) : "default";
            Preset preset = resolve(presetName);
            sendImmersive(player, raw, preset);
        } else {
            switch (upperMode) {
                case "TITLE" -> {
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(component));
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 70, 20));
                }
                case "ACTION_BAR" -> player.displayClientMessage(component, true);
                case "CHAT"       -> player.sendSystemMessage(component);
                default           -> player.sendSystemMessage(component);
            }
        }
    }

    /**
     * Envoie à tous les joueurs d'une liste.
     */
    public static void sendToAll(Iterable<ServerPlayer> players, String raw, String mode) {
        for (ServerPlayer p : players) send(p, raw, mode);
    }

    // =========================================================================
    // PRIVATE
    // =========================================================================

    private static void sendImmersive(ServerPlayer player, String raw, Preset preset) {
        try {
            toni.immersivemessages.api.ImmersiveMessage.builder(preset.duration(), raw)
                    .fadeIn(preset.fadeIn())
                    .fadeOut(preset.fadeOut())
                    .sendServer(player);
        } catch (Exception | NoClassDefFoundError e) {
            // ImmersiveMessages absent → fallback ACTION_BAR
            player.displayClientMessage(ColorHelper.parseColors(raw), true);
        }
    }
}
