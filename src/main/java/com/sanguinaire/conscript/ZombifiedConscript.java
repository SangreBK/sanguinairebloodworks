package com.sanguinaire.conscript;

import com.sanguinaire.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * A Conscript killed by an undead mob rises as one of them. It behaves like an ordinary zombie (burns in daylight,
 * hunts players and villagers, zombie damage), but never turns into a drowned.
 * <p>
 * Living Conscripts try to save it: a splash of Weakness, then a golden apple ({@link #startCuring}), after which it
 * shakes for 10 seconds and becomes a Conscript again, its old weapon back in hand.
 */
public class ZombifiedConscript extends Zombie {
    public static final int CURE_TICKS = 10 * 20;

    private static final EntityDataAccessor<Boolean> CURING =
            SynchedEntityData.defineId(ZombifiedConscript.class, EntityDataSerializers.BOOLEAN);

    private int cureTicks;
    /** The weapon it carried in life; it fights bare-handed now, and gets it back if cured. */
    private ItemStack originalWeapon = ItemStack.EMPTY;

    public ZombifiedConscript(EntityType<? extends ZombifiedConscript> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CURING, false);
    }

    public void setOriginalWeapon(ItemStack weapon) {
        this.originalWeapon = weapon.copy();
    }

    public boolean isCuring() {
        return entityData.get(CURING);
    }

    /** The golden apple went down: shakes for 10 seconds, then is a Conscript again. Server only. */
    public void startCuring() {
        if (isCuring()) {
            return;
        }
        cureTicks = CURE_TICKS;
        entityData.set(CURING, true);
        removeEffect(MobEffects.WEAKNESS);
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, CURE_TICKS, 0));
        setTarget(null);
        playSound(SoundEvents.ZOMBIE_VILLAGER_CURE, 1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && isAlive() && isCuring() && --cureTicks <= 0) {
            finishCuring((ServerLevel) level());
        }
    }

    private void finishCuring(ServerLevel level) {
        if (!ForgeEventFactory.canLivingConvert(this, ModEntities.CONSCRIPT.get(), timer -> cureTicks = timer)) {
            return;
        }
        ConscriptEntity conscript = convertTo(ModEntities.CONSCRIPT.get(), false);
        if (conscript == null) {
            return;
        }
        conscript.setItemSlot(EquipmentSlot.MAINHAND, originalWeapon.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.IRON_AXE) : originalWeapon);
        conscript.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, conscript.getX(), conscript.getEyeY(), conscript.getZ(), 8, 0.4D, 0.4D, 0.4D, 0.0D);
        level.levelEvent(null, 1027, conscript.blockPosition(), 0); // "zombie villager converted" sound
        ForgeEventFactory.onLivingConvert(this, conscript);
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return !isCuring() && super.removeWhenFarAway(distanceSq);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (!originalWeapon.isEmpty() && recentlyHit && source.getEntity() instanceof Player) {
            spawnAtLocation(originalWeapon.copy());
            originalWeapon = ItemStack.EMPTY;
        }
    }

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.ZOMBIE_VILLAGER_STEP;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CureTicks", isCuring() ? cureTicks : -1);
        if (!originalWeapon.isEmpty()) {
            tag.put("OriginalWeapon", originalWeapon.save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("CureTicks") && tag.getInt("CureTicks") >= 0) {
            cureTicks = tag.getInt("CureTicks");
            entityData.set(CURING, true);
        }
        originalWeapon = tag.contains("OriginalWeapon") ? ItemStack.of(tag.getCompound("OriginalWeapon")) : ItemStack.EMPTY;
    }
}
