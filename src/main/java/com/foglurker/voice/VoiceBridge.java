package com.foglurker.voice;

import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * The only door between Fog Lurker and Simple Voice Chat. When the voice chat mod is not installed
 * {@link #backend} stays null and every call here is a harmless no-op.
 */
public final class VoiceBridge {

    public interface Backend {
        /** Plays one of {@code voiceOwner}'s stolen snippets from {@code source}'s position. */
        boolean speak(Entity source, UUID voiceOwner, AudioFx.Style style, boolean splice);

        void stop(Entity source);

        void forget(UUID player);

        /** Called every server tick to close finished recordings. */
        void tick();
    }

    private static volatile Backend backend;

    private VoiceBridge() {
    }

    public static void setBackend(Backend b) {
        backend = b;
    }

    /** Simple Voice Chat is installed and its voice server is running. */
    public static boolean isActive() {
        return backend != null;
    }

    public static boolean canSpeakAs(UUID voiceOwner) {
        return backend != null && VoiceConsent.isOptedIn(voiceOwner) && SnippetStore.count(voiceOwner) > 0;
    }

    public static boolean speak(Entity source, UUID voiceOwner, AudioFx.Style style, boolean splice) {
        Backend b = backend;
        if (b == null || !VoiceConsent.isOptedIn(voiceOwner)) {
            return false;
        }
        return b.speak(source, voiceOwner, style, splice);
    }

    public static void stop(Entity source) {
        Backend b = backend;
        if (b != null) {
            b.stop(source);
        }
    }

    static void forget(UUID player) {
        Backend b = backend;
        if (b != null) {
            b.forget(player);
        }
    }

    public static void tick() {
        Backend b = backend;
        if (b != null) {
            b.tick();
        }
    }
}
