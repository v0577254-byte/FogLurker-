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
    /** A change could not be written to disk yet (it is retried). */
    private static volatile boolean unsaved;
    /** ...and that change removed someone, so a stale list on disk must not survive. */
    private static volatile boolean unsavedRemoval;

    private VoiceConsent() {
    }

    private static Path file() {
        return SecureStorage.root().resolve("voice_consent.json");
    }

    /**
     * A newer copy, written when the main file could not be (for example locked by another program on
     * Windows). It wins on the next start, so an opt-out is never undone by a stale main file.
     */
    private static Path newerFile() {
        return SecureStorage.root().resolve("voice_consent.next");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (VoiceConsent.class) {
            if (loaded) {
                return;
            }
            Path f = Files.isRegularFile(newerFile()) ? newerFile() : file();
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
                    // Half a list is worse than none: nobody is opted in unless we can read that they are.
                    OPTED_IN.clear();
                    LAST_SEEN.clear();
                    Path aside = f.resolveSibling(f.getFileName() + ".unreadable-" + System.currentTimeMillis());
                    String kept;
                    try {
                        Files.move(f, aside); // kept for the server owner; a later save must not replace it
                        kept = "kept as " + aside;
                    } catch (IOException | RuntimeException moveFailed) {
                        try {
                            Files.copy(f, aside);
                            kept = "a copy kept as " + aside;
                        } catch (IOException | RuntimeException copyFailed) {
                            kept = "no copy could be kept";
                        }
                    }
                    FogLurkerMod.LOGGER.error("Could not read the voice consent list ({}). Everyone is treated as opted out, "
                            + "the voice snippets stored for them are deleted, and players can opt in again.", kept, e);
                    // No readable consent, so no voice may be kept. Done before anyone else can opt in,
                    // so the sweep can never take a fresh opt-in's snippets with it.
                    SnippetStore.deleteExcept(Set.of());
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
        save(false);
    }

    /**
     * Deletes stored snippets of anyone who is not opted in. There should never be any; this makes sure
     * of it after a consent list could not be read, or a crash between an opt-out and its clean-up.
     *
     * @return number of snippet files deleted
     */
    public static int deleteOrphanSnippets() {
        ensureLoaded();
        return SnippetStore.deleteExcept(Set.copyOf(OPTED_IN));
    }

    /** True while a consent change is only in memory because the disk write failed. */
    public static boolean hasUnsavedChanges() {
        return unsaved;
    }

    /** Tries again to write a change that could not be saved. */
    public static void flushIfUnsaved() {
        if (unsaved) {
            save(unsavedRemoval);
        }
    }

    /** Records that an opted-in player is online now. */
    public static void touch(UUID player, long nowMillis) {
        ensureLoaded();
        if (OPTED_IN.contains(player)) {
            LAST_SEEN.put(player, nowMillis);
            save(false);
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
                save(false);
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
        save(true);
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
        save(true);
        VoiceBridge.forget(player);
        return SnippetStore.deleteAll(player);
    }

    /**
     * @param removal this change takes someone off the list (an opt-out or an expiry)
     * @return false if it could not be written; it is then retried by {@link #flushIfUnsaved}
     */
    private static synchronized boolean save(boolean removal) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        OPTED_IN.forEach(id -> arr.add(id.toString()));
        root.add("optedIn", arr);
        JsonObject seen = new JsonObject();
        LAST_SEEN.forEach((id, t) -> seen.addProperty(id.toString(), t));
        root.add("lastSeen", seen);
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        try {
            SecureStorage.writeEncrypted(file(), bytes);
            unsaved = false;
            unsavedRemoval = false;
            try {
                Files.deleteIfExists(newerFile()); // the main file is current again
            } catch (IOException | RuntimeException ignored) {
                // harmless: it holds the same list, and the next save tries again
            }
            return true;
        } catch (IOException e) {
            unsaved = true;
            unsavedRemoval |= removal;
            FogLurkerMod.LOGGER.error("Could not save the voice consent list; it will be tried again", e);
            // Keep the change somewhere that wins on the next start, even if the main file is locked.
            boolean kept;
            try {
                SecureStorage.writeEncrypted(newerFile(), bytes);
                kept = true;
            } catch (IOException | RuntimeException e2) {
                kept = false;
            }
            if (!kept && unsavedRemoval) {
                // Last resort: the old list on disk still names someone who has opted out, and would opt them
                // back in after a restart. With no list at all, everyone is opted out.
                try {
                    Files.deleteIfExists(file());
                } catch (IOException | RuntimeException ignored) {
                    // nothing more can be done until the disk works again; flushIfUnsaved keeps trying
                }
            }
            return false;
        }
    }
}
