package com.sanguinaire.mutation;

import com.sanguinaire.config.SanguinaireConfig;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every mutation from the design document, with the data that serum items and the (upcoming) Serum Synthesizer
 * and transformation system need: a stable ID, serum liquid color, duration and the three required ingredients.
 * <p>
 * The {@link #getId() id} is the gameplay identifier stored in serum NBT; it must never change once released.
 */
public enum MutationType {
    GOATMAN("goatman", 0x7A5230, 60, ModItems.GOAT_FUR, ModItems.TWISTED_FANG, ModItems.TOUGH_FLESH),
    ROTTING_ABOMINATION("rotting_abomination", 0x6B7A2E, 60, ModItems.DRIED_HEART, ModItems.DECAYED_HEART, ModItems.SOAKED_HEART),
    FADEHOUND("fadehound", 0x7B3FC4, 60, ModItems.CHITIN_PLATE, ModItems.CANINE_TONGUE, ModItems.NULL_RESIDUE),
    CREEPER("creeper", 0x56C23F, 30, ModItems.CREEPER_HEART, ModItems.FUSE_SPINE, ModItems.VOLATILE_FRONDS),
    ZOMBIE("zombie", 0x3E7A4A, 30, ModItems.FOUL_STOMACH, ModItems.DECAYED_HEART, ModItems.ROTTEN_BRAIN),
    HUSK("husk", 0xC2A25E, 30, ModItems.FOUL_STOMACH, ModItems.DRIED_HEART, ModItems.ROTTEN_BRAIN),
    DROWNED("drowned", 0x3A9A9A, 30, ModItems.FOUL_STOMACH, ModItems.SOAKED_HEART, ModItems.ROTTEN_BRAIN),
    SPIDER("spider", 0x9E1F24, 30, ModItems.SPIDER_GLAND, ModItems.TWISTED_FANG, ModItems.CHITIN_PLATE),
    VENOMOUS_SPIDER("venomous_spider", 0x1F7A68, 30, ModItems.SPIDER_GLAND, ModItems.VENOM_FANG, ModItems.CHITIN_PLATE),
    PHANTOM("phantom", 0x6C86D0, 30, ModItems.INSOMNIA_EYE, ModItems.HOLLOW_RIB, ModItems.PHANTOM_HEART),
    WARDEN("warden", 0x0F5866, 30, ModItems.DENSE_RIB, ModItems.RESONATING_ANTLER, ModItems.BOOMING_DIAPHRAGM),
    ENDERMAN("enderman", 0xB44EE0, 30, ModItems.NULL_RESIDUE, ModItems.ENDER_MARROW, ModItems.ECHOING_LENS),
    GHAST("ghast", 0xE6E2D6, 30, ModItems.DRIED_TENDRIL, ModItems.SHRIVELED_HEART, ModItems.CHARRED_EYE),
    GUARDIAN("guardian", 0x5FAE9C, 30, ModItems.SPIKED_HEART, ModItems.OILY_FLESH, ModItems.GUARDIAN_EYE),
    ELDER_GUARDIAN("elder_guardian", 0xCFC7A4, 30, ModItems.SPIKED_HEART, ModItems.OILY_FLESH, ModItems.ELDER_GUARDIAN_EYE),
    VILLAGER("villager", 0xB8735A, 30, ModItems.VILLAGER_HEART, ModItems.VILLAGER_NOSE, ModItems.VILLAGER_FLESH),
    WOLF("wolf", 0xA9A9A9, 30, ModItems.WOLF_FUR, ModItems.SHARP_FANGS, ModItems.CANINE_TONGUE),
    GOAT("goat", 0xD8CFBE, 30, ModItems.MOUNTAIN_TALLOW, ModItems.GOAT_FUR, ModItems.CLOVEN_HOOF),
    RAVAGER("ravager", 0x5C5650, 30, ModItems.ILLAGER_NOSE, ModItems.TOUGH_FLESH, ModItems.GIANT_TOOTH);

    private static final Map<String, MutationType> BY_ID = new HashMap<>();

    static {
        for (MutationType type : values()) {
            BY_ID.put(type.id, type);
        }
    }

    private final String id;
    private final int liquidColor;
    private final int durationSeconds;
    private final List<RegistryObject<Item>> ingredients;
    @Nullable
    private Set<MutationTrait> traitCache;

    @SafeVarargs
    MutationType(String id, int liquidColor, int durationSeconds, RegistryObject<Item>... ingredients) {
        this.id = id;
        this.liquidColor = liquidColor;
        this.durationSeconds = durationSeconds;
        this.ingredients = List.of(ingredients);
    }

    public String getId() {
        return id;
    }

    /** RGB color of the liquid inside this mutation's serum syringe. */
    public int getLiquidColor() {
        return liquidColor;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getDurationTicks() {
        return durationSeconds * 20;
    }

    /** The three body parts required to synthesize this serum (order is not significant). */
    public List<RegistryObject<Item>> getIngredients() {
        return ingredients;
    }

    public String getTranslationKey() {
        return "mutation." + Sanguinaire.MOD_ID + "." + id;
    }

    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }

    @Nullable
    public static MutationType byId(String id) {
        return BY_ID.get(id);
    }

    /** Journal text describing what the transformation grants. */
    public String getDescriptionKey() {
        return "journal." + Sanguinaire.MOD_ID + ".mutation." + id;
    }

    public boolean usesIngredient(Item item) {
        for (RegistryObject<Item> ingredient : ingredients) {
            if (ingredient.get() == item) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the serum whose three ingredients are exactly the given items, in any order.
     *
     * @return null for an invalid combination
     */
    @Nullable
    public static MutationType findByIngredients(List<Item> items) {
        if (items.size() != 3) {
            return null;
        }
        for (MutationType type : values()) {
            List<Item> remaining = new ArrayList<>(items);
            boolean matches = true;
            for (RegistryObject<Item> ingredient : type.ingredients) {
                if (!remaining.remove(ingredient.get())) {
                    matches = false;
                    break;
                }
            }
            if (matches && remaining.isEmpty()) {
                return type;
            }
        }
        return null;
    }

    /**
     * Statistics that replace the player's while transformed. Custom creatures use the design's explicit values;
     * vanilla-mob transformations read the mob's real default attributes. Resolved on demand (not at class load)
     * because entity attributes are only available after registration.
     */
    public TransformationStats getStats() {
        return switch (this) {
            // The three original creatures: health, damage and armor come from config/sanguinaire-common.toml.
            // Attack speed only sets how long a hit takes to recharge to full strength; the Goatman's real pace is set by
            // its 0.75 s attack animation (GoatmanAttacks). 2.0 recharges in 10 ticks, well inside that.
            case GOATMAN -> configured(SanguinaireConfig.GOATMAN, 0.30, GOATMAN_ATTACK_SPEED);
            // Same as the Goatman: the 1.125 s attack animation sets the real pace, so every hit lands at full strength.
            case ROTTING_ABOMINATION -> configured(SanguinaireConfig.ROTTING_ABOMINATION, 1.00, GOATMAN_ATTACK_SPEED);
            case FADEHOUND -> configured(SanguinaireConfig.FADEHOUND, 0.00, TransformationStats.SWORD_ATTACK_SPEED);
            case CREEPER -> TransformationStats.ofMob(EntityType.CREEPER, false);
            case ZOMBIE -> TransformationStats.ofMob(EntityType.ZOMBIE, true);
            case HUSK -> TransformationStats.ofMob(EntityType.HUSK, true);
            case DROWNED -> TransformationStats.ofMob(EntityType.DROWNED, true);
            case SPIDER -> TransformationStats.ofMob(EntityType.SPIDER, true);
            case VENOMOUS_SPIDER -> TransformationStats.ofMob(EntityType.CAVE_SPIDER, true);
            case PHANTOM -> TransformationStats.ofMob(EntityType.PHANTOM, true);
            case WARDEN -> TransformationStats.ofMob(EntityType.WARDEN, true);
            case ENDERMAN -> TransformationStats.ofMob(EntityType.ENDERMAN, true);
            case GHAST -> TransformationStats.ofMob(EntityType.GHAST, true);
            case GUARDIAN -> TransformationStats.ofMob(EntityType.GUARDIAN, true);
            case ELDER_GUARDIAN -> TransformationStats.ofMob(EntityType.ELDER_GUARDIAN, true);
            case VILLAGER -> TransformationStats.ofMob(EntityType.VILLAGER, false);
            case WOLF -> TransformationStats.ofMob(EntityType.WOLF, true);
            case GOAT -> TransformationStats.ofMob(EntityType.GOAT, true);
            case RAVAGER -> TransformationStats.ofMob(EntityType.RAVAGER, true);
        };
    }

    private static final double GOATMAN_ATTACK_SPEED = 2.0D;

    private static TransformationStats configured(SanguinaireConfig.CreatureStats config, double knockbackResistance,
                                                  double attackSpeed) {
        return TransformationStats.of(config.health.get(), config.armor.get(), 0.0D, knockbackResistance,
                config.damage.get(), attackSpeed);
    }

    /** Hostile creatures by nature (everything but the Villager, Wolf and Goat): villagers fear them, golems attack them. */
    public boolean isMonstrous() {
        return this != VILLAGER && this != WOLF && this != GOAT;
    }

    /** Passive traits implemented so far (see {@link MutationTrait}). */
    public Set<MutationTrait> getTraits() {
        Set<MutationTrait> traits = traitCache;
        if (traits == null) {
            EnumSet<MutationTrait> computed = computeTraits();
            if (isMonstrous()) {
                computed.add(MutationTrait.MONSTROUS);
            }
            traits = Collections.unmodifiableSet(computed);
            traitCache = traits;
        }
        return traits;
    }

    private EnumSet<MutationTrait> computeTraits() {
        return switch (this) {
            case GOATMAN -> EnumSet.of(MutationTrait.HUNGER_ON_KILL, MutationTrait.HALF_FALL_DAMAGE, MutationTrait.HIGH_JUMP,
                    MutationTrait.DISABLES_SHIELDS);
            case ROTTING_ABOMINATION -> EnumSet.of(MutationTrait.NAUSEATES_ATTACKERS, MutationTrait.PROJECTILE_IMMUNE,
                    MutationTrait.CANNOT_JUMP, MutationTrait.SURVIVES_ONE_HIT, MutationTrait.STEP_UP);
            case FADEHOUND -> EnumSet.of(MutationTrait.SLOWNESS_IMMUNE, MutationTrait.FADES_WHEN_HIT,
                    MutationTrait.LONG_REACH, MutationTrait.SWIFT, MutationTrait.NIGHT_HUNTER);
            case CREEPER -> EnumSet.of(MutationTrait.COUNTS_AS_MONSTER, MutationTrait.CANNOT_ATTACK);
            case ZOMBIE -> EnumSet.of(MutationTrait.COUNTS_AS_MONSTER, MutationTrait.BURNS_IN_SUNLIGHT,
                    MutationTrait.ZOMBIFIES_VILLAGERS);
            case HUSK -> EnumSet.of(MutationTrait.COUNTS_AS_MONSTER, MutationTrait.HUNGER_ON_HIT,
                    MutationTrait.ZOMBIFIES_VILLAGERS);
            case DROWNED -> EnumSet.of(MutationTrait.COUNTS_AS_MONSTER, MutationTrait.WATER_BREATHING,
                    MutationTrait.ZOMBIFIES_VILLAGERS);
            case SPIDER -> EnumSet.of(MutationTrait.COUNTS_AS_MONSTER, MutationTrait.WALL_CLIMBING, MutationTrait.COBWEB_WALKING);
            case VENOMOUS_SPIDER -> EnumSet.of(MutationTrait.COUNTS_AS_MONSTER, MutationTrait.WALL_CLIMBING,
                    MutationTrait.COBWEB_WALKING, MutationTrait.POISON_ON_HIT);
            case PHANTOM -> EnumSet.of(MutationTrait.COUNTS_AS_MONSTER, MutationTrait.FLIGHT, MutationTrait.BURNS_IN_SUNLIGHT);
            case WARDEN -> EnumSet.of(MutationTrait.NO_VIBRATIONS, MutationTrait.FIRE_IMMUNE, MutationTrait.IGNORED_BY_WARDENS);
            case ENDERMAN -> EnumSet.of(MutationTrait.COUNTS_AS_MONSTER, MutationTrait.HURT_BY_WATER);
            case GHAST -> EnumSet.of(MutationTrait.IGNORED_BY_NETHER_MOBS, MutationTrait.FIRE_IMMUNE, MutationTrait.FLIGHT);
            case GUARDIAN -> EnumSet.of(MutationTrait.IGNORED_BY_GUARDIANS, MutationTrait.WATER_BREATHING,
                    MutationTrait.HURT_OUTSIDE_WATER, MutationTrait.SWIFT_SWIMMER);
            case ELDER_GUARDIAN -> EnumSet.of(MutationTrait.IGNORED_BY_GUARDIANS, MutationTrait.WATER_BREATHING,
                    MutationTrait.HURT_OUTSIDE_WATER, MutationTrait.FATIGUE_AURA, MutationTrait.SWIFT_SWIMMER);
            case VILLAGER -> EnumSet.of(MutationTrait.IGNORED_BY_VILLAGE_DEFENDERS, MutationTrait.CANNOT_ATTACK);
            case WOLF -> EnumSet.of(MutationTrait.FEARED_BY_SKELETONS_AND_SHEEP);
            case GOAT -> EnumSet.of(MutationTrait.FALL_DAMAGE_IMMUNE);
            case RAVAGER -> EnumSet.of(MutationTrait.IGNORED_BY_ILLAGERS);
        };
    }

    public boolean hasTrait(MutationTrait trait) {
        return getTraits().contains(trait);
    }

    /** The vanilla mob this transformation is, or null for the custom creatures. */
    @Nullable
    public EntityType<? extends LivingEntity> getAppearance() {
        return switch (this) {
            case CREEPER -> EntityType.CREEPER;
            case ZOMBIE -> EntityType.ZOMBIE;
            case HUSK -> EntityType.HUSK;
            case DROWNED -> EntityType.DROWNED;
            case SPIDER -> EntityType.SPIDER;
            case VENOMOUS_SPIDER -> EntityType.CAVE_SPIDER;
            case PHANTOM -> EntityType.PHANTOM;
            case WARDEN -> EntityType.WARDEN;
            case ENDERMAN -> EntityType.ENDERMAN;
            case GHAST -> EntityType.GHAST;
            case GUARDIAN -> EntityType.GUARDIAN;
            case ELDER_GUARDIAN -> EntityType.ELDER_GUARDIAN;
            case VILLAGER -> EntityType.VILLAGER;
            case WOLF -> EntityType.WOLF;
            case GOAT -> EntityType.GOAT;
            case RAVAGER -> EntityType.RAVAGER;
            case GOATMAN, ROTTING_ABOMINATION, FADEHOUND -> null;
        };
    }

    /** The mob's hurt sound, played instead of the player's when a vanilla-form player is hurt (null for custom forms). */
    @Nullable
    public SoundEvent getHurtSound(boolean inWater) {
        return switch (this) {
            case CREEPER -> SoundEvents.CREEPER_HURT;
            case ZOMBIE -> SoundEvents.ZOMBIE_HURT;
            case HUSK -> SoundEvents.HUSK_HURT;
            case DROWNED -> inWater ? SoundEvents.DROWNED_HURT_WATER : SoundEvents.DROWNED_HURT;
            case SPIDER, VENOMOUS_SPIDER -> SoundEvents.SPIDER_HURT;
            case PHANTOM -> SoundEvents.PHANTOM_HURT;
            case WARDEN -> SoundEvents.WARDEN_HURT;
            case ENDERMAN -> SoundEvents.ENDERMAN_HURT;
            case GHAST -> SoundEvents.GHAST_HURT;
            case GUARDIAN -> inWater ? SoundEvents.GUARDIAN_HURT : SoundEvents.GUARDIAN_HURT_LAND;
            case ELDER_GUARDIAN -> inWater ? SoundEvents.ELDER_GUARDIAN_HURT : SoundEvents.ELDER_GUARDIAN_HURT_LAND;
            case VILLAGER -> SoundEvents.VILLAGER_HURT;
            case WOLF -> SoundEvents.WOLF_HURT;
            case GOAT -> SoundEvents.GOAT_HURT;
            case RAVAGER -> SoundEvents.RAVAGER_HURT;
            case GOATMAN, ROTTING_ABOMINATION, FADEHOUND -> null;
        };
    }

    /** A footstep replacement: the sound to play instead of the block's step sound, or silence if {@code sound} is null. */
    public record StepSound(@Nullable SoundEvent sound, float volume) {
        public static final StepSound SILENT = new StepSound(null, 0.0F);
    }

    /**
     * The mob's own footstep, used in place of the player's block step sound. Null keeps the block's step sound,
     * which is what vanilla creepers, endermen and villagers (and the custom creatures) use. Flying mobs make no
     * footsteps; guardians flop on land. Volumes match the mobs' own {@code playStepSound}.
     */
    @Nullable
    public StepSound getStepSound(boolean inWater) {
        return switch (this) {
            case ZOMBIE -> new StepSound(SoundEvents.ZOMBIE_STEP, 0.15F);
            case HUSK -> new StepSound(SoundEvents.HUSK_STEP, 0.15F);
            case DROWNED -> new StepSound(SoundEvents.DROWNED_STEP, 0.15F);
            case SPIDER, VENOMOUS_SPIDER -> new StepSound(SoundEvents.SPIDER_STEP, 0.15F);
            case WOLF -> new StepSound(SoundEvents.WOLF_STEP, 0.15F);
            case GOAT -> new StepSound(SoundEvents.GOAT_STEP, 0.15F);
            case RAVAGER -> new StepSound(SoundEvents.RAVAGER_STEP, 0.15F);
            case WARDEN -> new StepSound(SoundEvents.WARDEN_STEP, 10.0F);
            case GUARDIAN -> inWater ? StepSound.SILENT : new StepSound(SoundEvents.GUARDIAN_FLOP, 0.6F);
            case ELDER_GUARDIAN -> inWater ? StepSound.SILENT : new StepSound(SoundEvents.ELDER_GUARDIAN_FLOP, 0.6F);
            case PHANTOM, GHAST -> StepSound.SILENT;
            case CREEPER, ENDERMAN, VILLAGER, GOATMAN, ROTTING_ABOMINATION, FADEHOUND -> null;
        };
    }

    /** The mob's idle sound, played now and then while transformed (null if the mob has none or for custom forms). */
    @Nullable
    public SoundEvent getAmbientSound(boolean inWater) {
        return switch (this) {
            case ZOMBIE -> SoundEvents.ZOMBIE_AMBIENT;
            case HUSK -> SoundEvents.HUSK_AMBIENT;
            case DROWNED -> inWater ? SoundEvents.DROWNED_AMBIENT_WATER : SoundEvents.DROWNED_AMBIENT;
            case SPIDER, VENOMOUS_SPIDER -> SoundEvents.SPIDER_AMBIENT;
            case PHANTOM -> SoundEvents.PHANTOM_AMBIENT;
            case WARDEN -> SoundEvents.WARDEN_AMBIENT;
            case ENDERMAN -> SoundEvents.ENDERMAN_AMBIENT;
            case GHAST -> SoundEvents.GHAST_AMBIENT;
            case GUARDIAN -> inWater ? SoundEvents.GUARDIAN_AMBIENT : SoundEvents.GUARDIAN_AMBIENT_LAND;
            case ELDER_GUARDIAN -> inWater ? SoundEvents.ELDER_GUARDIAN_AMBIENT : SoundEvents.ELDER_GUARDIAN_AMBIENT_LAND;
            case VILLAGER -> SoundEvents.VILLAGER_AMBIENT;
            case WOLF -> SoundEvents.WOLF_AMBIENT;
            case GOAT -> SoundEvents.GOAT_AMBIENT;
            case RAVAGER -> SoundEvents.RAVAGER_AMBIENT;
            case CREEPER, GOATMAN, ROTTING_ABOMINATION, FADEHOUND -> null; // creepers are silent until they hiss
        };
    }

    /**
     * The transformed player's hitbox: the vanilla mob's own dimensions, or sizes matching the custom models
     * (Goatman ~2.6 blocks tall, Rotting Abomination ~2.1 and wide, FadeHound ~1 block tall and long).
     */
    public EntityDimensions getDimensions() {
        EntityType<? extends LivingEntity> appearance = getAppearance();
        if (appearance != null) {
            return appearance.getDimensions();
        }
        return switch (this) {
            case GOATMAN -> EntityDimensions.scalable(0.8F, 2.6F);
            case ROTTING_ABOMINATION -> EntityDimensions.scalable(1.0F, 2.1F);
            default -> EntityDimensions.scalable(0.9F, 2.0F); // FadeHound (Nico's model stands about 2 blocks tall)
        };
    }

    /**
     * Extra melee reach (vanilla is 3 blocks). The Fade Hound's long neck is a passive ({@link MutationTrait#LONG_REACH},
     * +1.5); the Goatman simply has long arms (+0.5, 3.5 in total) and lists nothing for it.
     */
    public double getReachBonus() {
        if (hasTrait(MutationTrait.LONG_REACH)) {
            return 1.5D;
        }
        return this == GOATMAN ? 0.5D : 0.0D;
    }

    /**
     * Extra knockback strength added to everything this form hits (vanilla's base knockback is 0.4; each level of
     * the Knockback enchantment adds 0.5). The Rotting Abomination's blows send things flying.
     */
    public float getKnockbackBonus() {
        return this == ROTTING_ABOMINATION ? 1.5F : 0.0F;
    }

    /**
     * Permanent change to movement speed while in this form (MULTIPLY_TOTAL), not shown as a passive: the Rotting
     * Abomination drags itself along on its arms, 30% slower.
     */
    public double getSpeedMultiplierBonus() {
        return this == ROTTING_ABOMINATION ? -0.3D : 0.0D;
    }

    /** Eye height for the transformed hitbox: vanilla's default for living entities (85% of the height). */
    public float getEyeHeight() {
        return getDimensions().height * 0.85F;
    }

    /**
     * Swim-speed bonus (multiplier on the total) for {@link MutationTrait#SWIFT_SWIMMER}. Vanilla Guardians move at
     * 0.5 and Elder Guardians at 0.3, against a player's 0.1, so the Guardian swims fastest.
     */
    public double getSwimSpeedBonus() {
        return switch (this) {
            case GUARDIAN -> 1.5D;
            case ELDER_GUARDIAN -> 1.0D;
            default -> 0.0D;
        };
    }

    /**
     * Flying speed for {@link MutationTrait#FLIGHT} transformations (player creative flight is 0.05, and
     * sprinting doubles it). Phantoms glide at a moderate pace; Ghasts drift slowly.
     */
    public float getFlyingSpeed() {
        return switch (this) {
            case PHANTOM -> 0.025F;
            case GHAST -> 0.018F;
            default -> 0.05F;
        };
    }
}
