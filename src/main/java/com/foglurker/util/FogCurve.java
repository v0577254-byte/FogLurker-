package com.foglurker.util;

/**
 * How far you can see in the creature's fog, for any render distance from 2 chunks (32 blocks) to 32
 * chunks (512 blocks) and beyond. Pure maths with no client classes, so the game tests can check it.
 *
 * <p>Two limits, and the closer one wins:</p>
 * <ul>
 *   <li>an absolute one, so a long render distance does not push the fog out of sight
 *       (about 118 blocks while it watches, 82 while it stalks, 32 while it lures, 11 when it attacks);</li>
 *   <li>a share of your own render distance, so a short render distance still gets visibly foggier
 *       at every stage (about 80% / 63% / 33% / 15% of your view).</li>
 * </ul>
 */
public final class FogCurve {
    /** Even the thickest fog leaves you this much (or your whole view, if that is shorter). */
    public static final float MIN_VIEW = 7.0F;
    /** Below this fog level it eases in from the normal view so it never pops. */
    private static final float EASE_UNTIL = 0.12F;

    private FogCurve() {
    }

    /**
     * @param fog        fog level, 0 (none) to 1 (thickest)
     * @param vanillaFar how far Minecraft would let you see right now, in blocks
     * @return how far you can see through the creature's fog; never more than {@code vanillaFar}
     */
    public static float far(float fog, float vanillaFar) {
        if (!(vanillaFar > 0F) || Float.isNaN(fog)) {
            return vanillaFar; // nothing sensible to do; leave Minecraft's value alone
        }
        float f = Math.max(0F, Math.min(1F, fog));
        float clear = 1F - f;
        float absolute = 8F + 152F * clear * clear;
        float share = vanillaFar * (0.1F + 0.9F * (float) Math.pow(clear, 1.5));
        float target = Math.max(Math.min(absolute, share), Math.min(vanillaFar, MIN_VIEW));
        float ease = Math.min(1F, f / EASE_UNTIL);
        return Math.min(vanillaFar, vanillaFar + (target - vanillaFar) * ease);
    }

    /**
     * Where the fog starts: closer the thicker it is, so thick fog is a wall rather than a far haze.
     * Always in front of {@code far}, and never further out than Minecraft's own start.
     */
    public static float near(float fog, float far, float vanillaNear) {
        float f = Float.isNaN(fog) ? 0F : Math.max(0F, Math.min(1F, fog));
        return Math.min(vanillaNear, far * (0.6F - 0.55F * f));
    }
}
