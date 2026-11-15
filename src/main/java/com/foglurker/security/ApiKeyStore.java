package com.foglurker.security;

import com.foglurker.FogLurkerMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Holds the Gemini API key. Stored encrypted in the secure folder next to the voice snippets.
 * Server owners can alternatively provide it through the FOGLURKER_GEMINI_API_KEY environment variable.
 * The key never leaves the server: it is only sent to Google's API, never to players.
 */
public final class ApiKeyStore {
    private static final String FILE = "gemini_api_key.enc";
    private static final String ENV = "FOGLURKER_GEMINI_API_KEY";
    private static volatile String cached;
    private static volatile boolean loaded;

    private ApiKeyStore() {
    }

    private static Path file() {
        return SecureStorage.root().resolve(FILE);
    }

    public static synchronized void set(String key) throws IOException {
        String k = key == null ? "" : key.trim();
        if (k.isEmpty()) {
            Files.deleteIfExists(file());
            cached = null;
        } else {
            SecureStorage.writeEncrypted(file(), k.getBytes(StandardCharsets.UTF_8));
            cached = k;
        }
        loaded = true;
    }

    /** @return the key, or null when none has been set. */
    public static String get() {
        if (!loaded) {
            synchronized (ApiKeyStore.class) {
                if (!loaded) {
                    cached = load();
                    loaded = true;
                }
            }
        }
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        String env = System.getenv(ENV);
        return env == null || env.isBlank() ? null : env.trim();
    }

    public static boolean isSet() {
        return get() != null;
    }

    private static String load() {
        Path f = file();
        if (!Files.isRegularFile(f)) {
            return null;
        }
        try {
            return new String(SecureStorage.readEncrypted(f), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            FogLurkerMod.LOGGER.error("Could not read the stored Gemini API key; set it again with /foglurker setgeminiapikey", e);
            return null;
        }
    }

    /** "AIza…k3Fq" style preview that is safe to show in chat. */
    public static String masked(String key) {
        if (key == null || key.length() < 8) {
            return "****";
        }
        return key.substring(0, 4) + "…" + key.substring(key.length() - 4);
    }
}
