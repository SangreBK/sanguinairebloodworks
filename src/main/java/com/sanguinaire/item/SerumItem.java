package com.sanguinaire.item;

import com.sanguinaire.blood.BloodInfusion;
import com.sanguinaire.registry.ModEffects;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.TransformationManager;
import com.sanguinaire.mutation.TransformationSequence;
import com.sanguinaire.mutation.UtilitySerum;
import com.sanguinaire.network.ModNetwork;
import com.sanguinaire.player.PlayerMutationData;
import com.sanguinaire.player.PlayerMutationProvider;
import com.sanguinaire.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A filled serum syringe. A single registered item represents every serum; the contained mutation is identified
 * by the {@value #TAG_MUTATION} NBT string (never by display name or texture). The liquid layer of the model is
 * tinted per mutation through an item color handler on the client.
 * <p>
 * Using a serum injects it: validated on the server, it starts the transformation through
 * {@link TransformationManager}, consumes the serum and returns an Empty Syringe. The empty syringe is placed outside
 * the hotbar when possible, because anything held is dropped while transformed.
 * <p>
 * Utility serums ({@link UtilitySerum}, NBT {@value #TAG_UTILITY}) are not transformations: they apply their effect
 * directly and never cause Weakened Hearts. Serum names are shown in red, like a rarity colour.
 */
public class SerumItem extends Item {
    public static final String TAG_MUTATION = "Mutation";
    public static final String TAG_UTILITY = "Utility";
    public static final String TAG_BLOOD = "Blood";
    private static final int UNKNOWN_LIQUID_COLOR = 0x7A0E14;

    public SerumItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(MutationType type) {
        ItemStack stack = new ItemStack(ModItems.SERUM.get());
        setMutation(stack, type);
        return stack;
    }

    public static ItemStack create(UtilitySerum serum) {
        ItemStack stack = new ItemStack(ModItems.SERUM.get());
        stack.getOrCreateTag().putString(TAG_UTILITY, serum.getId());
        return stack;
    }

    @Nullable
    public static UtilitySerum getUtility(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_UTILITY, Tag.TAG_STRING)) {
            return null;
        }
        return UtilitySerum.byId(tag.getString(TAG_UTILITY));
    }

    /** Blood mixed into this transformation serum at the Synthesizer. */
    public static void setBlood(ItemStack stack, BloodInfusion blood) {
        stack.getOrCreateTag().put(TAG_BLOOD, blood.save());
    }

    @Nullable
    public static BloodInfusion getBlood(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_BLOOD, Tag.TAG_COMPOUND) ? null : BloodInfusion.load(tag.getCompound(TAG_BLOOD));
    }

    public static void setMutation(ItemStack stack, MutationType type) {
        stack.getOrCreateTag().putString(TAG_MUTATION, type.getId());
    }

    @Nullable
    public static MutationType getMutation(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MUTATION, Tag.TAG_STRING)) {
            return null;
        }
        return MutationType.byId(tag.getString(TAG_MUTATION));
    }

    public static int getLiquidColor(ItemStack stack) {
        MutationType type = getMutation(stack);
        if (type != null) {
            return type.getLiquidColor();
        }
        UtilitySerum utility = getUtility(stack);
        return utility == null ? UNKNOWN_LIQUID_COLOR : utility.getLiquidColor();
    }

    /** Tainted Blood: 2 points (1 heart) of damage, 1 full Weakened Heart and Nausea I for 8 seconds. */
    private static final float TAINTED_BLOOD_DAMAGE = 2.0F;
    private static final float TAINTED_BLOOD_WEAKENED_HEALTH = 2.0F;
    private static final int TAINTED_BLOOD_NAUSEA_TICKS = 8 * 20;

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        MutationType type = getMutation(stack);
        UtilitySerum utility = getUtility(stack);
        if (type == null && utility == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.consume(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        if (utility != null) {
            return injectUtility(serverPlayer, stack, utility);
        }
        if (TransformationManager.isTransformed(serverPlayer) || TransformationSequence.isPending(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".already_transformed")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return InteractionResultHolder.fail(stack);
        }

        // Consume first so the serum is not dropped by the empty-hand rule when the transformation starts.
        BloodInfusion blood = getBlood(stack);
        ItemStack consumed = stack.split(1);
        // The change itself follows after a 2-second build-up.
        TransformationSequence.begin(serverPlayer, type, blood, consumed);
        serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        giveOutsideHotbar(serverPlayer, new ItemStack(ModItems.EMPTY_SYRINGE.get()));
        return InteractionResultHolder.success(stack);
    }

    private InteractionResultHolder<ItemStack> injectUtility(ServerPlayer player, ItemStack stack, UtilitySerum serum) {
        PlayerMutationData data = PlayerMutationProvider.get(player).orElse(null);
        if (data == null) {
            return InteractionResultHolder.fail(stack);
        }
        switch (serum) {
            case TAINTED_BLOOD -> {
                if (data.hasTaintedBlood()) {
                    player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".already_tainted")
                            .withStyle(ChatFormatting.DARK_RED), true);
                    return InteractionResultHolder.fail(stack);
                }
                data.setTaintedBlood(true);
                // Something foreign takes root in the blood: it hurts, scars a heart and turns the stomach.
                player.hurt(player.damageSources().magic(), TAINTED_BLOOD_DAMAGE);
                data.setWeakenedHealth(data.getWeakenedHealth() + TAINTED_BLOOD_WEAKENED_HEALTH);
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, TAINTED_BLOOD_NAUSEA_TICKS, 0));
                player.sendSystemMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".blood_tainted")
                        .withStyle(ChatFormatting.DARK_RED));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.PLAYERS, 1.0F, 0.6F);
            }
            case PHYSICAL_BOOST -> {
                player.addEffect(new MobEffectInstance(ModEffects.PHYSICAL_BOOST.get(), UtilitySerum.PHYSICAL_BOOST_TICKS, 0));
                data.setPhysicalBoostTicks(UtilitySerum.PHYSICAL_BOOST_TICKS);
                ModNetwork.syncMutationData(player);
                player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".physical_boost")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            case REVITALIZING -> {
                // Instant Health II: an instant heal, so it can refill Weakened Hearts.
                MobEffects.HEAL.applyInstantenousEffect(null, null, player, 1, 1.0D);
                player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".revitalizing")
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
        stack.shrink(1);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 1.4F);
        player.awardStat(Stats.ITEM_USED.get(this));
        ModNetwork.syncMutationData(player);
        ItemStack syringe = new ItemStack(ModItems.EMPTY_SYRINGE.get());
        if (stack.isEmpty()) {
            return InteractionResultHolder.success(syringe); // not transformed: the syringe can go back in the hand
        }
        if (!player.getInventory().add(syringe)) {
            player.drop(syringe, false);
        }
        return InteractionResultHolder.success(stack);
    }

    /** Puts the stack into the main inventory (not the hotbar or hands); drops it if there is no room. */
    private static void giveOutsideHotbar(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int slot = Inventory.getSelectionSize(); slot < inventory.items.size() && !stack.isEmpty(); slot++) {
                ItemStack existing = inventory.items.get(slot);
                if (pass == 0 && ItemStack.isSameItemSameTags(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                    int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                    existing.grow(moved);
                    stack.shrink(moved);
                } else if (pass == 1 && existing.isEmpty()) {
                    inventory.items.set(slot, stack.copy());
                    stack.setCount(0);
                }
            }
        }
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
        inventory.setChanged();
    }

    @Override
    public Component getName(ItemStack stack) {
        MutationType type = getMutation(stack);
        if (type != null) {
            return Component.translatable("item." + Sanguinaire.MOD_ID + ".serum.named", type.getDisplayName())
                    .withStyle(ChatFormatting.DARK_RED);
        }
        UtilitySerum utility = getUtility(stack);
        if (utility != null) {
            return utility.getDisplayName().copy().withStyle(ChatFormatting.DARK_RED);
        }
        return Component.translatable("item." + Sanguinaire.MOD_ID + ".serum.unknown").withStyle(ChatFormatting.DARK_RED);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MutationType type = getMutation(stack);
        if (type != null) {
            tooltip.add(Component.translatable("tooltip." + Sanguinaire.MOD_ID + ".serum.duration", type.getDurationSeconds())
                    .withStyle(ChatFormatting.DARK_GRAY));
            BloodInfusion blood = getBlood(stack);
            if (blood != null) {
                tooltip.add(Component.translatable("tooltip." + Sanguinaire.MOD_ID + ".serum.blood", blood.type().getDisplayName())
                        .withStyle(ChatFormatting.DARK_RED));
                BloodSampleItem.addEffectLines(blood.effects(), tooltip);
            }
        } else if (getUtility(stack) != null) {
            tooltip.add(Component.translatable("tooltip." + Sanguinaire.MOD_ID + ".serum.utility").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
