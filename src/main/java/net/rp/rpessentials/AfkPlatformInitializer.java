package net.rp.rpessentials;

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

public class AfkPlatformInitializer {

    private static final String SAVED_DATA_ID = "rpessentials_modstate";

    // =========================================================================
    // SavedData interne
    // =========================================================================

    public static class ModState extends SavedData {

        private boolean afkPlatformPlaced = false;

        public ModState() {}

        public static ModState load(CompoundTag tag, HolderLookup.Provider provider) {
            ModState state = new ModState();
            state.afkPlatformPlaced = tag.getBoolean("afkPlatformPlaced");
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.putBoolean("afkPlatformPlaced", afkPlatformPlaced);
            return tag;
        }

        public boolean isAfkPlatformPlaced() { return afkPlatformPlaced; }

        public void markAfkPlatformPlaced() {
            afkPlatformPlaced = true;
            setDirty();
        }
    }

    // =========================================================================

    public static void tryPlace(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        ModState state = overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ModState::new, ModState::load),
                SAVED_DATA_ID);

        if (state.isAfkPlatformPlaced()) return;

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

        ResourceKey<Level> dimKey = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.parse(dimId));
        ServerLevel level = server.getLevel(dimKey);

        if (level == null) {
            RpEssentials.LOGGER.warn("[AfkPlatform] Dimension '{}' not found, skipping.", dimId);
            return;
        }

        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y) - 1;
        int bz = (int) Math.floor(z);

        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                level.setBlock(new BlockPos(bx + dx, by, bz + dz),
                        Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            }
        }

        RpEssentials.LOGGER.info("[AfkPlatform] Platform placed at {},{},{} in {}.", bx, by, bz, dimId);
        state.markAfkPlatformPlaced();
    }
}