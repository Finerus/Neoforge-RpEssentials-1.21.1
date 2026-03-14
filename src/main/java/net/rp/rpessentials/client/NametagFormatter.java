package net.rp.rpessentials.client;

import net.rp.rpessentials.ColorHelper;
import net.rp.rpessentials.NametagConfig;

/**
 * Résout les tokens du format nametag configuré.
 *
 * Tokens disponibles dans FORMAT :
 *   $prefix      — LuckPerms prefix
 *   $name        — display name (nickname ou realName)
 *   $realname    — toujours le vrai nom Minecraft
 *   $suffix      — LuckPerms suffix
 *   $profession  — première licence active
 *
 * Tokens disponibles dans FORMAT_OBFUSCATED :
 *   $obfuscated  — le texte obfusqué généré
 */
public class NametagFormatter {

    /**
     * Construit le composant nametag pour un joueur lisible (distance proche).
     */
    public static net.minecraft.network.chat.Component formatReadable(
            ClientNametagCache.NametagData data,
            String realName
    ) {
        String format;
        try {
            format = NametagConfig.FORMAT.get();
        } catch (IllegalStateException e) {
            format = "$prefix$name";
        }

        String result = format
                .replace("$prefix",     data.prefix())
                .replace("$suffix",     data.suffix())
                .replace("$name",       data.displayName())
                .replace("$realname",   realName)
                .replace("$profession", data.profession());

        return ColorHelper.parseColors(result);
    }

    /**
     * Construit le composant nametag pour un joueur obfusqué (distance lointaine).
     *
     * @param realNameLength longueur du nom réel (utilisée si obfuscationLength = -1)
     */
    public static net.minecraft.network.chat.Component formatObfuscated(int realNameLength) {
        // Longueur du texte obfusqué
        int length;
        try {
            int configured = NametagConfig.OBFUSCATION_LENGTH.get();
            length = (configured == -1) ? realNameLength : configured;
        } catch (IllegalStateException e) {
            length = realNameLength;
        }
        length = Math.max(1, Math.min(length, 32));

        // Couleur de l'obfuscation
        String color;
        try {
            color = NametagConfig.OBFUSCATION_COLOR.get();
        } catch (IllegalStateException e) {
            color = "&8";
        }

        // §k active l'obfuscation vanilla (caractères animés aléatoires)
        String obfuscatedText = color + "§k" + "?".repeat(length);

        // Format obfusqué (ex: "$obfuscated")
        String format;
        try {
            format = NametagConfig.FORMAT_OBFUSCATED.get();
        } catch (IllegalStateException e) {
            format = "$obfuscated";
        }

        String result = format.replace("$obfuscated", obfuscatedText);
        return ColorHelper.parseColors(result);
    }
}
