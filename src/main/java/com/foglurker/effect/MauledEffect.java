package com.foglurker.effect;

import com.foglurker.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
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

    /** A full set of Armour of the Fog shakes it off. */
    public static boolean isMauled(LivingEntity e) {
        return ModEffects.MAULED.isPresent() && e.hasEffect(ModEffects.MAULED.get())
                && !com.foglurker.armor.FogArmor.hasFullSet(e);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        net.minecraft.server.MinecraftServer server = entity.getServer();
        if (server != null && com.foglurker.armor.FogArmor.hasFullSet(entity)) {
            // (Removed after this tick: an effect cannot remove itself while it is being ticked.)
            server.tell(new net.minecraft.server.TickTask(server.getTickCount(), () -> {
                if (com.foglurker.armor.FogArmor.hasFullSet(entity)) {
                    entity.removeEffect(this);
                }
            }));
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        if (isMauled(e)) {
            // The leg gives way: no height, and no free sprint-jump boost either (vanilla adds 0.2
            // forward when you jump while sprinting, which would make holding space a speed exploit).
            net.minecraft.world.phys.Vec3 v = e.getDeltaMovement();
            if (e.isSprinting()) {
                float f = e.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
                v = v.add(net.minecraft.util.Mth.sin(f) * 0.2F, 0.0, -net.minecraft.util.Mth.cos(f) * 0.2F);
            }
            e.setDeltaMovement(v.x, 0.0, v.z);
            if (e instanceof net.minecraft.server.level.ServerPlayer sp) {
                // Say why the jump failed (above the hotbar, so it never fills the chat).
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("effect.foglurker.mauled.description")
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
            }
        }
    }

    /** A riptide launch is a jump in all but name: the trident cannot be charged while Mauled. */
    @SubscribeEvent
    public static void onUseItem(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
        if (isMauled(event.getEntity()) && event.getItem().getItem() instanceof net.minecraft.world.item.TridentItem
                && net.minecraft.world.item.enchantment.EnchantmentHelper.getRiptide(event.getItem()) > 0) {
            event.setCanceled(true);
        }
    }

    /** ...and a charge started before the bite does not launch either. */
    @SubscribeEvent
    public static void onStopUsing(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Stop event) {
        if (isMauled(event.getEntity()) && event.getItem().getItem() instanceof net.minecraft.world.item.TridentItem
                && net.minecraft.world.item.enchantment.EnchantmentHelper.getRiptide(event.getItem()) > 0) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        // Server side only: the server decides who sits in a boat. Refusing on the client as well
        // could leave the two disagreeing for a moment when the effect runs out.
        if (event.isMounting() && event.getEntityMounting() instanceof ServerPlayer p && isMauled(p)
                && event.getEntityBeingMounted() instanceof Boat) {
            event.setCanceled(true);
            p.displayClientMessage(Component.literal("Your leg will not hold you.").withStyle(ChatFormatting.DARK_RED), true);
        }
    }
}
