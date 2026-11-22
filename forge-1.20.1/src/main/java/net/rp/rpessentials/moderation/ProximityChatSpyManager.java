package net.rp.rpessentials.moderation;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProximityChatSpyManager {

    private static final Set<UUID> spyEnabled = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static boolean toggle(UUID uuid) {
        if (spyEnabled.contains(uuid)) { spyEnabled.remove(uuid); return false; }
        else { spyEnabled.add(uuid); return true; }
    }

    public static boolean isEnabled(UUID uuid) { return spyEnabled.contains(uuid); }

    public static void onLogout(UUID uuid) { spyEnabled.remove(uuid); }
}