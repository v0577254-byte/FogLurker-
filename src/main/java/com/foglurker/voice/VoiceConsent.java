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
     * A second copy, written when the main file could not be (for example locked by another program on
     * Windows). Both copies carry a version number and the newer one wins on the next start, so an opt-out
     * is never undone by an older copy, whichever file it is in.
     */
    private static Path newerFile() {
        return SecureStorage.root().resolve("voice_consent.next");
    }

    /** Bumped on every save; the copy with the highest number is the current one. */
    private static long version;

    private record Copy(Path path, long version, java.util.Set<UUID> optedIn, java.util.Map<UUID, Long> lastSeen) {
    }

    private static final Copy ABSENT = new Copy(null, -1, Set.of(), java.util.Map.of());

    /** @return the copy in this file, ABSENT if there is none, or null if it exists but cannot be read */
    @javax.annotation.Nullable
    private static Copy read(Path f) {
        if (!Files.isRegularFile(f)) {
            return ABSENT;
        }
        try {
            String json = new String(SecureStorage.readEncrypted(f), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            java.util.Set<UUID> in = new java.util.HashSet<>();
            for (JsonElement e : root.getAsJsonArray("optedIn")) {
                in.add(UUID.fromString(e.getAsString()));
            }
            java.util.Map<UUID, Long> seen = new java.util.HashMap<>();
            if (root.has("lastSeen")) {
                root.getAsJsonObject("lastSeen").entrySet().forEach(en ->
                        seen.put(UUID.fromString(en.getKey()), en.getValue().getAsLong()));
            }
            long v = root.has("version") ? root.get("version").getAsLong() : 0L;
            return new Copy(f, v, in, seen);
        } catch (Exception e) {
            FogLurkerMod.LOGGER.error("Could not read the voice consent file {}", f, e);
            return null;
        }
    }

    private static String setAside(Path f) {
        Path aside = f.resolveSibling(f.getFileName() + ".unreadable-" + System.currentTimeMillis());
        try {
            Files.move(f, aside); // kept for the server owner
            return "kept as " + aside;
        } catch (IOException | RuntimeException moveFailed) {
            try {
                Files.copy(f, aside);
                return "a copy kept as " + aside;
            } catch (IOException | RuntimeException copyFailed) {
                return "no copy could be kept";
            }
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (VoiceConsent.class) {
            if (loaded) {
                return;
            }
            Copy main = read(file());
            Copy next = read(newerFile());
            if (main == null || next == null) {
                // A copy we cannot read might be the newest one, holding an opt-out. Half a list is worse than
                // none: nobody is opted in unless we can read that they are.
                String kept = (main == null ? setAside(file()) : "") + (next == null ? " " + setAside(newerFile()) : "");
                FogLurkerMod.LOGGER.error("Could not read the voice consent list ({}). Everyone is treated as opted out, "
                        + "the voice snippets stored for them are deleted, and players can opt in again.", kept.trim());
                OPTED_IN.clear();
                LAST_SEEN.clear();
                version = Math.max(main == null ? 0 : main.version(), next == null ? 0 : next.version());
                // Done before anyone else can opt in, so the sweep can never take a fresh opt-in's snippets with it.
                SnippetStore.deleteExcept(Set.of());
                loaded = true;
                save(true); // an empty list, newer than anything left on disk
                return;
            }
            Copy current = next.version() > main.version() ? next : main;
            OPTED_IN.addAll(current.optedIn());
            LAST_SEEN.putAll(current.lastSeen());
            version = Math.max(main.version(), next.version());
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
        int deleted = 0;
        try {
            save(true);
        } finally {
            // Even if writing the list failed unexpectedly, recording stops and the clips go now.
            VoiceBridge.forget(player);
            deleted = SnippetStore.deleteAll(player);
        }
        return deleted;
    }

    /**
     * @param removal this change takes someone off the list (an opt-out or an expiry)
     * @return false if it could not be written; it is then retried by {@link #flushIfUnsaved}
     */
    private static synchronized boolean save(boolean removal) {
        version++;
        JsonObject root = new JsonObject();
        root.addProperty("version", version);
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
                Files.deleteIfExists(newerFile()); // tidy: it is older than the main file now, so it can never win
            } catch (IOException | RuntimeException ignored) {
                // harmless for the same reason
            }
            return true;
        } catch (IOException e) {
            unsaved = true;
            unsavedRemoval |= removal;
            FogLurkerMod.LOGGER.error("Could not save the voice consent list; it will be tried again", e);
            try {
                SecureStorage.writeEncrypted(newerFile(), bytes); // newer than the main file, so it wins on the next start
                return false;
            } catch (IOException | RuntimeException e2) {
                // fall through
            }
            if (unsavedRemoval) {
                // Last resort: either copy on disk may still name someone who has opted out, and would opt them
                // back in after a restart. With no copy at all, everyone is opted out.
                for (Path f : new Path[]{file(), newerFile()}) {
                    try {
                        Files.deleteIfExists(f);
                    } catch (IOException | RuntimeException ignored) {
                        // nothing more can be done until the disk works again; flushIfUnsaved keeps trying
                    }
                }
            }
            return false;
        }
    }
}
