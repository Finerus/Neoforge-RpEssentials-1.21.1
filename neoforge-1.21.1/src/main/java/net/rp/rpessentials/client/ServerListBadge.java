package net.rp.rpessentials.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;
import net.rp.rpessentials.gameplay.ServerListState;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Badge of one server of the multiplayer list: "● Closed, opens in 2h 15min" after its name, in the language and the
 * time zone of the player. Shortened to what fits before the player count.
 */
public final class ServerListBadge {

    private static final int OPEN = 0xFF55FF55;
    private static final int CLOSED = 0xFFFF5555;

    /** What the state was read from: read again only after a new ping. */
    private ServerStatus.Players source;
    private ServerListState state;
    private long textMinute = -1;
    private List<Component> texts = List.of();
    /** The lines of the player count, in the language of the player. */
    private List<Component> lines;
//#if forge
//#else

    /** Null when the lines are the ones of the server. */
    public List<Component> lines() { return state != null && state.fromMod() ? lines : null; }
//#endif

    /** Draws the badge; the lines of its tooltip when the mouse is on it, else null. */
    public List<Component> draw(GuiGraphics graphics, ServerData server, int top, int left, int width, int mouseX, int mouseY) {
        ServerStatus.Players players = server.players;
        if (players != source) {
            source = players;
            state = players == null ? null : ServerListState.find(players.sample());
            textMinute = -1;
        }
        if (state == null) return null;

        Font font = Minecraft.getInstance().font;
        long now = System.currentTimeMillis() / 1000L;
        if (textMinute != now / 60) {
            textMinute = now / 60;
            texts = texts(now);
            lines = null;
        }
        // Also when the ping has just replaced the lines of the server.
        if (state.fromMod() && server.playerList != null && (lines == null || server.playerList != lines)) server.playerList = lines = translate(server.playerList, now);
        if (!net.rp.rpessentials.config.Cfg.get(net.rp.rpessentials.config.ClientConfig.SERVER_LIST_BADGE, true)) return null;
        int x = left + 35 + font.width(server.name) + 6;
        int room = left + width - 24 - (server.status == null ? 0 : font.width(server.status)) - x;
        for (Component text : texts) {
            int w = font.width(text);
            if (w > room) continue;
            graphics.drawString(font, text, x, top + 1, state.open() ? OPEN : CLOSED, false);
            boolean over = mouseX >= x && mouseX <= x + w && mouseY >= top && mouseY <= top + 9;
            return over ? tooltip(now) : null;
        }
        return null;
    }

    /** The server sends its lines in its own language: they must not differ from the badge. */
    private List<Component> translate(List<Component> sent, long now) {
        List<Component> out = new ArrayList<>(sent);
        for (int i = 0; i < Math.min(out.size(), source.sample().size()); i++) {
            GameProfile line = source.sample().get(i);
            String reason = ServerListState.reason(line);
            if (ServerListState.isState(line)) {
                out.set(i, text(now).withStyle(state.open() ? ChatFormatting.GREEN : ChatFormatting.RED));
            } else if (reason != null) {
                out.set(i, Component.translatable("rpessentials.serverlist.reason", Component.literal(reason).withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        return out;
    }

    /** "Closed, opens in 2h05". */
    private net.minecraft.network.chat.MutableComponent text(long now) {
        String base = state.open() ? "rpessentials.serverlist.open" : "rpessentials.serverlist.closed";
        return timeKnown(now) ? Component.translatable(base + ".in", ServerListState.duration(state.next() - now)) : Component.translatable(base);
    }

    private boolean timeKnown(long now) { return state.next() > now; }

    /** From the longest to the shortest. */
    private List<Component> texts(long now) {
        String base = state.open() ? "rpessentials.serverlist.open" : "rpessentials.serverlist.closed";
        List<Component> out = new ArrayList<>(3);
        if (timeKnown(now)) {
            out.add(Component.literal("● ").append(text(now)));
        }
        out.add(Component.literal("● ").append(Component.translatable(base)));
        out.add(Component.literal("●"));
        return out;
    }

    private List<Component> tooltip(long now) {
        List<Component> out = new ArrayList<>(3);
        out.add(Component.translatable(state.open() ? "rpessentials.serverlist.open" : "rpessentials.serverlist.closed")
                .withStyle(state.open() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (timeKnown(now)) {
            out.add(Component.translatable(state.open() ? "rpessentials.serverlist.closes_at" : "rpessentials.serverlist.opens_at",
                    date(state.next()), ServerListState.duration(state.next() - now)).withStyle(ChatFormatting.GRAY));
        }
        if (state.forced()) out.add(Component.translatable("rpessentials.serverlist.forced").withStyle(ChatFormatting.GOLD));
        return out;
    }

    private static String date(long epoch) {
        Locale locale = Locale.forLanguageTag(Minecraft.getInstance().options.languageCode.replace('_', '-'));
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
                .format(Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()));
    }
}
