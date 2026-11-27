package com.sanguinaire.enchantment;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.item.SerumBriefcaseItem;
import com.sanguinaire.item.SerumItem;
import com.sanguinaire.menu.BriefcaseItemHandler;
import com.sanguinaire.menu.SerumBriefcaseMenu;
import com.sanguinaire.registry.ModEnchantments;
import com.sanguinaire.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <b>Serum Gamble</b> (Serum Briefcase): when its owner takes {@value #GAMBLE_THRESHOLD} or more damage in a single
 * hit (after armor), the first briefcase with Serum Gamble in their inventory injects one of its serums at random.
 * Serums that can't take effect right now (a transformation serum while transformed, Tainted Blood twice) are skipped
 * for another. Half the time the serum is used for free; otherwise it is spent and its empty syringe goes back into
 * the case (or the inventory, if the case is full). Nothing happens while that briefcase's menu is open, so its
 * contents can never be edited from two places at once.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class EquipmentEnchantmentEvents {
    public static final float GAMBLE_THRESHOLD = 8.0F;
    private static final float FREE_CHANCE = 0.5F;

    private EquipmentEnchantmentEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() < GAMBLE_THRESHOLD || !player.isAlive()
                || player.containerMenu instanceof SerumBriefcaseMenu) {
            return;
        }
        ItemStack briefcase = findGamblingBriefcase(player.getInventory());
        if (briefcase.isEmpty()) {
            return;
        }
        BriefcaseItemHandler handler = new BriefcaseItemHandler();
        List<ItemStack> rejected = handler.loadFrom(briefcase);
        rejected.forEach(stack -> player.getInventory().placeItemBackInInventory(stack));
        List<Integer> serumSlots = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).is(ModItems.SERUM.get())) {
                serumSlots.add(slot);
            }
        }
        Collections.shuffle(serumSlots, new java.util.Random(player.getRandom().nextLong()));
        for (int slot : serumSlots) {
            ItemStack serum = handler.getStackInSlot(slot);
            Component name = serum.getHoverName();
            if (!SerumItem.injectFromBriefcase(player, serum)) {
                continue;
            }
            boolean free = player.getRandom().nextFloat() < FREE_CHANCE;
            if (!free) {
                serum.shrink(1);
                handler.setStackInSlot(slot, serum.isEmpty() ? ItemStack.EMPTY : serum);
                ItemStack syringe = new ItemStack(ModItems.EMPTY_SYRINGE.get());
                ItemStack left = ItemHandlerHelper.insertItemStacked(handler, syringe, false);
                if (!left.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(left);
                }
            }
            handler.saveTo(briefcase);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER,
                    SoundSource.PLAYERS, 0.8F, free ? 1.4F : 0.8F);
            player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID
                    + (free ? ".serum_gamble_free" : ".serum_gamble"), name).withStyle(ChatFormatting.GOLD), false);
            return;
        }
    }

    private static ItemStack findGamblingBriefcase(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof SerumBriefcaseItem
                    && EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.SERUM_GAMBLE.get(), stack) > 0) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
