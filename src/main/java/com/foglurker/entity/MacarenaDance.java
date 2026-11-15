package com.foglurker.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Timing for {@code /foglurker-macarena}: 16 counts per loop at 100 BPM (12 ticks a count).
 * Counts: 0-1 arms out palm down, 2-3 palms up, 4-5 hands to shoulders, 6-7 behind the head,
 * 8-9 to the hips, 10-11 to the back pockets, 12-14 hip wiggle, 15 hop and quarter turn.
 * The beat is an original note-block groove, not the song.
 */
public final class MacarenaDance {
    public static final int TICKS_PER_COUNT = 12;
    public static final int COUNTS = 16;
    public static final int CYCLE_TICKS = TICKS_PER_COUNT * COUNTS;
    /** Bass note pitch for each count (an original pattern). */
    private static final float[] BASS = {1.0F, 1.0F, 1.19F, 1.0F, 0.89F, 0.89F, 1.0F, 1.33F,
            1.0F, 1.0F, 1.19F, 1.0F, 0.84F, 0.89F, 1.0F, 1.5F};

    private MacarenaDance() {
    }

    public static int count(int danceTick) {
        return (danceTick / TICKS_PER_COUNT) % COUNTS;
    }

    /** Plays the groove for this tick of the dance. */
    public static void beat(FogLurkerEntity e, int danceTick) {
        int inCount = danceTick % TICKS_PER_COUNT;
        int count = count(danceTick);
        if (inCount == 0) {
            e.playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.9F, 1.0F);
            e.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8F, BASS[count]);
            if (count % 4 == 2) {
                e.playSound(SoundEvents.NOTE_BLOCK_SNARE.value(), 0.7F, 1.0F);
            }
        } else if (inCount == TICKS_PER_COUNT / 2) {
            SoundEvent hat = SoundEvents.NOTE_BLOCK_HAT.value();
            e.playSound(hat, 0.5F, 1.2F);
        }
    }
}
