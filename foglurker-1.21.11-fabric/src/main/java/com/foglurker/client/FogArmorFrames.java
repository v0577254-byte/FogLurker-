package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import net.minecraft.resources.Identifier;

/** The 32 frames of Armour of the Fog's drifting mist, as worn (textures/entity/equipment/.../fog/frame_N.png). */
public final class FogArmorFrames {
    private static final int FRAME_COUNT = 32;
    private static final String[] LAYERS = {"textures/entity/equipment/humanoid/fog", "textures/entity/equipment/humanoid_leggings/fog",
            "textures/entity/equipment/humanoid/fog_diamond", "textures/entity/equipment/humanoid_leggings/fog_diamond"};
    private static final Identifier[] BASE = new Identifier[LAYERS.length];
    private static final Identifier[][] FRAMES = new Identifier[LAYERS.length][FRAME_COUNT];

    private FogArmorFrames() {
    }

    public static Identifier texture(Identifier original) {
        for (int layer = 0; layer < BASE.length; layer++) {
            if (BASE[layer].equals(original)) {
                return FRAMES[layer][(int) Math.floorMod(System.nanoTime() / 100000000L, FRAME_COUNT)];
            }
        }
        return original;
    }

    static {
        for (int layer = 0; layer < LAYERS.length; layer++) {
            BASE[layer] = FogLurkerMod.id(LAYERS[layer] + ".png");
            for (int i = 0; i < FRAME_COUNT; i++) {
                FRAMES[layer][i] = FogLurkerMod.id(LAYERS[layer] + "/frame_" + i + ".png");
            }
        }
    }
}
