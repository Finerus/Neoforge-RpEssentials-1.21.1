package net.rp.rpessentials.core;

import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import net.rp.rpessentials.RpEssentials;

import java.util.Optional;

/**
 * Forgified Fabric API is not needed by RpEssentials, but TxniLib (library of Immersive Messages) ships an old
 * one (0.115.6) whose network encoder ({@code fabric:accepted_attachments_v1}) disconnects every player joining
 * a world from NeoForge {@value #NEOFORGE_BREAKING} on. The fixed version needs that NeoForge too, so the rule
 * depends on the NeoForge version and cannot be written in neoforge.mods.toml:
 * <ul>
 *   <li>no Fabric API, or NeoForge older than {@value #NEOFORGE_BREAKING}: nothing to do;</li>
 *   <li>NeoForge {@value #NEOFORGE_BREAKING}+ with a Fabric API older than {@value #FABRIC_API_MIN}: NeoForge shows
 *       its loading warning screen with a message saying what to install; the player can still go on.</li>
 * </ul>
 */
public final class FabricApiCheck {

    private FabricApiCheck() {}

    /** First NeoForge version on which the old Fabric API disconnects the players. */
    static final String NEOFORGE_BREAKING = "21.1.262";
    /** First Forgified Fabric API version that works on it. */
    static final String FABRIC_API_MIN = "0.116.15";

    public static void run() {
        try {
            Optional<ArtifactVersion> fabric = version("fabric_api");
            Optional<ArtifactVersion> neo = version("neoforge");
            if (fabric.isEmpty() || neo.isEmpty()) return;
            if (neo.get().compareTo(new DefaultArtifactVersion(NEOFORGE_BREAKING)) < 0) return;
            // Compare the numbers only (versions look like 0.116.15+2.3.6+1.21.1).
            ArtifactVersion installed = new DefaultArtifactVersion(fabric.get().toString().split("\\+")[0]);
            if (installed.compareTo(new DefaultArtifactVersion(FABRIC_API_MIN)) >= 0) return;
            String msg = "RpEssentials: Forgified Fabric API " + fabric.get() + " (shipped by TxniLib / Immersive Messages)"
                    + " disconnects every player on NeoForge " + NEOFORGE_BREAKING + " and newer."
                    + " Install Forgified Fabric API " + FABRIC_API_MIN + " or newer.";
            RpEssentials.LOGGER.warn(msg);
            // Warning screen of NeoForge (with a "Proceed" button), not an error that stops the game.
            ModLoader.addLoadingIssue(ModLoadingIssue.warning(msg));
        } catch (Throwable t) {
            // Never block the game because of this check.
            RpEssentials.LOGGER.warn("[RpEssentials] Could not check the Forgified Fabric API version", t);
        }
    }

    private static Optional<ArtifactVersion> version(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo()).map(IModInfo::getVersion);
    }
}
