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
import com.sanguinaire.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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

    /** How far behind the target the injector must stand: within 60 degrees of straight behind its back. */
    private static final double BACK_DOT_THRESHOLD = -0.5D;

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (getMutation(stack) == null && getUtility(stack) == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.consume(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        return inject(serverPlayer, serverPlayer, stack);
    }

    /**
     * Sneak and use a serum on another player's back to inject it into them instead of yourself. Clicking them
     * from the front (or the sides) does nothing, so a serum is never wasted on yourself by accident.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!player.isShiftKeyDown() || !(target instanceof Player targetPlayer) || targetPlayer == player
                || (getMutation(stack) == null && getUtility(stack) == null)) {
            return InteractionResult.PASS;
        }
        if (!isBehind(player, targetPlayer)) {
            if (!player.level().isClientSide()) {
                player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".inject_from_behind")
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
            return InteractionResult.CONSUME;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer injector) || !(targetPlayer instanceof ServerPlayer recipient)) {
            return InteractionResult.FAIL;
        }
        // In creative the game passes a copy, so the serum is not used up. Vanilla empties the hand itself when
        // the stack runs out, so a syringe handed back as the result goes into the inventory instead.
        InteractionResultHolder<ItemStack> result = inject(injector, recipient, stack);
        if (result.getResult().consumesAction()) {
            ItemStack returned = result.getObject();
            if (returned != stack && !returned.isEmpty() && !injector.getInventory().add(returned)) {
                injector.drop(returned, false);
            }
            recipient.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".injected_by",
                    injector.getDisplayName()).withStyle(ChatFormatting.DARK_RED), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    /** Whether {@code injector} stands behind {@code target}, judged by the way the target's body faces. */
    private static boolean isBehind(Player injector, Player target) {
        Vec3 facing = Vec3.directionFromRotation(0.0F, target.yBodyRot);
        Vec3 toInjector = new Vec3(injector.getX() - target.getX(), 0.0D, injector.getZ() - target.getZ());
        if (toInjector.lengthSqr() < 1.0E-4D) {
            return false;
        }
        return facing.dot(toInjector.normalize()) <= BACK_DOT_THRESHOLD;
    }

    /**
     * Serum Gamble: injects one dose of {@code serum} into its owner, straight from their briefcase. Works on a copy,
     * so {@code serum} itself is untouched and no syringe is handed out; the caller decides whether the dose is spent.
     *
     * @return whether the serum took effect (false: e.g. a transformation serum while transformed)
     */
    public static boolean injectFromBriefcase(ServerPlayer player, ItemStack serum) {
        if (!(serum.getItem() instanceof SerumItem item)) {
            return false;
        }
        return item.inject(player, player, serum.copyWithCount(1), false).getResult().consumesAction();
    }

    /** Injects the serum in {@code stack} into {@code recipient}; {@code injector} keeps the empty syringe. */
    private InteractionResultHolder<ItemStack> inject(ServerPlayer injector, ServerPlayer recipient, ItemStack stack) {
        return inject(injector, recipient, stack, true);
    }

    private InteractionResultHolder<ItemStack> inject(ServerPlayer injector, ServerPlayer recipient, ItemStack stack,
                                                      boolean giveSyringe) {
        MutationType type = getMutation(stack);
        UtilitySerum utility = getUtility(stack);
        boolean self = injector == recipient;
        if (utility != null) {
            return injectUtility(injector, recipient, stack, utility);
        }
        if (type == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (TransformationManager.isTransformed(recipient) || TransformationSequence.isPending(recipient)) {
            injector.displayClientMessage((self
                    ? Component.translatable("message." + Sanguinaire.MOD_ID + ".already_transformed")
                    : Component.translatable("message." + Sanguinaire.MOD_ID + ".target_already_transformed", recipient.getDisplayName()))
                    .withStyle(ChatFormatting.DARK_RED), true);
            return InteractionResultHolder.fail(stack);
        }

        // Consume first so the serum is not dropped by the empty-hand rule when the transformation starts.
        BloodInfusion blood = getBlood(stack);
        ItemStack consumed = stack.split(1);
        // The change itself follows after a 2-second build-up.
        TransformationSequence.begin(recipient, type, blood, consumed);
        injector.awardStat(Stats.ITEM_USED.get(this));
        if (!giveSyringe) {
            return InteractionResultHolder.success(stack);
        }
        ItemStack syringe = new ItemStack(ModItems.EMPTY_SYRINGE.get());
        if (self) {
            giveOutsideHotbar(injector, syringe);
        } else if (!injector.getInventory().add(syringe)) {
            injector.drop(syringe, false);
        }
        return InteractionResultHolder.success(stack);
    }

    private InteractionResultHolder<ItemStack> injectUtility(ServerPlayer injector, ServerPlayer player, ItemStack stack,
                                                             UtilitySerum serum) {
        boolean self = injector == player;
        PlayerMutationData data = PlayerMutationProvider.get(player).orElse(null);
        if (data == null) {
            return InteractionResultHolder.fail(stack);
        }
        switch (serum) {
            case TAINTED_BLOOD -> {
                if (data.hasTaintedBlood()) {
                    injector.displayClientMessage((self
                            ? Component.translatable("message." + Sanguinaire.MOD_ID + ".already_tainted")
                            : Component.translatable("message." + Sanguinaire.MOD_ID + ".target_already_tainted", player.getDisplayName()))
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
                com.sanguinaire.advancement.ModAdvancements.grant(player, com.sanguinaire.advancement.ModAdvancements.SECOND_WIND);
            }
            case DELAYED_ACCRETION -> {
                // The effect is the source of truth: when it ends (or is removed), the held damage lands at once.
                player.addEffect(new MobEffectInstance(ModEffects.DELAYED_ACCRETION.get(), UtilitySerum.DELAYED_ACCRETION_TICKS, 0));
                player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".delayed_accretion")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            case LEECHING -> {
                player.addEffect(new MobEffectInstance(ModEffects.LEECHING.get(), UtilitySerum.LEECHING_TICKS, 0));
                player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".leeching")
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
        }
        stack.shrink(1);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SERUM_INJECT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        injector.awardStat(Stats.ITEM_USED.get(this));
        ModNetwork.syncMutationData(player);
        ItemStack syringe = new ItemStack(ModItems.EMPTY_SYRINGE.get());
        if (stack.isEmpty()) {
            return InteractionResultHolder.success(syringe); // the syringe goes back in the injector's hand
        }
        if (!injector.getInventory().add(syringe)) {
            injector.drop(syringe, false);
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
