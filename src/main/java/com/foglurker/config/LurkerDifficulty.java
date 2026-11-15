package com.foglurker.config;

import java.util.Locale;

/**
 * The five Fog Lurker modes.
 *
 * <ul>
 *   <li><b>very easy</b>: rarely attacks, mostly only watches. 1 heart per hit, 45 min grace.</li>
 *   <li><b>easy</b>: attacks rarely (more than very easy). 3 hearts per hit, 30 min grace.</li>
 *   <li><b>medium</b> (default): attacks on 1 of every 3 spawns. 5 hearts per hit, 20 min (one Minecraft day) grace.</li>
 *   <li><b>hard</b>: attacks more than medium. 6 hearts per hit, 10 min grace.</li>
 *   <li><b>deathwish</b>: attacks on 3 of every 4 spawns, one-shots, and is 50% faster than every other mode.</li>
 * </ul>
 */
public enum LurkerDifficulty {
    //          id           attack  hearts  grace  hits  watch  speed
    VERY_EASY("very_easy",  0.10,   1.0F,   45,    1,    0.70,  1.0),
    EASY(     "easy",       0.20,   3.0F,   30,    2,    0.35,  1.0),
    MEDIUM(   "medium",     1 / 3D, 5.0F,   20,    3,    0.20,  1.0),
    HARD(     "hard",       0.50,   6.0F,   10,    4,    0.10,  1.0),
    DEATHWISH("deathwish",  0.75,   -1.0F,  5,     99,   0.05,  1.5);

    private final String id;
    private final double attackChance;
    private final float heartsPerHit;
    private final int graceMinutes;
    private final int maxHitsPerEncounter;
    private final double pureWatchChance;
    private final double speedMultiplier;

    LurkerDifficulty(String id, double attackChance, float heartsPerHit, int graceMinutes,
                     int maxHitsPerEncounter, double pureWatchChance, double speedMultiplier) {
        this.id = id;
        this.attackChance = attackChance;
        this.heartsPerHit = heartsPerHit;
        this.graceMinutes = graceMinutes;
        this.maxHitsPerEncounter = maxHitsPerEncounter;
        this.pureWatchChance = pureWatchChance;
        this.speedMultiplier = speedMultiplier;
    }

    public String id() {
        return id;
    }

    /** Human readable name, e.g. "Very Easy". */
    public String displayName() {
        return switch (this) {
            case VERY_EASY -> "Very Easy";
            case EASY -> "Easy";
            case MEDIUM -> "Medium";
            case HARD -> "Hard";
            case DEATHWISH -> "Deathwish";
        };
    }

    /** Chance that a spawn turns into an attack (the rest only watch / lure / stalk). */
    public double attackChance() {
        return attackChance;
    }

    public boolean isOneShot() {
        return heartsPerHit < 0;
    }

    /** Damage in half-hearts (health points). */
    public float damagePerHit() {
        return heartsPerHit * 2.0F;
    }

    public float heartsPerHit() {
        return heartsPerHit;
    }

    public int graceMinutes() {
        return graceMinutes;
    }

    public long graceTicks() {
        return graceMinutes * 60L * 20L;
    }

    public int maxHitsPerEncounter() {
        return maxHitsPerEncounter;
    }

    /** Of the non-attacking spawns, how many are a silent "watcher" instead of a disguise. */
    public double pureWatchChance() {
        return pureWatchChance;
    }

    /** Ground speed multiplier relative to the other modes (deathwish is 50% faster). */
    public double speedMultiplier() {
        return speedMultiplier;
    }

    public static LurkerDifficulty byId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (LurkerDifficulty d : values()) {
            if (d.id.equals(s) || d.id.replace("_", "").equals(s.replace("_", ""))) {
                return d;
            }
        }
        if (s.equals("normal") || s.equals("default")) {
            return MEDIUM;
        }
        return null;
    }
}
