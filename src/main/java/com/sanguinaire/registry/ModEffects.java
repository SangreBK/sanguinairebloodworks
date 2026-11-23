package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.effect.DecayedAnatomyEffect;
import com.sanguinaire.effect.PhysicalBoostEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Sanguinaire.MOD_ID);

    public static final RegistryObject<MobEffect> PHYSICAL_BOOST = EFFECTS.register("physical_boost", PhysicalBoostEffect::new);

    public static final RegistryObject<MobEffect> DECAYED_ANATOMY = EFFECTS.register("decayed_anatomy", DecayedAnatomyEffect::new);

    private ModEffects() {
    }
}
