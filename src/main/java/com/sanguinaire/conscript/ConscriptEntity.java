package com.sanguinaire.conscript;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.TransformationManager;
import com.sanguinaire.registry.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.InteractionHand;
import com.sanguinaire.registry.ModEntities;
import net.minecraftforge.event.ForgeEventFactory;
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
    /** Damage of a melee hit, whatever weapon is held (the axe's own bonus is not added on top). */
    public static final double ATTACK_DAMAGE = 10.0D;
    /** Damage of a crossbow bolt (applied in {@link ConscriptHunts#onHurt}). */
    public static final float CROSSBOW_DAMAGE = 8.0F;
    /** Persistent-data tag marking a bolt shot by a Conscript. */
    public static final String BOLT_TAG = Sanguinaire.MOD_ID + ":conscript_bolt";
    /** Same as a vindicator. */
    public static final double MOVE_SPEED = 0.35D;
    private static final int NULLIFY_COOLDOWN = 30 * 20;
    private static final double NULLIFY_RANGE = 12.0D;
    private static final int FIRE_RES_COOLDOWN = 60 * 20;
    private static final int FIRE_RES_DURATION = 60 * 20;
    private static final float HEAL_AMOUNT = 15.0F;
    private static final int FLEE_TICKS = 50;
    private static final int HORN_COOLDOWN = 30 * 20;
    private static final int GRUMBLE_COOLDOWN = 60;
    /** Out of combat (no target, not hurt for this long) they eat bread until healed. */
    private static final int OUT_OF_COMBAT_TICKS = 10 * 20;
    private static final float BREAD_HEAL = 8.0F;
    private static final int EAT_TICKS = 32;
    private static final double SCARE_RANGE = 10.0D;
    private static final int ANGER_TICKS = 60 * 20;
    /** A hunting party gives up after this long without seeing its quarry. */
    public static final int GIVE_UP_TICKS = 3 * 60 * 20;
    /** Hunting parties know roughly where their quarry is: they re-read the trail this often. */
    private static final int TRAIL_REFRESH_TICKS = 30 * 20;

    private static final EntityDataAccessor<Boolean> CHARGING_CROSSBOW =
            SynchedEntityData.defineId(ConscriptEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DRINKING =
            SynchedEntityData.defineId(ConscriptEntity.class, EntityDataSerializers.BOOLEAN);
    private static final UUID FLEE_BOOST_ID = UUID.fromString("5CD17E52-A79A-43D3-A529-90FDE04B181E");
    private static final AttributeModifier FLEE_BOOST =
            new AttributeModifier(FLEE_BOOST_ID, "Fleeing to heal", 0.5D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private static final int DRINK_NONE = 0;
    private static final int DRINK_HEAL = 1;
    private static final int DRINK_FIRE = 2;
    private static final int EAT_BREAD = 3;

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
    private int grumbleCooldown;
    private int lastHurtTick = -OUT_OF_COMBAT_TICKS;
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
        setDropChance(EquipmentSlot.MAINHAND, 1.0F); // always drops its weapon, at random durability
        setDropChance(EquipmentSlot.OFFHAND, 0.0F); // the golden apple is only borrowed
        reassessWeaponGoal();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, MOVE_SPEED)
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
        // Priority 2: above the patrol/raid wandering goals (priority 3-4), which would otherwise block it forever.
        goalSelector.addGoal(2, new CureComradeGoal());
        goalSelector.addGoal(5, new TrackQuarryGoal());
        goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 15.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 15.0F));

        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isHostileTowards));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Zombie.class, 10, true, false,
                e -> !(e instanceof ZombifiedPiglin) && !(e instanceof ZombifiedConscript)
                        && e.distanceToSqr(this) <= ZOMBIE_RANGE * ZOMBIE_RANGE));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractSkeleton.class, 10, true, false,
                e -> e.distanceToSqr(this) <= ZOMBIE_RANGE * ZOMBIE_RANGE));
        // Villagers are only attacked during a raid; otherwise they just run from Conscripts.
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, 10, true, false,
                e -> getCurrentRaid() != null));
    }

    // ---- Who is an enemy -----------------------------------------------------------------------------------------

    /** Players this Conscript will attack: the hunted (from afar), provokers, and anyone during a raid. */
    public boolean isHostileTowards(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.isCreative() || player.isSpectator()
                || TransformationManager.ignoresPlayer(this, player)) {
            return false; // includes players disguised as villagers
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
        if (hurt && !level().isClientSide()) {
            lastHurtTick = tickCount;
            if (isDrinking() && drinkKind == EAT_BREAD) {
                restoreWeapon(); // dinner is over
            }
            if (source.getEntity() instanceof Player player && !ConscriptHunts.isHunted(player)) {
                angerGroupAt(player);
            }
        }
        return hurt;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target instanceof ZombifiedConscript) {
            target = null; // a fallen comrade is to be saved, not killed
        }
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
        }
        angryPuff();
        for (ConscriptEntity other : group) {
            if (other != this && other.getTarget() == null) {
                other.suppressAlert = true;
                other.setTarget(player);
                other.suppressAlert = false;
                other.sawQuarry(player);
                if (other.getTarget() == player) {
                    other.angryPuff(); // every Conscript now chasing you
                }
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
        if (grumbleCooldown > 0) grumbleCooldown--;
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
            scareVillagers();
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
        } else if (++ticksSinceSighted % TRAIL_REFRESH_TICKS == 0 && !TransformationManager.ignoresPlayer(this, quarry)) {
            lastKnownPos = quarry.position(); // they follow the trail, but only now and then (and not a villager's)
        }
        if (ticksSinceSighted > GIVE_UP_TICKS) {
            ConscriptHunts.onSquadGaveUp(quarry, squadId);
            stopHunting();
        }
    }

    /** Grumbles at ordinary players who come close, and turns the group on those who come within 4 blocks. */
    private void checkNearbyPlayers() {
        if (getCurrentRaid() != null) {
            return;
        }
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(WARN_RANGE))) {
            if (player.isCreative() || player.isSpectator() || ConscriptHunts.isHunted(player) || isAngryAt(player)
                    || TransformationManager.ignoresPlayer(this, player)) {
                continue;
            }
            double distanceSq = distanceToSqr(player);
            if (distanceSq <= PROVOKE_RANGE * PROVOKE_RANGE) {
                angerGroupAt(player);
                grumble();
            } else if (distanceSq <= WARN_RANGE * WARN_RANGE && grumbleCooldown <= 0) {
                getLookControl().setLookAt(player);
                grumble();
            }
        }
    }

    /** Angry villager particles and an illager mutter: keep away. */
    private void grumble() {
        grumbleCooldown = GRUMBLE_COOLDOWN + random.nextInt(40);
        angryPuff();
        playSound(SoundEvents.EVOKER_CELEBRATE, 1.0F, 1.0F);
    }

    private void angryPuff() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getEyeY() + 0.4D, getZ(), 3, 0.3D, 0.2D, 0.3D, 0.0D);
        }
    }

    /** Outside raids, villagers run from Conscripts. */
    private void scareVillagers() {
        if (getCurrentRaid() != null) {
            return;
        }
        for (Villager villager : level().getEntitiesOfClass(Villager.class, getBoundingBox().inflate(SCARE_RANGE))) {
            if (villager.isSleeping() || villager.isPassenger()) {
                continue;
            }
            Vec3 away = DefaultRandomPos.getPosAway(villager, 16, 7, position());
            if (away != null) {
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(away, 0.6F, 0));
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
            if (drinkKind == EAT_BREAD) {
                if (getTarget() != null) {
                    restoreWeapon(); // back to work
                    return;
                }
                if (drinkTicks % 4 == 0) {
                    chew();
                }
            } else if (drinkTicks % 4 == 0) {
                // Gulping it down, like a player drinking.
                playSound(SoundEvents.GENERIC_DRINK, 0.5F, random.nextFloat() * 0.1F + 0.9F);
            }
            if (--drinkTicks <= 0) {
                finishDrinking();
            }
            return;
        }
        if (fleeTicks > 0) {
            if (--fleeTicks == 0) {
                setFleeBoost(false);
                startDrinking(DRINK_HEAL);
            }
            return;
        }
        if (!usedHealingPotion && getHealth() <= getMaxHealth() / 2.0F && isAlive()) {
            usedHealingPotion = true; // once per Conscript, ever
            fleeTicks = getTarget() != null ? FLEE_TICKS : 1;
            setFleeBoost(getTarget() != null);
            return;
        }
        if (isOnFire() && fireResCooldown <= 0 && !hasEffect(MobEffects.FIRE_RESISTANCE)) {
            fireResCooldown = FIRE_RES_COOLDOWN;
            startDrinking(DRINK_FIRE);
            return;
        }
        if (getHealth() < getMaxHealth() && getTarget() == null && !isOnFire()
                && tickCount - lastHurtTick >= OUT_OF_COMBAT_TICKS && isAlive()) {
            startDrinking(EAT_BREAD);
        }
    }

    private void startDrinking(int kind) {
        drinkKind = kind;
        drinkTicks = kind == DRINK_FIRE ? 20 : kind == EAT_BREAD ? EAT_TICKS : 32;
        storedWeapon = getMainHandItem().copy();
        entityData.set(DRINKING, true);
        if (kind == EAT_BREAD) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BREAD));
            return;
        }
        ItemStack potion = PotionUtils.setPotion(new ItemStack(Items.POTION),
                kind == DRINK_FIRE ? Potions.FIRE_RESISTANCE : Potions.HEALING);
        setItemSlot(EquipmentSlot.MAINHAND, potion);
    }

    private void chew() {
        playSound(SoundEvents.GENERIC_EAT, 0.5F + 0.5F * random.nextInt(2), (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 mouth = getEyePosition().add(getLookAngle().scale(0.3D)).subtract(0.0D, 0.15D, 0.0D);
            serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BREAD)),
                    mouth.x, mouth.y, mouth.z, 4, 0.1D, 0.05D, 0.1D, 0.05D);
        }
    }

    private void finishDrinking() {
        if (drinkKind == EAT_BREAD) {
            heal(BREAD_HEAL);
            restoreWeapon();
            return;
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_DRINK, getSoundSource(), 1.0F,
                0.8F + random.nextFloat() * 0.4F); // the final swallow, as the potion takes effect
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
        reassessWeaponGoal();
    }

    /** Runs away fast while fleeing to heal; back to normal speed once drinking. */
    private void setFleeBoost(boolean boost) {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (boost && !speed.hasModifier(FLEE_BOOST)) {
            speed.addTransientModifier(FLEE_BOOST);
        } else if (!boost) {
            speed.removeModifier(FLEE_BOOST);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (isDrinking()) {
            restoreWeapon(); // drop the weapon, not the half-drunk potion
        }
        setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        if (tryRiseAsUndead(source)) {
            return;
        }
        super.die(source);
    }

    /** Killed by an undead mob: rises as a Zombified Conscript instead of dying. */
    private boolean tryRiseAsUndead(DamageSource source) {
        if (!(level() instanceof ServerLevel serverLevel) || !(source.getEntity() instanceof LivingEntity killer)
                || killer.getMobType() != MobType.UNDEAD || killer instanceof Player || isRemoved()
                || !ForgeEventFactory.canLivingConvert(this, ModEntities.ZOMBIFIED_CONSCRIPT.get(), timer -> { })) {
            return false;
        }
        ItemStack weapon = getMainHandItem().copy();
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        ZombifiedConscript zombie = convertTo(ModEntities.ZOMBIFIED_CONSCRIPT.get(), false);
        if (zombie == null) {
            setItemSlot(EquipmentSlot.MAINHAND, weapon);
            return false;
        }
        zombie.setOriginalWeapon(weapon);
        serverLevel.levelEvent(null, 1026, zombie.blockPosition(), 0); // "zombie infects villager" sound
        ForgeEventFactory.onLivingConvert(this, zombie);
        return true;
    }

    // ---- Weapons ---------------------------------------------------------------------------------------------------

    /** The weapon's own damage bonus is ignored: every melee hit deals {@value #ATTACK_DAMAGE} (plus enchantments). */
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
        projectile.getPersistentData().putBoolean(BOLT_TAG, true);
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
        if (isCelebrating()) {
            return AbstractIllager.IllagerArmPose.CELEBRATING;
        }
        // Drinking, eating or carrying a golden apple: arms out, item in hand. Otherwise at ease, arms crossed.
        if (isDrinking() || !getOffhandItem().isEmpty()) {
            return AbstractIllager.IllagerArmPose.NEUTRAL;
        }
        return AbstractIllager.IllagerArmPose.CROSSED;
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
        setDropChance(EquipmentSlot.MAINHAND, 1.0F); // older Conscripts saved the vanilla chance
        if (getOffhandItem().is(Items.GOLDEN_APPLE)) {
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY); // saved mid-cure
        }
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

    /**
     * While drinking the potion is in the weapon hand, so no attacking, but they keep moving at normal speed: away from
     * the target while drinking the healing potion, after it while drinking Fire Resistance.
     */
    private class BusyGoal extends Goal {
        private int repath;

        BusyGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isDrinking();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                if (drinkKind == EAT_BREAD) {
                    getNavigation().stop(); // sit down to eat
                }
                return; // otherwise keep whatever path they were on
            }
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--repath > 0) {
                return;
            }
            repath = 10;
            if (drinkKind == DRINK_HEAL) {
                Vec3 away = DefaultRandomPos.getPosAway(ConscriptEntity.this, 16, 7, target.position());
                if (away != null) {
                    getNavigation().moveTo(away.x, away.y, away.z, 1.0D);
                }
            } else if (distanceToSqr(target) > 9.0D) {
                getNavigation().moveTo(target, 1.0D);
            } else {
                getNavigation().stop();
            }
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
                getNavigation().moveTo(away.x, away.y, away.z, 1.2D);
            }
        }
    }

    /**
     * A fallen comrade risen as a Zombified Conscript is saved, not killed: out of combat, a Conscript splashes it with
     * Weakness (thrown like the Nullification Potion, so it can miss), then walks up and feeds it a golden apple.
     */
    private class CureComradeGoal extends Goal {
        private static final double SEARCH_RANGE = 16.0D;
        @Nullable
        private ZombifiedConscript patient;
        private int throwCooldown;
        private int repath;
        private int timeout;
        private int searchDelay;

        CureComradeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean busy() {
            return getTarget() != null || isDrinking() || isFleeingToHeal();
        }

        @Override
        public boolean canUse() {
            // Own countdown: canUse() is only polled every other tick, so a tickCount % 10 check could never pass.
            if (busy() || --searchDelay > 0) {
                return false;
            }
            searchDelay = 10;
            patient = null;
            double best = Double.MAX_VALUE;
            for (ZombifiedConscript zombie : level().getEntitiesOfClass(ZombifiedConscript.class, getBoundingBox().inflate(SEARCH_RANGE))) {
                double distanceSq = distanceToSqr(zombie);
                if (zombie.isAlive() && !zombie.isCuring() && distanceSq < best && hasLineOfSight(zombie)) {
                    best = distanceSq;
                    patient = zombie;
                }
            }
            return patient != null;
        }

        @Override
        public boolean canContinueToUse() {
            return patient != null && patient.isAlive() && !patient.isCuring() && !busy() && timeout > 0
                    && distanceToSqr(patient) <= 24.0D * 24.0D;
        }

        @Override
        public void start() {
            timeout = 60 * 20;
            repath = 0;
        }

        @Override
        public void stop() {
            if (getOffhandItem().is(Items.GOLDEN_APPLE)) {
                setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            }
            patient = null;
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (patient == null) {
                return;
            }
            timeout--;
            if (throwCooldown > 0) {
                throwCooldown--;
            }
            getLookControl().setLookAt(patient, 30.0F, 30.0F);
            double distanceSq = distanceToSqr(patient);
            if (!patient.hasEffect(MobEffects.WEAKNESS)) {
                // Step 1: weaken it.
                if (distanceSq <= 10.0D * 10.0D && distanceSq >= 2.5D * 2.5D && throwCooldown <= 0 && hasLineOfSight(patient)) {
                    getNavigation().stop();
                    throwWeakness(patient);
                    throwCooldown = 60 + random.nextInt(20);
                } else if (distanceSq > 7.0D * 7.0D && --repath <= 0) {
                    repath = 10;
                    getNavigation().moveTo(patient, 1.0D);
                } else if (distanceSq < 2.5D * 2.5D && --repath <= 0) {
                    repath = 10;
                    Vec3 away = DefaultRandomPos.getPosAway(ConscriptEntity.this, 6, 3, patient.position());
                    if (away != null) {
                        getNavigation().moveTo(away.x, away.y, away.z, 1.0D);
                    }
                }
                return;
            }
            // Step 2: feed it a golden apple.
            if (!getOffhandItem().is(Items.GOLDEN_APPLE)) {
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLDEN_APPLE));
            }
            if (distanceSq <= 2.2D * 2.2D) {
                swing(InteractionHand.OFF_HAND);
                playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
                setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                patient.startCuring();
            } else if (--repath <= 0) {
                repath = 10;
                getNavigation().moveTo(patient, 1.1D);
            }
        }

        private void throwWeakness(ZombifiedConscript target) {
            Vec3 motion = target.getDeltaMovement();
            double dx = target.getX() + motion.x - getX();
            double dy = target.getEyeY() - 1.1D - getY();
            double dz = target.getZ() + motion.z - getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            ThrownPotion potion = new ThrownPotion(level(), ConscriptEntity.this);
            potion.setItem(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.WEAKNESS));
            potion.setXRot(potion.getXRot() - 20.0F);
            potion.shoot(dx, dy + horizontal * 0.2D, dz, 0.75F, 8.0F);
            swing(InteractionHand.MAIN_HAND);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_THROW, getSoundSource(), 1.0F,
                    0.8F + random.nextFloat() * 0.4F);
            level().addFreshEntity(potion);
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
