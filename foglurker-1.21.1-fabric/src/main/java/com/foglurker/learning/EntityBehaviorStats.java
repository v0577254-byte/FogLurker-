package com.foglurker.learning;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/**
 * What the creature has learned about how each kind of animal behaves, by quietly watching the real
 * ones near players: how often they wander, how fast, how long they idle, how often they look at you.
 */
public final class EntityBehaviorStats {

    public static final class Stats {
        /** Fraction of time spent walking around. */
        public float moveFraction;
        /** Blocks per second while walking. */
        public float moveSpeed;
        /** Average seconds an idle spell lasts. */
        public float idleSeconds;
        /** Fraction of time spent looking at a nearby player. */
        public float lookAtPlayer;
        public int samples;

        Stats(float moveFraction, float moveSpeed, float idleSeconds, float lookAtPlayer) {
            this.moveFraction = moveFraction;
            this.moveSpeed = moveSpeed;
            this.idleSeconds = idleSeconds;
            this.lookAtPlayer = lookAtPlayer;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putFloat("move", moveFraction);
            t.putFloat("speed", moveSpeed);
            t.putFloat("idle", idleSeconds);
            t.putFloat("look", lookAtPlayer);
            t.putInt("n", samples);
            return t;
        }

        static Stats load(String type, CompoundTag t) {
            // Missing or damaged values (NaN would stick forever through the averaging) fall back to the defaults.
            Stats d = defaults(type);
            Stats s = new Stats(value(t, "move", d.moveFraction), value(t, "speed", d.moveSpeed),
                    value(t, "idle", d.idleSeconds), value(t, "look", d.lookAtPlayer));
            s.samples = Math.max(0, com.foglurker.util.Nbt.getIntOr(t, "n", 0));
            return s;
        }

        private static float value(CompoundTag t, String key, float fallback) {
            if (!(t.get(key) instanceof net.minecraft.nbt.NumericTag)) {
                return fallback;
            }
            float v = com.foglurker.util.Nbt.getFloatOr(t, key, 0F);
            return Float.isFinite(v) ? v : fallback;
        }
    }

    private final Map<String, Stats> byType = new HashMap<>();

    /** Sensible starting values before anything has been observed. */
    private static Stats defaults(String type) {
        return switch (type) {
            case "chicken" -> new Stats(0.35F, 1.2F, 4F, 0.15F);
            case "zombie" -> new Stats(0.55F, 1.4F, 3F, 0.6F);
            case "wolf", "dog" -> new Stats(0.4F, 2.2F, 5F, 0.35F);
            default -> new Stats(0.25F, 0.9F, 7F, 0.2F); // cow, sheep, pig
        };
    }

    /** What has been learned about this kind of animal (or sensible defaults). Never modifies the data. */
    public Stats get(String type) {
        Stats s = byType.get(type);
        return s != null ? s : defaults(type);
    }

    public void observe(String type, boolean moving, float speed, boolean lookingAtPlayer, float finishedIdleSeconds) {
        Stats s = byType.computeIfAbsent(type, EntityBehaviorStats::defaults);
        float a = s.samples < 50 ? 0.08F : 0.02F;
        s.moveFraction += ((moving ? 1F : 0F) - s.moveFraction) * a;
        if (moving && speed > 0.05F && speed < 8F) {
            s.moveSpeed += (speed - s.moveSpeed) * a;
        }
        s.lookAtPlayer += ((lookingAtPlayer ? 1F : 0F) - s.lookAtPlayer) * a;
        if (finishedIdleSeconds > 0 && finishedIdleSeconds < 120) {
            s.idleSeconds += (finishedIdleSeconds - s.idleSeconds) * 0.15F;
        }
        s.samples++;
    }

    public Map<String, Stats> all() {
        return java.util.Collections.unmodifiableMap(byType);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        byType.forEach((k, v) -> t.put(k, v.save()));
        return t;
    }

    public static EntityBehaviorStats load(CompoundTag t) {
        EntityBehaviorStats e = new EntityBehaviorStats();
        for (String k : t.getAllKeys()) {
            e.byType.put(k, Stats.load(k, com.foglurker.util.Nbt.getCompoundOrEmpty(t, k)));
        }
        return e;
    }
}
