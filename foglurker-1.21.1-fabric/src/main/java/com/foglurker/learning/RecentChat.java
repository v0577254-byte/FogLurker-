package com.foglurker.learning;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** The last few lines of server chat, used as context when writing fake messages. Never saved. */
public final class RecentChat {
    private static final int MAX = 16;
    private static final Deque<String> LINES = new ArrayDeque<>();

    private RecentChat() {
    }

    public static synchronized void add(String name, String text) {
        LINES.addLast("<" + name + "> " + text);
        while (LINES.size() > MAX) {
            LINES.removeFirst();
        }
    }

    public static synchronized List<String> last(int n) {
        List<String> all = new ArrayList<>(LINES);
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    /** Drops everything one player said (they turned chat learning off). */
    public static synchronized void forget(String name) {
        String prefix = "<" + name + "> ";
        LINES.removeIf(l -> l.startsWith(prefix));
    }

    public static synchronized void clear() {
        LINES.clear();
    }
}
