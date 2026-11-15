package com.foglurker.chat;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.security.ApiKeyStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal async client for the Gemini API (generativelanguage.googleapis.com).
 * Only chat text is ever sent; voice snippets never leave the server.
 */
public final class GeminiClient {
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    /** Models that reject "thinkingBudget: 0"; we stop sending it to them. */
    private static final Map<String, Boolean> THINKING_REJECTED = new ConcurrentHashMap<>();
    private static volatile long rateLimitedUntil;

    private GeminiClient() {
    }

    public record Result(Optional<String> text, int status, String error) {
        static Result ok(String t) {
            return new Result(Optional.of(t), 200, null);
        }

        static Result fail(int status, String error) {
            return new Result(Optional.empty(), status, error);
        }
    }

    public static boolean available() {
        return ApiKeyStore.isSet() && System.currentTimeMillis() > rateLimitedUntil;
    }

    private static List<String> models() {
        LinkedHashSet<String> m = new LinkedHashSet<>();
        m.add(FogLurkerConfig.COMMON.geminiModel.get().trim());
        for (String s : FogLurkerConfig.COMMON.geminiFallbackModels.get()) {
            m.add(s.trim());
        }
        m.removeIf(String::isEmpty);
        return new ArrayList<>(m);
    }

    /** Generates text with the stored key, trying fallback models when one is unavailable. */
    public static CompletableFuture<Result> generate(String system, String user, float temperature) {
        String key = ApiKeyStore.get();
        if (key == null) {
            return CompletableFuture.completedFuture(Result.fail(0, "No Gemini API key set"));
        }
        return tryModels(key, models(), 0, system, user, temperature);
    }

    private static CompletableFuture<Result> tryModels(String key, List<String> models, int index, String system,
                                                       String user, float temperature) {
        if (index >= models.size()) {
            return CompletableFuture.completedFuture(Result.fail(404, "None of the configured Gemini models are available"));
        }
        String model = models.get(index);
        boolean disableThinking = !THINKING_REJECTED.containsKey(model);
        return call(key, model, system, user, temperature, disableThinking).thenCompose(r -> {
            if (r.status() == 400 && disableThinking && r.error() != null
                    && r.error().toLowerCase().contains("think")) {
                THINKING_REJECTED.put(model, Boolean.TRUE);
                return call(key, model, system, user, temperature, false);
            }
            return CompletableFuture.completedFuture(r);
        }).thenCompose(r -> {
            if (r.status() == 404 || (r.status() >= 500 && r.status() < 600)) {
                return tryModels(key, models, index + 1, system, user, temperature);
            }
            if (r.status() == 429) {
                rateLimitedUntil = System.currentTimeMillis() + 60_000L;
            }
            return CompletableFuture.completedFuture(r);
        });
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
        gen.addProperty("maxOutputTokens", disableThinking ? 96 : 1024);
        if (disableThinking) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("thinkingBudget", 0);
            gen.add("thinkingConfig", thinking);
        }
        body.add("generationConfig", gen);

        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(BASE + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(FogLurkerConfig.COMMON.geminiTimeoutSeconds.get()))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(Result.fail(0, "Bad model name: " + model));
        }
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> parse(resp.statusCode(), resp.body()))
                .exceptionally(t -> {
                    FogLurkerMod.LOGGER.debug("Gemini request failed", t);
                    return Result.fail(0, "Could not reach Gemini: " + rootMessage(t));
                });
    }

    private static JsonArray parts(String text) {
        JsonArray arr = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty("text", text);
        arr.add(p);
        return arr;
    }

    private static Result parse(int status, String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (status != 200) {
                String msg = root.has("error") && root.getAsJsonObject("error").has("message")
                        ? root.getAsJsonObject("error").get("message").getAsString() : ("HTTP " + status);
                return Result.fail(status, msg);
            }
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return Result.fail(200, "Gemini returned no text");
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null || !content.has("parts")) {
                return Result.fail(200, "Gemini returned no text");
            }
            StringBuilder sb = new StringBuilder();
            for (JsonElement el : content.getAsJsonArray("parts")) {
                JsonObject part = el.getAsJsonObject();
                if (part.has("thought") && part.get("thought").getAsBoolean()) {
                    continue;
                }
                if (part.has("text")) {
                    sb.append(part.get("text").getAsString());
                }
            }
            String text = sb.toString().trim();
            return text.isEmpty() ? Result.fail(200, "Gemini returned no text") : Result.ok(text);
        } catch (Exception e) {
            return Result.fail(status, status == 200 ? "Unreadable reply from Gemini" : "HTTP " + status);
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }

    /** Sends a tiny request to check that a key works. */
    public static CompletableFuture<Result> test() {
        return generate("Reply with exactly the word: ok", "ping", 0F);
    }
}
