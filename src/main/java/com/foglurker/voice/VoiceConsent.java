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
    /** The file is there but cannot be opened right now: try again later, change and delete nothing. */
    private static final Copy BUSY = new Copy(null, -2, Set.of(), java.util.Map.of());
    /** While the list cannot be opened: when to try again (System.currentTimeMillis). */
    private static volatile long retryAt;
    /** Opt-outs made while the list could not be opened; applied (and saved) as soon as it can be. */
    private static final Set<UUID> PENDING_OUT = ConcurrentHashMap.newKeySet();
    /** wipeEverything() ran while the list could not be opened; applied as soon as it can be. */
    private static volatile boolean pendingWipe;

    /** @return the copy in this file, ABSENT if there is none, or null if it exists but cannot be read */
    @javax.annotation.Nullable
    private static Copy read(Path f) {
        try {
            if (!Files.readAttributes(f, java.nio.file.attribute.BasicFileAttributes.class).isRegularFile()) {
                return ABSENT;
            }
        } catch (java.nio.file.NoSuchFileException missing) {
            return ABSENT;
        } catch (IOException | RuntimeException e) {
            FogLurkerMod.LOGGER.warn("The voice consent file {} cannot be looked at right now ({}); nobody is recorded "
                    + "until it can (if this keeps happening, check the folder's permissions)", f.getFileName(), e.toString());
            return BUSY; // there, but cannot even be looked at right now
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
        } catch (java.nio.file.NoSuchFileException gone) {
            return ABSENT; // deleted in between
        } catch (SecureStorage.Unavailable e) {
            FogLurkerMod.LOGGER.warn("The voice consent file {} cannot be opened right now ({}); trying again shortly",
                    f.getFileName(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return BUSY;
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

    /**
     * @return true once the list is loaded. False while it cannot be opened (another program has it):
     * then nobody counts as opted in, so nobody is recorded or played, and nothing is deleted.
     */
    private static boolean ensureLoaded() {
        if (loaded) {
            return true;
        }
        synchronized (VoiceConsent.class) {
            if (loaded) {
                return true;
            }
            if (retryAt != 0 && System.nanoTime() - retryAt < 0) {
                return false;
            }
            Copy main = read(file());
            Copy next = read(newerFile());
            if (main == BUSY || next == BUSY) {
                retryAt = System.nanoTime() + 30_000_000_000L; // nanoTime: a clock change cannot stretch the wait
                return false;
            }
            retryAt = 0;
            if (main == null || next == null) {
                // A copy we cannot read might be the newest one, holding an opt-out. Half a list is worse than
                // none: nobody is opted in unless we can read that they are.
                // Both copies are kept aside for the server owner (the readable one too: it is about to be replaced).
                String kept = (main == null || main != ABSENT ? setAside(file()) : "")
                        + (next == null || next != ABSENT ? " " + setAside(newerFile()) : "");
                FogLurkerMod.LOGGER.error("Could not read the voice consent list ({}). Everyone is treated as opted out, "
                        + "the voice snippets stored for them are deleted, and players can opt in again.", kept.trim());
                OPTED_IN.clear();
                LAST_SEEN.clear();
                version = Math.max(main == null ? 0 : main.version(), next == null ? 0 : next.version());
                // Done before anyone else can opt in, so the sweep can never take a fresh opt-in's snippets with it.
                SnippetStore.deleteExcept(Set.of());
                PENDING_OUT.clear();
                pendingWipe = false;
                save(true); // an empty list, newer than anything left on disk
                loaded = true; // published last: nobody sees the list before it is final
                return true;
            }
            Copy current = next.version() > main.version() ? next : main;
            loadPendingOptOuts(); // opt-outs from a run where the list stayed locked
            OPTED_IN.addAll(current.optedIn());
            LAST_SEEN.putAll(current.lastSeen());
            version = Math.max(main.version(), next.version());
            // Apply what happened while the list could not be opened, before anyone can see the list.
            boolean removed = false;
            if (pendingWipe) {
                removed = !OPTED_IN.isEmpty();
                OPTED_IN.clear();
                LAST_SEEN.clear();
                pendingWipe = false;
                SnippetStore.deleteEveryone();
            }
            for (UUID out : PENDING_OUT) {
                removed |= OPTED_IN.remove(out);
                LAST_SEEN.remove(out);
                SnippetStore.deleteAll(out); // anything written for them meanwhile goes too
            }
            PENDING_OUT.clear();
            if (removed) {
                save(true);
            }
            if (!hasUnsavedChanges()) {
                try {
                    Files.deleteIfExists(pendingFile()); // applied and saved
                } catch (IOException | RuntimeException ignored) {
                    // applying it again next time is harmless
                }
            }
            loaded = true; // published last: nobody sees the list before it is final
            return true;
        }
    }

    public static boolean isOptedIn(UUID player) {
        return ensureLoaded() && OPTED_IN.contains(player);
    }

    /** False while the consent list cannot be opened (then everyone counts as opted out). */
    public static boolean isReadable() {
        return ensureLoaded();
    }

    /** Opt-outs made while the list could not be opened, kept in their own small file until it can be. */
    private static Path pendingFile() {
        return SecureStorage.root().resolve("voice_optouts.pending");
    }

    private static void savePendingOptOuts() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        PENDING_OUT.forEach(id -> arr.add(id.toString()));
        root.add("optedOut", arr);
        root.addProperty("wipe", pendingWipe);
        try {
            SecureStorage.writeEncrypted(pendingFile(), root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            FogLurkerMod.LOGGER.error("Could not save an opt-out while the voice consent list is locked; it applies until "
                    + "the server stops and is retried when the list opens", e);
        }
    }

    /** Reads opt-outs saved while the list was locked (from an earlier run) into PENDING_OUT. */
    private static void loadPendingOptOuts() {
        try {
            JsonObject root = JsonParser.parseString(new String(SecureStorage.readEncrypted(pendingFile()),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("optedOut")) {
                PENDING_OUT.add(UUID.fromString(e.getAsString()));
            }
            if (root.has("wipe") && root.get("wipe").getAsBoolean()) {
                pendingWipe = true;
            }
        } catch (java.nio.file.NoSuchFileException none) {
            // nothing pending
        } catch (Exception e) {
            FogLurkerMod.LOGGER.error("Could not read the opt-outs saved while the consent list was locked", e);
        }
    }

    /** @return false if the list cannot be opened right now (nothing changed; try again in a minute) */
    public static boolean optIn(UUID player) {
        if (!ensureLoaded()) {
            return false;
        }
        PENDING_OUT.remove(player);
        OPTED_IN.add(player);
        LAST_SEEN.put(player, System.currentTimeMillis());
        save(false);
        return true;
    }

    /**
     * Deletes stored snippets of anyone who is not opted in. There should never be any; this makes sure
     * of it after a consent list could not be read, or a crash between an opt-out and its clean-up.
     *
     * @return number of snippet files deleted
     */
    public static int deleteOrphanSnippets() {
        if (!ensureLoaded()) {
            return 0; // the list cannot be opened: never guess who is opted in
        }
        return SnippetStore.deleteExcept(java.util.Collections.unmodifiableSet(OPTED_IN)); // live: someone opting in mid-sweep is kept
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
        if (ensureLoaded() && OPTED_IN.contains(player)) {
            LAST_SEEN.put(player, nowMillis);
            save(false);
        }
    }

    /** Like {@link #touch} for many players at once, with a single write (not one disk flush per player). */
    public static void touchAll(Iterable<UUID> players, long nowMillis) {
        if (!ensureLoaded()) {
            return;
        }
        boolean any = false;
        for (UUID id : players) {
            if (OPTED_IN.contains(id)) {
                LAST_SEEN.put(id, nowMillis);
                any = true;
            }
        }
        if (any) {
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
        if (!ensureLoaded()) {
            return 0;
        }
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
        if (!ensureLoaded()) {
            synchronized (VoiceConsent.class) {
                if (!loaded) {
                    pendingWipe = true; // forgets every opt-in as soon as the list can be opened
                    savePendingOptOuts();
                }
            }
            if (loaded) {
                OPTED_IN.clear();
                LAST_SEEN.clear();
                save(true);
            }
            return SnippetStore.deleteEveryone();
        }
        OPTED_IN.clear();
        LAST_SEEN.clear();
        save(true);
        return SnippetStore.deleteEveryone();
    }

    public static java.util.Optional<Long> lastSeen(UUID player) {
        return ensureLoaded() ? java.util.Optional.ofNullable(LAST_SEEN.get(player)) : java.util.Optional.empty();
    }

    /** Opts out and deletes every snippet stored for this player. */
    public static int optOut(UUID player) {
        if (!ensureLoaded()) {
            // The list cannot be opened right now: recording stops and the clips go now; the list catches up later.
            synchronized (VoiceConsent.class) {
                if (!loaded) {
                    PENDING_OUT.add(player);
                    savePendingOptOuts(); // so a restart before the list opens cannot undo it
                }
            }
            if (loaded) {
                // It opened in the meantime: apply it the normal way (removing them twice is harmless).
                OPTED_IN.remove(player);
                LAST_SEEN.remove(player);
                save(true);
            }
            VoiceBridge.forget(player);
            return SnippetStore.deleteAll(player);
        }
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
