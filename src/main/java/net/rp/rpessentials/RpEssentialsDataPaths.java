package net.rp.rpessentials;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.File;

public class RpEssentialsDataPaths {

    private RpEssentialsDataPaths() {}

    public static File getDataFolder() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/rpessentials")
                    .toFile();
        }
        RpEssentials.LOGGER.warn("[RpEssentials] Server unavailable, using fallback data path");
        return new File("world/data/rpessentials");
    }

    public static File getDataFile(String filename) {
        return new File(getDataFolder(), filename);
    }
}