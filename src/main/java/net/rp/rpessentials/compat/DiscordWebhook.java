package net.rp.rpessentials.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.moderation.HistoryManager;
import net.rp.rpessentials.util.ColorHelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Sends staff events to a Discord channel through a webhook (discordWebhookUrl in rpessentials-core.toml).
 * <p>
 * Messages go out one by one on a background thread, in order, so the server never waits for Discord.
 * The webhook URL is a secret: it is never written to the log.
 */
public final class DiscordWebhook {

    private static final Pattern URL = Pattern.compile(
            "https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+");

    /** Messages waiting to be sent; beyond this, new ones are dropped (Discord down, spam...). */
    private static final int MAX_QUEUE = 100;
    private static final int MAX_RETRIES = 3;

    private static final int COLOR_RED = 0xE74C3C;
    private static final int COLOR_ORANGE = 0xE67E22;
    private static final int COLOR_YELLOW = 0xF1C40F;
    private static final int COLOR_GREEN = 0x2ECC71;
    private static final int COLOR_BLUE = 0x3498DB;
    private static final int COLOR_GREY = 0x95A5A6;
    private static final int COLOR_PURPLE = 0x9B59B6;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static volatile ThreadPoolExecutor executor = createExecutor();
    /** Logged once per problem, not once per message. */
    private static volatile boolean warnedBadUrl = false;

    private DiscordWebhook() {}

    private static ThreadPoolExecutor createExecutor() {
        ThreadPoolExecutor ex = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(MAX_QUEUE), r -> {
                    Thread t = new Thread(r, "rpessentials-discord");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.DiscardPolicy());
        return ex;
    }

    // =========================================================================
    // CONFIG
    // =========================================================================
    private static String url() {
        return Cfg.get(RpEssentialsConfig.DISCORD_WEBHOOK_URL, "").trim();
    }

    /** True when a webhook URL is set (it may still be invalid). */
    public static boolean configured() {
        return !url().isEmpty();
    }

    public static boolean validUrl(String url) {
        return URL.matcher(url).matches();
    }

    private static boolean on(ModConfigSpec.BooleanValue toggle) {
        return configured() && Cfg.get(toggle, false);
    }

    // =========================================================================
    // EVENTS
    // =========================================================================
    /**
     * Called for every line added to a player's history. Lines hidden by vanish are never sent.
     */
    public static void onHistory(String targetName, String type, String actor, String detail, int hiddenLevel) {
        if (hiddenLevel > 0 || type == null) return;
        ModConfigSpec.BooleanValue toggle;
        int color;
        switch (type) {
            case HistoryManager.REPORT -> { toggle = RpEssentialsConfig.DISCORD_REPORTS; color = COLOR_ORANGE; }
            case HistoryManager.WARN -> { toggle = RpEssentialsConfig.DISCORD_WARNS; color = COLOR_YELLOW; }
            case HistoryManager.WARN_REMOVE, HistoryManager.WARN_CLEAR -> { toggle = RpEssentialsConfig.DISCORD_WARNS; color = COLOR_GREEN; }
            case HistoryManager.MUTE -> { toggle = RpEssentialsConfig.DISCORD_MUTES; color = COLOR_ORANGE; }
            case HistoryManager.UNMUTE -> { toggle = RpEssentialsConfig.DISCORD_MUTES; color = COLOR_GREEN; }
            case HistoryManager.MANAGE -> { toggle = RpEssentialsConfig.DISCORD_MANAGE; color = COLOR_RED; }
            case HistoryManager.DEATH_RP -> { toggle = RpEssentialsConfig.DISCORD_DEATH_RP; color = COLOR_PURPLE; }
            case HistoryManager.LICENSE -> { toggle = RpEssentialsConfig.DISCORD_LICENSES; color = COLOR_BLUE; }
            case HistoryManager.NOTE, HistoryManager.NOTE_REMOVE -> { toggle = RpEssentialsConfig.DISCORD_NOTES; color = COLOR_GREY; }
            default -> { return; }
        }
        if (!on(toggle)) return;
        String title = HistoryManager.typeLabel(type) + " - " + (targetName != null ? targetName : "?");
        String footer = actor != null && !actor.isBlank()
                ? ServerLang.get(null, MessagesConfig.DISCORD_BY, "actor", actor) : null;
        send(title, detail, color, footer, null);
    }

    /** {@code open} / {@code close} null = opened by hand (forceopen), sent without hours. */
    public static void onServerOpened(String open, String close) {
        if (!on(RpEssentialsConfig.DISCORD_SCHEDULE)) return;
        String desc = open != null && close != null
                ? ServerLang.get(null, MessagesConfig.DISCORD_SERVER_OPENED_DESC, "open", open, "close", close) : null;
        send(ServerLang.get(null, MessagesConfig.DISCORD_SERVER_OPENED), desc, COLOR_GREEN, null, null);
    }

    public static void onServerClosed() {
        if (!on(RpEssentialsConfig.DISCORD_SCHEDULE)) return;
        send(ServerLang.get(null, MessagesConfig.DISCORD_SERVER_CLOSED), null, COLOR_RED, null, null);
    }

    /**
     * /rpessentials discord test: sends a test message and reports the result to {@code viewer}
     * (null = console) through {@code reply}, on the server thread.
     */
    public static void test(ServerPlayer viewer, Consumer<String> reply) {
        String url = url();
        if (url.isEmpty()) { reply.accept(ServerLang.get(viewer, MessagesConfig.DISCORD_NOT_SET)); return; }
        if (!validUrl(url)) { reply.accept(ServerLang.get(viewer, MessagesConfig.DISCORD_BAD_URL)); return; }
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        send(ServerLang.get(viewer, MessagesConfig.DISCORD_TEST_TITLE), ServerLang.get(viewer, MessagesConfig.DISCORD_TEST_DESC),
                COLOR_BLUE, null, error -> {
                    String msg = error == null
                            ? ServerLang.get(viewer, MessagesConfig.DISCORD_TEST_OK)
                            : ServerLang.get(viewer, MessagesConfig.DISCORD_TEST_FAIL, "error", error);
                    if (server != null) server.execute(() -> reply.accept(msg));
                });
    }

    // =========================================================================
    // SENDING
    // =========================================================================
    /**
     * Queues one embed. {@code done} (optional) receives null on success or a short error text,
     * on the Discord thread.
     */
    private static void send(String title, String description, int color, String footer, Consumer<String> done) {
        String url = url();
        if (url.isEmpty()) return;
        if (!validUrl(url)) {
            if (!warnedBadUrl) {
                warnedBadUrl = true;
                RpEssentials.LOGGER.warn("[Discord] discordWebhookUrl is not a Discord webhook URL, nothing is sent.");
            }
            if (done != null) done.accept("invalid URL");
            return;
        }
        warnedBadUrl = false;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", cut(clean(title), 256));
        if (description != null && !description.isBlank()) embed.addProperty("description", cut(clean(description), 4000));
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());
        if (footer != null) {
            JsonObject f = new JsonObject();
            f.addProperty("text", cut(clean(footer), 2048));
            embed.add("footer", f);
        }
        JsonObject body = new JsonObject();
        body.addProperty("username", cut(Cfg.get(RpEssentialsConfig.DISCORD_USERNAME, "RpEssentials"), 80));
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        body.add("embeds", embeds);
        // Never ping anyone, even if a reason contains @everyone or a role mention.
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        body.add("allowed_mentions", mentions);

        String json = body.toString();
        if (executor.isShutdown()) executor = createExecutor();
        int queued = executor.getQueue().size();
        if (queued >= MAX_QUEUE) {
            if (done != null) done.accept("queue full");
            return;
        }
        executor.execute(() -> {
            String error = post(url, json);
            if (error != null) RpEssentials.LOGGER.warn("[Discord] Message not sent: {}", error);
            if (done != null) done.accept(error);
        });
    }

    /** Sends the request, waiting when Discord asks to slow down. Returns null or an error text. */
    private static String post(String url, String json) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", "RpEssentials (Minecraft mod)")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> res = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                int code = res.statusCode();
                if (code >= 200 && code < 300) return null;
                if (code == 429) {
                    Thread.sleep(retryAfterMs(res.body()));
                    continue;
                }
                return switch (code) {
                    case 401, 403, 404 -> "HTTP " + code + " (webhook deleted or wrong URL)";
                    case 400 -> "HTTP 400 (message refused by Discord)";
                    default -> "HTTP " + code;
                };
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "interrupted";
            } catch (Exception e) {
                // The message of an IOException can contain the URL: keep only the type.
                return e.getClass().getSimpleName();
            }
        }
        return "rate limited by Discord";
    }

    private static long retryAfterMs(String body) {
        try {
            double s = JsonParser.parseString(body).getAsJsonObject().get("retry_after").getAsDouble();
            return Math.min(10_000L, Math.max(250L, (long) (s * 1000)));
        } catch (Exception e) {
            return 2_000L;
        }
    }

    /** Waits up to 3 seconds for the last messages (server closing...) when the server stops. */
    public static void shutdown() {
        ThreadPoolExecutor ex = executor;
        ex.shutdown();
        try {
            if (!ex.awaitTermination(3, TimeUnit.SECONDS)) ex.shutdownNow();
        } catch (InterruptedException e) {
            ex.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static String clean(String s) {
        return ColorHelper.stripAll(s == null ? "" : s);
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
