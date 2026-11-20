package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static final Reg<SoundEvent> SCREAM = register("lurker.scream");
    public static final Reg<SoundEvent> REVEAL = register("lurker.reveal");
    public static final Reg<SoundEvent> WHISPER = register("lurker.whisper");
    public static final Reg<SoundEvent> VANISH = register("lurker.vanish");
    public static final Reg<SoundEvent> HEARTBEAT = register("lurker.heartbeat");
    public static final Reg<SoundEvent> HURT = register("lurker.hurt");
    public static final Reg<SoundEvent> ATTACK = register("lurker.attack");

    private ModSounds() {
    }

    private static Reg<SoundEvent> register(String name) {
        return Reg.of(Registry.register(BuiltInRegistries.SOUND_EVENT, FogLurkerMod.id(name),
                SoundEvent.createVariableRangeEvent(FogLurkerMod.id(name))));
    }

    /** Loads the class (and so registers the sounds). */
    public static void register() {
    }
}
