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
        public final ForgeConfigSpec.IntValue spawnRollMinutes;
        public final ForgeConfigSpec.DoubleValue spawnChance;
        public final ForgeConfigSpec.BooleanValue breakBlocks;
        public final ForgeConfigSpec.BooleanValue hauntings;
        public final ForgeConfigSpec.IntValue hauntingMinSeconds;
        public final ForgeConfigSpec.IntValue hauntingMaxSeconds;
        public final ForgeConfigSpec.BooleanValue sabotage;
        public final ForgeConfigSpec.IntValue wardRadius;
        public final ForgeConfigSpec.BooleanValue eyeContactRule;
        public final ForgeConfigSpec.IntValue encounterMaxMinutes;
        public final ForgeConfigSpec.BooleanValue overworldOnly;
        public final ForgeConfigSpec.BooleanValue allowDoppelganger;
        public final ForgeConfigSpec.EnumValue<ChatVisibility> chatVisibility;
        public final ForgeConfigSpec.BooleanValue learnChat;
        public final ForgeConfigSpec.IntValue minPlayerSeparation;
        public final ForgeConfigSpec.BooleanValue impersonateInChat;
        public final ForgeConfigSpec.IntValue bellCooldownMinutes;
        public final ForgeConfigSpec.BooleanValue escapeEscalation;
        public final ForgeConfigSpec.IntValue maxConcurrent;

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
            spawnRollMinutes = b.comment("After the grace period it rolls whether to appear every this many minutes.",
                            "A roll that fails makes the next roll a guaranteed appearance.")
                    .defineInRange("spawnRollMinutes", 5, 1, 600);
            spawnChance = b.comment("Chance that a normal roll makes it appear (0.5 = 50/50).")
                    .defineInRange("spawnChance", 0.5, 0.0, 1.0);
            breakBlocks = b.comment("Let its attacking form break blocks to get to you (also needs the mobGriefing gamerule).",
                            "It never breaks anything in the foglurker:unbreakable block tag (deepslate, obsidian, bedrock...),",
                            "blocks as hard as obsidian or harder, or anything with an inventory or other block data.")
                    .define("breakBlocks", true);
            hauntings = b.comment("Small scares between appearances (after the grace period): sounds only you can hear,",
                            "something at the edge of your vision, a torch going out, a message from a friend who is not online,",
                            "a book left in one of your chests.")
                    .define("hauntings", true);
            hauntingMinSeconds = b.defineInRange("hauntingMinSeconds", 90, 10, 36000);
            hauntingMaxSeconds = b.defineInRange("hauntingMaxSeconds", 240, 10, 36000);
            sabotage = b.comment("Let hauntings change the world a little: put out a torch behind you (the torch drops as an item,",
                            "needs the mobGriefing gamerule) and leave a book in a chest at your base.")
                    .define("sabotage", true);
            wardRadius = b.comment("A soul torch, soul lantern or lit soul campfire protects players within this many blocks",
                            "from the attacking form (0 turns wards off).")
                    .defineInRange("wardRadius", 6, 0, 16);
            eyeContactRule = b.comment("While it hunts you: looking at it makes it charge faster, looking away slows it down.")
                    .define("eyeContactRule", true);
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
            impersonateInChat = b.comment("Let it send fake chat messages as a player (the mimic's lines and the 'friend who is not online' haunting).",
                            "Turn this off for servers where fake player chat is not okay; it still copies skins, voices and movement.")
                    .define("impersonateInChat", true);
            bellCooldownMinutes = b.comment("After a bell drives it off a player, bells do not scare it away from that player again",
                            "for this many minutes (it learns). 0 = bells always work.")
                    .defineInRange("bellCooldownMinutes", 15, 0, 240);
            escapeEscalation = b.comment("Every time you escape it (water, soul fire, a bell) it gets 10% more likely to attack you next time,",
                            "up to +30%. Landing a hit on you resets it.")
                    .define("escapeEscalation", true);
            maxConcurrent = b.comment("How many players it can be after at the same time on a multiplayer server.",
                            "It never goes after the same player twice at once.")
                    .defineInRange("maxConcurrent", 2, 1, 8);
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
