package com.sanguinaire.enchantment;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.bodypart.BodyPartDropTable;
import com.sanguinaire.item.SurgicalSawItem;
import com.sanguinaire.registry.ModEnchantments;
import com.sanguinaire.research.ResearchManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Applies Vivisection's bonus on direct melee hits with an enchanted Surgical Saw. Server only. */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class VivisectionEvents {
    private VivisectionEvents() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getSource().getDirectEntity() != player) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!(weapon.getItem() instanceof SurgicalSawItem)
                || EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.VIVISECTION.get(), weapon) <= 0) {
            return;
        }
        if (isFullyResearched(player, event.getEntity().getType())) {
            event.setAmount(event.getAmount() + VivisectionEnchantment.BONUS_DAMAGE);
        }
    }

    /** True if the creature has specimens and the player has researched every one of them (its journal entry is complete). */
    public static boolean isFullyResearched(Player player, EntityType<?> type) {
        List<BodyPartDropTable.Entry> entries = BodyPartDropTable.getEntries(type);
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        for (BodyPartDropTable.Entry entry : entries) {
            if (!ResearchManager.isResearched(player, entry.item().get())) {
                return false;
            }
        }
        return true;
    }
}
