package com.sanguinaire.mutation;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.item.VileCaneItem;
import com.sanguinaire.player.PlayerMutationData;
import com.sanguinaire.player.PlayerMutationProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * The Vile Cane's effects that depend on its bearer (see {@link VileCaneItem}): cane-mode step height and speed,
 * kept as transient attribute modifiers while a human holds the cane in cane mode in the main hand (attributes are
 * synced, so the client moves accordingly), and the sword-mode bonus damage from Weakened Hearts.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class VileCaneEvents {
    private static final UUID STEP_UUID = UUID.fromString("0e3a9c52-7d41-4b6e-a1f8-2c5d7e9b3f10");
    private static final UUID SPEED_UUID = UUID.fromString("8f1b4d6a-2c3e-4a5b-9d7f-1e2a3b4c5d6e");
    /** 0.6 vanilla step + 0.4 = a full block. */
    private static final double STEP_BONUS = 0.4D;

    private VileCaneEvents() {
    }

    /** The cane in the main hand of a human (never while transformed), or empty. */
    public static ItemStack heldCane(Player player) {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof VileCaneItem && !TransformationManager.isTransformed(player) ? stack : ItemStack.EMPTY;
    }

    private static float weakenedHealth(Player player) {
        return PlayerMutationProvider.get(player).map(PlayerMutationData::getWeakenedHealth).orElse(0.0F);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ItemStack cane = heldCane(player);
        boolean caneMode = !cane.isEmpty() && !VileCaneItem.isSwordMode(cane);
        double speed = caneMode ? VileCaneItem.speedSteps(weakenedHealth(player)) * VileCaneItem.SPEED_PER_STEP_PERCENT / 100.0D : 0.0D;
        apply(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_UUID, "Vile Cane step", caneMode ? STEP_BONUS : 0.0D,
                AttributeModifier.Operation.ADDITION);
        apply(player, Attributes.MOVEMENT_SPEED, SPEED_UUID, "Vile Cane speed", speed, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    /** Keeps exactly one transient modifier with this amount (none for 0). */
    private static void apply(Player player, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && current.getAmount() == amount) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        if (amount != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
        }
    }

    /** Sword mode: +1 damage on a melee hit for every point (half heart) of Weakened Hearts. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer player) || !event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        ItemStack cane = heldCane(player);
        if (!cane.isEmpty() && VileCaneItem.isSwordMode(cane)) {
            event.setAmount(event.getAmount() + VileCaneItem.swordBonus(weakenedHealth(player)));
        }
    }
}
