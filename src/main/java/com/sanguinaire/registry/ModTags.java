package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;

public final class ModTags {
    /**
     * Items that may be stored inside a Serum Briefcase (serums and Empty Syringes).
     * The briefcase additionally hard-rejects other briefcases and container items in code, so a datapack
     * adding them to this tag cannot create recursive storage.
     */
    public static final TagKey<Item> BRIEFCASE_STORABLE = TagKey.create(Registries.ITEM, Sanguinaire.id("briefcase_storable"));

    /** Every special body part: the only items the Research Station accepts. */
    public static final TagKey<Item> BODY_PARTS = TagKey.create(Registries.ITEM, Sanguinaire.id("body_parts"));

    /**
     * Damage types that count as "physical" for the Weakened Hearts extra-damage rule: melee, projectiles,
     * falling, impacts, explosions and the like. Fire, magic, poison, wither, drowning, starvation, freezing and the
     * void are excluded. Data-driven so it can be adjusted by datapacks.
     */
    public static final TagKey<DamageType> PHYSICAL_DAMAGE = TagKey.create(Registries.DAMAGE_TYPE, Sanguinaire.id("physical"));

    private ModTags() {
    }
}
