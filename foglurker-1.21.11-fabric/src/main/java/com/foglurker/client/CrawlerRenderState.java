package com.foglurker.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the crawler model needs each frame, on top of the usual walk, head and age values. */
public class CrawlerRenderState extends LivingEntityRenderState {
    /** The bite, 0 to 1 while it swings. */
    public float attackTime;
    /** Hunting: the jaw gapes. */
    public boolean aggressive;
    /** Gives each creature its own twitch timing. */
    public int seed;
    /** Doing the Macarena (and how far into it). */
    public boolean dancing;
    public float danceTime;
    /** Lying flat in cover. */
    public boolean lying;
    /** Shaking while it changes shape. */
    public boolean transforming;
}
