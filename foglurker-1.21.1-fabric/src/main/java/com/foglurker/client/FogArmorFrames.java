package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import net.minecraft.resources.ResourceLocation;

public final class FogArmorFrames {
   private static final int FRAME_COUNT = 32;
   private static final String[] LAYERS = new String[]{"textures/models/armor/fog_layer_1", "textures/models/armor/fog_layer_2",
      "textures/models/armor/fog_diamond_layer_1", "textures/models/armor/fog_diamond_layer_2"};
   private static final ResourceLocation[] BASE = new ResourceLocation[LAYERS.length];
   private static final ResourceLocation[][] FRAMES = new ResourceLocation[LAYERS.length][32];

   private FogArmorFrames() {
   }

   public static ResourceLocation texture(ResourceLocation original) {
      for (int layer = 0; layer < BASE.length; layer++) {
         if (BASE[layer].equals(original)) {
            int frame = Math.floorMod(System.nanoTime() / 100000000L, 32);
            return FRAMES[layer][frame];
         }
      }

      return original;
   }

   static {
      for (int layer = 0; layer < LAYERS.length; layer++) {
         BASE[layer] = FogLurkerMod.id(LAYERS[layer] + ".png");

         for (int i = 0; i < 32; i++) {
            FRAMES[layer][i] = FogLurkerMod.id(LAYERS[layer] + "/frame_" + i + ".png");
         }
      }
   }
}
