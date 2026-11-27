package com.sanguinaire.mutation;

import com.sanguinaire.client.ClientHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * An invisible second hitbox for the Ender Dragon form, around the raised body of the full-size model (the player's
 * own hitbox stays small and low so the dragon can move about). It follows its owner every tick, and anything that
 * hits it (arrows, other players' swings, mob attacks) hurts the owner instead. It takes no explosion damage (the
 * owner is hit by the same explosion already), cannot be pushed or collided with, is never saved, and its owner's
 * own crosshair and projectiles pass through it. Spawned and kept alive by {@link DragonForm}.
 */
public class DragonHitbox extends Entity {
    /** How far above the owner's feet the box starts (the player's own hitbox is 1.5 tall). */
    public static final double OFFSET_Y = 1.5D;
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(DragonHitbox.class, EntityDataSerializers.INT);

    public DragonHitbox(EntityType<? extends DragonHitbox> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, -1);
    }

    public void setOwner(Player owner) {
        entityData.set(OWNER, owner.getId());
        follow(owner);
    }

    @Nullable
    public Player getOwner() {
        Entity entity = level().getEntity(entityData.get(OWNER));
        return entity instanceof Player player ? player : null;
    }

    private void follow(Player owner) {
        setPos(owner.getX(), owner.getY() + OFFSET_Y, owner.getZ());
    }

    @Override
    public void tick() {
        super.tick();
        Player owner = getOwner();
        if (!level().isClientSide()) {
            if (owner == null || !owner.isAlive() || owner.level() != level()
                    || TransformationManager.getActive(owner) != MutationType.ENDER_DRAGON) {
                discard();
                return;
            }
        }
        if (owner != null) {
            follow(owner);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || isInvulnerableTo(source) || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        Player owner = getOwner();
        if (owner == null || source.getEntity() == owner || source.getDirectEntity() == owner) {
            return false;
        }
        return owner.hurt(source, amount);
    }

    /** Pickable by everyone except its owner, whose own aim goes straight through it. */
    @Override
    public boolean isPickable() {
        if (level().isClientSide()) {
            Boolean own = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ClientHooks.isLocalPlayer(entityData.get(OWNER)));
            return own == null || !own;
        }
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
