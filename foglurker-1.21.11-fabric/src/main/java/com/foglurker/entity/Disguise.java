package com.foglurker.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import org.jetbrains.annotations.Nullable;

/**
 * Every shape the creature can take. CRAWLER is the Fog Lurker itself: the pale thing on all fours
 * that watches from the fog and attacks. Everything else is a stolen shape.
 *
 * <p>The order is saved with the world (by position), so new shapes are only ever added at the end.</p>
 */
public enum Disguise {
    PLAYER("player", null, 0.6F, 1.8F, Group.PLAYER),
    COW("cow", EntityType.COW, 0.9F, 1.4F, Group.ANIMAL),
    SHEEP("sheep", EntityType.SHEEP, 0.9F, 1.3F, Group.ANIMAL),
    PIG("pig", EntityType.PIG, 0.9F, 0.9F, Group.ANIMAL),
    CHICKEN("chicken", EntityType.CHICKEN, 0.4F, 0.7F, Group.ANIMAL),
    ZOMBIE("zombie", EntityType.ZOMBIE, 0.6F, 1.95F, Group.HOSTILE),
    WOLF("wolf", EntityType.WOLF, 0.6F, 0.85F, Group.ANIMAL),
    /** A copy of a specific player's tamed wolf: same collar colour, same name. */
    DOG("dog", EntityType.WOLF, 0.6F, 0.85F, Group.ANIMAL),
    /** The attacking form: pale, long-limbed, on all fours. It cannot swim. */
    CRAWLER("crawler", null, 0.9F, 1.3F, Group.TRUE_FORM),

    // ---- villagers: in a village it copies one of them (any job, dressed for the biome)
    VILLAGER("villager", EntityType.VILLAGER, 0.6F, 1.95F, Group.VILLAGER,
            SoundEvents.VILLAGER_AMBIENT, SoundEvents.VILLAGER_HURT, null),
    WANDERING_TRADER("wandering_trader", EntityType.WANDERING_TRADER, 0.6F, 1.95F, Group.VILLAGER,
            SoundEvents.WANDERING_TRADER_AMBIENT, SoundEvents.WANDERING_TRADER_HURT, null),

    // ---- more animals and mounts
    HORSE("horse", EntityType.HORSE, 1.3964844F, 1.6F, Group.ANIMAL,
            SoundEvents.HORSE_AMBIENT, SoundEvents.HORSE_HURT, SoundEvents.HORSE_STEP),
    DONKEY("donkey", EntityType.DONKEY, 1.3964844F, 1.5F, Group.ANIMAL,
            SoundEvents.DONKEY_AMBIENT, SoundEvents.DONKEY_HURT, SoundEvents.HORSE_STEP),
    LLAMA("llama", EntityType.LLAMA, 0.9F, 1.87F, Group.ANIMAL,
            SoundEvents.LLAMA_AMBIENT, SoundEvents.LLAMA_HURT, SoundEvents.LLAMA_STEP),
    GOAT("goat", EntityType.GOAT, 0.9F, 1.3F, Group.ANIMAL,
            SoundEvents.GOAT_AMBIENT, SoundEvents.GOAT_HURT, SoundEvents.GOAT_STEP),
    RABBIT("rabbit", EntityType.RABBIT, 0.4F, 0.5F, Group.ANIMAL,
            SoundEvents.RABBIT_AMBIENT, SoundEvents.RABBIT_HURT, null),
    FOX("fox", EntityType.FOX, 0.6F, 0.7F, Group.ANIMAL,
            SoundEvents.FOX_AMBIENT, SoundEvents.FOX_HURT, null),
    CAT("cat", EntityType.CAT, 0.6F, 0.7F, Group.ANIMAL,
            SoundEvents.CAT_AMBIENT, SoundEvents.CAT_HURT, null),
    MOOSHROOM("mooshroom", EntityType.MOOSHROOM, 0.9F, 1.4F, Group.ANIMAL,
            SoundEvents.COW_AMBIENT, SoundEvents.COW_HURT, SoundEvents.COW_STEP),

    // ---- hostile mobs: in the dark it can be any of these (husks in the desert, strays where it snows)
    SKELETON("skeleton", EntityType.SKELETON, 0.6F, 1.99F, Group.HOSTILE,
            SoundEvents.SKELETON_AMBIENT, SoundEvents.SKELETON_HURT, SoundEvents.SKELETON_STEP),
    /** Creepers make no sound at all until it is too late. */
    CREEPER("creeper", EntityType.CREEPER, 0.6F, 1.7F, Group.HOSTILE,
            null, SoundEvents.CREEPER_HURT, null),
    SPIDER("spider", EntityType.SPIDER, 1.4F, 0.9F, Group.HOSTILE,
            SoundEvents.SPIDER_AMBIENT, SoundEvents.SPIDER_HURT, SoundEvents.SPIDER_STEP),
    HUSK("husk", EntityType.HUSK, 0.6F, 1.95F, Group.HOSTILE,
            SoundEvents.HUSK_AMBIENT, SoundEvents.HUSK_HURT, SoundEvents.HUSK_STEP),
    STRAY("stray", EntityType.STRAY, 0.6F, 1.99F, Group.HOSTILE,
            SoundEvents.STRAY_AMBIENT, SoundEvents.STRAY_HURT, SoundEvents.STRAY_STEP),
    ENDERMAN("enderman", EntityType.ENDERMAN, 0.6F, 2.9F, Group.HOSTILE,
            SoundEvents.ENDERMAN_AMBIENT, SoundEvents.ENDERMAN_HURT, null),
    WITCH("witch", EntityType.WITCH, 0.6F, 1.95F, Group.HOSTILE,
            SoundEvents.WITCH_AMBIENT, SoundEvents.WITCH_HURT, null),
    PILLAGER("pillager", EntityType.PILLAGER, 0.6F, 1.95F, Group.HOSTILE,
            SoundEvents.PILLAGER_AMBIENT, SoundEvents.PILLAGER_HURT, null);

    /** What kind of thing a shape is: decides how it moves and which encounters can use it. */
    public enum Group {
        /** A copy of a player. */
        PLAYER,
        /** A passive animal. */
        ANIMAL,
        /** A villager or wandering trader: passive, walks like one. */
        VILLAGER,
        /** A hostile mob: it stalks you in the dark like one. */
        HOSTILE,
        /** The Fog Lurker itself. */
        TRUE_FORM
    }

    /** The jobs a villager copy can have (its variant is the position in this list). */
    @SuppressWarnings("unchecked")
    public static final ResourceKey<VillagerProfession>[] VILLAGER_JOBS = new ResourceKey[]{
            VillagerProfession.NONE, VillagerProfession.ARMORER, VillagerProfession.BUTCHER, VillagerProfession.CARTOGRAPHER,
            VillagerProfession.CLERIC, VillagerProfession.FARMER, VillagerProfession.FISHERMAN, VillagerProfession.FLETCHER,
            VillagerProfession.LEATHERWORKER, VillagerProfession.LIBRARIAN, VillagerProfession.MASON, VillagerProfession.NITWIT,
            VillagerProfession.SHEPHERD, VillagerProfession.TOOLSMITH, VillagerProfession.WEAPONSMITH};

    private final String id;
    @Nullable
    private final EntityType<?> renderType;
    private final EntityDimensions dimensions;
    private final Group group;
    @Nullable
    private final SoundEvent ambient;
    @Nullable
    private final SoundEvent hurt;
    @Nullable
    private final SoundEvent step;

    Disguise(String id, @Nullable EntityType<?> renderType, float width, float height, Group group) {
        this(id, renderType, width, height, group, null, null, null);
    }

    Disguise(String id, @Nullable EntityType<?> renderType, float width, float height, Group group,
             @Nullable SoundEvent ambient, @Nullable SoundEvent hurt, @Nullable SoundEvent step) {
        this.id = id;
        this.renderType = renderType;
        this.dimensions = EntityDimensions.scalable(width, height);
        this.group = group;
        this.ambient = ambient;
        this.hurt = hurt;
        this.step = step;
    }

    public String id() {
        return id;
    }

    @Nullable
    public EntityType<?> renderType() {
        return renderType;
    }

    public EntityDimensions dimensions() {
        return dimensions;
    }

    public Group group() {
        return group;
    }

    /** Idle sound of the shapes added later (the first ones have theirs in FogLurkerEntity). */
    @Nullable
    public SoundEvent ambientSound() {
        return ambient;
    }

    @Nullable
    public SoundEvent hurtSound() {
        return hurt;
    }

    @Nullable
    public SoundEvent stepSound() {
        return step;
    }

    public boolean isAnimal() {
        return group == Group.ANIMAL;
    }

    /** Animals, villagers and traders: slow walkers that keep out of water. */
    public boolean isPassive() {
        return group == Group.ANIMAL || group == Group.VILLAGER;
    }

    public boolean isHostile() {
        return group == Group.HOSTILE;
    }

    public boolean isHumanoid() {
        return switch (this) {
            case PLAYER, ZOMBIE, VILLAGER, WANDERING_TRADER, SKELETON, HUSK, STRAY, WITCH, PILLAGER, ENDERMAN -> true;
            default -> false;
        };
    }

    /** The attacking form: water is its weakness. Every other form can swim. */
    public boolean isAttackForm() {
        return this == CRAWLER;
    }

    /** Key used for learned animal behaviour. */
    public String learnKey() {
        return id;
    }

    public static Disguise byId(int ordinal) {
        Disguise[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : CRAWLER;
    }

    @Nullable
    public static Disguise byName(String name) {
        for (Disguise d : values()) {
            if (d.id.equalsIgnoreCase(name) || d.name().equalsIgnoreCase(name)) {
                return d;
            }
        }
        if (name.equalsIgnoreCase("true_form") || name.equalsIgnoreCase("watcher") || name.equalsIgnoreCase("fog_lurker")) {
            return CRAWLER;
        }
        if (name.equalsIgnoreCase("merchant") || name.equalsIgnoreCase("trader")) {
            return WANDERING_TRADER;
        }
        return null;
    }

    /** Which disguise a real entity corresponds to (for camouflage and learning), or null. */
    @Nullable
    public static Disguise of(Entity e) {
        EntityType<?> t = e.getType();
        if (t == EntityType.WOLF) {
            return e instanceof Wolf w && w.isTame() ? DOG : WOLF;
        }
        if (t == EntityType.TRADER_LLAMA) {
            return LLAMA;
        }
        for (Disguise d : values()) {
            if (d.renderType == t) {
                return d;
            }
        }
        return null;
    }
}
