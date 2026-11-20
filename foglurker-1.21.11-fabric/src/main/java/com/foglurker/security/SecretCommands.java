package com.foglurker.security;

import java.util.Locale;

/**
 * Typed lines that carry a secret (the Gemini API key in {@code /foglurker setgeminiapikey <key>}). The game keeps
 * what you type so the up arrow can bring it back, and newer versions also write it to {@code command_history.txt}:
 * such a line must never be kept anywhere, or anyone using the computer later could read the key.
 */
public final class SecretCommands {
    private SecretCommands() {
    }

    /** True for any line that sets the key, however it is typed ({@code /execute ... run foglurker setgeminiapikey}). */
    public static boolean isSecret(String typed) {
        return typed != null && typed.toLowerCase(Locale.ROOT).contains("setgeminiapikey");
    }
}
