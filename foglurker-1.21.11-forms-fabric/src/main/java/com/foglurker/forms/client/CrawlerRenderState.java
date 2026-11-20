package com.foglurker.forms.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the crawler model needs each frame, on top of the usual walk, head and age values. */
public class CrawlerRenderState extends LivingEntityRenderState {
    /** The bite, 0 to 1 while it swings. */
    public float attackTime;
    /** Hunting: the jaw gapes. */
    public boolean aggressive;
    /** Gives each crawler its own twitch timing. */
    public int seed;
}
