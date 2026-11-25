package com.sanguinaire.conscript;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.TransformationManager;
import com.sanguinaire.registry.ModEntities;
import com.sanguinaire.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

/**
 * A thrown Nullification Potion. Where it shatters, every transformed player within {@value #RADIUS} blocks is turned
 * back into a human at once (the transformation ends as if its time had run out) and gets Nausea II for 4 seconds.
 */
public class ThrownNullificationPotion extends ThrowableItemProjectile {
    public static final double RADIUS = 4.0D;
    /** Splash colour: a pale, sickly grey-blue. */
    public static final int COLOR = 0xB8C7D9;
    private static final int NAUSEA_TICKS = 4 * 20;

    public ThrownNullificationPotion(EntityType<? extends ThrownNullificationPotion> type, Level level) {
        super(type, level);
    }

    public ThrownNullificationPotion(Level level, LivingEntity thrower) {
        super(ModEntities.NULLIFICATION_POTION.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.NULLIFICATION_POTION.get();
    }

    @Override
    protected float getGravity() {
        return 0.05F; // like a splash potion
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide()) {
            return;
        }
        AABB area = getBoundingBox().inflate(RADIUS, 2.0D, RADIUS);
        for (ServerPlayer player : level().getEntitiesOfClass(ServerPlayer.class, area)) {
            if (player.distanceToSqr(this) <= RADIUS * RADIUS && TransformationManager.isTransformed(player)) {
                TransformationManager.end(player);
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS, 1), getOwner());
                player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".nullified")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
        }
        level().levelEvent(2002, blockPosition(), COLOR); // splash particles + glass sound
        discard();
    }
}
