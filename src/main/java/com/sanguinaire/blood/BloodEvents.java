package com.sanguinaire.blood;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.TransformationManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Monster blood in a transformation serum: while transformed, +10 damage on everything you deal and 4 less damage
 * on everything you take. Runs early so other bonuses (e.g. armor piercing) see the full amount.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class BloodEvents {
    private static final float MONSTER_DAMAGE_BONUS = 10.0F;
    private static final float MONSTER_DAMAGE_REDUCTION = 4.0F;

    private BloodEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof Player attacker && attacker != event.getEntity() && TransformationManager.hasMonsterBlood(attacker)) {
            event.setAmount(event.getAmount() + MONSTER_DAMAGE_BONUS);
        }
        if (event.getEntity() instanceof Player victim && TransformationManager.hasMonsterBlood(victim)) {
            event.setAmount(Math.max(0.0F, event.getAmount() - MONSTER_DAMAGE_REDUCTION));
        }
    }
}
