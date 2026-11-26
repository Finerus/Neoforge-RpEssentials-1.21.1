package net.rp.rpessentials.gameplay;

import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.RpConfig;
import net.minecraft.core.BlockPos;
//#if forge
//#else
import net.minecraft.core.HolderLookup;
//#endif
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
        /** Last mod version this world ran with ("" before 6.1.0 or on a new world). See ModNotices. */
        private String lastModVersion = "";
        /** Notice shown to staff at login (welcome / update), and who already saw it. */
        private String noticeFrom = "";
        private String noticeTo = "";
        private final java.util.Set<java.util.UUID> noticeSeen = new java.util.HashSet<>();
        /** OPs who were already told how to get a role (once per player, see ModNotices). */
        private final java.util.Set<java.util.UUID> roleHintSeen = new java.util.HashSet<>();

        public ModState() {}

        //#if forge
        //? public static ModState load(CompoundTag tag) {
        //#else
        public static ModState load(CompoundTag tag, HolderLookup.Provider provider) {
        //#endif
            ModState state = new ModState();
            state.afkPlatformPlaced = tag.getBoolean("afkPlatformPlaced");
            state.platformKey = tag.getString("platformKey");
            state.lastModVersion = tag.getString("lastModVersion");
            state.noticeFrom = tag.getString("noticeFrom");
            state.noticeTo = tag.getString("noticeTo");
            for (net.minecraft.nbt.Tag t : tag.getList("noticeSeen", net.minecraft.nbt.Tag.TAG_STRING)) {
                try { state.noticeSeen.add(java.util.UUID.fromString(t.getAsString())); } catch (IllegalArgumentException ignored) {}
            }
            for (net.minecraft.nbt.Tag t : tag.getList("roleHintSeen", net.minecraft.nbt.Tag.TAG_STRING)) {
                try { state.roleHintSeen.add(java.util.UUID.fromString(t.getAsString())); } catch (IllegalArgumentException ignored) {}
            }
            return state;
        }

        @Override
        //#if forge
        //? public CompoundTag save(CompoundTag tag) {
        //#else
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        //#endif
            tag.putBoolean("afkPlatformPlaced", afkPlatformPlaced);
            tag.putString("platformKey", platformKey);
            tag.putString("lastModVersion", lastModVersion);
            tag.putString("noticeFrom", noticeFrom);
            tag.putString("noticeTo", noticeTo);
            net.minecraft.nbt.ListTag seen = new net.minecraft.nbt.ListTag();
            for (java.util.UUID u : noticeSeen) seen.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
            tag.put("noticeSeen", seen);
            net.minecraft.nbt.ListTag hints = new net.minecraft.nbt.ListTag();
            for (java.util.UUID u : roleHintSeen) hints.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
            tag.put("roleHintSeen", hints);
            return tag;
        }

        public String platformKey() { return platformKey; }

        /** True when the mod already ran on this world (AFK platform placed) before versions were saved. */
        public boolean usedBefore() { return afkPlatformPlaced || !platformKey.isEmpty(); }

        public String lastModVersion() { return lastModVersion; }
        public String noticeFrom() { return noticeFrom; }
        public String noticeTo() { return noticeTo; }
        public boolean noticeSeenBy(java.util.UUID uuid) { return noticeSeen.contains(uuid); }

        /** Saves the version this world runs with, and the notice to show ("" from = first launch). */
        public void startNotice(String from, String to) {
            noticeFrom = from;
            noticeTo = to;
            noticeSeen.clear();
            setDirty();
        }

        public void setLastModVersion(String version) {
            lastModVersion = version;
            setDirty();
        }

        public void markNoticeSeen(java.util.UUID uuid) {
            if (noticeSeen.add(uuid)) setDirty();
        }

        public boolean roleHintSeenBy(java.util.UUID uuid) { return roleHintSeen.contains(uuid); }

        public void markRoleHintSeen(java.util.UUID uuid) {
            if (roleHintSeen.add(uuid)) setDirty();
        }

        public void markPlaced(String key) {
            afkPlatformPlaced = true;
            platformKey = key;
            setDirty();
        }
    }

    // =========================================================================

    /** Mod state of the server (saved with the overworld, data/rpessentials_modstate.dat). */
    public static ModState state(MinecraftServer server) {
        //#if forge
        //? return server.overworld().getDataStorage().computeIfAbsent(ModState::load, ModState::new, SAVED_DATA_ID);
        //#else
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ModState::new, ModState::load),
                SAVED_DATA_ID);
        //#endif
    }

    public static void tryPlace(MinecraftServer server) {
        ModState state = state(server);

        String dimId;
        double x, y, z;
        if (Cfg.ready(RpConfig.SPEC)) {
            dimId = net.rp.rpessentials.config.RpConfig.AFK_DIMENSION.get();
            x     = net.rp.rpessentials.config.RpConfig.AFK_X.get();
            y     = net.rp.rpessentials.config.RpConfig.AFK_Y.get();
            z     = net.rp.rpessentials.config.RpConfig.AFK_Z.get();
        } else {
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