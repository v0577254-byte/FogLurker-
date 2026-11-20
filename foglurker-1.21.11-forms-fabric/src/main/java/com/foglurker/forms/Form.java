package com.foglurker.forms;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * Every shape the figure can stand in: a copy of a player (their skin), the pale crawler itself, or a vanilla mob
 * (villagers in every job, the wandering trader, animals, horses, golems and hostile mobs). Bosses (the Wither, the
 * Ender Dragon, the Elder Guardian, the Warden) and creatures that only live in water are left out. A vanilla shape
 * has that mob's own hitbox and eye height.
 */
public enum Form {
    PLAYER("player", EntityDimensions.scalable(0.6F, 1.8F).withEyeHeight(1.62F)),
    /** The Fog Lurker itself: pale, long-limbed, on all fours. */
    CRAWLER("crawler", EntityDimensions.scalable(0.9F, 1.3F).withEyeHeight(0.95F)),

    // ---- villagers (an unemployed one, every job) and the wandering trader
    VILLAGER("villager", EntityType.VILLAGER),
    ARMORER("armorer", EntityType.VILLAGER),
    BUTCHER("butcher", EntityType.VILLAGER),
    CARTOGRAPHER("cartographer", EntityType.VILLAGER),
    CLERIC("cleric", EntityType.VILLAGER),
    FARMER("farmer", EntityType.VILLAGER),
    FISHERMAN("fisherman", EntityType.VILLAGER),
    FLETCHER("fletcher", EntityType.VILLAGER),
    LEATHERWORKER("leatherworker", EntityType.VILLAGER),
    LIBRARIAN("librarian", EntityType.VILLAGER),
    MASON("mason", EntityType.VILLAGER),
    NITWIT("nitwit", EntityType.VILLAGER),
    SHEPHERD("shepherd", EntityType.VILLAGER),
    TOOLSMITH("toolsmith", EntityType.VILLAGER),
    WEAPONSMITH("weaponsmith", EntityType.VILLAGER),
    WANDERING_TRADER("wandering_trader", EntityType.WANDERING_TRADER),

    // ---- animals
    COW("cow", EntityType.COW),
    MOOSHROOM("mooshroom", EntityType.MOOSHROOM),
    SHEEP("sheep", EntityType.SHEEP),
    PIG("pig", EntityType.PIG),
    CHICKEN("chicken", EntityType.CHICKEN),
    RABBIT("rabbit", EntityType.RABBIT),
    GOAT("goat", EntityType.GOAT),
    WOLF("wolf", EntityType.WOLF),
    /** A tamed dog: a wolf with a red collar. */
    DOG("dog", EntityType.WOLF),
    CAT("cat", EntityType.CAT),
    OCELOT("ocelot", EntityType.OCELOT),
    FOX("fox", EntityType.FOX),
    PANDA("panda", EntityType.PANDA),
    POLAR_BEAR("polar_bear", EntityType.POLAR_BEAR),
    BEE("bee", EntityType.BEE, Flag.WHOLE),
    FROG("frog", EntityType.FROG, Flag.WHOLE),
    TURTLE("turtle", EntityType.TURTLE),
    AXOLOTL("axolotl", EntityType.AXOLOTL),
    ARMADILLO("armadillo", EntityType.ARMADILLO),
    SNIFFER("sniffer", EntityType.SNIFFER),
    PARROT("parrot", EntityType.PARROT),
    BAT("bat", EntityType.BAT),
    STRIDER("strider", EntityType.STRIDER),
    ALLAY("allay", EntityType.ALLAY),
    HAPPY_GHAST("happy_ghast", EntityType.HAPPY_GHAST, Flag.WHOLE),

    // ---- horses and other mounts
    HORSE("horse", EntityType.HORSE),
    DONKEY("donkey", EntityType.DONKEY),
    MULE("mule", EntityType.MULE),
    SKELETON_HORSE("skeleton_horse", EntityType.SKELETON_HORSE),
    ZOMBIE_HORSE("zombie_horse", EntityType.ZOMBIE_HORSE),
    LLAMA("llama", EntityType.LLAMA),
    TRADER_LLAMA("trader_llama", EntityType.TRADER_LLAMA),
    CAMEL("camel", EntityType.CAMEL),
    CAMEL_HUSK("camel_husk", EntityType.CAMEL_HUSK),

    // ---- golems
    IRON_GOLEM("iron_golem", EntityType.IRON_GOLEM),
    SNOW_GOLEM("snow_golem", EntityType.SNOW_GOLEM),
    COPPER_GOLEM("copper_golem", EntityType.COPPER_GOLEM),

    // ---- hostile mobs
    ZOMBIE("zombie", EntityType.ZOMBIE),
    HUSK("husk", EntityType.HUSK),
    DROWNED("drowned", EntityType.DROWNED),
    ZOMBIE_VILLAGER("zombie_villager", EntityType.ZOMBIE_VILLAGER),
    SKELETON("skeleton", EntityType.SKELETON),
    STRAY("stray", EntityType.STRAY),
    BOGGED("bogged", EntityType.BOGGED),
    PARCHED("parched", EntityType.PARCHED),
    WITHER_SKELETON("wither_skeleton", EntityType.WITHER_SKELETON),
    CREEPER("creeper", EntityType.CREEPER),
    SPIDER("spider", EntityType.SPIDER),
    CAVE_SPIDER("cave_spider", EntityType.CAVE_SPIDER),
    ENDERMAN("enderman", EntityType.ENDERMAN),
    ENDERMITE("endermite", EntityType.ENDERMITE, Flag.WHOLE),
    SILVERFISH("silverfish", EntityType.SILVERFISH, Flag.WHOLE),
    WITCH("witch", EntityType.WITCH),
    SLIME("slime", EntityType.SLIME, Flag.WHOLE_BIG),
    MAGMA_CUBE("magma_cube", EntityType.MAGMA_CUBE, Flag.WHOLE_BIG),
    BLAZE("blaze", EntityType.BLAZE),
    GHAST("ghast", EntityType.GHAST, Flag.WHOLE),
    PHANTOM("phantom", EntityType.PHANTOM, Flag.WHOLE),
    BREEZE("breeze", EntityType.BREEZE, Flag.WHOLE),
    GUARDIAN("guardian", EntityType.GUARDIAN),
    SHULKER("shulker", EntityType.SHULKER),
    CREAKING("creaking", EntityType.CREAKING),
    PILLAGER("pillager", EntityType.PILLAGER),
    VINDICATOR("vindicator", EntityType.VINDICATOR),
    EVOKER("evoker", EntityType.EVOKER),
    VEX("vex", EntityType.VEX),
    RAVAGER("ravager", EntityType.RAVAGER),
    PIGLIN("piglin", EntityType.PIGLIN),
    PIGLIN_BRUTE("piglin_brute", EntityType.PIGLIN_BRUTE),
    ZOMBIFIED_PIGLIN("zombified_piglin", EntityType.ZOMBIFIED_PIGLIN),
    HOGLIN("hoglin", EntityType.HOGLIN),
    ZOGLIN("zoglin", EntityType.ZOGLIN);

    /** How a vanilla shape differs from the mob's defaults. */
    private enum Flag {
        NONE,
        /** No separate head (its face is its body): the whole thing turns to look at you. */
        WHOLE,
        /** Like WHOLE, and the big size (a slime or magma cube of size 4, as tall as a door). */
        WHOLE_BIG
    }

    /** The size a big slime or magma cube has. */
    public static final int BIG_SLIME_SIZE = 4;

    private final String id;
    private final @Nullable EntityType<?> vanilla;
    private final EntityDimensions dimensions;
    private final boolean turnsWhole;

    /** A player copy or the crawler (drawn by this mod). */
    Form(String id, EntityDimensions dimensions) {
        this.id = id;
        this.vanilla = null;
        this.dimensions = dimensions;
        this.turnsWhole = false;
    }

    Form(String id, EntityType<?> vanilla) {
        this(id, vanilla, Flag.NONE);
    }

    Form(String id, EntityType<?> vanilla, Flag flag) {
        this.id = id;
        this.vanilla = vanilla;
        EntityDimensions d = vanilla.getDimensions();
        this.dimensions = flag == Flag.WHOLE_BIG ? d.scale(BIG_SLIME_SIZE) : d;
        this.turnsWhole = flag != Flag.NONE;
    }

    public String id() {
        return id;
    }

    /** What chat calls it: "an iron golem", "a cleric". */
    public String described() {
        String name = id.replace('_', ' ');
        return ("aeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name;
    }

    /** The vanilla mob it looks like (drawn by that mob's own renderer), or null for a player copy and the crawler. */
    public @Nullable EntityType<?> vanilla() {
        return vanilla;
    }

    public EntityDimensions dimensions() {
        return dimensions;
    }

    /** It has no separate head, so the whole thing turns to look at you (everything else turns only its head). */
    public boolean turnsWhole() {
        return turnsWhole;
    }

    public static @Nullable Form byId(String id) {
        String want = id.toLowerCase(Locale.ROOT);
        if (want.equals("merchant") || want.equals("trader")) {
            return WANDERING_TRADER;
        }
        return Arrays.stream(values()).filter(f -> f.id.equals(want)).findFirst().orElse(null);
    }

    public static Form byOrdinal(int i) {
        return i >= 0 && i < values().length ? values()[i] : CRAWLER;
    }
}
