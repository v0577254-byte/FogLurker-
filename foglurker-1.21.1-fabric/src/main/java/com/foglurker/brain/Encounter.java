package com.foglurker.brain;

import com.foglurker.config.LurkerDifficulty;
import com.foglurker.entity.Disguise;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * The plan for one appearance.
 *
 * @param copiedId   the player whose face/voice it wears (MIMIC, DOPPELGANGER), or the dog owner (DOG)
 * @param copiedName name it chats under / shows above its head
 * @param variant    sheep wool colour or dog collar colour
 * @param dogName    the copied dog's name tag, if any
 */
public record Encounter(UUID targetId, LurkerBrain.Mode mode, Disguise disguise, @Nullable UUID copiedId,
                        @Nullable String copiedName, boolean willAttack, LurkerDifficulty difficulty, int variant,
                        @Nullable String dogName) {
}
