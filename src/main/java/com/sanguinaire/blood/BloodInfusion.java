package com.sanguinaire.blood;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

/**
 * Blood mixed into a transformation serum at the Serum Synthesizer: its type, plus the potion effect bound to it
 * at a Sanguine Alembic (if any). Stored on the serum and, while the transformation lasts, on the player.
 */
public record BloodInfusion(BloodType type, @Nullable MobEffectInstance effect) {
    private static final String TAG_TYPE = "Type";
    private static final String TAG_EFFECT = "Effect";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_TYPE, type.getId());
        if (effect != null) {
            tag.put(TAG_EFFECT, effect.save(new CompoundTag()));
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
        MobEffectInstance effect = tag.contains(TAG_EFFECT, Tag.TAG_COMPOUND) ? MobEffectInstance.load(tag.getCompound(TAG_EFFECT)) : null;
        return new BloodInfusion(type, effect);
    }
}
