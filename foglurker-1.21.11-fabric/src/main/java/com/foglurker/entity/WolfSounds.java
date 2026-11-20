package com.foglurker.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;

/** The ordinary wolf's sounds (1.21 gives wolves sound variants; a dog it copies sounds like the classic one). */
public final class WolfSounds {
    private WolfSounds() {
    }

    private static WolfSoundVariant classic() {
        return SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.CLASSIC);
    }

    public static SoundEvent ambient() {
        return classic().ambientSound().value();
    }

    public static SoundEvent whine() {
        return classic().whineSound().value();
    }

    public static SoundEvent growl() {
        return classic().growlSound().value();
    }

    public static SoundEvent pant() {
        return classic().pantSound().value();
    }

    public static SoundEvent hurt() {
        return classic().hurtSound().value();
    }
}
