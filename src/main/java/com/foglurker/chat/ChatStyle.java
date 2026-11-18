package com.foglurker.chat;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Learns how a player types (capitalisation, punctuation, slang, "u" for "you"...) from their real
 * chat messages, and rewrites any sentence so it reads like they typed it.
 */
public final class ChatStyle {
    private static final Set<String> SLANG = Set.of("bro", "lol", "lmao", "lmfao", "dude", "bruh", "ngl", "fr", "man",
            "xd", "omg", "tbh", "idk", "pls", "plz", "rn", "ye", "yea", "yeah", "nah", "haha", "mate", "guys", "fam",
            "yo", "bestie", "dawg", "rly", "srsly", "tho", "lowkey", "deadass", "bud", "buddy", ":)", ":d");

    private final boolean lowercase;
    private final boolean allCaps;
    private final boolean punctuates;
    private final boolean multiBang;
    private final boolean usesU;
    private final boolean usesUr;
    private final boolean dropsApostrophes;
    private final List<String> fillers;
    private final float avgWords;

    private ChatStyle(boolean lowercase, boolean allCaps, boolean punctuates, boolean multiBang, boolean usesU,
                      boolean usesUr, boolean dropsApostrophes, List<String> fillers, float avgWords) {
        this.lowercase = lowercase;
        this.allCaps = allCaps;
        this.punctuates = punctuates;
        this.multiBang = multiBang;
        this.usesU = usesU;
        this.usesUr = usesUr;
        this.dropsApostrophes = dropsApostrophes;
        this.fillers = fillers;
        this.avgWords = avgWords;
    }

    /** Most players type casually; this is what we assume before we have seen them chat. */
    public static ChatStyle casualDefault() {
        return new ChatStyle(true, false, false, false, false, false, true, List.of(), 4F);
    }

    public static ChatStyle analyze(Collection<String> samples) {
        if (samples == null || samples.size() < 3) {
            return casualDefault();
        }
        int lower = 0, caps = 0, punct = 0, bang = 0, u = 0, ur = 0, apostropheWords = 0, droppedApostrophes = 0;
        int words = 0;
        Map<String, Integer> slang = new HashMap<>();
        for (String s : samples) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean hasLetters = trimmed.chars().anyMatch(Character::isLetter);
            if (hasLetters && trimmed.equals(trimmed.toLowerCase(Locale.ROOT))) {
                lower++;
            }
            if (hasLetters && trimmed.length() > 3 && trimmed.equals(trimmed.toUpperCase(Locale.ROOT))) {
                caps++;
            }
            char last = trimmed.charAt(trimmed.length() - 1);
            if (last == '.' || last == '!' || last == '?') {
                punct++;
            }
            if (trimmed.contains("!!") || trimmed.contains("??")) {
                bang++;
            }
            for (String w : trimmed.toLowerCase(Locale.ROOT).split("\\s+")) {
                String bare = w.replaceAll("[^a-z0-9':()]", "");
                if (bare.isEmpty()) {
                    continue;
                }
                words++;
                if (bare.equals("u")) {
                    u++;
                }
                if (bare.equals("ur")) {
                    ur++;
                }
                if (bare.contains("'")) {
                    apostropheWords++;
                }
                if (bare.equals("dont") || bare.equals("im") || bare.equals("its") || bare.equals("cant")
                        || bare.equals("thats") || bare.equals("ill") || bare.equals("youre")) {
                    droppedApostrophes++;
                }
                if (SLANG.contains(bare)) {
                    slang.merge(bare, 1, Integer::sum);
                }
            }
        }
        int n = samples.size();
        List<String> fillers = new ArrayList<>();
        slang.forEach((k, v) -> {
            if (v >= 2) {
                fillers.add(k);
            }
        });
        return new ChatStyle(lower > n * 0.6, caps > n * 0.5, punct > n * 0.5, bang > n * 0.2,
                u >= 2, ur >= 1, droppedApostrophes >= apostropheWords, fillers, Math.max(1F, words / (float) n));
    }

    public float averageWords() {
        return avgWords;
    }

    public String apply(String text, RandomSource random) {
        String s = text.trim();
        if (usesU) {
            s = s.replaceAll("(?i)\\byou\\b", "u");
        }
        if (usesUr) {
            s = s.replaceAll("(?i)\\byour\\b", "ur").replaceAll("(?i)\\byou're\\b", "ur");
        }
        if (dropsApostrophes) {
            s = s.replace("'", "");
        }
        if (!fillers.isEmpty() && s.chars().anyMatch(Character::isLetter) && random.nextFloat() < 0.4F) {
            String f = fillers.get(random.nextInt(fillers.size()));
            if (!s.toLowerCase(Locale.ROOT).contains(f)) {
                s = random.nextBoolean() ? s + " " + f : f + " " + s;
            }
        }
        // Strip or keep sentence punctuation the way they do.
        String core = s.replaceAll("[.!?]+$", "");
        if (punctuates) {
            char end = text.trim().endsWith("?") ? '?' : (multiBang ? '!' : '.');
            s = core + end;
            if (multiBang && end != '.') {
                s += end;
            }
        } else {
            s = text.trim().endsWith("?") && random.nextBoolean() ? core + "?" : core;
        }
        if (allCaps) {
            s = s.toUpperCase(Locale.ROOT);
        } else if (lowercase) {
            s = s.toLowerCase(Locale.ROOT);
        } else if (!s.isEmpty()) {
            s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
        return s;
    }
}
