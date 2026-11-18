package com.foglurker.security;

import com.foglurker.FogLurkerMod;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;

/**
 * The "secure folder": {@code <game or server dir>/foglurker_secure/}.
 *
 * <p>Everything in it (the Gemini API key and every voice snippet) is encrypted with AES-256-GCM
 * using a random key generated on first use. On Linux/macOS the folder and files are restricted to
 * the account that runs Minecraft (chmod 700/600). The folder lives next to the server/game files,
 * never inside a world save, so it is not copied when a world is shared or uploaded, and it is never
 * sent to clients.</p>
 *
 * <p>Honest limits: anyone with full access to the server's OS account can still read the master
 * key. This protects against casual snooping, world downloads and accidental sharing, not against
 * someone who already owns the machine.</p>
 */
public final class SecureStorage {
    private static final byte[] MAGIC = "FLK1".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private static volatile SecretKey masterKey;

    private SecureStorage() {
    }

    public static Path root() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("foglurker_secure");
        ensurePrivateDir(dir);
        return dir;
    }

    public static Path voiceRoot() {
        Path dir = root().resolve("voice_snippets");
        ensurePrivateDir(dir);
        return dir;
    }

    public static void ensurePrivateDir(Path dir) {
        try {
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
            restrict(dir, true);
        } catch (IOException e) {
            FogLurkerMod.LOGGER.error("Could not create secure folder {}", dir, e);
        }
    }

    private static void restrict(Path path, boolean directory) {
        try {
            if (POSIX) {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------");
                Files.setPosixFilePermissions(path, perms);
            } else if (Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView.class) != null) {
                restrictWindows(path, directory);
            } else {
                java.io.File f = path.toFile();
                // Last resort on other systems: the best java.io.File can do (owner-only where supported).
                f.setReadable(false, false);
                f.setReadable(true, true);
                f.setWritable(false, false);
                f.setWritable(true, true);
                if (directory) {
                    f.setExecutable(false, false);
                    f.setExecutable(true, true);
                }
            }
        } catch (Exception ignored) {
            // Permissions are defence in depth; the contents are encrypted anyway.
        }
    }

    /**
     * Windows: only the account running Minecraft (and the file's owner, if that is someone else, such as the
     * Administrators group for files made while running as administrator) may open the file. If anything goes
     * wrong, or this process could no longer read and write the file afterwards, the old access list is put
     * back: locking the server out of its own folder would make the consent list unreadable.
     */
    private static void restrictWindows(Path path, boolean directory) throws IOException {
        var view = Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView.class);
        var owner = Files.getOwner(path);
        java.nio.file.attribute.UserPrincipal me;
        try {
            me = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
        } catch (IOException | RuntimeException e) {
            me = owner; // cannot look ourselves up: owner only (the check below undoes it if that locks us out)
        }
        var flags = directory
                ? java.util.EnumSet.of(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT, java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT)
                : java.util.EnumSet.noneOf(java.nio.file.attribute.AclEntryFlag.class);
        java.util.List<java.nio.file.attribute.AclEntry> entries = new java.util.ArrayList<>();
        for (var who : new java.util.LinkedHashSet<>(java.util.List.of(me, owner))) {
            entries.add(java.nio.file.attribute.AclEntry.newBuilder()
                    .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                    .setPrincipal(who)
                    .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class))
                    .setFlags(flags)
                    .build());
        }
        var before = view.getAcl();
        view.setAcl(entries);
        boolean stillOurs;
        try {
            stillOurs = Files.isReadable(path) && Files.isWritable(path);
        } catch (RuntimeException e) {
            stillOurs = false;
        }
        if (!stillOurs) {
            view.setAcl(before); // we would have locked ourselves out: undo
        }
    }

    /** True if anything encrypted (a voice snippet or the saved API key) is already in the folder. */
    private static boolean hasEncryptedData() {
        try (java.util.stream.Stream<Path> all = Files.walk(root(), 3)) {
            return all.anyMatch(p -> {
                String n = p.getFileName().toString();
                return n.endsWith(".snip") || n.endsWith(".enc") || n.startsWith("gemini") || n.startsWith("voice_consent");
            });
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * The file (or the key) is there but cannot be opened right now, for example because another program
     * (a virus scanner, a backup tool) has it open. Nothing is wrong with its contents: try again later and
     * never treat it as damaged.
     */
    public static final class Unavailable extends IOException {
        Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static SecretKey key() throws IOException {
        SecretKey k = masterKey;
        if (k != null) {
            return k;
        }
        synchronized (SecureStorage.class) {
            if (masterKey != null) {
                return masterKey;
            }
            Path keyFile = root().resolve(".master.key");
            byte[] raw = null;
            boolean intact;
            try {
                intact = Files.isRegularFile(keyFile) && Files.size(keyFile) == 32;
                if (intact) {
                    raw = Files.readAllBytes(keyFile);
                }
            } catch (IOException e) {
                throw new Unavailable("The secure folder key cannot be opened right now", e);
            }
            if (!intact) {
                boolean wasThere = Files.exists(keyFile);
                if (wasThere) {
                    // Damaged key: keep it (it may be recoverable) and say so loudly instead of silently
                    // replacing it, which would make everything encrypted with it unreadable without warning.
                    Path aside = keyFile.resolveSibling(".master.key.damaged-" + System.currentTimeMillis());
                    Files.move(keyFile, aside);
                    FogLurkerMod.LOGGER.error("Fog Lurker's secure folder key was damaged and was moved to {}. A new key was "
                            + "created: existing voice snippets and the Gemini API key cannot be read and must be set again.", aside);
                }
                if (!wasThere && hasEncryptedData()) {
                    FogLurkerMod.LOGGER.error("Fog Lurker's secure folder key is missing but encrypted files are still there "
                            + "(was the folder copied without its .master.key?). A new key was created: existing voice "
                            + "snippets and the Gemini API key cannot be read and must be set again.");
                }
                raw = new byte[32];
                RANDOM.nextBytes(raw);
                writeAtomic(keyFile, raw);
            }
            masterKey = new SecretKeySpec(raw, "AES");
            Arrays.fill(raw, (byte) 0);
            return masterKey;
        }
    }

    public static byte[] encrypt(byte[] plain) throws IOException {
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(MAGIC);
            byte[] body = cipher.doFinal(plain);
            byte[] out = new byte[MAGIC.length + IV_LEN + body.length];
            System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
            System.arraycopy(iv, 0, out, MAGIC.length, IV_LEN);
            System.arraycopy(body, 0, out, MAGIC.length + IV_LEN, body.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IOException("Encryption failed", e);
        }
    }

    public static byte[] decrypt(byte[] data) throws IOException {
        if (data.length < MAGIC.length + IV_LEN + 16
                || !Arrays.equals(Arrays.copyOfRange(data, 0, MAGIC.length), MAGIC)) {
            throw new IOException("Not a Fog Lurker secure file");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(TAG_BITS, data, MAGIC.length, IV_LEN));
            cipher.updateAAD(MAGIC);
            return cipher.doFinal(data, MAGIC.length + IV_LEN, data.length - MAGIC.length - IV_LEN);
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not decrypt (wrong key or tampered file)", e);
        }
    }

    public static void writeEncrypted(Path file, byte[] plain) throws IOException {
        writeAtomic(file, encrypt(plain));
    }

    public static byte[] readEncrypted(Path file) throws IOException {
        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new Unavailable("Cannot open " + file.getFileName() + " right now", e);
        }
        return decrypt(data);
    }

    /** Writes via a temp file + move so a crash never leaves half a file behind. */
    public static void writeAtomic(Path file, byte[] bytes) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            ensurePrivateDir(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.deleteIfExists(tmp);
        if (POSIX) {
            Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } else {
            Files.createFile(tmp);
            restrict(tmp, false); // narrowed while still empty, before the secret goes in
        }
        // Flushed to the disk before it replaces the old file, so a power cut cannot leave an empty file behind.
        try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(tmp,
                java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
            ch.force(true);
        }
        restrict(tmp, false);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        restrict(file, false);
    }
}
