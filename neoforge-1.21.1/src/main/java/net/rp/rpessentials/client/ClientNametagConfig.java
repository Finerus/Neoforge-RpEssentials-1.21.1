package net.rp.rpessentials.client;

/**
 * Stockage client-side de la config hideNametags reçue via HideNametagsPacket.
 */
public class ClientNametagConfig {

    private static boolean hideNametags = false;

    public static void setHideNametags(boolean hide) {
        hideNametags = hide;
    }

    public static boolean shouldHideNametags() {
        return hideNametags;
    }

    public static void reset() {
        hideNametags = false;
    }
}
