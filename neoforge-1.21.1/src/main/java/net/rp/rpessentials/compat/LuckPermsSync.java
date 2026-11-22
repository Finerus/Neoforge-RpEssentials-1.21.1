package net.rp.rpessentials.compat;

import net.minecraft.server.MinecraftServer;
import net.rp.rpessentials.RpEssentials;

import java.util.Collection;

public final class LuckPermsSync {

    private LuckPermsSync() {}

    public static void start(MinecraftServer server) {
        try { LuckPermsSyncImpl.start(server); }
        catch (NoClassDefFoundError ignored) {}
        catch (RuntimeException e) { RpEssentials.LOGGER.warn("[LuckPerms] Sync could not start: {}", e.getMessage()); }
    }

    public static void stop() {
        try { LuckPermsSyncImpl.stop(); }
        catch (NoClassDefFoundError ignored) {}
        catch (RuntimeException e) { RpEssentials.LOGGER.warn("[LuckPerms] Sync could not stop cleanly: {}", e.getMessage()); }
    }

    public static void pushAll() {
        try { LuckPermsSyncImpl.pushAll(false); }
        catch (NoClassDefFoundError ignored) {}
        catch (RuntimeException e) { RpEssentials.LOGGER.warn("[LuckPerms] Push failed: {}", e.getMessage()); }
    }

    public static void pushRole(String lpGroup, Collection<String> perms) {
        try { LuckPermsSyncImpl.pushRole(lpGroup, perms); }
        catch (NoClassDefFoundError ignored) {}
        catch (RuntimeException e) { RpEssentials.LOGGER.warn("[LuckPerms] Push failed: {}", e.getMessage()); }
    }
}