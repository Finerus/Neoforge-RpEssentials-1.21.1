package net.rp.rpessentials;

import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.config.RpConfig;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.identity.NicknameManager;

import java.util.*;

/**
 * Gestionnaire des lancers de dé côté serveur.
 * Les types de dés sont configurables dans rpessentials-rp.toml.
 */
public class DiceManager {

    // =========================================================================
    // DICE TYPES
    // =========================================================================

    public record DiceType(String name, int maxValue, List<String> customFaces) {

        /** Retourne true si ce dé a des faces personnalisées (ex: Pile/Face) */
        public boolean hasCustomFaces() {
            return customFaces != null && !customFaces.isEmpty();
        }

        /** Effectue un lancer et retourne le résultat sous forme de String */
        public String roll(Random random) {
            if (hasCustomFaces()) {
                return customFaces.get(random.nextInt(customFaces.size()));
            }
            return String.valueOf(random.nextInt(maxValue) + 1);
        }
    }

    private static final Random RANDOM = new Random();

    // =========================================================================
    // PARSING CONFIG
    // =========================================================================

    public static List<DiceType> getAvailableDice() {
        List<DiceType> result = new ArrayList<>();
        try {
            for (String entry : RpConfig.DICE_TYPES.get()) {
                DiceType type = parseDiceType(entry);
                if (type != null) result.add(type);
            }
        } catch (IllegalStateException e) {
            // Config pas encore chargée — retourner liste vide
        }
        return result;
    }

    private static DiceType parseDiceType(String entry) {
        if (!entry.contains(";")) return null;
        String[] parts = entry.split(";", 2);
        String name  = parts[0].trim();
        String value = parts[1].trim();

        // Faces personnalisées : "coin;Heads,Tails"
        if (value.contains(",")) {
            List<String> faces = Arrays.asList(value.split(","));
            return new DiceType(name, 0, faces);
        }

        // Numérique : "d20;20"
        try {
            int max = Integer.parseInt(value);
            return new DiceType(name, max, null);
        } catch (NumberFormatException e) {
            RpEssentials.LOGGER.warn("[DiceManager] Invalid dice entry: {}", entry);
            return null;
        }
    }

    public static DiceType getDiceByName(String name) {
        return getAvailableDice().stream()
                .filter(d -> d.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    // =========================================================================
    // ROLL
    // =========================================================================

    /**
     * Effectue un lancer et diffuse le résultat aux joueurs proches.
     * @param player  le joueur qui lance
     * @param diceName le nom du type de dé
     * @return true si le lancer a réussi, false si le type est introuvable
     */
    public static boolean roll(ServerPlayer player, String diceName) {
        DiceType dice = getDiceByName(diceName);
        if (dice == null) return false;

        String result      = dice.roll(RANDOM);
        String playerName  = NicknameManager.getDisplayName(player);

        String format = MessagesConfig.get(MessagesConfig.DICE_ROLL_FORMAT,
                "player", playerName, "dice", dice.name(), "result", result);
        String spyFormat = MessagesConfig.get(MessagesConfig.DICE_ROLL_SPY_FORMAT,
                "player", playerName, "dice", dice.name(), "result", result);

        net.minecraft.network.chat.Component message =
                ColorHelper.parseColors(format);
        net.minecraft.network.chat.Component spyMessage =
                ColorHelper.parseColors(spyFormat);

        int distance;
        try { distance = RpConfig.DICE_ROLL_DISTANCE.get(); }
        catch (IllegalStateException e) { distance = 32; }

        boolean global = distance < 0;
        double distSq  = (double) distance * distance;

        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            if (global || p.getUUID().equals(player.getUUID())
                    || (p.level() == player.level() && p.distanceToSqr(player) <= distSq)) {
                p.sendSystemMessage(message);
            } else if (RpEssentialsPermissions.isStaff(p)) {
                p.sendSystemMessage(spyMessage);
            }
        }

        RpEssentials.LOGGER.info("[DICE] {} rolled {} and got {}", playerName, diceName, result);
        return true;
    }

    // =========================================================================
    // AJOUTER dans MessagesConfig (références)
    // =========================================================================
    // MessagesConfig.DICE_ROLL_FORMAT — défini dans RpConfig.DICE_ROLL_FORMAT (redirection)
}