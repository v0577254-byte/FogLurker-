package com.foglurker.voice;

import com.foglurker.FogLurkerMod;
import com.foglurker.security.SecureStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Voice snippets never outlive their purpose by more than 30 days.
 * <ul>
 *   <li>An opted-in player who has not been online for 30 days (for example because they removed the
 *       mod and can no longer join) has their snippets deleted.</li>
 *   <li>If the mod itself was not run for 30 days (removed, then reinstalled later), every snippet is
 *       wiped the moment it starts again, before anything else happens.</li>
 * </ul>
 * A removed mod cannot run code, so the secure folder also carries a plain-text note explaining that
 * it is safe to delete by hand.
 */
public final class VoiceRetention {
    public static final long RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final String STAMP = "last_active.txt";
    private static final String NOTE = "READ_ME.txt";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private VoiceRetention() {
    }

    /** Runs when the server (or singleplayer world) starts. @return snippets deleted */
    public static int onStart(long nowMillis) {
        int deleted = 0;
        Long last = readStamp();
        if (last != null && nowMillis - last > RETENTION_MILLIS) {
            deleted = VoiceConsent.wipeEverything();
            FogLurkerMod.LOGGER.info("Fog Lurker was not used for over 30 days: deleted all {} stored voice snippets", deleted);
        }
        deleted += VoiceConsent.expireStale(nowMillis, RETENTION_MILLIS);
        int orphans = VoiceConsent.deleteOrphanSnippets();
        if (orphans > 0) {
            FogLurkerMod.LOGGER.warn("Deleted {} voice snippet(s) that had no opt-in on record", orphans);
        }
        deleted += orphans;
        stamp(nowMillis);
        return deleted;
    }

    /** Runs every so often while playing, and when players join or leave. */
    public static void heartbeat(long nowMillis, Iterable<UUID> onlinePlayers) {
        VoiceConsent.flushIfUnsaved();
        for (UUID id : onlinePlayers) {
            VoiceConsent.touch(id, nowMillis);
        }
        int expired = VoiceConsent.expireStale(nowMillis, RETENTION_MILLIS);
        if (expired > 0) {
            FogLurkerMod.LOGGER.info("Deleted the voice snippets of {} player(s) not seen for 30 days", expired);
        }
        stamp(nowMillis);
    }

    static Long readStamp() {
        Path f = SecureStorage.root().resolve(STAMP);
        try {
            return Files.isRegularFile(f) ? Long.parseLong(Files.readString(f, StandardCharsets.UTF_8).trim()) : null;
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    /** For tests: pretend the mod last ran at this time. */
    public static void stamp(long millis) {
        try {
            SecureStorage.writeAtomic(SecureStorage.root().resolve(STAMP), Long.toString(millis).getBytes(StandardCharsets.UTF_8));
            String wipeAfter = DATE.format(Instant.ofEpochMilli(millis + RETENTION_MILLIS));
            String note = """
                    FOG LURKER - SECURE FOLDER

                    This folder holds the Fog Lurker mod's encrypted data: the Gemini API key (if one was set),
                    who opted in to voice snippets, and their voice snippets.

                    If you have removed Fog Lurker, nothing uses this folder any more. You can delete it.

                    Automatic clean-up:
                    - The snippets of anyone who has not been online for 30 days are deleted.
                    - The mod last ran on %s. If it is started again after %s, it deletes every
                      voice snippet in here before doing anything else.
                    - Opting out (/fglurk optout) deletes your snippets immediately.
                    """.formatted(DATE.format(Instant.ofEpochMilli(millis)), wipeAfter);
            SecureStorage.writeAtomic(SecureStorage.root().resolve(NOTE), note.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            FogLurkerMod.LOGGER.warn("Could not update the secure folder's activity stamp", e);
        }
    }
}
