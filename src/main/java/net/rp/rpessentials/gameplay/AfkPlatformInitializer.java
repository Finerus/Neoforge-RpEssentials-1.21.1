package net.rp.rpessentials.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.rp.rpessentials.RpEssentials;

public class AfkPlatformInitializer {

    private static final String SAVED_DATA_ID = "rpessentials_modstate";

    // =========================================================================
    // SavedData interne
    // =========================================================================
    public static class ModState extends SavedData {

        private boolean afkPlatformPlaced = false;
        private String platformKey = "";

        public ModState() {}

        public static ModState load(CompoundTag tag, HolderLookup.Provider provider) {
            ModState state = new ModState();
            state.afkPlatformPlaced = tag.getBoolean("afkPlatformPlaced");
            state.platformKey = tag.getString("platformKey");
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.putBoolean("afkPlatformPlaced", afkPlatformPlaced);
            tag.putString("platformKey", platformKey);
            return tag;
        }

        public String platformKey() { return platformKey; }

        public void markPlaced(String key) {
            afkPlatformPlaced = true;
            platformKey = key;
            setDirty();
        }
    }

    // =========================================================================

    public static void tryPlace(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        ModState state = overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ModState::new, ModState::load),
                SAVED_DATA_ID);

        String dimId;
        double x, y, z;
        try {
            dimId = net.rp.rpessentials.config.RpConfig.AFK_DIMENSION.get();
            x     = net.rp.rpessentials.config.RpConfig.AFK_X.get();
            y     = net.rp.rpessentials.config.RpConfig.AFK_Y.get();
            z     = net.rp.rpessentials.config.RpConfig.AFK_Z.get();
        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.warn("[AfkPlatform] Config not loaded, skipping.");
            return;
        }

        ResourceLocation dimRl = ResourceLocation.tryParse(dimId);
        ServerLevel level = dimRl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimRl));

        if (level == null) {
            RpEssentials.LOGGER.warn("[AfkPlatform] Dimension '{}' not found, skipping.", dimId);
            return;
        }

        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y) - 1;
        int bz = (int) Math.floor(z);

        String key = dimId + "," + bx + "," + by + "," + bz;
        if (key.equals(state.platformKey())) return;

        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                level.setBlock(new BlockPos(bx + dx, by, bz + dz),
                        Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            }
        }

        RpEssentials.LOGGER.info("[AfkPlatform] Platform placed at {},{},{} in {}.", bx, by, bz, dimId);
        state.markPlaced(key);
    }
}