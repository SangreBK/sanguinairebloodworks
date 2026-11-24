package com.sanguinaire.item;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.blood.BloodInfusion;
import com.sanguinaire.blood.BloodType;
import com.sanguinaire.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A syringe of drawn blood. One registered item for every sample; everything about it lives in NBT:
 * <ul>
 *     <li>{@value #TAG_TYPE}: Monster, Animal or Humanoid ({@link BloodType}).</li>
 *     <li>{@value #TAG_DONOR}: the player it was drawn from, if any. The tooltip then reads "Jorge's Blood"
 *     instead of "Humanoid Blood"; it still counts as Humanoid Blood everywhere.</li>
 *     <li>{@value #TAG_EFFECTS}: up to {@value #MAX_EFFECTS} potion effects bound at a Sanguine Alembic, one per
 *     pass, never the same effect twice. The sample is then an "Altered Blood Sample". (Older samples kept a single
 *     effect under {@value #TAG_EFFECT}; it is still read.)</li>
 * </ul>
 * The liquid is tinted by blood type through the same color handler as serums.
 */
public class BloodSampleItem extends Item {
    public static final String TAG_TYPE = "BloodType";
    public static final String TAG_DONOR = "Donor";
    public static final String TAG_EFFECT = "Effect";
    public static final String TAG_EFFECTS = "Effects";
    /** How many potion effects one sample can carry. */
    public static final int MAX_EFFECTS = 3;

    public BloodSampleItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(BloodType type, @Nullable String donor) {
        ItemStack stack = new ItemStack(ModItems.BLOOD_SAMPLE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_TYPE, type.getId());
        if (donor != null) {
            tag.putString(TAG_DONOR, donor);
        }
        return stack;
    }

    @Nullable
    public static BloodType getType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (!stack.is(ModItems.BLOOD_SAMPLE.get()) || tag == null || !tag.contains(TAG_TYPE, Tag.TAG_STRING)) {
            return null;
        }
        return BloodType.byId(tag.getString(TAG_TYPE));
    }

    @Nullable
    public static String getDonor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_DONOR, Tag.TAG_STRING) ? tag.getString(TAG_DONOR) : null;
    }

    /** The potion effects bound to this sample, in the order they were added (possibly none). */
    public static List<MobEffectInstance> getEffects(ItemStack stack) {
        List<MobEffectInstance> effects = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return effects;
        }
        ListTag list = tag.getList(TAG_EFFECTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MobEffectInstance effect = MobEffectInstance.load(list.getCompound(i));
            if (effect != null) {
                effects.add(effect);
            }
        }
        if (effects.isEmpty() && tag.contains(TAG_EFFECT, Tag.TAG_COMPOUND)) {
            MobEffectInstance legacy = MobEffectInstance.load(tag.getCompound(TAG_EFFECT));
            if (legacy != null) {
                effects.add(legacy);
            }
        }
        return effects;
    }

    public static boolean isAltered(ItemStack stack) {
        return !getEffects(stack).isEmpty();
    }

    /** Whether this sample already carries an effect of the same kind. */
    public static boolean hasEffect(ItemStack stack, MobEffectInstance effect) {
        for (MobEffectInstance bound : getEffects(stack)) {
            if (bound.getEffect() == effect.getEffect()) {
                return true;
            }
        }
        return false;
    }

    /** A copy of this sample with one more potion effect bound to it (Sanguine Alembic). */
    public static ItemStack withEffect(ItemStack stack, MobEffectInstance effect) {
        ItemStack altered = stack.copyWithCount(1);
        List<MobEffectInstance> effects = getEffects(stack);
        effects.add(new MobEffectInstance(effect));
        ListTag list = new ListTag();
        for (MobEffectInstance bound : effects) {
            list.add(bound.save(new CompoundTag()));
        }
        CompoundTag tag = altered.getOrCreateTag();
        tag.remove(TAG_EFFECT);
        tag.put(TAG_EFFECTS, list);
        return altered;
    }

    /** What this sample adds to a transformation serum, or null if it isn't valid blood. */
    @Nullable
    public static BloodInfusion toInfusion(ItemStack stack) {
        BloodType type = getType(stack);
        return type == null ? null : new BloodInfusion(type, getEffects(stack));
    }

    public static int getLiquidColor(ItemStack stack) {
        BloodType type = getType(stack);
        return type == null ? BloodType.HUMANOID.getColor() : type.getColor();
    }

    @Override
    public Component getName(ItemStack stack) {
        String key = isAltered(stack) ? getDescriptionId(stack) + ".altered" : getDescriptionId(stack);
        return Component.translatable(key).withStyle(ChatFormatting.DARK_RED);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BloodType type = getType(stack);
        if (type == null) {
            return;
        }
        String donor = getDonor(stack);
        tooltip.add((donor != null
                ? Component.translatable("tooltip." + Sanguinaire.MOD_ID + ".blood.donor", donor)
                : type.getDisplayName().copy()).withStyle(ChatFormatting.GRAY));
        addEffectLines(getEffects(stack), tooltip);
    }

    /** One "Infused with ..." line per bound effect, colored like a potion tooltip. */
    public static void addEffectLines(List<MobEffectInstance> effects, List<Component> tooltip) {
        for (MobEffectInstance effect : effects) {
            tooltip.add(Component.translatable("tooltip." + Sanguinaire.MOD_ID + ".blood.infused", describeEffect(effect))
                    .withStyle(effect.getEffect().getCategory().getTooltipFormatting()));
        }
    }

    /** "Speed II (1:30)", formatted like a potion tooltip. */
    public static MutableComponent describeEffect(MobEffectInstance effect) {
        MutableComponent text = Component.translatable(effect.getDescriptionId());
        if (effect.getAmplifier() > 0) {
            text = Component.translatable("potion.withAmplifier", text, Component.translatable("potion.potency." + effect.getAmplifier()));
        }
        if (!effect.getEffect().isInstantenous() && effect.getDuration() > 20) {
            text = Component.translatable("potion.withDuration", text, MobEffectUtil.formatDuration(effect, 1.0F));
        }
        return text;
    }
}
