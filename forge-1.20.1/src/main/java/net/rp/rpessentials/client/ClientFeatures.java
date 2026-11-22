package net.rp.rpessentials.client;

/** Server systems that are turned on, as sent by the server (SyncFeaturesPacket). Reset at logout. */
public final class ClientFeatures {

    private ClientFeatures() {}

    private static boolean professionsEnabled = true;

    public static boolean professionsEnabled() {
        return professionsEnabled;
    }

    public static void set(boolean professions) {
        boolean changed = professions != professionsEnabled;
        professionsEnabled = professions;
        if (changed && !professions) {
            ClientProfessionRestrictions.clear();
            ClientTooltipRestrictions.clear();
            net.rp.rpessentials.profession.ProfessionRestrictionEventHandler.clearTooltipCache();
        }
    }

    public static void reset() {
        professionsEnabled = true;
    }
}
