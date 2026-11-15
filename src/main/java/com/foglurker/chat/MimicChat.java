package com.foglurker.chat;

import com.foglurker.learning.PlayerBehaviorProfile;
import com.foglurker.learning.RecentChat;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Writes what the creature "says" in chat while wearing someone's face. Uses Gemini when an API key
 * is set (so it can actually hold a conversation), and a style-copying template system otherwise.
 */
public final class MimicChat {

    public enum Intent {
        GREET, LURE, WAIT, IMPATIENT, ARRIVED, REPLY, CREEPY
    }

    /** Everything needed to write one message. Built on the server thread. */
    public record Context(String copiedName, String targetName, @Nullable PlayerBehaviorProfile copiedProfile,
                          String woodsDirection, boolean night, List<String> alreadySaid) {
    }

    private static final String[] GREET = {"hey", "yo", "hi", "there you are", "oh hey", "yo {t}", "hey {t}", "sup"};
    private static final String[] LURE = {"come here", "follow me", "i found something", "come look at this",
            "this way", "you gotta see this", "i found diamonds over here", "theres a cave over here",
            "come to the woods", "over here", "i found a village", "theres something weird in the forest",
            "come with me", "found a really cool spot", "i need help with something over here"};
    private static final String[] WAIT = {"come on", "this way", "keep following", "almost there", "over here",
            "follow me", "its just past the trees", "hurry"};
    private static final String[] IMPATIENT = {"?", "come on", "hurry up", "why are you not coming", "{t}",
            "{t} come here", "are you coming", "bro come on", "pls just follow me", "hello?"};
    private static final String[] ARRIVED = {"its right here", "look", "behind the trees", "closer",
            "come closer", "right here", "look at this", "just a bit further"};
    private static final String[] REPLY_WHERE = {"here", "by the trees", "just follow me", "in the forest",
            "{d} of you", "over here in the woods"};
    private static final String[] REPLY_DOUBT = {"what? its me", "lol what", "its me", "?? just come", "what are you on about",
            "huh", "its literally me"};
    private static final String[] REPLY_WHY = {"just come", "you need to see this", "trust me", "ill show you",
            "its hard to explain", "youll see"};
    private static final String[] REPLY_NO = {"pls", "come on", "its important", "please", "just for a sec", "pls come"};
    private static final String[] REPLY_YES = {"ok", "hurry", "this way", "follow me", "ok come", "good"};
    private static final String[] REPLY_OTHER = {"yeah", "come here", "just follow me", "lol", "come", "yea", "ok but come here"};
    private static final String[] CREEPY = {"{t}", "you came", "{t} {t} {t}", "why did you follow me", "i am right behind you",
            "it is so dark here", "come closer {t}", "you shouldnt have come"};
    private static final String[] LURE_WORDS = {"come", "here", "look", "follow", "wait", "hurry", "found", "omg",
            "quick", "check"};

    private MimicChat() {
    }

    /**
     * @return a future that completes (possibly off-thread) with the message to send.
     */
    public static CompletableFuture<String> compose(Context ctx, Intent intent, @Nullable String heard, RandomSource random) {
        String fallback = template(ctx, intent, heard, random);
        if (!GeminiClient.available() || intent == Intent.CREEPY && random.nextFloat() < 0.5F) {
            return CompletableFuture.completedFuture(fallback);
        }
        String system = systemPrompt(ctx);
        String user = userPrompt(ctx, intent, heard);
        return GeminiClient.generate(system, user, 1.0F)
                .thenApply(r -> r.text().map(t -> sanitize(t, ctx)).filter(t -> !t.isEmpty()).orElse(fallback))
                .exceptionally(t -> fallback);
    }

    private static String systemPrompt(Context ctx) {
        return "You write exactly one Minecraft multiplayer chat message. You are secretly a shapeshifting creature "
                + "impersonating the player \"" + ctx.copiedName() + "\" to lure the player \"" + ctx.targetName()
                + "\" into the woods. Never admit you are not " + ctx.copiedName() + " and never mention AI, bots or "
                + "being a creature. Copy " + ctx.copiedName() + "'s typing style exactly: capitalisation, punctuation, "
                + "slang, emoticons, typos and message length. Keep it short, usually under 8 words. "
                + "Output only the message text: no quotes, no name prefix, no explanations.";
    }

    private static String userPrompt(Context ctx, Intent intent, @Nullable String heard) {
        StringBuilder sb = new StringBuilder();
        List<String> samples = ctx.copiedProfile() == null ? List.of() : ctx.copiedProfile().recentChat(15);
        if (!samples.isEmpty()) {
            sb.append("Real messages ").append(ctx.copiedName()).append(" has typed before:\n");
            for (String s : samples) {
                sb.append("- ").append(s).append('\n');
            }
        } else {
            sb.append(ctx.copiedName()).append(" has not chatted much; type like a casual teenage Minecraft player "
                    + "(lowercase, little punctuation).\n");
        }
        List<String> recent = RecentChat.last(8);
        if (!recent.isEmpty()) {
            sb.append("\nRecent server chat:\n");
            for (String s : recent) {
                sb.append(s).append('\n');
            }
        }
        if (!ctx.alreadySaid().isEmpty()) {
            sb.append("\nYou already said (do not repeat): ").append(String.join(" | ", ctx.alreadySaid())).append('\n');
        }
        sb.append("\nThe woods are to the ").append(ctx.woodsDirection()).append(" of ").append(ctx.targetName())
                .append(". It is ").append(ctx.night() ? "night" : "daytime").append(".\nSituation: ");
        String t = ctx.targetName();
        switch (intent) {
            case GREET -> sb.append("You just walked up to ").append(t).append(". Greet them casually.");
            case LURE -> sb.append("Convince ").append(t).append(" to follow you toward the woods. Give a believable "
                    + "reason (something cool you found, a cave, diamonds, a village, a weird structure).");
            case WAIT -> sb.append(t).append(" is following but lagging behind. Tell them to keep coming.");
            case IMPATIENT -> sb.append(t).append(" stopped following you. Get them to come, a bit impatient.");
            case ARRIVED -> sb.append("You are in the woods and ").append(t)
                    .append(" is close. Get them to come the last few steps.");
            case REPLY -> sb.append(t).append(" just said something to you. Answer naturally and keep luring them.");
            case CREEPY -> sb.append("Your disguise is slipping. Write something short and unsettling, still in the same style.");
        }
        if (heard != null) {
            sb.append("\n").append(t).append(" just said: \"").append(heard.replace('"', '\'')).append("\"");
        }
        sb.append("\nWrite ").append(ctx.copiedName()).append("'s next chat message.");
        return sb.toString();
    }

    public static String sanitize(String raw, Context ctx) {
        String s = raw.replace('\r', '\n').strip();
        int nl = s.indexOf('\n');
        if (nl >= 0) {
            s = s.substring(0, nl).strip();
        }
        s = s.replaceAll("§.", "").replace("§", "");
        String lowerName = ctx.copiedName().toLowerCase(Locale.ROOT);
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("<" + lowerName + ">")) {
            s = s.substring(lowerName.length() + 2).strip();
        } else if (lower.startsWith(lowerName + ":")) {
            s = s.substring(lowerName.length() + 1).strip();
        }
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).strip();
        }
        if (s.length() > 120) {
            s = s.substring(0, 120);
        }
        return s;
    }

    private static String template(Context ctx, Intent intent, @Nullable String heard, RandomSource random) {
        PlayerBehaviorProfile prof = ctx.copiedProfile();
        ChatStyle style = prof == null ? ChatStyle.casualDefault() : ChatStyle.analyze(prof.chatSamples);

        // Sometimes reuse something they really said that fits: the creepiest kind of copying.
        if (prof != null && (intent == Intent.LURE || intent == Intent.WAIT || intent == Intent.IMPATIENT)
                && random.nextFloat() < 0.3F) {
            List<String> fitting = new ArrayList<>();
            for (String s : prof.chatSamples) {
                String l = s.toLowerCase(Locale.ROOT);
                for (String w : LURE_WORDS) {
                    if (l.contains(w) && s.length() < 60 && !ctx.alreadySaid().contains(s)) {
                        fitting.add(s);
                        break;
                    }
                }
            }
            if (!fitting.isEmpty()) {
                return fitting.get(random.nextInt(fitting.size()));
            }
        }

        String[] pool = switch (intent) {
            case GREET -> GREET;
            case LURE -> LURE;
            case WAIT -> WAIT;
            case IMPATIENT -> IMPATIENT;
            case ARRIVED -> ARRIVED;
            case CREEPY -> CREEPY;
            case REPLY -> replyPool(heard);
        };
        String pick = pool[random.nextInt(pool.length)];
        for (int tries = 0; tries < 4 && ctx.alreadySaid().contains(fill(pick, ctx)); tries++) {
            pick = pool[random.nextInt(pool.length)];
        }
        String text = fill(pick, ctx);
        return intent == Intent.CREEPY ? text : style.apply(text, random);
    }

    private static String[] replyPool(@Nullable String heard) {
        if (heard == null) {
            return REPLY_OTHER;
        }
        String h = " " + heard.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9? ]", " ") + " ";
        if (h.contains(" where ")) {
            return REPLY_WHERE;
        }
        if (h.contains(" who ") || h.contains(" real ") || h.contains(" fake ") || h.contains(" bot ")
                || h.contains("not you") || h.contains("are you") || h.contains(" imposter ") || h.contains(" impostor ")) {
            return REPLY_DOUBT;
        }
        if (h.contains(" why ") || h.contains(" what ")) {
            return REPLY_WHY;
        }
        if (h.contains(" no ") || h.contains(" nah ") || h.contains(" not coming ") || h.contains(" busy ")) {
            return REPLY_NO;
        }
        if (h.contains(" ok ") || h.contains(" coming ") || h.contains(" omw ") || h.contains(" sure ")
                || h.contains(" k ") || h.contains(" fine ")) {
            return REPLY_YES;
        }
        return REPLY_OTHER;
    }

    private static String fill(String template, Context ctx) {
        return template.replace("{t}", ctx.targetName()).replace("{d}", ctx.woodsDirection());
    }
}
