package com.sanguinaire.event;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.bodypart.BodyPartDropTable;
import com.sanguinaire.registry.ModItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Adds the Surgical Saw's special body-part drop on top of a mob's normal loot.
 * <p>
 * {@link LivingDropsEvent} fires exactly once per death on the logical server, after the loot table has been
 * collected, so the extra drop is appended to the normal drops (never replacing them) and cannot be duplicated.
 * <p>
 * Qualifying kill: the killing damage is a direct melee player attack ({@code minecraft:player_attack}) by a player
 * whose main hand holds the Surgical Saw. Projectiles, thorns, fire, sweeps from other items and indirect deaths do
 * not qualify. The {@code doMobLoot} game rule is respected, as for normal loot.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class BodyPartDropEvents {

    private BodyPartDropEvents() {
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();
        if (level.isClientSide()) {
            return;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }

        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player player) || source.getDirectEntity() != player) {
            return;
        }
        if (!source.is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        if (!player.getMainHandItem().is(ModItems.SURGICAL_SAW.get())) {
            return;
        }

        if (BodyPartDropTable.dropsAll(victim.getType())) {
            for (BodyPartDropTable.Entry entry : BodyPartDropTable.getEntries(victim.getType())) {
                addDrop(event, victim, entry.item().get());
            }
            return;
        }
        Item material = BodyPartDropTable.roll(victim.getType(), victim.getRandom());
        if (material == null) {
            return;
        }
        addDrop(event, victim, material);
    }

    private static void addDrop(LivingDropsEvent event, LivingEntity victim, Item material) {
        ItemEntity drop = new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), new ItemStack(material));
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }
}
