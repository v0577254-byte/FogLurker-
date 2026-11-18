package com.foglurker.chat;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.security.ApiKeyStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal async client for the Gemini API (generativelanguage.googleapis.com).
 *
 * <ul>
 *   <li>Only chat text is ever sent; voice snippets never leave the server.</li>
 *   <li>The key travels in the {@code x-goog-api-key} header, never in the URL, and is never logged.</li>
 *   <li>Every future returned here always completes (with a failed {@link Result} rather than an
 *       exception), within {@link #overallTimeoutSeconds()}.</li>
 *   <li>A key Google rejects is not retried for 10 minutes (or until a key is set again), and a model
 *       that is rate limited is skipped for as long as Google asks, so a broken key or an empty quota
 *       never costs a request per chat line.</li>
 * </ul>
 */
public final class GeminiClient {
    private static final String DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    /** Model ids as Google writes them, e.g. gemini-2.5-flash, gemini-flash-latest. */
    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final Pattern RETRY_DELAY = Pattern.compile("^(\\d+(?:\\.\\d+)?)s$");
    private static final long KEY_REJECTED_MILLIS = 10 * 60_000L;
    private static final long DEFAULT_RATE_LIMIT_MILLIS = 60_000L;
    private static final long MAX_RATE_LIMIT_MILLIS = 10 * 60_000L;
    /**
     * Output token caps. They only cap the reply (you pay for what is generated, not the cap), but on
     * models that think, thinking counts against them, so they must be roomy or the reply comes back empty.
     */
    private static final int MAX_TOKENS = 1024;
    private static final int MAX_TOKENS_THINKING = 4096;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    /** Models that reject "thinkingBudget: 0"; we stop sending it to them. */
    private static final Map<String, Boolean> THINKING_REJECTED = new ConcurrentHashMap<>();
    private static final Set<String> WARNED_MODELS = ConcurrentHashMap.newKeySet();
    /** Free-tier limits are per model, so rate limits are tracked per model (model -> wait until, epoch ms). */
    private static final Map<String, Long> MODEL_LIMITED_UNTIL = new ConcurrentHashMap<>();
    private static volatile String base = DEFAULT_BASE;
    /** Request timeout override for the game tests (seconds), or 0 to use the config. */
    private static volatile int testTimeoutSeconds;
    private static volatile long keyRejectedUntil;
    private static final long START_NANOS = System.nanoTime();

    /** Milliseconds on a clock that never jumps (a wall-clock change cannot stretch a pause); always above 0. */
    private static long monoMillis() {
        return (System.nanoTime() - START_NANOS) / 1_000_000L + 1;
    }
    /** The key Google rejected (compared by value, so setting a different key works straight away). */
    private static volatile String rejectedKey;
    private static volatile String lastProblem;

    private GeminiClient() {
    }

    /**
     * @param reason Google's machine-readable reason when there is one (API_KEY_INVALID, SAFETY, MAX_TOKENS...)
     */
    public record Result(Optional<String> text, int status, @Nullable String error, @Nullable String reason) {
        static Result ok(String t) {
            return new Result(Optional.of(t), 200, null, null);
        }

        static Result fail(int status, String error) {
            return new Result(Optional.empty(), status, error, null);
        }

        static Result fail(int status, String error, @Nullable String reason) {
            return new Result(Optional.empty(), status, error, reason);
        }
    }

    /** Whether a request is worth making right now: a key is set, not rejected, and some model is not rate limited. */
    public static boolean available() {
        return available(models());
    }

    public static boolean available(List<String> models) {
        String key = ApiKeyStore.get();
        if (key == null || models.isEmpty()) {
            return false;
        }
        long now = monoMillis();
        if (now < keyRejectedUntil && key.equals(rejectedKey)) {
            return false;
        }
        for (String m : models) {
            if (!isLimited(m, now)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLimited(String model, long now) {
        Long until = MODEL_LIMITED_UNTIL.get(model);
        return until != null && now < until;
    }

    /** Human-readable reason Gemini is paused, or null when it is not. Never contains the key. */
    @Nullable
    public static String pausedReason() {
        return pausedReason(models());
    }

    @Nullable
    public static String pausedReason(List<String> models) {
        long now = monoMillis();
        String key = ApiKeyStore.get();
        if (key != null && now < keyRejectedUntil && key.equals(rejectedKey)) {
            return "Google rejected the key (" + lastProblem + "); retrying in " + ((keyRejectedUntil - now) / 1000 + 1)
                    + "s or when a key is set again";
        }
        long soonest = Long.MAX_VALUE;
        for (String m : models) {
            Long until = MODEL_LIMITED_UNTIL.get(m); // read once: the map can be cleared from another thread
            if (until == null || now >= until) {
                return null;
            }
            soonest = Math.min(soonest, until);
        }
        return models.isEmpty() ? null : "rate limited by Google; retrying in " + ((soonest - now) / 1000 + 1) + "s";
    }

    /** Forget a rejected key or rate limits (a key was just set, or tests). */
    public static void clearBackoff() {
        MODEL_LIMITED_UNTIL.clear();
        clearKeyRejection();
    }

    /** Lifts only the "key refused" pause; models waiting out a rate limit keep waiting. */
    private static void clearKeyRejection() {
        keyRejectedUntil = 0;
        rejectedKey = null;
        lastProblem = null;
    }

    /**
     * Points the client at a local fake Gemini server with a short request timeout (game tests only);
     * null restores Google's and the configured timeout.
     */
    public static void setBaseUrlForTests(@Nullable String url) {
        base = url == null ? DEFAULT_BASE : url;
        testTimeoutSeconds = url == null ? 0 : 2;
        THINKING_REJECTED.clear();
    }

    private static int requestTimeoutSeconds() {
        int t = testTimeoutSeconds;
        return t > 0 ? t : FogLurkerConfig.COMMON.geminiTimeoutSeconds.get();
    }

    public static int overallTimeoutSeconds() {
        // One slow attempt plus the "no thinking" retry, with a little slack.
        return requestTimeoutSeconds() * 2 + 5;
    }

    /** The configured models, cleaned up: "models/" prefixes removed, duplicates and invalid names dropped. */
    public static List<String> models() {
        List<String> raw = new ArrayList<>();
        raw.add(FogLurkerConfig.COMMON.geminiModel.get());
        raw.addAll(FogLurkerConfig.COMMON.geminiFallbackModels.get());
        return cleanModels(raw);
    }

    public static List<String> cleanModels(List<String> raw) {
        LinkedHashSet<String> m = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String name = s.trim();
            if (name.startsWith("models/")) {
                name = name.substring("models/".length());
            }
            if (name.isEmpty()) {
                continue;
            }
            if (!MODEL_NAME.matcher(name).matches()) {
                if (WARNED_MODELS.add(name)) {
                    FogLurkerMod.LOGGER.warn("Ignoring Gemini model name \"{}\" in the Fog Lurker config: not a valid model id", name);
                }
                continue;
            }
            m.add(name);
        }
        return new ArrayList<>(m);
    }

    /** Generates text with the stored key, trying fallback models when one is unavailable. */
    public static CompletableFuture<Result> generate(String system, String user, float temperature) {
        return generate(system, user, temperature, false);
    }

    private static CompletableFuture<Result> generate(String system, String user, float temperature, boolean ignoreLimits) {
        String key = ApiKeyStore.get();
        if (key == null) {
            return CompletableFuture.completedFuture(Result.fail(0, "No Gemini API key set"));
        }
        return generate(models(), key, system, user, temperature, ignoreLimits);
    }

    /** Same as {@link #generate(String, String, float)} with explicit models and key. Always completes normally. */
    public static CompletableFuture<Result> generate(List<String> models, String key, String system, String user,
                                                     float temperature) {
        return generate(models, key, system, user, temperature, false);
    }

    private static CompletableFuture<Result> generate(List<String> models, String key, String system, String user,
                                                      float temperature, boolean ignoreLimits) {
        if (!ApiKeyStore.isPlausible(key)) {
            return CompletableFuture.completedFuture(Result.fail(0,
                    "The stored key has characters a Gemini key cannot have; set it again"));
        }
        if (models.isEmpty()) {
            return CompletableFuture.completedFuture(Result.fail(0, "No valid Gemini model names in the config"));
        }
        // The chain stops trying further models once the caller has stopped waiting.
        Chain chain = new Chain(key, models, system, user, temperature,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(overallTimeoutSeconds()), ignoreLimits);
        CompletableFuture<Result> attempt;
        try {
            attempt = tryModels(chain, 0, null, true);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(Result.fail(0, "Gemini request failed: " + e.getClass().getSimpleName()));
        }
        return attempt
                .exceptionally(t -> Result.fail(0, "Gemini request failed: " + rootMessage(t)))
                .completeOnTimeout(Result.fail(0, "Gemini took too long to answer"), overallTimeoutSeconds(), TimeUnit.SECONDS);
    }

    private record Chain(String key, List<String> models, String system, String user, float temperature,
                         long deadlineNanos, boolean ignoreLimits) {
        boolean overdue() {
            return System.nanoTime() - deadlineNanos > 0;
        }
    }

    /**
     * @param allKeyProblems every model tried so far refused because of the key (or project): only then is the
     *                       key paused. One model a project cannot use, after another that was just busy, is not.
     */
    private static CompletableFuture<Result> tryModels(Chain c, int index, @Nullable Result lastFailure, boolean allKeyProblems) {
        if (index >= c.models().size() || (index > 0 && c.overdue())) {
            Result r = lastFailure != null ? lastFailure
                    : Result.fail(404, "None of the configured Gemini models are available");
            if (allKeyProblems && lastFailure != null && isKeyProblem(r)) {
                rejectKey(c.key(), r); // every model refused this key
            }
            return CompletableFuture.completedFuture(r);
        }
        String model = c.models().get(index);
        if (!c.ignoreLimits() && isLimited(model, monoMillis())) {
            // Still waiting out this model's rate limit: do not even ask.
            return tryModels(c, index + 1,
                    Result.fail(429, "Gemini model " + model + " is rate limited; waiting as Google asked", "RATE_LIMITED"), false);
        }
        String key = c.key();
        boolean disableThinking = !THINKING_REJECTED.containsKey(model);
        return call(key, model, c.system(), c.user(), c.temperature(), disableThinking).thenCompose(r -> {
            if (r.status() == 400 && disableThinking && mentionsThinking(r.error())) {
                THINKING_REJECTED.put(model, Boolean.TRUE);
                return call(key, model, c.system(), c.user(), c.temperature(), false);
            }
            return CompletableFuture.completedFuture(r);
        }).thenCompose(r -> {
            if (r.text().isPresent()) {
                if (key.equals(rejectedKey)) {
                    clearKeyRejection(); // the key works after all (an API was enabled, billing fixed...)
                }
                return CompletableFuture.completedFuture(r);
            }
            if ("API_KEY_INVALID".equals(r.reason())) {
                rejectKey(key, r); // the key itself is wrong: no model will take it
                return CompletableFuture.completedFuture(r);
            }
            if (isKeyProblem(r)) {
                // Could be this model only (e.g. a preview model the project has no access to): try the others.
                return tryModels(c, index + 1, r, allKeyProblems);
            }
            if (r.status() == 429) {
                // Free-tier limits are per model: the next model may still have quota.
                String current = ApiKeyStore.get(); // (null while the key store is busy: then it is still this key)
                if (current == null || c.key().equals(current)) { // an answer for a replaced key says nothing about the new one
                    MODEL_LIMITED_UNTIL.put(model, monoMillis() + rateLimitMillis(r));
                }
                return tryModels(c, index + 1, r, false);
            }
            if (r.status() == 400 || r.status() == 404 || (r.status() >= 500 && r.status() < 600)) {
                // Missing, broken, or refusing this request shape (some models reject a system prompt): next model.
                return tryModels(c, index + 1, r, false);
            }
            return CompletableFuture.completedFuture(r);
        });
    }

    private static void rejectKey(String key, Result r) {
        rejectedKey = key;
        keyRejectedUntil = monoMillis() + KEY_REJECTED_MILLIS;
        lastProblem = r.error();
        FogLurkerMod.LOGGER.warn("Gemini refused the API key ({}). Using built-in phrases; retrying in 10 minutes "
                + "or when a key is set again.", r.error());
    }

    private static boolean mentionsThinking(@Nullable String error) {
        if (error == null) {
            return false;
        }
        String e = error.toLowerCase(Locale.ROOT);
        return e.contains("think") || e.contains("budget");
    }

    /** For messages: did Google refuse because of the key or project (rather than the request or a model)? */
    public static boolean isKeyRefusal(Result r) {
        return r != null && isKeyProblem(r);
    }

    /** Errors that will not go away by themselves: a bad, disabled or restricted key, or an unsupported location. */
    private static boolean isKeyProblem(Result r) {
        if (r.status() == 401 || r.status() == 403) {
            return true;
        }
        String reason = r.reason() == null ? "" : r.reason();
        if (reason.equals("API_KEY_INVALID") || reason.equals("API_KEY_SERVICE_BLOCKED") || reason.equals("SERVICE_DISABLED")
                || reason.equals("FAILED_PRECONDITION")) {
            return true;
        }
        String e = r.error() == null ? "" : r.error().toLowerCase(Locale.ROOT);
        return r.status() == 400 && (e.contains("api key") || e.contains("location is not supported"));
    }

    /**
     * A rate limit can also say how long to wait in the HTTP Retry-After header (whole seconds). The longer
     * of that and the wait in the body wins.
     */
    static Result withRetryAfter(Result r, @Nullable String retryAfter) {
        if (r.status() != 429 || retryAfter == null || !retryAfter.trim().matches("\\d{1,7}")) {
            return r;
        }
        long header = Long.parseLong(retryAfter.trim()) * 1000L;
        long body = -1;
        if (r.reason() != null) {
            Matcher m = RETRY_DELAY.matcher(r.reason());
            if (m.matches()) {
                body = (long) (Double.parseDouble(m.group(1)) * 1000);
            }
        }
        return header > body ? Result.fail(429, r.error(), (header / 1000) + "s") : r;
    }

    /** Google's error text is shown to operators and logged: never let it carry the key, should it ever quote it. */
    private static Result scrubKey(Result r, String key) {
        if (r.error() != null && key != null && !key.isEmpty() && r.error().contains(key)) {
            return new Result(r.text(), r.status(), r.error().replace(key, ApiKeyStore.masked(key)), r.reason());
        }
        return r;
    }

    private static long rateLimitMillis(Result r) {
        // Google says how long to wait ("retryDelay": "37s") in the error details; parse() puts it in reason.
        if (r.reason() != null) {
            Matcher m = RETRY_DELAY.matcher(r.reason());
            if (m.matches()) {
                long ms = (long) (Double.parseDouble(m.group(1)) * 1000);
                return Math.max(1000L, Math.min(MAX_RATE_LIMIT_MILLIS, ms));
            }
        }
        return DEFAULT_RATE_LIMIT_MILLIS;
    }

    private static CompletableFuture<Result> call(String key, String model, String system, String user,
                                                  float temperature, boolean disableThinking) {
        JsonObject body = new JsonObject();
        JsonObject sys = new JsonObject();
        sys.add("parts", parts(system));
        body.add("systemInstruction", sys);
        JsonArray contents = new JsonArray();
        JsonObject turn = new JsonObject();
        turn.addProperty("role", "user");
        turn.add("parts", parts(user));
        contents.add(turn);
        body.add("contents", contents);
        JsonObject gen = new JsonObject();
        gen.addProperty("temperature", temperature);
        gen.addProperty("maxOutputTokens", disableThinking ? MAX_TOKENS : MAX_TOKENS_THINKING);
        if (disableThinking) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("thinkingBudget", 0);
            gen.add("thinkingConfig", thinking);
        }
        body.add("generationConfig", gen);

        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(base + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (IllegalArgumentException e) {
            // Never include the exception text: it can quote the header value (the key).
            return CompletableFuture.completedFuture(Result.fail(0, "Could not build the request for model " + model));
        }
        return HTTP.sendAsync(req, cappedBody())
                .thenApply(resp -> scrubKey(withRetryAfter(parse(resp.statusCode(), resp.body()),
                        resp.headers().firstValue("Retry-After").orElse(null)), key))
                .exceptionally(t -> {
                    FogLurkerMod.LOGGER.debug("Gemini request to {} failed: {}", model, rootMessage(t));
                    return Result.fail(0, "Could not reach Gemini: " + rootMessage(t));
                });
    }

    /** Replies bigger than this are refused unread: a real answer is a few kilobytes. */
    private static final int MAX_REPLY_BYTES = 256 * 1024;

    /** Reads the reply as text, but gives up (without buffering the rest) once it passes {@link #MAX_REPLY_BYTES}. */
    private static HttpResponse.BodyHandler<String> cappedBody() {
        return info -> new HttpResponse.BodySubscriber<>() {
            private final CompletableFuture<String> result = new CompletableFuture<>();
            private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            private java.util.concurrent.Flow.Subscription subscription;
            private boolean done;

            @Override
            public java.util.concurrent.CompletionStage<String> getBody() {
                return result;
            }

            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
                subscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(List<java.nio.ByteBuffer> items) {
                if (done) {
                    return;
                }
                for (java.nio.ByteBuffer b : items) {
                    if (buf.size() + b.remaining() > MAX_REPLY_BYTES) {
                        done = true;
                        subscription.cancel();
                        if (info.statusCode() != 200) {
                            result.complete(""); // an oversized error page: keep its status, so 403/429/5xx are handled as usual
                        } else {
                            result.completeExceptionally(new java.io.IOException("the reply was too large"));
                        }
                        return;
                    }
                    byte[] bytes = new byte[b.remaining()];
                    b.get(bytes);
                    buf.write(bytes, 0, bytes.length);
                }
            }

            @Override
            public void onError(Throwable t) {
                done = true;
                result.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                if (!done) {
                    done = true;
                    result.complete(buf.toString(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        };
    }

    private static JsonArray parts(String text) {
        JsonArray arr = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty("text", text);
        arr.add(p);
        return arr;
    }

    /** Turns a raw HTTP reply into a {@link Result}. Never throws. Public for the game tests. */
    public static Result parse(int status, String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body == null ? "" : body);
            if (!parsed.isJsonObject()) {
                return Result.fail(status, status == 200 ? "Unreadable reply from Gemini" : "HTTP " + status);
            }
            JsonObject root = parsed.getAsJsonObject();
            if (status != 200) {
                return error(status, root);
            }
            if (root.has("candidates") && !root.get("candidates").isJsonArray()) {
                return Result.fail(200, "Unreadable reply from Gemini");
            }
            JsonArray candidates = root.has("candidates") ? root.getAsJsonArray("candidates") : null;
            if (candidates == null || candidates.isEmpty()) {
                String block = root.has("promptFeedback") && root.get("promptFeedback").isJsonObject()
                        ? string(root.getAsJsonObject("promptFeedback"), "blockReason") : null;
                return block != null
                        ? Result.fail(200, "Gemini blocked the request (" + block + ")", block)
                        : Result.fail(200, "Gemini returned no text");
            }
            JsonObject first = candidates.get(0).getAsJsonObject();
            String finish = string(first, "finishReason");
            StringBuilder sb = new StringBuilder();
            JsonObject content = first.has("content") && first.get("content").isJsonObject() ? first.getAsJsonObject("content") : null;
            if (content != null && content.has("parts") && content.get("parts").isJsonArray()) {
                for (JsonElement el : content.getAsJsonArray("parts")) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject part = el.getAsJsonObject();
                    if (part.has("thought") && part.get("thought").isJsonPrimitive() && part.get("thought").getAsBoolean()) {
                        continue; // its private reasoning, not the answer
                    }
                    String t = string(part, "text");
                    if (t != null) {
                        sb.append(t);
                    }
                }
            }
            String text = sb.toString().trim();
            if (text.length() > 4000) {
                text = text.substring(0, 4000); // every use is far shorter; this keeps the clean-up cheap
            }
            if (!text.isEmpty()) {
                return Result.ok(text);
            }
            if ("MAX_TOKENS".equals(finish)) {
                return Result.fail(200, "Gemini used up its token limit before answering", finish);
            }
            return finish != null && !"STOP".equals(finish)
                    ? Result.fail(200, "Gemini returned no text (" + finish + ")", finish)
                    : Result.fail(200, "Gemini returned no text");
        } catch (RuntimeException e) {
            return Result.fail(status, status == 200 ? "Unreadable reply from Gemini" : "HTTP " + status);
        }
    }

    private static Result error(int status, JsonObject root) {
        JsonObject err = root.has("error") && root.get("error").isJsonObject() ? root.getAsJsonObject("error") : null;
        if (err == null) {
            return Result.fail(status, "HTTP " + status);
        }
        String msg = string(err, "message");
        String reason = null;
        if (err.has("details") && err.get("details").isJsonArray()) {
            for (JsonElement d : err.getAsJsonArray("details")) {
                if (!d.isJsonObject()) {
                    continue;
                }
                JsonObject o = d.getAsJsonObject();
                String r = string(o, "reason");
                String delay = string(o, "retryDelay");
                if (status == 429 && delay != null) {
                    reason = delay;
                } else if (r != null && reason == null) {
                    reason = r;
                }
            }
        }
        if (reason == null) {
            reason = string(err, "status");
        }
        return Result.fail(status, msg != null && !msg.isBlank() ? oneLine(msg) : "HTTP " + status, reason);
    }

    @Nullable
    private static String string(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static String oneLine(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + oneLine(c.getMessage()));
    }

    /** Sends a tiny request to check that the stored key works (asks even models that are waiting out a rate limit). */
    public static CompletableFuture<Result> test() {
        return generate("Reply with exactly the word: ok", "ping", 1.0F, true); // asks even a model that is resting
    }
}
