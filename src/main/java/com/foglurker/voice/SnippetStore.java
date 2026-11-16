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
        IO.execute(() -> {
            if (!VoiceConsent.isOptedIn(player)) {
                return; // opted out while this was being processed
            }
            try {
                Path d = dir(player);
                SecureStorage.ensurePrivateDir(d);
                String name = System.currentTimeMillis() + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt()) + ".snip";
                Path file = d.resolve(name);
                SecureStorage.writeEncrypted(file, toBytes(pcm));
                if (!VoiceConsent.isOptedIn(player)) {
                    Files.deleteIfExists(file); // they opted out while this was being written
                    return;
                }
                prune(d);
            } catch (IOException e) {
                FogLurkerMod.LOGGER.error("Could not store voice snippet", e);
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
        try {
            List<Path> files = list(dir(player));
            if (files.isEmpty()) {
                return null;
            }
            Path pick = files.get(ThreadLocalRandom.current().nextInt(files.size()));
            return fromBytes(SecureStorage.readEncrypted(pick));
        } catch (IOException e) {
            FogLurkerMod.LOGGER.warn("Could not load a voice snippet: {}", e.getMessage());
            return null;
        }
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
        } catch (IOException e) {
            FogLurkerMod.LOGGER.error("Could not list voice snippet folders", e);
        }
        return deleted;
    }

    public static int deleteAll(UUID player) {
        Path d = dir(player);
        int deleted = 0;
        try {
            for (Path p : list(d)) {
                Files.deleteIfExists(p);
                deleted++;
            }
            if (Files.isDirectory(d)) {
                try (Stream<Path> rest = Files.list(d)) {
                    for (Path p : rest.toList()) {
                        Files.deleteIfExists(p);
                    }
                }
                Files.deleteIfExists(d);
            }
        } catch (IOException e) {
            FogLurkerMod.LOGGER.error("Could not delete all voice snippets for {}", player, e);
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
