package com.foglurker.world;

import com.foglurker.FogLurkerMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Fog Lurker's advancements that its own triggers cannot see (data/foglurker/advancement, written by
 * tools/advancements.py): seeing it as itself, getting away from it, sleep paralysis, the red words, a bell or a scare
 * burst driving it off. Each of those has one "impossible" criterion, granted from here.
 */
public final class ModAdvancements {
    public static final String FIRST_SIGHTING = "first_sighting";
    public static final String STILL_BREATHING = "still_breathing";
    public static final String SLEEP_PARALYSIS = "hmm_did_i_do_that";
    public static final String WORDS = "can_you_see_me";
    public static final String BELL = "for_whom_the_bell_tolls";
    public static final String BEGONE = "begone";

    private ModAdvancements() {
    }

    /** Grants it (if it is not done yet). Never throws: an advancement missing from a data pack is just skipped. */
    public static void award(ServerPlayer p, String name) {
        MinecraftServer server = p == null ? null : p.level().getServer();
        if (server == null || p instanceof net.fabricmc.fabric.api.entity.FakePlayer) {
            return;
        }
        AdvancementHolder adv = server.getAdvancements().get(FogLurkerMod.id(name));
        if (adv == null) {
            return;
        }
        AdvancementProgress progress = p.getAdvancements().getOrStartProgress(adv);
        if (progress.isDone()) {
            return;
        }
        for (String criterion : progress.getRemainingCriteria()) {
            p.getAdvancements().award(adv, criterion);
        }
    }

    /** Whether they have it (game tests, debug). */
    public static boolean has(ServerPlayer p, String name) {
        AdvancementHolder adv = p.level().getServer().getAdvancements().get(FogLurkerMod.id(name));
        return adv != null && p.getAdvancements().getOrStartProgress(adv).isDone();
    }
}
