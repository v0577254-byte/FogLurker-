package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /**
     * Blocks it will never break: deepslate (all of it), obsidian, bedrock, portals, spawners and so on.
     * Data driven (data/foglurker/tags/block/unbreakable.json), so server owners can add their own.
     */
    public static final TagKey<Block> UNBREAKABLE = TagKey.create(Registries.BLOCK, FogLurkerMod.id("unbreakable"));

    /** Soul fire (torches, lanterns, lit campfires): the attacking form will not come near a player standing by one. */
    public static final TagKey<Block> WARDS = TagKey.create(Registries.BLOCK, FogLurkerMod.id("wards"));

    private ModTags() {
    }
}
