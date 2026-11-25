package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.conscript.ConscriptEntity;
import com.sanguinaire.conscript.ThrownNullificationPotion;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The mod's entities: the Conscript illager and its thrown Nullification Potion. */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Sanguinaire.MOD_ID);

    public static final RegistryObject<EntityType<ConscriptEntity>> CONSCRIPT = ENTITY_TYPES.register("conscript",
            () -> EntityType.Builder.of(ConscriptEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(Sanguinaire.id("conscript").toString()));

    public static final RegistryObject<EntityType<ThrownNullificationPotion>> NULLIFICATION_POTION =
            ENTITY_TYPES.register("nullification_potion",
                    () -> EntityType.Builder.<ThrownNullificationPotion>of(ThrownNullificationPotion::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build(Sanguinaire.id("nullification_potion").toString()));

    private ModEntities() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(CONSCRIPT.get(), ConscriptEntity.createAttributes().build());
    }
}
