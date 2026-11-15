package com.foglurker.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;

import javax.annotation.Nullable;

/**
 * Every shape the creature can take. NONE is the tall "watcher" seen far away in the fog; CRAWLER is
 * its pale attacking form. Ordinals are synced to clients, so new shapes go at the end.
 */
public enum Disguise {
    NONE("watcher", null, 0.6F, 1.95F),
    PLAYER("player", null, 0.6F, 1.8F),
    COW("cow", EntityType.COW, 0.9F, 1.4F),
    SHEEP("sheep", EntityType.SHEEP, 0.9F, 1.3F),
    PIG("pig", EntityType.PIG, 0.9F, 0.9F),
    CHICKEN("chicken", EntityType.CHICKEN, 0.4F, 0.7F),
    ZOMBIE("zombie", EntityType.ZOMBIE, 0.6F, 1.95F),
    WOLF("wolf", EntityType.WOLF, 0.6F, 0.85F),
    /** A copy of a specific player's tamed wolf: same collar colour, same name. */
    DOG("dog", EntityType.WOLF, 0.6F, 0.85F),
    /** The attacking form: pale, long-limbed, on all fours. It cannot swim. */
    CRAWLER("crawler", null, 0.9F, 1.3F);

    private final String id;
    @Nullable
    private final EntityType<?> renderType;
    private final EntityDimensions dimensions;

    Disguise(String id, @Nullable EntityType<?> renderType, float width, float height) {
        this.id = id;
        this.renderType = renderType;
        this.dimensions = EntityDimensions.scalable(width, height);
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

    public boolean isAnimal() {
        return this == COW || this == SHEEP || this == PIG || this == CHICKEN || this == WOLF || this == DOG;
    }

    public boolean isHumanoid() {
        return this == PLAYER || this == ZOMBIE || this == NONE;
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
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : NONE;
    }

    @Nullable
    public static Disguise byName(String name) {
        for (Disguise d : values()) {
            if (d.id.equalsIgnoreCase(name) || d.name().equalsIgnoreCase(name)) {
                return d;
            }
        }
        if (name.equalsIgnoreCase("true_form") || name.equalsIgnoreCase("attack_form")) {
            return name.equalsIgnoreCase("true_form") ? NONE : CRAWLER;
        }
        return null;
    }

    /** Which disguise a real entity corresponds to (for camouflage and learning), or null. */
    @Nullable
    public static Disguise of(Entity e) {
        EntityType<?> t = e.getType();
        if (t == EntityType.COW) {
            return COW;
        } else if (t == EntityType.SHEEP) {
            return SHEEP;
        } else if (t == EntityType.PIG) {
            return PIG;
        } else if (t == EntityType.CHICKEN) {
            return CHICKEN;
        } else if (t == EntityType.ZOMBIE) {
            return ZOMBIE;
        } else if (t == EntityType.WOLF) {
            return e instanceof Wolf w && w.isTame() ? DOG : WOLF;
        }
        return null;
    }
}
