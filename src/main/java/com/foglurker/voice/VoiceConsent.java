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
        save();
    }

    /** Opts out and deletes every snippet stored for this player. */
    public static int optOut(UUID player) {
        ensureLoaded();
        OPTED_IN.remove(player);
        save();
        VoiceBridge.forget(player);
        return SnippetStore.deleteAll(player);
    }

    private static synchronized void save() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        OPTED_IN.forEach(id -> arr.add(id.toString()));
        root.add("optedIn", arr);
        try {
            SecureStorage.writeEncrypted(file(), root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            FogLurkerMod.LOGGER.error("Could not save voice consent list", e);
        }
    }
}
