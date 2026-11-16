package com.foglurker.effect;

import com.foglurker.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * "Mauled": its teeth went into your leg. While it lasts you cannot jump and cannot get into a boat
 * (so water stops being an instant escape). Enforced on both sides so it never rubber-bands.
 */
@Mod.EventBusSubscriber(modid = com.foglurker.FogLurkerMod.MOD_ID)
public class MauledEffect extends MobEffect {
    public MauledEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A0A12);
    }

    public static boolean isMauled(LivingEntity e) {
        return ModEffects.MAULED.isPresent() && e.hasEffect(ModEffects.MAULED.get());
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        if (isMauled(e)) {
            e.setDeltaMovement(e.getDeltaMovement().multiply(1.0, 0.0, 1.0)); // the leg gives way
        }
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof Player p && isMauled(p)
                && event.getEntityBeingMounted() instanceof Boat) {
            event.setCanceled(true);
            if (p instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("Your leg will not hold you.").withStyle(ChatFormatting.DARK_RED), true);
            }
        }
    }
}
