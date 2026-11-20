package com.foglurker.forms;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * Every shape the Fog Lurker can stand in, the same as in the full mod: a copy of a player (their skin), the farm
 * animals, a zombie, a wolf, a tamed dog, or the pale crawler itself. Same hitboxes as the full mod.
 */
public enum Form {
    PLAYER("player", null, 0.6F, 1.8F, 1.62F),
    COW("cow", EntityType.COW, 0.9F, 1.4F, -1F),
    SHEEP("sheep", EntityType.SHEEP, 0.9F, 1.3F, -1F),
    PIG("pig", EntityType.PIG, 0.9F, 0.9F, -1F),
    CHICKEN("chicken", EntityType.CHICKEN, 0.4F, 0.7F, -1F),
    ZOMBIE("zombie", EntityType.ZOMBIE, 0.6F, 1.95F, 1.74F),
    WOLF("wolf", EntityType.WOLF, 0.6F, 0.85F, -1F),
    /** A tamed dog: a wolf with a red collar. */
    DOG("dog", EntityType.WOLF, 0.6F, 0.85F, -1F),
    /** The Fog Lurker itself: pale, long-limbed, on all fours. */
    CRAWLER("crawler", null, 0.9F, 1.3F, 0.95F);

    private final String id;
    private final @Nullable EntityType<?> vanilla;
    private final EntityDimensions dimensions;

    Form(String id, @Nullable EntityType<?> vanilla, float width, float height, float eyeHeight) {
        this.id = id;
        this.vanilla = vanilla;
        this.dimensions = EntityDimensions.scalable(width, height).withEyeHeight(eyeHeight > 0 ? eyeHeight : height * 0.85F);
    }

    public String id() {
        return id;
    }

    /** The vanilla mob it looks like (drawn by that mob's own renderer), or null for a player copy and the crawler. */
    public @Nullable EntityType<?> vanilla() {
        return vanilla;
    }

    public EntityDimensions dimensions() {
        return dimensions;
    }

    public static @Nullable Form byId(String id) {
        String want = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(f -> f.id.equals(want)).findFirst().orElse(null);
    }

    public static Form byOrdinal(int i) {
        return i >= 0 && i < values().length ? values()[i] : CRAWLER;
    }
}
