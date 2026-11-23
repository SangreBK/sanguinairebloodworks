package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's own sound events. For now each one points at a fitting vanilla sound through {@code sounds.json}
 * ({@code "type": "event"} entries); replacing them with original recordings only needs new .ogg files in
 * {@code assets/sanguinaire/sounds/} and a change to {@code sounds.json}, no code.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Sanguinaire.MOD_ID);

    /** The needle going in when a transformation serum is used. */
    public static final RegistryObject<SoundEvent> SERUM_INJECT = register("serum_inject");
    /** The heart racing as the serum spreads (played several times, faster and higher each time). */
    public static final RegistryObject<SoundEvent> SERUM_HEARTBEAT = register("serum_heartbeat");
    /** Bones cracking and shifting just before the change. */
    public static final RegistryObject<SoundEvent> TRANSFORMATION_CRACK = register("transformation_crack");
    /** The moment the body gives way and the transformation completes. */
    public static final RegistryObject<SoundEvent> TRANSFORMATION_BURST = register("transformation_burst");

    /** A Goatman's claws landing: a heavy, low swipe. */
    public static final RegistryObject<SoundEvent> GOATMAN_SWIPE = register("goatman_swipe");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Sanguinaire.id(name)));
    }
}
