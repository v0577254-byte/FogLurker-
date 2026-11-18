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
    /** A saved key file exists but could not be decrypted (told to the operator by /foglurker geministatus). */
    private static volatile boolean unreadable;

    /** The last attempt to open the saved key found it locked (not damaged). */
    private static volatile boolean busy;

    public static boolean storedKeyBusy() {
        return busy && !loaded;
    }

    public static boolean storedKeyUnreadable() {
        return unreadable && cached == null;
    }

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
        unreadable = false;
    }

    /** @return the key, or null when none has been set. */
    public static String get() {
        if (!loaded) {
            synchronized (ApiKeyStore.class) {
                if (!loaded) {
                    try {
                        cached = load();
                        loaded = true;
                        busy = false;
                    } catch (SecureStorage.Unavailable e) {
                        busy = true;
                        // Locked for a moment (a virus scanner, a backup): not cached, so the next call tries again.
                        // No falling back to the environment key either: that may be a different key.
                        return null;
                    }
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

    /** The key saved with /foglurker setgeminiapikey, ignoring the environment variable. */
    public static String storedKey() {
        get(); // make sure it is loaded
        String k = cached;
        return k == null || k.isEmpty() ? null : k;
    }

    /** True when the key in use comes from the FOGLURKER_GEMINI_API_KEY environment variable. */
    public static boolean fromEnvironment() {
        return storedKey() == null && get() != null;
    }

    /**
     * Whether this could be a Gemini API key: 20 to 256 visible ASCII characters with no spaces.
     * (Google keys are "AIza" + 35 letters, digits, '-' and '_', but newer formats are allowed through.)
     * Anything else could never be sent in an HTTP header, and would only produce confusing errors.
     */
    public static boolean isPlausible(String key) {
        if (key == null || key.length() < 20 || key.length() > 256) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    private static String load() throws SecureStorage.Unavailable {
        Path f = file();
        try {
            // Only "definitely not there" means no key; trouble looking at the file means "try again later".
            if (!Files.readAttributes(f, java.nio.file.attribute.BasicFileAttributes.class).isRegularFile()) {
                return null;
            }
        } catch (java.nio.file.NoSuchFileException none) {
            return null;
        } catch (IOException e) {
            throw new SecureStorage.Unavailable("Cannot look at the saved Gemini key right now", e);
        }
        try {
            String k = new String(SecureStorage.readEncrypted(f), StandardCharsets.UTF_8).trim();
            return k.isEmpty() ? null : k;
        } catch (java.nio.file.NoSuchFileException gone) {
            return null;
        } catch (SecureStorage.Unavailable e) {
            throw e;
        } catch (IOException e) {
            FogLurkerMod.LOGGER.error("Could not read the stored Gemini API key; set it again with /foglurker setgeminiapikey", e);
            unreadable = true;
            return null;
        }
    }

    /** "AIza…k3Fq" style preview that is safe to show in chat. */
    public static String masked(String key) {
        if (key == null || key.length() < 24) {
            return "****"; // too short to show any of it safely
        }
        return key.substring(0, 4) + "…" + key.substring(key.length() - 4);
    }
}
