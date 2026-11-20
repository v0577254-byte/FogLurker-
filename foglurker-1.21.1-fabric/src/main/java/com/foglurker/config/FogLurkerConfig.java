package com.foglurker.config;


import java.util.List;

public final class FogLurkerConfig {
    public static final ConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ConfigSpec.Builder common = new ConfigSpec.Builder();
        COMMON = new Common(common);
        COMMON_SPEC = common.build();
        ConfigSpec.Builder client = new ConfigSpec.Builder();
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

    /** The config revision this version writes (see {@link #update}). */
    private static final int REVISION = 1;

    /**
     * Brings an older config file up to date, once (call after loading it): a setting still at an old default moves to the
     * new default (a value someone chose stays as it is). Revision 1: spawnRollMinutes 5 -> 10.
     */
    public static void update() {
        if (COMMON.configRevision.get() >= REVISION) {
            return;
        }
        if (COMMON.spawnRollMinutes.get() == 5) {
            COMMON.spawnRollMinutes.set(10);
        }
        COMMON.configRevision.set(REVISION);
        COMMON_SPEC.save();
    }

    public static final class Common {
        public final ConfigSpec.ConfigValue<String> defaultDifficulty;
        public final ConfigSpec.IntValue spawnRollMinutes;
        public final ConfigSpec.DoubleValue spawnChance;
        public final ConfigSpec.BooleanValue breakBlocks;
        public final ConfigSpec.BooleanValue hauntings;
        public final ConfigSpec.IntValue hauntingMinSeconds;
        public final ConfigSpec.IntValue hauntingMaxSeconds;
        public final ConfigSpec.BooleanValue sabotage;
        public final ConfigSpec.IntValue wardRadius;
        public final ConfigSpec.BooleanValue eyeContactRule;
        public final ConfigSpec.IntValue encounterMaxMinutes;
        public final ConfigSpec.BooleanValue overworldOnly;
        public final ConfigSpec.BooleanValue allowDoppelganger;
        public final ConfigSpec.EnumValue<ChatVisibility> chatVisibility;
        public final ConfigSpec.BooleanValue learnChat;
        public final ConfigSpec.IntValue minPlayerSeparation;
        public final ConfigSpec.BooleanValue impersonateInChat;
        public final ConfigSpec.IntValue bellCooldownMinutes;
        public final ConfigSpec.BooleanValue escapeEscalation;
        public final ConfigSpec.IntValue maxConcurrent;
        public final ConfigSpec.BooleanValue afkProtection;
        public final ConfigSpec.IntValue afkMinutes;
        public final ConfigSpec.BooleanValue jumpscares;
        public final ConfigSpec.BooleanValue villagersHide;
        public final ConfigSpec.IntValue configRevision;

        public final ConfigSpec.ConfigValue<String> geminiModel;
        public final ConfigSpec.ConfigValue<List<? extends String>> geminiFallbackModels;
        public final ConfigSpec.IntValue geminiTimeoutSeconds;

        public final ConfigSpec.IntValue maxSnippetsPerPlayer;
        public final ConfigSpec.DoubleValue recordChance;
        public final ConfigSpec.IntValue voiceDistance;
        public final ConfigSpec.BooleanValue showVoiceNoticeOnJoin;

        Common(ConfigSpec.Builder b) {
            b.push("creature");
            defaultDifficulty = b.comment("Mode used for new worlds: very_easy, easy, medium, hard, deathwish.",
                            "Change it in game with /foglurker difficulty <mode>.")
                    .define("defaultDifficulty", "medium", o -> o instanceof String s && LurkerDifficulty.byId(s) != null);
            spawnRollMinutes = b.comment("After the grace period it rolls whether to appear every this many minutes (10 by default:",
                            "time to take in what just happened before it can come again).",
                            "A roll that fails makes the next roll a guaranteed appearance.")
                    .defineInRange("spawnRollMinutes", 10, 1, 600);
            spawnChance = b.comment("Chance that a normal roll makes it appear (0.5 = 50/50). 0 = it never appears on its own",
                            "in any world (hauntings still happen; /foglurker naturalspawning off stops those too).")
                    .defineInRange("spawnChance", 0.5, 0.0, 1.0);
            breakBlocks = b.comment("Let its attacking form break blocks to get to you (also needs the mobGriefing gamerule).",
                            "It never breaks anything in the foglurker:unbreakable block tag (deepslate, obsidian, bedrock...),",
                            "blocks as hard as obsidian or harder, or anything with an inventory or other block data.")
                    .define("breakBlocks", true);
            hauntings = b.comment("Small scares between appearances (after the grace period): sounds only you can hear,",
                            "something at the edge of your vision, a torch going out, a message from a friend who is not online,",
                            "a book left in one of your chests.")
                    .define("hauntings", true);
            jumpscares = b.comment("Random jumpscares among the hauntings (needs hauntings on): the fog suddenly rushes in around you and",
                            "lifts again, a scream right behind you, a roar in the distance, a whisper at your ear, a heartbeat that",
                            "speeds up and stops, something running past behind you. They never hurt you.")
                    .define("jumpscares", true);
            hauntingMinSeconds = b.comment("Seconds between hauntings: a random wait between these two.",
                            "If the maximum is set below the minimum, the minimum is used every time.")
                    .defineInRange("hauntingMinSeconds", 90, 10, 36000);
            hauntingMaxSeconds = b.defineInRange("hauntingMaxSeconds", 240, 10, 36000);
            sabotage = b.comment("Let hauntings change the world a little: put out a torch behind you (the torch drops as an item,",
                            "needs the mobGriefing gamerule and breakBlocks) and leave a book in a chest at your base.")
                    .define("sabotage", true);
            wardRadius = b.comment("A soul torch, soul lantern or lit soul campfire protects players within this many blocks",
                            "from the attacking form (0 turns wards off).")
                    .defineInRange("wardRadius", 6, 0, 16);
            eyeContactRule = b.comment("While it hunts you: looking at it makes it charge faster, looking away slows it down.")
                    .define("eyeContactRule", true);
            encounterMaxMinutes = b.comment("An appearance gives up and fades away after this long (a watcher that only",
                            "stares leaves after 2 minutes at most). Applies from the next appearance.")
                    .defineInRange("encounterMaxMinutes", 5, 1, 60);
            villagersHide = b.comment("Villagers sense it: when it comes within 32 blocks of them (48 while it attacks), in any form, they",
                            "run into their houses and hide, like when a bell rings. So villagers running inside is a warning.")
                    .define("villagersHide", true);
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
            afkProtection = b.comment("Leave AFK players alone: it never comes for or attacks a player who is away from the keyboard.",
                            "Players are AFK after afkMinutes without doing anything, or straight away with /afk.")
                    .define("afkProtection", true);
            afkMinutes = b.comment("Minutes without any activity before a player counts as AFK.")
                    .defineInRange("afkMinutes", 5, 1, 120);
            configRevision = b.comment("Used to update old defaults when the mod is updated. Leave it alone.")
                    .defineInRange("configRevision", 0, 0, 1000);
            b.pop();

            b.push("gemini");
            geminiModel = b.comment("Gemini model used to write the creature's chat messages.",
                            "Set the API key in game with /foglurker setgeminiapikey <key>.")
                    .define("model", "gemini-flash-latest");
            geminiFallbackModels = b.comment("Tried in order if the main model is unavailable.")
                    .defineList("fallbackModels", List.of("gemini-2.5-flash", "gemini-flash-lite-latest"),
                            o -> o instanceof String s && !s.isBlank());
            // (A new name: the old "timeoutSeconds" default of 10 was too short for newer models and would have stayed.)
            geminiTimeoutSeconds = b.comment("How long to wait for one answer from Gemini, in seconds.")
                    .defineInRange("requestTimeoutSeconds", 20, 2, 60);
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
        public final ConfigSpec.BooleanValue fogEffects;
        public final ConfigSpec.BooleanValue screenEffects;
        public final ConfigSpec.BooleanValue fogOverlay;
        public final ConfigSpec.BooleanValue fogParticles;

        Client(ConfigSpec.Builder b) {
            fogEffects = b.comment("Thick fog rolls in while the creature is hunting you.")
                    .define("fogEffects", true);
            screenEffects = b.comment("Camera shake and the dark red flash when it reveals itself, the darkening edges and the",
                            "heartbeat while it hunts you. (The heartbeat on the reveal itself always plays.) The shake also",
                            "follows Minecraft's Distortion Effects slider.")
                    .define("screenEffects", true);
            fogOverlay = b.comment("Also draw the fog as a see-through layer over the screen. This shows even when a shader pack",
                            "or another mod replaces Minecraft's own fog.")
                    .define("fogOverlay", true);
            fogParticles = b.comment("Wisps of fog drifting around you while it hunts you: real fog in the world, which shows",
                            "even with Distant Horizons, Embeddium or a shader pack. Follows Minecraft's Particles setting.")
                    .define("fogParticles", true);
        }
    }
}
