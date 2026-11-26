package net.rp.rpessentials.gameplay;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * State of the server in the multiplayer list. It travels as the UUID of the first line shown when hovering the player
 * count (the text of this line is for everyone, also without the mod): no extra packet, nothing in the MOTD.
 *
 * @param fromMod the texts are the ones of the mod: a client with the mod shows them in its own language
 * @param next when the state changes (epoch seconds), 0 = not planned
 */
public record ServerListState(boolean open, boolean forced, boolean fromMod, long next) {

    /** "2h05", rounded up to the minute, translated by the game of the player. */
    public static Component duration(long seconds) {
        long min = Math.max(1, (seconds + 59) / 60);
        if (min < 60) return Component.translatable("rpessentials.serverlist.min", min);
        if (min < 1440) return Component.translatable("rpessentials.serverlist.hours", min / 60, String.format("%02d", min % 60));
        return Component.translatable("rpessentials.serverlist.days", min / 1440, (min % 1440) / 60);
    }

    /** "RPESSENT": a real player UUID never has it. */
    private static final long MAGIC = 0x52504553_53454E54L;
    private static final long MAGIC_REASON = MAGIC + 1;

    public UUID toUuid() {
        return new UUID(MAGIC, (open ? 1L : 0L) | (forced ? 2L : 0L) | (fromMod ? 4L : 0L) | (next << 8));
    }

    /** UUID of the line with the reason: where the reason is in its text, so that only the words around are translated. */
    public static UUID reasonUuid(int before, int after) {
        return new UUID(MAGIC_REASON, ((long) before << 32) | after);
    }

    /** True for the line that carries the state. */
    public static boolean isState(GameProfile line) {
        return line.getId() != null && line.getId().getMostSignificantBits() == MAGIC;
    }

    /** The reason alone, null when this line is not the one of the reason. */
    public static String reason(GameProfile line) {
        if (line.getId() == null || line.getId().getMostSignificantBits() != MAGIC_REASON) return null;
        int before = (int) (line.getId().getLeastSignificantBits() >>> 32), after = (int) line.getId().getLeastSignificantBits();
        String text = line.getName();
        if (before < 0 || after < 0 || (long) before + after > text.length()) return null;
        return text.substring(before, text.length() - after);
    }

    /** The state in the lines of a server, null when it has none (no mod, no schedule, option off). */
    public static ServerListState find(List<GameProfile> sample) {
        for (GameProfile p : sample) {
            UUID id = p.getId();
            if (id == null || id.getMostSignificantBits() != MAGIC) continue;
            long bits = id.getLeastSignificantBits();
            return new ServerListState((bits & 1L) != 0, (bits & 2L) != 0, (bits & 4L) != 0, bits >>> 8);
        }
        return null;
    }
}
