package com.foglurker.client;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A tiny client for the local Discord RPC socket (the Discord app, or Vesktop with its built-in arRPC): only the
 * handshake and SET_ACTIVITY, which is all Rich Presence needs. Plain JDK, no native libraries. Nothing here
 * ever leaves the computer: it talks only to the Discord app running on it.
 * <p>
 * Not thread-safe: use it from one thread.
 */
public final class DiscordIpc implements Closeable {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;
    /** Discord never sends anything big here; a frame claiming more is garbage. */
    private static final int MAX_FRAME = 1 << 20;

    private interface Pipe extends Closeable {
        void write(byte[] bytes) throws IOException;

        /** @return bytes read, 0 if nothing is waiting, -1 once the other side has closed */
        int read(byte[] buf) throws IOException;
    }

    /** Linux and macOS: a Unix domain socket, read without blocking. */
    private static final class UnixPipe implements Pipe {
        private final SocketChannel channel;

        UnixPipe(Path path) throws IOException {
            channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            try {
                channel.connect(UnixDomainSocketAddress.of(path));
                channel.configureBlocking(false);
            } catch (IOException | RuntimeException e) {
                channel.close();
                throw e;
            }
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            ByteBuffer b = ByteBuffer.wrap(bytes);
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (b.hasRemaining()) {
                if (channel.write(b) == 0) {
                    if (System.nanoTime() > deadline) {
                        throw new IOException("Discord is not reading");
                    }
                    pause(5);
                }
            }
        }

        @Override
        public int read(byte[] buf) throws IOException {
            return channel.read(ByteBuffer.wrap(buf));
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    /** Windows: a named pipe. Only read what is already waiting, so a read never blocks a write. */
    private static final class WindowsPipe implements Pipe {
        private final RandomAccessFile file;

        WindowsPipe(String name) throws IOException {
            file = new RandomAccessFile(name, "rw");
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            file.write(bytes);
        }

        @Override
        public int read(byte[] buf) throws IOException {
            long waiting = file.length(); // on a pipe: the bytes ready to read
            if (waiting <= 0) {
                return 0;
            }
            return file.read(buf, 0, (int) Math.min(waiting, buf.length));
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }

    private record Frame(int op, String json) {
    }

    private final Pipe pipe;
    private byte[] pending = new byte[0];
    private final long pid = ProcessHandle.current().pid();

    private DiscordIpc(Pipe pipe) {
        this.pipe = pipe;
    }

    /**
     * Finds a running Discord and introduces this application to it.
     *
     * @return the connection, or null if no Discord is running (or it refused the application id)
     */
    @Nullable
    public static DiscordIpc connect(String applicationId) {
        for (String where : candidates()) {
            DiscordIpc ipc;
            try {
                ipc = new DiscordIpc(isWindows() ? new WindowsPipe(where) : new UnixPipe(Path.of(where)));
            } catch (IOException | RuntimeException notThere) {
                continue;
            }
            try {
                if (ipc.handshake(applicationId)) {
                    return ipc;
                }
            } catch (IOException | RuntimeException refused) {
                // closed on us (a wrong application id): try the next one, then give up
            }
            ipc.close();
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Where Discord listens: the normal, Flatpak and Snap installs, and Vesktop, sockets 0 to 9. */
    static List<String> candidates() {
        List<String> out = new ArrayList<>();
        if (isWindows()) {
            for (int i = 0; i < 10; i++) {
                out.add("\\\\.\\pipe\\discord-ipc-" + i);
            }
            return out;
        }
        LinkedHashSet<String> bases = new LinkedHashSet<>();
        for (String env : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String v = System.getenv(env);
            if (v != null && !v.isBlank()) {
                bases.add(v);
            }
        }
        bases.add("/tmp");
        String[] subs = {"", "app/com.discordapp.Discord", "app/com.discordapp.DiscordCanary", "snap.discord",
                "app/dev.vencord.Vesktop", ".flatpak/dev.vencord.Vesktop/xdg-run"};
        for (String base : bases) {
            for (String sub : subs) {
                for (int i = 0; i < 10; i++) {
                    try {
                        Path p = sub.isEmpty() ? Path.of(base, "discord-ipc-" + i) : Path.of(base, sub, "discord-ipc-" + i);
                        if (Files.exists(p)) {
                            out.add(p.toString());
                        }
                    } catch (RuntimeException badPath) {
                        // an odd TMPDIR value: skip it
                    }
                }
            }
        }
        return out;
    }

    private boolean handshake(String applicationId) throws IOException {
        send(OP_HANDSHAKE, "{\"v\":1,\"client_id\":\"" + json(applicationId) + "\"}");
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            for (Frame f : poll()) {
                if (f.op() == OP_FRAME && f.json().contains("\"READY\"")) {
                    return true;
                }
            }
            pause(25);
        }
        return false;
    }

    /**
     * Shows this activity under "Playing &lt;application name&gt;", or clears it when {@code details} is null.
     *
     * @param startSeconds when it started (Unix seconds), shown as "elapsed"; 0 for none
     */
    public void setActivity(@Nullable String details, @Nullable String state, long startSeconds) throws IOException {
        poll(); // answers pings and drops Discord's replies to earlier updates
        StringBuilder args = new StringBuilder("{\"pid\":").append(pid);
        if (details != null) {
            args.append(",\"activity\":{\"details\":\"").append(json(details)).append('"');
            if (state != null) {
                args.append(",\"state\":\"").append(json(state)).append('"');
            }
            if (startSeconds > 0) {
                args.append(",\"timestamps\":{\"start\":").append(startSeconds).append('}');
            }
            args.append('}');
        }
        args.append('}');
        send(OP_FRAME, "{\"cmd\":\"SET_ACTIVITY\",\"args\":" + args + ",\"nonce\":\"" + UUID.randomUUID() + "\"}");
    }

    /** Reads whatever Discord sent: answers pings, throws if it closed the connection. */
    private List<Frame> poll() throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = pipe.read(buf)) != 0) {
            if (n < 0) {
                throw new IOException("Discord closed the connection");
            }
            byte[] joined = new byte[pending.length + n];
            System.arraycopy(pending, 0, joined, 0, pending.length);
            System.arraycopy(buf, 0, joined, pending.length, n);
            pending = joined;
        }
        List<Frame> frames = new ArrayList<>();
        while (pending.length >= 8) {
            ByteBuffer head = ByteBuffer.wrap(pending, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
            int op = head.getInt();
            int len = head.getInt();
            if (len < 0 || len > MAX_FRAME) {
                throw new IOException("garbled frame from Discord");
            }
            if (pending.length < 8 + len) {
                break; // the rest is still on its way
            }
            String body = new String(pending, 8, len, StandardCharsets.UTF_8);
            byte[] rest = new byte[pending.length - 8 - len];
            System.arraycopy(pending, 8 + len, rest, 0, rest.length);
            pending = rest;
            if (op == OP_PING) {
                send(OP_PONG, body);
            } else if (op == OP_CLOSE) {
                throw new IOException("Discord refused the connection");
            } else {
                frames.add(new Frame(op, body));
            }
        }
        return frames;
    }

    private void send(int op, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(op).putInt(body.length).put(body);
        pipe.write(b.array());
    }

    /** Escapes text for a JSON string. */
    static String json(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static void pause(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    @Override
    public void close() {
        try {
            pipe.close();
        } catch (IOException ignored) {
            // already gone
        }
    }
}
