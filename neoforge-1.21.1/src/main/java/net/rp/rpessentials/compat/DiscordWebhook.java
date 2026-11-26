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
 * Sends staff events to a Discord channel, through a bot (discordBotToken + discordChannelId in
 * rpessentials-core.toml: the channel is chosen by its ID) or a webhook (discordWebhookUrl: the channel it was
 * created in).
 * <p>
 * Messages go out one by one on a background thread, in order, so the server never waits for Discord.
 * The webhook URL and the bot token are secrets: they are never written to the log.
 */
public final class DiscordWebhook {

    private static final Pattern URL = Pattern.compile(
            "https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+");

    private static final Pattern BOT_TOKEN = Pattern.compile("[\\w-]+\\.[\\w-]+\\.[\\w-]+");
    private static final Pattern CHANNEL_ID = Pattern.compile("\\d{17,20}");

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
    private static volatile Failure warned = null;

    private DiscordWebhook() {}

    /** Why a message was not sent; turned into a translated text with {@link #describe}. */
    public enum Failure {
        INVALID_URL, INVALID_TOKEN, INVALID_CHANNEL, QUEUE_FULL, WEBHOOK_GONE, BOT_REFUSED, BOT_NO_ACCESS,
        CHANNEL_NOT_FOUND, REFUSED, HTTP, RATE_LIMITED, NETWORK, INTERRUPTED
    }

    /** A failure with its detail (HTTP code, exception type), or null when the message was sent. */
    public record Result(Failure failure, String detail) {
        static Result of(Failure f, String detail) { return new Result(f, detail); }
    }

    /** Text of a failure in the language of {@code viewer} (null = server language). */
    public static String describe(ServerPlayer viewer, Result r) {
        String d = r.detail() == null ? "" : r.detail();
        return switch (r.failure()) {
            case INVALID_URL -> ServerLang.get(viewer, MessagesConfig.DISCORD_ERR_INVALID_URL);
            case INVALID_TOKEN, BOT_REFUSED -> ServerLang.get(viewer, MessagesConfig.DISCORD_ERR_TOKEN);
            case INVALID_CHANNEL -> ServerLang.get(viewer, MessagesConfig.DISCORD_ERR_CHANNEL_ID);
            case BOT_NO_ACCESS -> ServerLang.get(viewer, MessagesConfig.DISCORD_ERR_NO_ACCESS);
            case CHANNEL_NOT_FOUND -> ServerLang.get(viewer, MessagesConfig.DISCORD_ERR_CHANNEL_GONE);
            case QUEUE_FULL -> ServerLang.get(viewer, MessagesConfig.DISCORD_ERR_QUEUE_FULL);
            case WEBHOOK_GONE -> ServerLang.get(viewer, MessagesConfig.DISCORD_ERR_GONE, "code", d);
            case REFUSED -> ServerLang.get(viewer, MessagesConfig.DISCORD_ERR_REFUSED);
            case HTTP -> ServerLang.get(viewer, MessagesConfig.DISCORD_ERR_HTTP, "code", d);
            case RATE_LIMITED -> ServerLang.get(viewer, MessagesConfig.DISCORD_ERR_RATE);
            case NETWORK, INTERRUPTED -> ServerLang.get(viewer, MessagesConfig.DISCORD_ERR_NETWORK, "type", d);
        };
    }

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

    private static String token() {
        return Cfg.get(RpEssentialsConfig.DISCORD_BOT_TOKEN, "").trim();
    }

    private static String channel() {
        return Cfg.get(RpEssentialsConfig.DISCORD_CHANNEL_ID, "").trim();
    }

    /** Where the messages go: {@code token} null = webhook. {@code problem} not null = wrong value in the config. */
    private record Target(String url, String token, Failure problem) {
        boolean bot() { return token != null; }
    }

    /** The bot when its token and the channel ID are both set, else the webhook; null = nothing set. */
    private static Target target() {
        String token = token();
        String channel = channel();
        if (!token.isEmpty() && !channel.isEmpty()) {
            Failure problem = !BOT_TOKEN.matcher(token).matches() ? Failure.INVALID_TOKEN
                    : !CHANNEL_ID.matcher(channel).matches() ? Failure.INVALID_CHANNEL : null;
            return new Target("https://discord.com/api/v10/channels/" + channel + "/messages", token, problem);
        }
        String url = url();
        if (url.isEmpty()) return null;
        return new Target(url, null, validUrl(url) ? null : Failure.INVALID_URL);
    }

    /** True when a webhook URL, or a bot token and a channel ID, are set (they may still be invalid). */
    public static boolean configured() {
        return target() != null;
    }

    /** True when messages can be sent (nothing wrong seen in the config). */
    public static boolean ready() {
        Target t = target();
        return t != null && t.problem() == null;
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
    public static void onServerOpened(String open, String close, String reason) {
        if (!on(RpEssentialsConfig.DISCORD_SCHEDULE)) return;
        String desc = open != null && close != null
                ? ServerLang.get(null, MessagesConfig.DISCORD_SERVER_OPENED_DESC, "open", open, "close", close) : reasonText(reason);
        send(ServerLang.get(null, MessagesConfig.DISCORD_SERVER_OPENED), desc, COLOR_GREEN, null, null);
    }

    public static void onServerClosed(String reason) {
        if (!on(RpEssentialsConfig.DISCORD_SCHEDULE)) return;
        send(ServerLang.get(null, MessagesConfig.DISCORD_SERVER_CLOSED), reasonText(reason), COLOR_RED, null, null);
    }

    /** The server restarted and its state (open or closed to the players) is still the same. */
    public static void onServerRestarted(boolean open) {
        if (!on(RpEssentialsConfig.DISCORD_SCHEDULE)) return;
        send(ServerLang.get(null, open ? MessagesConfig.DISCORD_RESTARTED_OPEN : MessagesConfig.DISCORD_RESTARTED_CLOSED),
                null, COLOR_GREY, null, null);
    }

    private static String reasonText(String reason) {
        return reason == null || reason.isBlank() ? null : ServerLang.get(null, MessagesConfig.SCHED_FORCE_REASON, "reason", reason);
    }

    /**
     * /rpessentials discord test: sends a test message and reports the result to {@code viewer}
     * (null = console) through {@code reply}, on the server thread.
     */
    public static void test(ServerPlayer viewer, Consumer<String> reply) {
        // Only one of the two bot options: they do nothing alone, and a webhook cannot choose its channel.
        if (token().isEmpty() != channel().isEmpty()) {
            reply.accept(ServerLang.get(viewer, MessagesConfig.DISCORD_BOT_INCOMPLETE));
        }
        Target to = target();
        if (to == null) { reply.accept(ServerLang.get(viewer, MessagesConfig.DISCORD_NOT_SET)); return; }
        if (to.problem() == Failure.INVALID_URL) { reply.accept(ServerLang.get(viewer, MessagesConfig.DISCORD_BAD_URL)); return; }
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        send(ServerLang.get(viewer, MessagesConfig.DISCORD_TEST_TITLE), ServerLang.get(viewer, to.bot()
                        ? MessagesConfig.DISCORD_TEST_DESC_BOT : MessagesConfig.DISCORD_TEST_DESC),
                COLOR_BLUE, null, error -> {
                    String msg = error == null
                            ? ServerLang.get(viewer, MessagesConfig.DISCORD_TEST_OK)
                            : ServerLang.get(viewer, MessagesConfig.DISCORD_TEST_FAIL, "error", describe(viewer, error));
                    if (server != null) server.execute(() -> reply.accept(msg));
                });
    }

    // =========================================================================
    // SENDING
    // =========================================================================
    /**
     * Queues one embed. {@code done} (optional) receives null on success or the failure,
     * on the Discord thread.
     */
    private static void send(String title, String description, int color, String footer, Consumer<Result> done) {
        Target to = target();
        if (to == null) return;
        if (to.problem() != null) {
            if (warned != to.problem()) {
                warned = to.problem();
                RpEssentials.LOGGER.warn("[Discord] Nothing is sent: {}", describe(null, Result.of(to.problem(), null)));
            }
            if (done != null) done.accept(Result.of(to.problem(), null));
            return;
        }
        warned = null;

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
        if (!to.bot()) body.addProperty("username", cut(Cfg.get(RpEssentialsConfig.DISCORD_USERNAME, "RpEssentials"), 80));
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
            if (done != null) done.accept(Result.of(Failure.QUEUE_FULL, null));
            return;
        }
        executor.execute(() -> {
            Result error = post(to, json);
            if (error != null) RpEssentials.LOGGER.warn("[Discord] Message not sent: {}", describe(null, error));
            if (done != null) done.accept(error);
        });
    }

    /** Sends the request, waiting when Discord asks to slow down. Returns null or the failure. */
    private static Result post(Target to, String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(to.url()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                // The bot API only accepts the "DiscordBot (name, version)" form.
                .header("User-Agent", to.bot() ? "DiscordBot (RpEssentials, 1.0)" : "RpEssentials (Minecraft mod)")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (to.bot()) builder.header("Authorization", "Bot " + to.token());
        HttpRequest request = builder.build();
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> res = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                int code = res.statusCode();
                if (code >= 200 && code < 300) return null;
                if (code == 429) {
                    Thread.sleep(retryAfterMs(res.body()));
                    continue;
                }
                if (to.bot()) {
                    if (code == 401) return Result.of(Failure.BOT_REFUSED, "401");
                    if (code == 403) return Result.of(Failure.BOT_NO_ACCESS, "403");
                    if (code == 404) return Result.of(Failure.CHANNEL_NOT_FOUND, "404");
                }
                return switch (code) {
                    case 401, 403, 404 -> Result.of(Failure.WEBHOOK_GONE, String.valueOf(code));
                    case 400 -> Result.of(Failure.REFUSED, "400");
                    default -> Result.of(Failure.HTTP, String.valueOf(code));
                };
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.of(Failure.INTERRUPTED, "interrupted");
            } catch (Exception e) {
                // The message of an IOException can contain the URL: keep only the type.
                return Result.of(Failure.NETWORK, e.getClass().getSimpleName());
            }
        }
        return Result.of(Failure.RATE_LIMITED, null);
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
