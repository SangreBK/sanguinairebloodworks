package com.sanguinaire.mutation;

import com.sanguinaire.Sanguinaire;

import net.minecraft.world.entity.Entity;

import net.minecraft.nbt.CompoundTag;

import net.minecraft.nbt.NbtUtils;

import net.minecraft.nbt.Tag;

import net.minecraft.nbt.ListTag;

import net.minecraft.world.entity.player.Inventory;

import com.sanguinaire.settings.SanguinaireSettings;

import com.sanguinaire.advancement.ModAdvancements;
import com.sanguinaire.blood.BloodInfusion;
import com.sanguinaire.blood.BloodType;
import com.sanguinaire.network.ModNetwork;
import com.sanguinaire.registry.ModEffects;
import com.sanguinaire.player.PlayerMutationData;
import com.sanguinaire.player.PlayerMutationProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Central, server-authoritative transformation logic shared by every mutation: starting, ticking and ending a
 * transformation, temporary attributes, equipment rules, Weakened Heart accrual and per-tick passive traits.
 * Event-driven passives live in {@link TransformationEvents}.
 *
 * <h2>Attributes</h2>
 * Stats are applied as <em>transient</em> attribute modifiers with fixed UUIDs, so they are never saved to the
 * player file, can never stack, and are re-applied on login/respawn from the persisted capability. Each tick the
 * modifier is re-checked against the target value, so external modifiers cannot shift the transformed stats.
 */
public final class TransformationManager {
    /** Health converted into Weakened Hearts each time a transformation ends: half a heart. */
    public static final float WEAKENED_HEALTH_PER_SERUM = 1.0F;
    /** Normal player creative-flight speed, restored after a flying transformation. */
    private static final float DEFAULT_FLYING_SPEED = 0.05F;
    /** "Blockstepup 1": vanilla player step height is 0.6, so +0.4 reaches a full block. */
    private static final double STEP_UP_BONUS = 0.4D;
    private static final double TARGET_CLEAR_RADIUS = 32.0D;
    private static final double FATIGUE_AURA_RADIUS = 16.0D;
    private static final int FATIGUE_AURA_INTERVAL = 200;

    private record StatModifier(Supplier<Attribute> attribute, UUID id, String name, ToDoubleFunction<TransformationStats> target) {
    }

    private static final List<StatModifier> STAT_MODIFIERS = List.of(
            new StatModifier(() -> Attributes.MAX_HEALTH, UUID.fromString("5b0c9d7e-1f43-4a7e-9a1d-0c6a3e0b7a01"),
                    "Sanguinaire transformation health", TransformationStats::maxHealth),
            new StatModifier(() -> Attributes.ARMOR, UUID.fromString("5b0c9d7e-1f43-4a7e-9a1d-0c6a3e0b7a02"),
                    "Sanguinaire transformation armor", TransformationStats::armor),
            new StatModifier(() -> Attributes.ARMOR_TOUGHNESS, UUID.fromString("5b0c9d7e-1f43-4a7e-9a1d-0c6a3e0b7a03"),
                    "Sanguinaire transformation toughness", TransformationStats::armorToughness),
            new StatModifier(() -> Attributes.KNOCKBACK_RESISTANCE, UUID.fromString("5b0c9d7e-1f43-4a7e-9a1d-0c6a3e0b7a04"),
                    "Sanguinaire transformation knockback resistance", TransformationStats::knockbackResistance),
            new StatModifier(() -> Attributes.ATTACK_DAMAGE, UUID.fromString("5b0c9d7e-1f43-4a7e-9a1d-0c6a3e0b7a05"),
                    "Sanguinaire transformation attack damage", TransformationStats::attackDamage),
            new StatModifier(() -> Attributes.ATTACK_SPEED, UUID.fromString("5b0c9d7e-1f43-4a7e-9a1d-0c6a3e0b7a06"),
                    "Sanguinaire transformation attack speed", TransformationStats::attackSpeed));
    private static final UUID STEP_UP_ID = UUID.fromString("5b0c9d7e-1f43-4a7e-9a1d-0c6a3e0b7a07");
    private static final UUID SWIM_SPEED_ID = UUID.fromString("5b0c9d7e-1f43-4a7e-9a1d-0c6a3e0b7a08");
    private static final UUID NIGHT_SPEED_ID = UUID.fromString("5b0c9d7e-1f43-4a7e-9a1d-0c6a3e0b7a09");
    private static final UUID REACH_ID = UUID.fromString("5b0c9d7e-1f43-4a7e-9a1d-0c6a3e0b7a0a");
    private static final UUID SWIFT_ID = UUID.fromString("5b0c9d7e-1f43-4a7e-9a1d-0c6a3e0b7a0b");
    /** SWIFT: vanilla sprinting is +30%, so +30% makes walking as fast as a normal sprint (and sprinting faster). */
    private static final double SWIFT_BONUS = 0.3D;
    /** Speed I: +20% movement speed. */
    private static final double NIGHT_SPEED_BONUS = 0.2D;
    private static final double WARDEN_CALM_RADIUS = 48.0D;
    private static final double VILLAGER_FEAR_RADIUS = 10.0D;
    private static final double GOLEM_AGGRO_RADIUS = 16.0D;

    /** Humanoid blood: the transformation lasts 30 seconds longer, and leaves 2 extra Weakened Hearts. */
    private static final int HUMANOID_BLOOD_BONUS_TICKS = 30 * 20;
    private static final float HUMANOID_BLOOD_WEAKENED_HEALTH = 4.0F;
    /** Monster blood: 4 extra Weakened Hearts, then Weakness I and Slowness I for 2 minutes. */
    private static final float MONSTER_BLOOD_WEAKENED_HEALTH = 8.0F;
    private static final int MONSTER_BLOOD_AFTERMATH_TICKS = 2 * 60 * 20;
    /** Animal blood: Speed I and Regeneration I for 8 seconds afterwards. */
    private static final int ANIMAL_BLOOD_AFTERMATH_TICKS = 8 * 20;

    /** Like {@code Mob#getAmbientSoundInterval}: at least 80 ticks between idle sounds. */
    private static final int AMBIENT_SOUND_INTERVAL = 80;
    /** Per-player idle-sound timers (same algorithm as vanilla mobs). Transient. */
    private static final Map<UUID, Integer> AMBIENT_SOUND_TIMERS = new HashMap<>();

    private static final EquipmentSlot[] FORBIDDEN_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private TransformationManager() {
    }

    // ---- Queries (safe on both sides; the client uses synced data) ---------------------------------------------

    public static Optional<PlayerMutationData> data(Player player) {
        return PlayerMutationProvider.get(player);
    }

    @Nullable
    public static MutationType getActive(Player player) {
        return data(player).map(PlayerMutationData::getActiveMutation).orElse(null);
    }

    public static boolean isTransformed(Player player) {
        return getActive(player) != null;
    }

    public static boolean hasTrait(Player player, MutationTrait trait) {
        MutationType active = getActive(player);
        return active != null && active.hasTrait(trait);
    }

    // ---- Lifecycle -----------------------------------------------------------------------------------------------

    /**
     * Starts a transformation. Server only.
     *
     * @return false if the player is already transformed (nothing is changed).
     */
    public static boolean tryStart(ServerPlayer player, MutationType type) {
        return tryStart(player, type, null);
    }

    /** Starts a transformation from a serum that may carry blood ({@link BloodInfusion}). Server only. */
    public static boolean tryStart(ServerPlayer player, MutationType type, @Nullable BloodInfusion blood) {
        Optional<PlayerMutationData> optional = data(player);
        if (optional.isEmpty() || optional.get().isTransformed()) {
            return false;
        }
        PlayerMutationData data = optional.get();
        data.setHealthBeforeTransformation(player.getHealth());
        data.setActiveMutation(type);
        data.setActiveBlood(blood);
        data.setRemainingTicks(type.getDurationTicks() + (data.hasTaintedBlood() ? UtilitySerum.TAINTED_BLOOD_BONUS_TICKS : 0)
                + (blood != null && blood.type() == BloodType.HUMANOID ? HUMANOID_BLOOD_BONUS_TICKS : 0));

        player.stopRiding(); // no transformed rider on boats, horses or minecarts
        dropForbiddenEquipment(player);
        applyStats(player, type);
        player.refreshDimensions();
        player.setHealth(player.getMaxHealth());
        data.setTransformedHealth(player.getHealth());
        // The serum feeds the new flesh: the hunger bar is restored in full.
        player.getFoodData().setFoodLevel(20);
        applyStartEffects(player, type);
        ModAdvancements.grant(player, ModAdvancements.FIRST_TRANSFORMATION);

        Level level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.5F);
        ModNetwork.syncMutationData(player);
        return true;
    }

    /**
     * Ends the active transformation (duration expired, death, or an ability that ends it). Server only.
     * Restores the player's statistics and converts another half heart into a Weakened Heart.
     */
    public static void end(ServerPlayer player) {
        Optional<PlayerMutationData> optional = data(player);
        if (optional.isEmpty() || !optional.get().isTransformed()) {
            return;
        }
        PlayerMutationData data = optional.get();
        MutationType type = data.getActiveMutation();
        BloodInfusion blood = data.getActiveBlood();
        data.setActiveMutation(null);
        data.setRemainingTicks(0);
        data.setActiveBlood(null);

        MutationAbilities.clear(player);
        AMBIENT_SOUND_TIMERS.remove(player.getUUID());
        removeStats(player);
        player.refreshDimensions();
        if (type != null && type.hasTrait(MutationTrait.FLIGHT)) {
            disableFlight(player);
        }
        if (player.isAlive()) {
            player.setHealth(Math.min(Math.max(data.getHealthBeforeTransformation(), 1.0F), player.getMaxHealth()));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, 0.8F, 0.7F);
            spawnDetransformationGore(player);
        }
        data.setWeakenedHealth(data.getWeakenedHealth() + WEAKENED_HEALTH_PER_SERUM + extraWeakenedHealth(blood));
        if (player.isAlive() && blood != null) {
            applyBloodAftermath(player, blood);
        }
        ModNetwork.syncMutationData(player);
    }

    /** The borrowed flesh sloughs off in a spray of blood, seen and heard by everyone nearby. */
    private static void spawnDetransformationGore(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double x = player.getX();
        double y = player.getY(0.55D);
        double z = player.getZ();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                x, y, z, 60, 0.4D, 0.6D, 0.4D, 0.15D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState()),
                x, y, z, 30, 0.35D, 0.5D, 0.35D, 0.12D);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.45F, 0.02F, 0.02F), 1.6F),
                x, y, z, 40, 0.5D, 0.7D, 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y, z, 8, 0.3D, 0.4D, 0.3D, 0.1D);
        level.playSound(null, x, player.getY(), z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, x, player.getY(), z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    /** Extra Weakened Hearts (in health points) a blood-infused serum leaves behind. */
    private static float extraWeakenedHealth(@Nullable BloodInfusion blood) {
        if (blood == null) {
            return 0.0F;
        }
        return switch (blood.type()) {
            case MONSTER -> MONSTER_BLOOD_WEAKENED_HEALTH;
            case HUMANOID -> HUMANOID_BLOOD_WEAKENED_HEALTH;
            case ANIMAL -> 0.0F;
        };
    }

    /**
     * What the blood does to the body once the transformation ends: Monster blood leaves you weak and slow,
     * Animal blood leaves you quick and mending, and any potion effect bound at the Sanguine Alembic takes hold.
     */
    private static void applyBloodAftermath(ServerPlayer player, BloodInfusion blood) {
        switch (blood.type()) {
            case MONSTER -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, MONSTER_BLOOD_AFTERMATH_TICKS, 0));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MONSTER_BLOOD_AFTERMATH_TICKS, 0));
            }
            case ANIMAL -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ANIMAL_BLOOD_AFTERMATH_TICKS, 0));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ANIMAL_BLOOD_AFTERMATH_TICKS, 0));
            }
            case HUMANOID -> {
            }
        }
        for (MobEffectInstance bound : blood.effects()) {
            if (bound.getEffect().isInstantenous()) {
                bound.getEffect().applyInstantenousEffect(null, null, player, bound.getAmplifier(), 1.0D);
            } else {
                player.addEffect(new MobEffectInstance(bound));
            }
        }
    }

    /** Whether this transformed player's serum carried Monster blood (+10 damage dealt, -8 damage taken). */
    public static boolean hasMonsterBlood(Player player) {
        return data(player).map(data -> data.isTransformed() && data.getActiveBlood() != null
                && data.getActiveBlood().type() == BloodType.MONSTER).orElse(false);
    }

    /**
     * Re-applies the transient parts of an active transformation to a (new) player entity after login, respawn
     * (returning from the End) or a dimension change. Idempotent. Server only.
     */
    public static void reapply(ServerPlayer player) {
        data(player).ifPresent(data -> {
            MutationType type = data.getActiveMutation();
            player.refreshDimensions();
            if (type == null) {
                removeStats(player);
                return;
            }
            applyStats(player, type);
            if (data.getTransformedHealth() > 0.0F) {
                player.setHealth(Math.min(data.getTransformedHealth(), player.getMaxHealth()));
            }
            if (type.hasTrait(MutationTrait.FLIGHT)) {
                enableFlight(player, type);
            }
        });
    }

    /**
     * Mirrors the Physical Boost effect into the synced data that suppresses Weakened Hearts (on both sides).
     * The effect is the source of truth, so milk or /effect clear also end the suppression. Server, every tick.
     */
    public static void tickPhysicalBoost(ServerPlayer player) {
        data(player).ifPresent(data -> {
            MobEffectInstance effect = player.getEffect(ModEffects.PHYSICAL_BOOST.get());
            int ticks = effect == null ? 0 : Math.max(1, effect.getDuration());
            boolean wasActive = data.getPhysicalBoostTicks() > 0;
            data.setPhysicalBoostTicks(ticks);
            if (wasActive != ticks > 0) {
                ModNetwork.syncMutationData(player);
            }
            if (wasActive && ticks == 0) {
                if (data.hasWeakenedHearts()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.sanguinaire.physical_boost_ended").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
                }
            }
        });
    }

    /** Called every server tick for every player. */
    public static void tick(ServerPlayer player) {
        Optional<PlayerMutationData> optional = data(player);
        if (optional.isEmpty() || !optional.get().isTransformed() || !player.isAlive()) {
            return;
        }
        PlayerMutationData data = optional.get();
        MutationType type = data.getActiveMutation();

        data.setRemainingTicks(data.getRemainingTicks() - 1);
        if (data.getRemainingTicks() <= 0) {
            end(player);
            return;
        }

        dropForbiddenEquipment(player);
        applyStats(player, type);
        tickTraits(player, type);
        MutationAbilities.tick(player);
        data.setTransformedHealth(player.getHealth());
    }

    // ---- Equipment -------------------------------------------------------------------------------------------

    /**
     * Armor and anything held come off while transformed. They are tucked into a free inventory slot (never the
     * selected hotbar slot) and only dropped when the inventory is full. With {@code /sanguinaire
     * allowtransformationitems true} the hands are left alone; armor still comes off.
     */
    private static void dropForbiddenEquipment(ServerPlayer player) {
        boolean itemsAllowed = SanguinaireSettings.get(player.server).areTransformationItemsAllowed();
        for (EquipmentSlot slot : FORBIDDEN_SLOTS) {
            if (itemsAllowed && slot.getType() == EquipmentSlot.Type.HAND) {
                continue;
            }
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                player.setItemSlot(slot, ItemStack.EMPTY);
                stash(player, stack);
            }
        }
    }

    /** Puts a stack into the first empty main-inventory slot other than the selected one, or drops it. */
    private static void stash(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (i != inventory.selected && inventory.items.get(i).isEmpty()) {
                inventory.items.set(i, stack);
                return;
            }
        }
        player.drop(stack, true, false);
    }

    // ---- Attributes ------------------------------------------------------------------------------------------

    private static void applyStats(ServerPlayer player, MutationType type) {
        TransformationStats stats = type.getStats();
        for (StatModifier modifier : STAT_MODIFIERS) {
            Attribute attribute = modifier.attribute().get();
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            // Clamp to the attribute's legal range first (e.g. toughness <= 20); otherwise the clamped value would
            // never match the target and the correction below would grow every tick.
            double target = attribute.sanitizeValue(modifier.target().applyAsDouble(stats));
            AttributeModifier existing = instance.getModifier(modifier.id());
            double currentWithout = instance.getValue() - (existing == null ? 0.0D : existing.getAmount());
            double wanted = target - currentWithout;
            if (existing != null && Math.abs(existing.getAmount() - wanted) < 1.0E-4D) {
                continue;
            }
            instance.removeModifier(modifier.id());
            if (Math.abs(wanted) > 1.0E-4D) {
                instance.addTransientModifier(new AttributeModifier(modifier.id(), modifier.name(), wanted,
                        AttributeModifier.Operation.ADDITION));
            }
        }

        AttributeInstance stepHeight = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (stepHeight != null) {
            boolean wantsStep = type.hasTrait(MutationTrait.STEP_UP);
            boolean hasStep = stepHeight.getModifier(STEP_UP_ID) != null;
            if (wantsStep && !hasStep) {
                stepHeight.addTransientModifier(new AttributeModifier(STEP_UP_ID, "Sanguinaire step up", STEP_UP_BONUS,
                        AttributeModifier.Operation.ADDITION));
            } else if (!wantsStep && hasStep) {
                stepHeight.removeModifier(STEP_UP_ID);
            }
        }
        applySwimSpeed(player, type);
        applyReach(player, type.getReachBonus());
        applyTraitModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SWIFT_ID, "Sanguinaire swiftness",
                type.hasTrait(MutationTrait.SWIFT), SWIFT_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** Melee reach bonus of the current form (Fade Hound +1.5, Goatman +0.5), replaced whenever the amount changes. */
    private static void applyReach(ServerPlayer player, double bonus) {
        AttributeInstance reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reach == null) {
            return;
        }
        AttributeModifier existing = reach.getModifier(REACH_ID);
        if (existing != null && existing.getAmount() == bonus) {
            return;
        }
        reach.removeModifier(REACH_ID);
        if (bonus > 0.0D) {
            reach.addTransientModifier(new AttributeModifier(REACH_ID, "Sanguinaire reach", bonus, AttributeModifier.Operation.ADDITION));
        }
    }

    /** Adds or removes one fixed trait-driven attribute modifier, touching nothing when it is already right. */
    private static void applyTraitModifier(@Nullable AttributeInstance instance, UUID id, String name, boolean wanted,
                                           double amount, AttributeModifier.Operation operation) {
        if (instance == null) {
            return;
        }
        boolean present = instance.getModifier(id) != null;
        if (wanted && !present) {
            instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
        } else if (!wanted && present) {
            instance.removeModifier(id);
        }
    }

    private static void applySwimSpeed(ServerPlayer player, MutationType type) {
        AttributeInstance swimSpeed = player.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (swimSpeed == null) {
            return;
        }
        double bonus = type.hasTrait(MutationTrait.SWIFT_SWIMMER) ? type.getSwimSpeedBonus() : 0.0D;
        AttributeModifier existing = swimSpeed.getModifier(SWIM_SPEED_ID);
        if (existing != null && existing.getAmount() == bonus) {
            return;
        }
        swimSpeed.removeModifier(SWIM_SPEED_ID);
        if (bonus > 0.0D) {
            swimSpeed.addTransientModifier(new AttributeModifier(SWIM_SPEED_ID, "Sanguinaire swim speed", bonus,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeStats(ServerPlayer player) {
        for (StatModifier modifier : STAT_MODIFIERS) {
            AttributeInstance instance = player.getAttribute(modifier.attribute().get());
            if (instance != null) {
                instance.removeModifier(modifier.id());
            }
        }
        AttributeInstance stepHeight = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (stepHeight != null) {
            stepHeight.removeModifier(STEP_UP_ID);
        }
        AttributeInstance swimSpeed = player.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (swimSpeed != null) {
            swimSpeed.removeModifier(SWIM_SPEED_ID);
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(NIGHT_SPEED_ID);
            speed.removeModifier(SWIFT_ID);
        }
        AttributeInstance reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reach != null) {
            reach.removeModifier(REACH_ID);
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    // ---- Start effects & flight --------------------------------------------------------------------------------

    private static void applyStartEffects(ServerPlayer player, MutationType type) {
        if (type.hasTrait(MutationTrait.SLOWNESS_IMMUNE)) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        if (type.hasTrait(MutationTrait.FIRE_IMMUNE)) {
            player.clearFire();
        }
        if (type.hasTrait(MutationTrait.FLIGHT)) {
            enableFlight(player, type);
        }
        // Mobs that should now ignore the player forget their current target.
        List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(TARGET_CLEAR_RADIUS),
                mob -> mob.getTarget() == player && ignoresPlayer(mob, player));
        for (Mob mob : mobs) {
            mob.setTarget(null);
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    private static void enableFlight(ServerPlayer player, MutationType type) {
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.setFlyingSpeed(type.getFlyingSpeed());
        player.onUpdateAbilities();
    }

    private static void disableFlight(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED);
        if (!player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        player.onUpdateAbilities();
    }

    // ---- Targeting ---------------------------------------------------------------------------------------------

    /** Mob persistent-data key: UUIDs of players who attacked it while it treated them as an ally. */
    private static final String TAG_BETRAYED_BY = Sanguinaire.MOD_ID + ":betrayed_by";

    /** Whether {@code player} once attacked {@code mob} while it treated them as one of its own (permanent). */
    public static boolean hasBetrayed(Entity mob, Player player) {
        ListTag list = mob.getPersistentData().getList(TAG_BETRAYED_BY, Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            if (NbtUtils.loadUUID(list.get(i)).equals(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A transformed player attacked a creature of its own faction: that creature never treats this player as an ally
     * again, in any form. Saved with the mob.
     */
    public static void markBetrayed(Mob mob, Player player) {
        if (hasBetrayed(mob, player)) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        ListTag list = data.getList(TAG_BETRAYED_BY, Tag.TAG_INT_ARRAY);
        list.add(NbtUtils.createUUID(player.getUUID()));
        data.put(TAG_BETRAYED_BY, list);
    }

    /** Whether {@code mob} must not target {@code player} because of the player's transformation. */
    public static boolean ignoresPlayer(Mob mob, Player player) {
        MutationType type = getActive(player);
        if (type == null || hasBetrayed(mob, player)) {
            return false;
        }
        if (type.hasTrait(MutationTrait.COUNTS_AS_MONSTER) && mob instanceof Enemy) {
            return true;
        }
        if (type.hasTrait(MutationTrait.IGNORED_BY_NETHER_MOBS)
                && (mob instanceof Ghast || mob instanceof ZombifiedPiglin || mob instanceof AbstractPiglin)) {
            return true;
        }
        if (type.hasTrait(MutationTrait.IGNORED_BY_GUARDIANS) && mob instanceof Guardian) {
            return true;
        }
        if (type.hasTrait(MutationTrait.IGNORED_BY_WARDENS) && mob instanceof Warden) {
            return true;
        }
        if (type.hasTrait(MutationTrait.IGNORED_BY_ILLAGERS) && mob instanceof Raider) {
            return true;
        }
        return type.hasTrait(MutationTrait.IGNORED_BY_VILLAGE_DEFENDERS)
                && (mob instanceof IronGolem || mob instanceof AbstractVillager);
    }

    /** Mobs that flee from {@link MutationTrait#FEARED_BY_SKELETONS_AND_SHEEP} players. */
    public static boolean fearsWolfForm(Mob mob) {
        return mob instanceof AbstractSkeleton || mob instanceof Sheep;
    }

    // ---- Per-tick passive traits -------------------------------------------------------------------------------

    private static void tickTraits(ServerPlayer player, MutationType type) {
        Level level = player.level();
        int age = player.tickCount;

        tickAmbientSound(player, type);
        if (type.hasTrait(MutationTrait.FLIGHT) && !player.getAbilities().mayfly) {
            enableFlight(player, type);
        }
        if (type.hasTrait(MutationTrait.WALL_CLIMBING) && player.horizontalCollision) {
            player.resetFallDistance(); // the client climbs; the server must not count it as a fall
        }
        if (type.hasTrait(MutationTrait.WATER_BREATHING)) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        if (type.hasTrait(MutationTrait.FIRE_IMMUNE) && player.isOnFire()) {
            player.clearFire();
        }
        if (type.hasTrait(MutationTrait.BURNS_IN_SUNLIGHT) && isSunBurnTick(player)) {
            player.setSecondsOnFire(8);
        }
        if (type.hasTrait(MutationTrait.HURT_BY_WATER) && age % 10 == 0 && player.isInWaterRainOrBubble()) {
            player.hurt(player.damageSources().drown(), 1.0F);
        }
        if (type.hasTrait(MutationTrait.HURT_OUTSIDE_WATER) && age % 20 == 0 && !player.isInWaterOrBubble()) {
            player.hurt(player.damageSources().dryOut(), 1.0F);
        }
        tickNightHunter(player, type);
        if (type.hasTrait(MutationTrait.IGNORED_BY_WARDENS) && age % 10 == 0) {
            calmWardens(player);
        }
        if (type.hasTrait(MutationTrait.MONSTROUS) && age % 5 == 0) {
            terrifyVillage(player);
        }
        if (type.hasTrait(MutationTrait.FATIGUE_AURA) && age % FATIGUE_AURA_INTERVAL == 0) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FATIGUE_AURA_RADIUS),
                    mob -> mob instanceof Enemy && mob.isAlive())) {
                mob.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 2));
            }
        }
    }

    /** Whether a {@link MutationTrait#NIGHT_HUNTER} player is currently empowered. Safe on both sides. */
    public static boolean isNightHunting(Player player) {
        Level level = player.level();
        return hasTrait(player, MutationTrait.NIGHT_HUNTER) && !level.dimensionType().hasFixedTime() && !level.isDay();
    }

    /**
     * Night Hunter: +20% movement speed (Speed I) as a hidden attribute modifier, so no effect icon breaks the
     * immersion. The Jump Boost I half is applied on jump by {@link TransformationEvents#onJump}.
     */
    private static void tickNightHunter(ServerPlayer player, MutationType type) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean active = type.hasTrait(MutationTrait.NIGHT_HUNTER) && isNightHunting(player);
        boolean present = speed.getModifier(NIGHT_SPEED_ID) != null;
        if (active && !present) {
            speed.addTransientModifier(new AttributeModifier(NIGHT_SPEED_ID, "Sanguinaire night hunter",
                    NIGHT_SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!active && present) {
            speed.removeModifier(NIGHT_SPEED_ID);
        }
    }

    /**
     * A monstrous form near a village: villagers panic and run (as they do from zombies), wandering traders flee,
     * and iron and snow golems that can see you attack. Golems a player built themselves won't attack players,
     * as in vanilla.
     */
    private static void terrifyVillage(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        Level level = player.level();
        for (AbstractVillager villager : level.getEntitiesOfClass(AbstractVillager.class,
                player.getBoundingBox().inflate(VILLAGER_FEAR_RADIUS))) {
            if (villager instanceof Villager v) {
                v.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, player);
            } else if (villager.getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(villager, 16, 7, player.position());
                if (away != null) {
                    villager.getNavigation().moveTo(away.x, away.y, away.z, 0.7D);
                }
            }
        }
        for (Mob golem : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(GOLEM_AGGRO_RADIUS),
                mob -> mob instanceof IronGolem || mob instanceof SnowGolem)) {
            if (golem.getTarget() == null && golem.canAttack(player) && golem.hasLineOfSight(player)) {
                golem.setTarget(player);
            }
        }
    }

    /** Keeps nearby Wardens calm around a Warden-form player: no anger, no roar, no attack target. */
    private static void calmWardens(ServerPlayer player) {
        for (Warden warden : player.level().getEntitiesOfClass(Warden.class,
                player.getBoundingBox().inflate(WARDEN_CALM_RADIUS))) {
            if (hasBetrayed(warden, player)) {
                continue; // it remembers being attacked
            }
            warden.clearAnger(player);
            if (warden.getBrain().getMemory(MemoryModuleType.ROAR_TARGET).orElse(null) == player) {
                warden.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
            }
            if (warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player) {
                warden.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    /** Vanilla mob idle-sound cadence: each tick, a chance that grows with time since the last sound. */
    private static void tickAmbientSound(ServerPlayer player, MutationType type) {
        SoundEvent sound = type.getAmbientSound(player.isInWater());
        if (sound == null) {
            return;
        }
        int timer = AMBIENT_SOUND_TIMERS.getOrDefault(player.getUUID(), -AMBIENT_SOUND_INTERVAL);
        if (player.getRandom().nextInt(1000) < timer++) {
            timer = -AMBIENT_SOUND_INTERVAL;
            float pitch = (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.2F + 1.0F;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, pitch);
        }
        AMBIENT_SOUND_TIMERS.put(player.getUUID(), timer);
    }

    /** Mirrors vanilla {@code Mob#isSunBurnTick} (undead burning) for a player without a helmet. */
    private static boolean isSunBurnTick(ServerPlayer player) {
        Level level = player.level();
        if (!(level instanceof ServerLevel) || !level.isDay() || player.isInWaterRainOrBubble()
                || player.isInPowderSnow || player.wasInPowderSnow) {
            return false;
        }
        float brightness = player.getLightLevelDependentMagicValue();
        BlockPos eyes = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        return brightness > 0.5F
                && player.getRandom().nextFloat() * 30.0F < (brightness - 0.4F) * 2.0F
                && level.canSeeSky(eyes);
    }
}
