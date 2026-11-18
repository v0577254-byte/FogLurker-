package com.foglurker.voice;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.security.SecureStorage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Encrypted voice snippets: foglurker_secure/voice_snippets/&lt;player uuid&gt;/*.snip
 * Each file is 48 kHz mono 16-bit PCM, AES-GCM encrypted. Snippets never leave the server.
 */
public final class SnippetStore {
    public static final int SAMPLE_RATE = 48000;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FogLurker-Snippets");
        t.setDaemon(true);
        return t;
    });

    private SnippetStore() {
    }

    private static Path dir(UUID player) {
        return SecureStorage.voiceRoot().resolve(player.toString());
    }

    public static void saveAsync(UUID player, short[] pcm) {
        if (!VoiceConsent.isOptedIn(player)) {
            return; // never store anything for someone who has not opted in
        }
        long recordedUnder = VoiceConsent.generation(player);
        IO.execute(() -> {
            // Opted out while this was waiting (even if they opted in again since): it is not kept.
            if (!VoiceConsent.isOptedIn(player) || VoiceConsent.generation(player) != recordedUnder) {
                return;
            }
            try {
                Path d = dir(player);
                SecureStorage.ensurePrivateDir(d);
                String name = System.currentTimeMillis() + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt()) + ".snip";
                Path file = d.resolve(name);
                SecureStorage.writeEncrypted(file, toBytes(pcm));
                if (!VoiceConsent.isOptedIn(player) || VoiceConsent.generation(player) != recordedUnder) {
                    Files.deleteIfExists(file); // they opted out while this was being written
                    return;
                }
                prune(d);
            } catch (IOException | RuntimeException e) {
                FogLurkerMod.LOGGER.error("Could not store voice snippet", e);
            }
        });
    }

    /** Trims everyone's clips to maxSnippetsPerPlayer, so lowering it also applies to people who stopped talking. */
    public static void pruneAllAsync() {
        io().execute(() -> {
            try (java.util.stream.Stream<Path> dirs = Files.list(SecureStorage.voiceRoot())) {
                for (Path d : dirs.filter(Files::isDirectory).toList()) {
                    prune(d);
                }
            } catch (IOException | RuntimeException e) {
                FogLurkerMod.LOGGER.warn("Could not trim the stored voice snippets: {}", e.toString());
            }
        });
    }

    private static void prune(Path d) throws IOException {
        List<Path> files = list(d);
        int max = FogLurkerConfig.COMMON.maxSnippetsPerPlayer.get();
        if (files.size() <= max) {
            return;
        }
        // Mostly drop the oldest, but keep a few old ones around so there is variety.
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        while (files.size() > max) {
            int idx = ThreadLocalRandom.current().nextFloat() < 0.8F ? 0 : ThreadLocalRandom.current().nextInt(files.size());
            Files.deleteIfExists(files.remove(idx));
        }
    }

    private static List<Path> list(Path d) throws IOException {
        if (!Files.isDirectory(d)) {
            return new ArrayList<>();
        }
        try (Stream<Path> s = Files.list(d)) {
            return new ArrayList<>(s.filter(p -> p.getFileName().toString().endsWith(".snip")).toList());
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause(); // a directory read error mid-listing: an ordinary IOException for the callers
        }
    }

    public static int count(UUID player) {
        try {
            return list(dir(player)).size();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Loads a random snippet (blocking; call off the server thread). */
    public static short[] loadRandom(UUID player) {
        Path pick = null;
        try {
            List<Path> files = list(dir(player));
            if (files.isEmpty()) {
                return null;
            }
            pick = files.get(ThreadLocalRandom.current().nextInt(files.size()));
            return fromBytes(SecureStorage.readEncrypted(pick));
        } catch (java.nio.file.NoSuchFileException gone) {
            return null; // deleted in between (pruned, or an opt-out): nothing to play, nothing to clean up
        } catch (SecureStorage.Unavailable e) {
            FogLurkerMod.LOGGER.warn("Could not open a voice snippet right now: {}", e.getMessage());
            return null;
        } catch (IOException e) {
            if (pick != null) {
                try {
                    Files.deleteIfExists(pick); // damaged or from an old key: it can never play, so it goes
                } catch (IOException | RuntimeException ignored) {
                    // the sweep or the next attempt tries again
                }
            }
            FogLurkerMod.LOGGER.warn("Could not load a voice snippet: {}", e.getMessage());
            return null;
        }
    }

    /** Deletes the snippets of every player who is not in {@code keep}. @return number of snippet files deleted */
    public static int deleteExcept(java.util.Set<UUID> keep) {
        int deleted = 0;
        Path root = SecureStorage.voiceRoot();
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path d : dirs.filter(Files::isDirectory).toList()) {
                try {
                    UUID owner = UUID.fromString(d.getFileName().toString());
                    if (!keep.contains(owner)) {
                        deleted += deleteAll(owner);
                    }
                } catch (IllegalArgumentException notAPlayer) {
                    // leave anything that is not a player folder alone
                }
            }
        } catch (IOException | RuntimeException e) {
            FogLurkerMod.LOGGER.error("Could not list voice snippet folders", e);
        }
        return deleted;
    }

    /** Deletes the snippets of every player. @return number of snippet files deleted */
    public static int deleteEveryone() {
        int deleted = 0;
        Path root = SecureStorage.voiceRoot();
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path d : dirs.filter(Files::isDirectory).toList()) {
                try {
                    deleted += deleteAll(UUID.fromString(d.getFileName().toString()));
                } catch (IllegalArgumentException notAPlayer) {
                    // leave anything that is not a player folder alone
                }
            }
        } catch (IOException | RuntimeException e) {
            FogLurkerMod.LOGGER.error("Could not list voice snippet folders", e);
        }
        return deleted;
    }

    /**
     * Deletes every snippet of this player. One file that cannot be deleted right now (for example held open
     * by a virus scanner on Windows) does not stop the rest; whatever is left is deleted by the sweep that
     * runs at start-up and every 10 minutes while someone is playing (VoiceConsent.deleteOrphanSnippets).
     */
    public static int deleteAll(UUID player) {
        Path d = dir(player);
        int deleted = 0;
        int failed = 0;
        if (!Files.isDirectory(d)) {
            return 0;
        }
        List<Path> everything;
        try (Stream<Path> all = Files.list(d)) {
            everything = all.toList();
        } catch (IOException e) {
            FogLurkerMod.LOGGER.error("Could not list the voice snippets of {}; they will be deleted on the next sweep", player, e);
            return 0;
        }
        for (Path p : everything) {
            try {
                Files.deleteIfExists(p);
                if (p.getFileName().toString().endsWith(".snip")) {
                    deleted++;
                }
            } catch (IOException | RuntimeException e) {
                failed++;
            }
        }
        try {
            Files.deleteIfExists(d);
        } catch (IOException | RuntimeException e) {
            failed++;
        }
        if (failed > 0) {
            FogLurkerMod.LOGGER.warn("{} voice snippet file(s) of {} could not be deleted yet; the sweep will retry", failed, player);
        }
        return deleted;
    }

    /** Writes, reads back and deletes a test snippet in the secure folder. Used by the debug command. */
    public static String selfTest() throws IOException {
        Path d = SecureStorage.voiceRoot().resolve("_selftest");
        SecureStorage.ensurePrivateDir(d);
        short[] pcm = new short[4800];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (Math.sin(i / 10.0) * 9000);
        }
        Path f = d.resolve("test.snip");
        SecureStorage.writeEncrypted(f, toBytes(pcm));
        short[] back = fromBytes(SecureStorage.readEncrypted(f));
        boolean same = java.util.Arrays.equals(pcm, back);
        Files.deleteIfExists(f);
        Files.deleteIfExists(d);
        if (!same) {
            throw new IOException("snippet came back different");
        }
        return pcm.length * 2 + " bytes written, encrypted, read back and deleted";
    }

    public static ExecutorService io() {
        return IO;
    }

    private static byte[] toBytes(short[] pcm) {
        ByteBuffer b = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.asShortBuffer().put(pcm);
        return b.array();
    }

    private static short[] fromBytes(byte[] bytes) {
        short[] pcm = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
        return pcm;
    }
}
