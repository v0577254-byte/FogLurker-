package com.foglurker.voice;

import com.foglurker.FogLurkerMod;
import com.foglurker.security.SecureStorage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who has opted in to voice snippets. Nobody is ever recorded without opting in first
 * (/flurk voicesnippets terms, or /fglurk optin). Opting out deletes every stored snippet.
 */
public final class VoiceConsent {
    public static final String TERMS =
            "by agreeing to these terms you allow for the mod to make small snippets of your voice and store it in a secure folder in your account/server settings";

    private static final Set<UUID> OPTED_IN = ConcurrentHashMap.newKeySet();
    /** When each opted-in player was last online (epoch millis). Snippets expire 30 days after. */
    private static final java.util.Map<UUID, Long> LAST_SEEN = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    private VoiceConsent() {
    }

    private static Path file() {
        return SecureStorage.root().resolve("voice_consent.json");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (VoiceConsent.class) {
            if (loaded) {
                return;
            }
            Path f = file();
            if (Files.isRegularFile(f)) {
                try {
                    String json = new String(SecureStorage.readEncrypted(f), StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    for (JsonElement e : root.getAsJsonArray("optedIn")) {
                        OPTED_IN.add(UUID.fromString(e.getAsString()));
                    }
                    if (root.has("lastSeen")) {
                        root.getAsJsonObject("lastSeen").entrySet().forEach(en ->
                                LAST_SEEN.put(UUID.fromString(en.getKey()), en.getValue().getAsLong()));
                    }
                } catch (Exception e) {
                    FogLurkerMod.LOGGER.error("Could not read voice consent list; treating everyone as opted out", e);
                }
            }
            loaded = true;
        }
    }

    public static boolean isOptedIn(UUID player) {
        ensureLoaded();
        return OPTED_IN.contains(player);
    }

    public static void optIn(UUID player) {
        ensureLoaded();
        OPTED_IN.add(player);
        LAST_SEEN.put(player, System.currentTimeMillis());
        save();
    }

    /** Records that an opted-in player is online now. */
    public static void touch(UUID player, long nowMillis) {
        ensureLoaded();
        if (OPTED_IN.contains(player)) {
            LAST_SEEN.put(player, nowMillis);
            save();
        }
    }

    /**
     * Opts out (and deletes the snippets of) every opted-in player who has not been online for
     * {@code maxAgeMillis}, e.g. someone who removed the mod and so can no longer join.
     *
     * @return how many players' snippets were deleted
     */
    public static int expireStale(long nowMillis, long maxAgeMillis) {
        ensureLoaded();
        int expired = 0;
        for (UUID id : java.util.List.copyOf(OPTED_IN)) {
            Long seen = LAST_SEEN.get(id);
            if (seen == null) {
                LAST_SEEN.put(id, nowMillis); // from before this feature: start their clock now
                save();
            } else if (nowMillis - seen > maxAgeMillis) {
                optOut(id);
                expired++;
            }
        }
        return expired;
    }

    /** Forgets every opt-in and deletes every snippet (used when the mod was unused for too long). */
    public static int wipeEverything() {
        ensureLoaded();
        OPTED_IN.clear();
        LAST_SEEN.clear();
        save();
        return SnippetStore.deleteEveryone();
    }

    public static java.util.Optional<Long> lastSeen(UUID player) {
        ensureLoaded();
        return java.util.Optional.ofNullable(LAST_SEEN.get(player));
    }

    /** Opts out and deletes every snippet stored for this player. */
    public static int optOut(UUID player) {
        ensureLoaded();
        OPTED_IN.remove(player);
        LAST_SEEN.remove(player);
        save();
        VoiceBridge.forget(player);
        return SnippetStore.deleteAll(player);
    }

    private static synchronized void save() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        OPTED_IN.forEach(id -> arr.add(id.toString()));
        root.add("optedIn", arr);
        JsonObject seen = new JsonObject();
        LAST_SEEN.forEach((id, t) -> seen.addProperty(id.toString(), t));
        root.add("lastSeen", seen);
        try {
            SecureStorage.writeEncrypted(file(), root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            FogLurkerMod.LOGGER.error("Could not save voice consent list", e);
        }
    }
}
