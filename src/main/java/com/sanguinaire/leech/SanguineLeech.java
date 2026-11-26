package com.sanguinaire.leech;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Sanguine Leech: a small bloodsucker of swamps and mangroves. It comes out onto land at night (natural spawns need
 * darkness, solid ground and night time), but water is its element: it breathes underwater, hangs in the water
 * without sinking and swims fast, much faster than it crawls (crawling is a silverfish's pace).
 * <p>
 * 6 health, 2 attack damage; every bite that lands heals it 2 health. Sounds are a silverfish's, pitched down.
 */
public class SanguineLeech extends Monster {
    /** Silverfish speed on land. */
    private static final double MOVE_SPEED = 0.25D;
    /** Swim speed relative to the movement attribute: about 6 blocks per second, a little faster than a dolphin cruises. */
    private static final float WATER_SPEED_MODIFIER = 0.12F;
    private static final float PITCH = 0.6F;
    private static final float BITE_HEAL = 2.0F;

    private final MoveControl landControl;
    private final MoveControl swimControl;

    public SanguineLeech(EntityType<? extends SanguineLeech> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.landControl = this.moveControl;
        this.swimControl = new SmoothSwimmingMoveControl(this, 85, 10, WATER_SPEED_MODIFIER, 1.0F, false);
    }

    /**
     * Uses the swimming move control in water and the ordinary one (which can jump up blocks) on land. Navigation
     * hands the next waypoint to the current control every tick; it is copied over when the control changes.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        MoveControl wanted = this.isInWater() ? this.swimControl : this.landControl;
        if (wanted != this.moveControl) {
            MoveControl previous = this.moveControl;
            this.moveControl = wanted;
            if (previous.hasWanted()) {
                wanted.setWantedPosition(previous.getWantedX(), previous.getWantedY(), previous.getWantedZ(), previous.getSpeedModifier());
            }
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, MOVE_SPEED);
    }

    /** Monster spawn rules (darkness, solid ground) and, for natural spawns, night time. */
    public static boolean checkLeechSpawnRules(EntityType<? extends Monster> type, ServerLevelAccessor level, MobSpawnType reason,
                                               BlockPos pos, RandomSource random) {
        if (reason == MobSpawnType.NATURAL && level.getLevel().isDay()) {
            return false;
        }
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    /** In water it swims without gravity (so it floats in place when still); on land it crawls normally. */
    @Override
    public void travel(Vec3 input) {
        if (this.isControlledByLocalInstance() && this.isInWater()) {
            this.moveRelative(this.getSpeed(), input);
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
        } else {
            super.travel(input);
        }
    }

    /** Each bite that lands feeds the leech. */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            this.heal(BITE_HEAL);
        }
        return hit;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SILVERFISH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SILVERFISH_STEP, 0.15F, PITCH);
    }

    /** Silverfish sounds, a good deal deeper. */
    @Override
    public float getVoicePitch() {
        return (this.random.nextFloat() - this.random.nextFloat()) * 0.1F + PITCH;
    }
}
