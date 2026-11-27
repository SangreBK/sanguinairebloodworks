package com.sanguinaire.item;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.menu.BriefcaseItemHandler;
import com.sanguinaire.menu.SerumBriefcaseMenu;
import com.sanguinaire.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Portable storage for serums and Empty Syringes. Right-click to open.
 * <p>
 * The menu is opened on the server through {@link NetworkHooks#openScreen}; the hand used is sent to the client so
 * both sides build an identical menu bound to the same inventory slot.
 * <p>
 * Takes only its own enchantments (table or anvil): Tough Case (+{@value #TOUGH_CASE_ARMOR} armor in the offhand),
 * Serum Gamble and Brief Management (see {@code SanguinaireEnchantment}).
 */
public class SerumBriefcaseItem extends Item {
    public static final int TOUGH_CASE_ARMOR = 10;
    private static final java.util.UUID TOUGH_CASE_UUID = java.util.UUID.fromString("8e2d4c1a-6b3f-4a7e-9d05-1f2c3b4a5d6e");
    private static final com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> TOUGH_CASE_MODIFIERS =
            com.google.common.collect.ImmutableMultimap.of(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(TOUGH_CASE_UUID, "Tough Case",
                            TOUGH_CASE_ARMOR, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));

    public SerumBriefcaseItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((containerId, inventory, p) -> new SerumBriefcaseMenu(containerId, inventory, hand),
                            stack.getHoverName()),
                    buffer -> buffer.writeEnum(hand));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8F, 0.8F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Tough Case: armor while carried in the offhand (shown by vanilla as "When in Off Hand: +10 Armor"). */
    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> getAttributeModifiers(
            net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
        if (slot == net.minecraft.world.entity.EquipmentSlot.OFFHAND
                && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                com.sanguinaire.registry.ModEnchantments.TOUGH_CASE.get(), stack) > 0) {
            return TOUGH_CASE_MODIFIERS;
        }
        return super.getAttributeModifiers(slot, stack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 10; // like iron
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, net.minecraft.world.item.enchantment.Enchantment enchantment) {
        return enchantment.category == com.sanguinaire.enchantment.SanguinaireEnchantment.SERUM_BRIEFCASE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int serums = 0;
        int syringes = 0;
        for (ItemStack content : BriefcaseItemHandler.readContents(stack)) {
            if (content.is(ModItems.SERUM.get())) {
                serums += content.getCount();
            } else if (content.is(ModItems.EMPTY_SYRINGE.get())) {
                syringes += content.getCount();
            }
        }
        if (serums == 0 && syringes == 0) {
            tooltip.add(Component.translatable("tooltip." + Sanguinaire.MOD_ID + ".serum_briefcase.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (serums > 0) {
            tooltip.add(Component.translatable("tooltip." + Sanguinaire.MOD_ID + ".serum_briefcase.serums", serums)
                    .withStyle(ChatFormatting.DARK_RED));
        }
        if (syringes > 0) {
            tooltip.add(Component.translatable("tooltip." + Sanguinaire.MOD_ID + ".serum_briefcase.syringes", syringes)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
