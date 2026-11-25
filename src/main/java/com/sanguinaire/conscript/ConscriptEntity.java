package com.sanguinaire.conscript;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.TransformationManager;
import com.sanguinaire.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Conscript: an illager soldier that hunts the mutated. Neutral to ordinary players unless they come within
 * {@value #PROVOKE_RANGE} blocks of one (then the whole group turns on them for a while); always hostile to players
 * marked as hunted ({@link ConscriptHunts}), whom it can spot from {@value #SPOT_RANGE} blocks (with line of sight;
 * sneaking and invisibility shorten that like for any mob). Attacks nearby zombies and skeletons. Allied with every
 * illager (vanilla illager alliance) and with other Conscripts.
 * <p>
 * Special actions: throws a Nullification Potion at transformed targets every 30 seconds (can miss), flees and drinks
 * a 15-health healing potion once at half health, and drinks Fire Resistance when set on fire (60 s cooldown).
 * Carries an iron axe or a Piercing crossbow. Never carries a raid banner (killing one never gives Bad Omen).
 */
public class ConscriptEntity extends AbstractIllager implements CrossbowAttackMob {
    public static final double SPOT_RANGE = 40.0D;
    public static final double PROVOKE_RANGE = 4.0D;
    private static final double WARN_RANGE = 9.0D;
    private static final double ZOMBIE_RANGE = 16.0D;
    private static final double ALERT_RANGE = 32.0D;
    /** Damage of a hit, whatever weapon is held (the axe's own bonus is not added on top). */
    public static final double ATTACK_DAMAGE = 8.0D;
    private static final int NULLIFY_COOLDOWN = 30 * 20;
    private static final double NULLIFY_RANGE = 12.0D;
    private static final int FIRE_RES_COOLDOWN = 60 * 20;
    private static final int FIRE_RES_DURATION = 60 * 20;
    private static final float HEAL_AMOUNT = 15.0F;
    private static final int FLEE_TICKS = 50;
    private static final int HORN_COOLDOWN = 30 * 20;
    private static final int ANGER_TICKS = 60 * 20;
    /** A hunting party gives up after this long without seeing its quarry. */
    public static final int GIVE_UP_TICKS = 3 * 60 * 20;
    /** Hunting parties know roughly where their quarry is: they re-read the trail this often. */
    private static final int TRAIL_REFRESH_TICKS = 30 * 20;

    private static final EntityDataAccessor<Boolean> CHARGING_CROSSBOW =
            SynchedEntityData.defineId(ConscriptEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DRINKING =
            SynchedEntityData.defineId(ConscriptEntity.class, EntityDataSerializers.BOOLEAN);
    private static final UUID DRINK_SLOW_ID = UUID.fromString("5CD17E52-A79A-43D3-A529-90FDE04B181E");
    private static final AttributeModifier DRINK_SLOW =
            new AttributeModifier(DRINK_SLOW_ID, "Drinking speed penalty", -0.25D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private static final int DRINK_NONE = 0;
    private static final int DRINK_HEAL = 1;
    private static final int DRINK_FIRE = 2;

    private final MeleeAttackGoal meleeGoal = new MeleeAttackGoal(this, 1.0D, false);
    private final RangedCrossbowAttackGoal<ConscriptEntity> crossbowGoal = new RangedCrossbowAttackGoal<>(this, 1.0D, 8.0F);

    private boolean usedHealingPotion;
    private int fleeTicks;
    private int drinkTicks;
    private int drinkKind = DRINK_NONE;
    private ItemStack storedWeapon = ItemStack.EMPTY;
    private int fireResCooldown;
    private int nullifyCooldown;
    private int hornCooldown;
    @Nullable
    private UUID angryAt;
    private int angerTicks;
    private boolean suppressAlert;
    @Nullable
    private LivingEntity primedTarget;

    // Hunting party state (set when spawned by a hunt).
    @Nullable
    private UUID huntTarget;
    @Nullable
    private Vec3 lastKnownPos;
    private int ticksSinceSighted;
    private int squadId;

    public ConscriptEntity(EntityType<? extends ConscriptEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
        reassessWeaponGoal();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.10D)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CHARGING_CROSSBOW, false);
        entityData.define(DRINKING, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals(); // raid and patrol goals
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BusyGoal());
        goalSelector.addGoal(2, new FleeToHealGoal());
        goalSelector.addGoal(5, new TrackQuarryGoal());
        goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 15.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 15.0F));

        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isHostileTowards));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Zombie.class, 10, true, false,
                e -> !(e instanceof ZombifiedPiglin) && e.distanceToSqr(this) <= ZOMBIE_RANGE * ZOMBIE_RANGE));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractSkeleton.class, 10, true, false,
                e -> e.distanceToSqr(this) <= ZOMBIE_RANGE * ZOMBIE_RANGE));
    }

    // ---- Who is an enemy -----------------------------------------------------------------------------------------

    /** Players this Conscript will attack: the hunted (from afar), provokers, and anyone during a raid. */
    public boolean isHostileTowards(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (ConscriptHunts.isHunted(player)) {
            return distanceToSqr(player) <= SPOT_RANGE * SPOT_RANGE;
        }
        if (isAngryAt(player) || getCurrentRaid() != null) {
            return true;
        }
        return distanceToSqr(player) <= PROVOKE_RANGE * PROVOKE_RANGE;
    }

    public boolean isAngryAt(Player player) {
        return angerTicks > 0 && player.getUUID().equals(angryAt);
    }

    /** Every Conscript near this one turns on {@code player} for a minute (provocation, theft, an attack). */
    public void angerGroupAt(Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        for (ConscriptEntity other : level().getEntitiesOfClass(ConscriptEntity.class, getBoundingBox().inflate(24.0D))) {
            other.angryAt = player.getUUID();
            other.angerTicks = ANGER_TICKS;
        }
    }

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return isHostileTowards(player);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && source.getEntity() instanceof Player player && !ConscriptHunts.isHunted(player)) {
            angerGroupAt(player);
        }
        return hurt;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity previous = getTarget();
        super.setTarget(target);
        if (!level().isClientSide() && !suppressAlert && target != previous && target instanceof ServerPlayer player
                && getTarget() == player && ConscriptHunts.isHunted(player)) {
            spotted(player);
        }
    }

    /** A hunted player was spotted: blow the horn (once per group every 30 s) and send the whole group after them. */
    private void spotted(ServerPlayer player) {
        sawQuarry(player);
        List<ConscriptEntity> group = level().getEntitiesOfClass(ConscriptEntity.class, getBoundingBox().inflate(ALERT_RANGE));
        boolean hornReady = group.stream().noneMatch(c -> c.hornCooldown > 0);
        if (hornReady) {
            hornCooldown = HORN_COOLDOWN;
            swing(getUsedItemHand());
            level().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(),
                    SoundSource.HOSTILE, 16.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".conscript_spotted")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        }
        for (ConscriptEntity other : group) {
            if (other != this && other.getTarget() == null) {
                other.suppressAlert = true;
                other.setTarget(player);
                other.suppressAlert = false;
                other.sawQuarry(player);
            }
        }
    }

    private void sawQuarry(Player player) {
        if (player.getUUID().equals(huntTarget)) {
            lastKnownPos = player.position();
            ticksSinceSighted = 0;
        }
    }

    // ---- Hunting parties -----------------------------------------------------------------------------------------

    public void setHunting(ServerPlayer player, int squad) {
        huntTarget = player.getUUID();
        lastKnownPos = player.position();
        ticksSinceSighted = 0;
        squadId = squad;
    }

    public boolean isHunting(Player player) {
        return player.getUUID().equals(huntTarget);
    }

    public int getSquadId() {
        return squadId;
    }

    /** Stops hunting (gave up, or the quarry died) and lets this Conscript despawn normally. */
    public void stopHunting() {
        huntTarget = null;
        lastKnownPos = null;
    }

    @Nullable
    private ServerPlayer quarry() {
        if (huntTarget == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Player player = serverLevel.getPlayerByUUID(huntTarget);
        return player instanceof ServerPlayer serverPlayer && player.isAlive() && !player.isSpectator() ? serverPlayer : null;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return huntTarget == null && super.removeWhenFarAway(distanceSq);
    }

    // ---- Tick ------------------------------------------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        if (hornCooldown > 0) hornCooldown--;
        if (fireResCooldown > 0) fireResCooldown--;
        if (nullifyCooldown > 0) nullifyCooldown--;
        if (angerTicks > 0 && --angerTicks == 0) {
            angryAt = null;
        }
        tickHunt();
        tickDrinking();
        if (!isDrinking()) {
            tryNullify();
        }
        if (tickCount % 10 == 0) {
            checkNearbyPlayers();
        }
    }

    private void tickHunt() {
        if (huntTarget == null) {
            return;
        }
        ServerPlayer quarry = quarry();
        if (quarry == null) {
            if (++ticksSinceSighted > GIVE_UP_TICKS) {
                stopHunting();
            }
            return;
        }
        if (getTarget() == quarry && tickCount % 10 == 0 && hasLineOfSight(quarry)) {
            sawQuarry(quarry);
        } else if (++ticksSinceSighted % TRAIL_REFRESH_TICKS == 0) {
            lastKnownPos = quarry.position(); // they follow the trail, but only now and then
        }
        if (ticksSinceSighted > GIVE_UP_TICKS) {
            ConscriptHunts.onSquadGaveUp(quarry, squadId);
            stopHunting();
        }
    }

    /** Warns ordinary players who come close, and turns the group on those who come within 4 blocks. */
    private void checkNearbyPlayers() {
        if (getCurrentRaid() != null) {
            return;
        }
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(WARN_RANGE))) {
            if (player.isCreative() || player.isSpectator() || ConscriptHunts.isHunted(player) || isAngryAt(player)) {
                continue;
            }
            double distanceSq = distanceToSqr(player);
            if (distanceSq <= PROVOKE_RANGE * PROVOKE_RANGE) {
                angerGroupAt(player);
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".conscript_provoked")
                            .withStyle(ChatFormatting.RED), true);
                }
            } else if (distanceSq <= WARN_RANGE * WARN_RANGE && player instanceof ServerPlayer serverPlayer
                    && ConscriptHunts.tryWarn(serverPlayer)) {
                getLookControl().setLookAt(player);
                serverPlayer.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".conscript_warning")
                        .withStyle(ChatFormatting.GOLD), true);
                playSound(SoundEvents.PILLAGER_AMBIENT, 1.0F, 0.8F);
            }
        }
    }

    // ---- Nullification Potion ----------------------------------------------------------------------------------------

    private void tryNullify() {
        LivingEntity target = getTarget();
        if (!(target instanceof ServerPlayer player) || !TransformationManager.isTransformed(player)) {
            return;
        }
        if (nullifyCooldown > 0) {
            return;
        }
        double distanceSq = distanceToSqr(player);
        if (distanceSq > NULLIFY_RANGE * NULLIFY_RANGE || distanceSq < 4.0D || !hasLineOfSight(player)) {
            return;
        }
        // Thrown like a witch's potion: aimed where the target is heading, and it can miss.
        Vec3 motion = player.getDeltaMovement();
        double dx = player.getX() + motion.x - getX();
        double dy = player.getEyeY() - 1.1D - getY();
        double dz = player.getZ() + motion.z - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        ThrownNullificationPotion potion = new ThrownNullificationPotion(level(), this);
        potion.setItem(new ItemStack(ModItems.NULLIFICATION_POTION.get()));
        potion.setXRot(potion.getXRot() - 20.0F);
        potion.shoot(dx, dy + horizontal * 0.2D, dz, 0.75F, 8.0F);
        swing(getUsedItemHand());
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_THROW, getSoundSource(), 1.0F,
                0.8F + random.nextFloat() * 0.4F);
        level().addFreshEntity(potion);
        nullifyCooldown = NULLIFY_COOLDOWN;
    }

    /** Called when a new target is picked up: the first potion comes after a short, random delay. */
    private void primeNullify() {
        if (nullifyCooldown <= 0) {
            nullifyCooldown = 60 + random.nextInt(160);
        }
    }

    // ---- Potions they drink ------------------------------------------------------------------------------------------

    public boolean isDrinking() {
        return entityData.get(DRINKING);
    }

    boolean isFleeingToHeal() {
        return fleeTicks > 0;
    }

    private void tickDrinking() {
        if (isDrinking()) {
            if (--drinkTicks <= 0) {
                finishDrinking();
            }
            return;
        }
        if (fleeTicks > 0) {
            if (--fleeTicks == 0) {
                startDrinking(DRINK_HEAL);
            }
            return;
        }
        if (!usedHealingPotion && getHealth() <= getMaxHealth() / 2.0F && isAlive()) {
            usedHealingPotion = true; // once per Conscript, ever
            fleeTicks = getTarget() != null ? FLEE_TICKS : 1;
            return;
        }
        if (isOnFire() && fireResCooldown <= 0 && !hasEffect(MobEffects.FIRE_RESISTANCE)) {
            fireResCooldown = FIRE_RES_COOLDOWN;
            startDrinking(DRINK_FIRE);
        }
    }

    private void startDrinking(int kind) {
        drinkKind = kind;
        drinkTicks = kind == DRINK_FIRE ? 20 : 32;
        storedWeapon = getMainHandItem().copy();
        entityData.set(DRINKING, true);
        ItemStack potion = PotionUtils.setPotion(new ItemStack(Items.POTION),
                kind == DRINK_FIRE ? Potions.FIRE_RESISTANCE : Potions.HEALING);
        setItemSlot(EquipmentSlot.MAINHAND, potion);
        if (getAttribute(Attributes.MOVEMENT_SPEED) != null && !getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(DRINK_SLOW)) {
            getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(DRINK_SLOW);
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_DRINK, getSoundSource(), 1.0F,
                0.8F + random.nextFloat() * 0.4F);
    }

    private void finishDrinking() {
        if (drinkKind == DRINK_HEAL) {
            heal(HEAL_AMOUNT);
        } else if (drinkKind == DRINK_FIRE) {
            addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_RES_DURATION));
        }
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(drinkKind == DRINK_HEAL ? ParticleTypes.HEART : ParticleTypes.FLAME,
                    getX(), getEyeY(), getZ(), 4, 0.3D, 0.3D, 0.3D, 0.01D);
        }
        restoreWeapon();
    }

    private void restoreWeapon() {
        entityData.set(DRINKING, false);
        drinkKind = DRINK_NONE;
        drinkTicks = 0;
        setItemSlot(EquipmentSlot.MAINHAND, storedWeapon);
        storedWeapon = ItemStack.EMPTY;
        if (getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(DRINK_SLOW);
        }
        reassessWeaponGoal();
    }

    @Override
    public void die(DamageSource source) {
        if (isDrinking()) {
            restoreWeapon(); // drop the weapon, not the half-drunk potion
        }
        super.die(source);
    }

    // ---- Weapons ---------------------------------------------------------------------------------------------------

    /** The weapon's own damage bonus is ignored: every hit deals {@value #ATTACK_DAMAGE} (plus enchantments). */
    @Override
    public double getAttributeValue(net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        double value = super.getAttributeValue(attribute);
        if (attribute == Attributes.ATTACK_DAMAGE) {
            for (AttributeModifier modifier : getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
                if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                    value -= modifier.getAmount();
                }
            }
        }
        return value;
    }

    private void reassessWeaponGoal() {
        if (level() == null || level().isClientSide() || isDrinking()) {
            return;
        }
        goalSelector.removeGoal(meleeGoal);
        goalSelector.removeGoal(crossbowGoal);
        goalSelector.addGoal(3, isHolding(Items.CROSSBOW) ? crossbowGoal : meleeGoal);
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        super.setItemSlot(slot, stack);
        if (slot == EquipmentSlot.MAINHAND && !level().isClientSide()) {
            reassessWeaponGoal();
        }
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (random.nextFloat() < 0.6F) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        } else {
            ItemStack crossbow = new ItemStack(Items.CROSSBOW);
            crossbow.enchant(Enchantments.PIERCING, 1 + random.nextInt(2));
            setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        }
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData, @Nullable CompoundTag tag) {
        RandomSource random = level.getRandom();
        populateDefaultEquipmentSlots(random, difficulty);
        populateDefaultEquipmentEnchantments(random, difficulty);
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData, tag);
        // Conscripts never carry a raid banner, so killing one never brings Bad Omen.
        if (getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof BannerItem) {
            setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            setDropChance(EquipmentSlot.HEAD, 0.085F);
        }
        reassessWeaponGoal();
        return data;
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon == Items.CROSSBOW;
    }

    @Override
    public void setChargingCrossbow(boolean charging) {
        entityData.set(CHARGING_CROSSBOW, charging);
    }

    public boolean isChargingCrossbow() {
        return entityData.get(CHARGING_CROSSBOW);
    }

    @Override
    public void shootCrossbowProjectile(LivingEntity target, ItemStack crossbow, Projectile projectile, float angle) {
        shootCrossbowProjectile(this, target, projectile, angle, 1.6F);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        noActionTime = 0;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        performCrossbowAttack(this, 1.6F);
    }

    @Override
    public AbstractIllager.IllagerArmPose getArmPose() {
        if (isChargingCrossbow()) {
            return AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE;
        }
        if (isHolding(Items.CROSSBOW) && !isDrinking()) {
            return AbstractIllager.IllagerArmPose.CROSSBOW_HOLD;
        }
        if (isAggressive() && !isDrinking() && getMainHandItem().getItem() instanceof AxeItem) {
            return AbstractIllager.IllagerArmPose.ATTACKING;
        }
        return isCelebrating() ? AbstractIllager.IllagerArmPose.CELEBRATING : AbstractIllager.IllagerArmPose.NEUTRAL;
    }

    // ---- Raider --------------------------------------------------------------------------------------------------

    @Override
    public void applyRaidBuffs(int wave, boolean unused) {
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.PILLAGER_CELEBRATE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    // ---- Saving --------------------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (isDrinking() && tag.contains("HandItems", 9)) {
            // Never save the borrowed potion in place of the weapon.
            net.minecraft.nbt.ListTag hands = tag.getList("HandItems", 10);
            if (!hands.isEmpty()) {
                hands.set(0, storedWeapon.save(new CompoundTag()));
            }
        }
        tag.putBoolean("UsedHealingPotion", usedHealingPotion);
        tag.putInt("FireResCooldown", fireResCooldown);
        if (huntTarget != null) {
            tag.put("HuntTarget", NbtUtils.createUUID(huntTarget));
            tag.putInt("TicksSinceSighted", ticksSinceSighted);
            tag.putInt("Squad", squadId);
            if (lastKnownPos != null) {
                tag.putDouble("TrailX", lastKnownPos.x);
                tag.putDouble("TrailY", lastKnownPos.y);
                tag.putDouble("TrailZ", lastKnownPos.z);
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        usedHealingPotion = tag.getBoolean("UsedHealingPotion");
        fireResCooldown = tag.getInt("FireResCooldown");
        if (tag.contains("HuntTarget")) {
            huntTarget = NbtUtils.loadUUID(tag.get("HuntTarget"));
            ticksSinceSighted = tag.getInt("TicksSinceSighted");
            squadId = tag.getInt("Squad");
            if (tag.contains("TrailX")) {
                lastKnownPos = new Vec3(tag.getDouble("TrailX"), tag.getDouble("TrailY"), tag.getDouble("TrailZ"));
            }
        }
        reassessWeaponGoal();
    }

    // ---- Goals -----------------------------------------------------------------------------------------------------

    /** Stops attacking and moving freely while drinking (the potion is in the weapon hand). */
    private class BusyGoal extends Goal {
        BusyGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isDrinking();
        }

        @Override
        public void start() {
            getNavigation().stop();
        }
    }

    /** At half health: run away from the target for a moment before drinking the healing potion. */
    private class FleeToHealGoal extends Goal {
        private int repath;

        FleeToHealGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isFleeingToHeal() && getTarget() != null;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null || --repath > 0) {
                return;
            }
            repath = 10;
            Vec3 away = DefaultRandomPos.getPosAway(ConscriptEntity.this, 16, 7, target.position());
            if (away != null) {
                getNavigation().moveTo(away.x, away.y, away.z, 1.35D);
            }
        }
    }

    /** Hunting parties follow their quarry's trail (last known position) until they spot them. */
    private class TrackQuarryGoal extends Goal {
        private int repath;

        TrackQuarryGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getTarget() == null && huntTarget != null && lastKnownPos != null && quarry() != null
                    && quarry().level() == level();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (--repath > 0 || lastKnownPos == null) {
                return;
            }
            repath = 40;
            if (distanceToSqr(lastKnownPos) > 9.0D) {
                getNavigation().moveTo(lastKnownPos.x, lastKnownPos.y, lastKnownPos.z, 1.0D);
            } else {
                // Reached the trail's end: search around it.
                Vec3 around = DefaultRandomPos.getPos(ConscriptEntity.this, 10, 4);
                if (around != null) {
                    getNavigation().moveTo(around.x, around.y, around.z, 0.8D);
                }
            }
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        // An axe knocks a raised shield aside, like a vindicator's.
        if (hit && target instanceof Player player && player.isBlocking() && getMainHandItem().getItem() instanceof AxeItem) {
            player.getCooldowns().addCooldown(Items.SHIELD, 100);
            player.stopUsingItem();
            level().broadcastEntityEvent(player, (byte) 30);
        }
        return hit;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target != primedTarget) {
            primedTarget = target;
            if (target != null) {
                primeNullify();
            }
        }
    }
}
