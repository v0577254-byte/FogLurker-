package com.foglurker.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** The ordinary wolf's sounds (on 1.21.1 every wolf sounds the same). */
public final class WolfSounds {
    private WolfSounds() {
    }

    public static SoundEvent ambient() {
        return SoundEvents.WOLF_AMBIENT;
    }

    public static SoundEvent whine() {
        return SoundEvents.WOLF_WHINE;
    }

    public static SoundEvent growl() {
        return SoundEvents.WOLF_GROWL;
    }

    public static SoundEvent pant() {
        return SoundEvents.WOLF_PANT;
    }

    public static SoundEvent hurt() {
        return SoundEvents.WOLF_HURT;
    }
}
