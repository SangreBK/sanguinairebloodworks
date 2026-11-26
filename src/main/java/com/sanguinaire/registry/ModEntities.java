package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.conscript.ConscriptEntity;
import com.sanguinaire.conscript.ThrownNullificationPotion;
import com.sanguinaire.conscript.ZombifiedConscript;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import com.sanguinaire.leech.SanguineLeech;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The mod's entities: the Conscript illager, its zombified form, its thrown Nullification Potion and the Sanguine Leech. */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Sanguinaire.MOD_ID);

    public static final RegistryObject<EntityType<ConscriptEntity>> CONSCRIPT = ENTITY_TYPES.register("conscript",
            () -> EntityType.Builder.of(ConscriptEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(Sanguinaire.id("conscript").toString()));

    public static final RegistryObject<EntityType<ZombifiedConscript>> ZOMBIFIED_CONSCRIPT = ENTITY_TYPES.register("zombified_conscript",
            () -> EntityType.Builder.of(ZombifiedConscript::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build(Sanguinaire.id("zombified_conscript").toString()));

    public static final RegistryObject<EntityType<ThrownNullificationPotion>> NULLIFICATION_POTION =
            ENTITY_TYPES.register("nullification_potion",
                    () -> EntityType.Builder.<ThrownNullificationPotion>of(ThrownNullificationPotion::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build(Sanguinaire.id("nullification_potion").toString()));

    /** Small swamp leech: about a silverfish in size. */
    public static final RegistryObject<EntityType<SanguineLeech>> SANGUINE_LEECH = ENTITY_TYPES.register("sanguine_leech",
            () -> EntityType.Builder.of(SanguineLeech::new, MobCategory.MONSTER)
                    .sized(0.5F, 0.35F)
                    .clientTrackingRange(8)
                    .build(Sanguinaire.id("sanguine_leech").toString()));

    private ModEntities() {
    }

    /** Leeches spawn on land (swamps and mangroves, see the biome modifier), only at night. */
    @SubscribeEvent
    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(SANGUINE_LEECH.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SanguineLeech::checkLeechSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(CONSCRIPT.get(), ConscriptEntity.createAttributes().build());
        event.put(ZOMBIFIED_CONSCRIPT.get(), Zombie.createAttributes().build());
        event.put(SANGUINE_LEECH.get(), SanguineLeech.createAttributes().build());
    }
}
