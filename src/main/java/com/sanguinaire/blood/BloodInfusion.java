package com.sanguinaire.blood;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Blood mixed into a transformation serum at the Serum Synthesizer: its type, plus the potion effects bound to it
 * at a Sanguine Alembic (none to {@value com.sanguinaire.item.BloodSampleItem#MAX_EFFECTS}). Stored on the serum
 * and, while the transformation lasts, on the player.
 */
public record BloodInfusion(BloodType type, List<MobEffectInstance> effects) {
    private static final String TAG_TYPE = "Type";
    private static final String TAG_EFFECTS = "Effects";
    /** Before several effects were allowed, a single effect was stored under this key. Still read. */
    private static final String TAG_LEGACY_EFFECT = "Effect";

    public BloodInfusion {
        effects = List.copyOf(effects);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_TYPE, type.getId());
        if (!effects.isEmpty()) {
            ListTag list = new ListTag();
            for (MobEffectInstance effect : effects) {
                list.add(effect.save(new CompoundTag()));
            }
            tag.put(TAG_EFFECTS, list);
        }
        return tag;
    }

    @Nullable
    public static BloodInfusion load(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains(TAG_TYPE, Tag.TAG_STRING)) {
            return null;
        }
        BloodType type = BloodType.byId(tag.getString(TAG_TYPE));
        if (type == null) {
            return null;
        }
        List<MobEffectInstance> effects = new ArrayList<>();
        ListTag list = tag.getList(TAG_EFFECTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MobEffectInstance effect = MobEffectInstance.load(list.getCompound(i));
            if (effect != null) {
                effects.add(effect);
            }
        }
        if (effects.isEmpty() && tag.contains(TAG_LEGACY_EFFECT, Tag.TAG_COMPOUND)) {
            MobEffectInstance effect = MobEffectInstance.load(tag.getCompound(TAG_LEGACY_EFFECT));
            if (effect != null) {
                effects.add(effect);
            }
        }
        return new BloodInfusion(type, effects);
    }
}
