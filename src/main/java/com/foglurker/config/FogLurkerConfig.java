package com.foglurker.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class FogLurkerConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();
        COMMON = new Common(common);
        COMMON_SPEC = common.build();
        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        CLIENT = new Client(client);
        CLIENT_SPEC = client.build();
    }

    private FogLurkerConfig() {
    }

    public enum ChatVisibility {
        /** Only the player being lured sees the fake chat messages (most convincing). */
        TARGET_ONLY,
        /** Everyone sees them, including the player who is being impersonated. */
        EVERYONE
    }

    public static final class Common {
        public final ForgeConfigSpec.ConfigValue<String> defaultDifficulty;
        public final ForgeConfigSpec.IntValue spawnIntervalMinMinutes;
        public final ForgeConfigSpec.IntValue spawnIntervalMaxMinutes;
        public final ForgeConfigSpec.IntValue encounterMaxMinutes;
        public final ForgeConfigSpec.BooleanValue overworldOnly;
        public final ForgeConfigSpec.BooleanValue allowDoppelganger;
        public final ForgeConfigSpec.EnumValue<ChatVisibility> chatVisibility;
        public final ForgeConfigSpec.BooleanValue learnChat;
        public final ForgeConfigSpec.IntValue minPlayerSeparation;

        public final ForgeConfigSpec.ConfigValue<String> geminiModel;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> geminiFallbackModels;
        public final ForgeConfigSpec.IntValue geminiTimeoutSeconds;

        public final ForgeConfigSpec.IntValue maxSnippetsPerPlayer;
        public final ForgeConfigSpec.DoubleValue recordChance;
        public final ForgeConfigSpec.IntValue voiceDistance;
        public final ForgeConfigSpec.BooleanValue showVoiceNoticeOnJoin;

        Common(ForgeConfigSpec.Builder b) {
            b.push("creature");
            defaultDifficulty = b.comment("Mode used for new worlds: very_easy, easy, medium, hard, deathwish.",
                            "Change it in game with /foglurker difficulty <mode>.")
                    .define("defaultDifficulty", "medium", o -> o instanceof String s && LurkerDifficulty.byId(s) != null);
            spawnIntervalMinMinutes = b.comment("Shortest real-time wait between two appearances (after the grace period).")
                    .defineInRange("spawnIntervalMinMinutes", 7, 1, 600);
            spawnIntervalMaxMinutes = b.comment("Longest real-time wait between two appearances.")
                    .defineInRange("spawnIntervalMaxMinutes", 14, 1, 600);
            encounterMaxMinutes = b.comment("An appearance gives up and fades away after this long.")
                    .defineInRange("encounterMaxMinutes", 5, 1, 60);
            overworldOnly = b.comment("Only appear on its own in the Overworld (/foglurker spawn works everywhere).")
                    .define("overworldOnly", true);
            allowDoppelganger = b.comment("Let it copy the player it is hunting (used when nobody else is online).")
                    .define("allowDoppelganger", true);
            minPlayerSeparation = b.comment("How far (blocks) two players must be apart before it will impersonate one to the other.")
                    .defineInRange("minPlayerSeparation", 48, 16, 512);
            chatVisibility = b.comment("Who sees the creature's fake chat messages.")
                    .defineEnum("chatVisibility", ChatVisibility.TARGET_ONLY);
            learnChat = b.comment("Remember players' recent chat messages so it can copy how they type.")
                    .define("learnChat", true);
            b.pop();

            b.push("gemini");
            geminiModel = b.comment("Gemini model used to write the creature's chat messages.",
                            "Set the API key in game with /foglurker setgeminiapikey <key>.")
                    .define("model", "gemini-flash-latest");
            geminiFallbackModels = b.comment("Tried in order if the main model is unavailable.")
                    .defineList("fallbackModels", List.of("gemini-2.5-flash", "gemini-flash-lite-latest"),
                            o -> o instanceof String s && !s.isBlank());
            geminiTimeoutSeconds = b.defineInRange("timeoutSeconds", 10, 2, 60);
            b.pop();

            b.push("voice");
            maxSnippetsPerPlayer = b.comment("Voice snippets kept per opted-in player (oldest are replaced).")
                    .defineInRange("maxSnippetsPerPlayer", 40, 1, 500);
            recordChance = b.comment("Chance that any one thing an opted-in player says gets saved as a snippet.")
                    .defineInRange("recordChance", 0.35, 0.0, 1.0);
            voiceDistance = b.comment("How far away the creature's stolen voice can be heard.")
                    .defineInRange("voiceDistance", 32, 4, 128);
            showVoiceNoticeOnJoin = b.comment("Tell each player once, on first join, that voice snippets are opt-in.")
                    .define("showVoiceNoticeOnJoin", true);
            b.pop();
        }

        public LurkerDifficulty parsedDefaultDifficulty() {
            LurkerDifficulty d = LurkerDifficulty.byId(defaultDifficulty.get());
            return d == null ? LurkerDifficulty.MEDIUM : d;
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue fogEffects;
        public final ForgeConfigSpec.BooleanValue screenEffects;

        Client(ForgeConfigSpec.Builder b) {
            fogEffects = b.comment("Thick fog rolls in while the creature is hunting you.")
                    .define("fogEffects", true);
            screenEffects = b.comment("Camera shake and vignette when it reveals itself.")
                    .define("screenEffects", true);
        }
    }
}
