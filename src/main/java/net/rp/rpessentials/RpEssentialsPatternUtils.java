package net.rp.rpessentials;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Utilitaire partagé pour le matching de patterns avec wildcards.
 * Utilisé par ClientProfessionRestrictions, ProfessionSyncHelper, etc.
 * Les patterns compilés sont mis en cache pour éviter de les recompiler à chaque appel.
 */
public class RpEssentialsPatternUtils {

    private static final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

    /**
     * Vérifie si un resourceId correspond à un pattern.
     * Supporte les wildcards * (ex: minecraft:*_sword)
     */
    public static boolean matchesPattern(String resourceId, String pattern) {
        pattern = pattern.trim();
        if (pattern.indexOf('|') >= 0) return false;
        if (pattern.startsWith("#")) return inTag(resourceId, pattern.substring(1));
        if (resourceId.equals(pattern)) {
            return true;
        }

        if (pattern.contains("*")) {
            Pattern compiled = compiledPatterns.computeIfAbsent(pattern, p -> {
                String regex = p.replace(".", "\\.").replace("*", ".*");
                return Pattern.compile(regex);
            });
            return compiled.matcher(resourceId).matches();
        }

        return false;
    }

    public static boolean inTag(String resourceId, String tag) {
        ResourceLocation id = ResourceLocation.tryParse(resourceId);
        ResourceLocation t = ResourceLocation.tryParse(tag);
        if (id == null || t == null) return false;
        return BuiltInRegistries.ITEM.getHolder(id).map(h -> h.is(TagKey.create(Registries.ITEM, t))).orElse(false)
                || BuiltInRegistries.BLOCK.getHolder(id).map(h -> h.is(TagKey.create(Registries.BLOCK, t))).orElse(false);
    }

    /**
     * Vide le cache des patterns compilés (à appeler lors d'un reload de config)
     */
    public static void clearCache() {
        compiledPatterns.clear();
    }
}