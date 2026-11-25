package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's own sound events. Some point at a fitting vanilla sound through {@code sounds.json}
 * ({@code "type": "event"} entries); the creature sounds are Nico's recordings in {@code assets/sanguinaire/sounds/entity/}.
 * Replacing a placeholder only needs a new .ogg file and a change to {@code sounds.json}, no code.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Sanguinaire.MOD_ID);

    /** The needle going in when any serum is injected, into yourself or another player (Nico's recording). */
    public static final RegistryObject<SoundEvent> SERUM_INJECT = register("serum_inject");
    /** The heart racing as the serum spreads (played several times, faster and higher each time). */
    public static final RegistryObject<SoundEvent> SERUM_HEARTBEAT = register("serum_heartbeat");
    /** Bones cracking and shifting just before the change (Nico's three bone-break recordings, picked at random). */
    public static final RegistryObject<SoundEvent> TRANSFORMATION_CRACK = register("transformation_crack");
    /** The moment the body gives way and the transformation completes (the same bone breaks, deeper and louder). */
    public static final RegistryObject<SoundEvent> TRANSFORMATION_BURST = register("transformation_burst");

    /** A Goatman's claws landing: a heavy, low swipe. */
    public static final RegistryObject<SoundEvent> GOATMAN_SWIPE = register("goatman_swipe");

    /** Goatman breathing, looped for as long as the transformation lasts. */
    public static final RegistryObject<SoundEvent> GOATMAN_IDLE = register("goatman_idle");
    /** Goatman footstep. */
    public static final RegistryObject<SoundEvent> GOATMAN_STEP = register("goatman_step");
    /** Rotting Abomination groaning, looped for as long as the transformation lasts. */
    public static final RegistryObject<SoundEvent> ABOMINATION_IDLE = register("abomination_idle");
    /** Rotting Abomination footstep. */
    public static final RegistryObject<SoundEvent> ABOMINATION_STEP = register("abomination_step");
    /** Fade Hound ambient sound, played now and then like a vanilla mob's. */
    public static final RegistryObject<SoundEvent> FADEHOUND_AMBIENT = register("fadehound_ambient");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Sanguinaire.id(name)));
    }
}
