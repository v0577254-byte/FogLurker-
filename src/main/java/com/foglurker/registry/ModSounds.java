package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FogLurkerMod.MOD_ID);

    public static final RegistryObject<SoundEvent> SCREAM = register("lurker.scream");
    public static final RegistryObject<SoundEvent> REVEAL = register("lurker.reveal");
    public static final RegistryObject<SoundEvent> WHISPER = register("lurker.whisper");
    public static final RegistryObject<SoundEvent> VANISH = register("lurker.vanish");
    public static final RegistryObject<SoundEvent> HEARTBEAT = register("lurker.heartbeat");
    public static final RegistryObject<SoundEvent> HURT = register("lurker.hurt");
    public static final RegistryObject<SoundEvent> ATTACK = register("lurker.attack");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(FogLurkerMod.id(name)));
    }
}
