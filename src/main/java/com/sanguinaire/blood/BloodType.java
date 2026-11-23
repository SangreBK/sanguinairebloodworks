package com.sanguinaire.blood;

import com.sanguinaire.Sanguinaire;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The three kinds of blood a syringe can draw. What a creature bleeds is decided by what it is:
 * <ul>
 *     <li><b>Humanoid</b>: players, villagers (and wandering traders) and illagers. Checked first, since illagers
 *     are also monsters.</li>
 *     <li><b>Monster</b>: every hostile creature ({@link Enemy}) and every arthropod (spiders, bees, silverfish...).</li>
 *     <li><b>Animal</b>: animals, water creatures and bats.</li>
 * </ul>
 * Golems, armor stands and other bloodless things give nothing.
 * <p>
 * Each type is also a piece of research ({@link #getResearchKey()}) with its own Research Journal entry.
 */
public enum BloodType {
    MONSTER("monster", 0x45601F),
    ANIMAL("animal", 0xB8261A),
    HUMANOID("humanoid", 0x780A0E);

    private final String id;
    private final int color;

    BloodType(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    /** Liquid tint of a syringe holding this blood. */
    public int getColor() {
        return color;
    }

    /** "Monster Blood", "Animal Blood", "Humanoid Blood". */
    public Component getDisplayName() {
        return Component.translatable("blood." + Sanguinaire.MOD_ID + "." + id);
    }

    public String getDescriptionKey() {
        return "journal." + Sanguinaire.MOD_ID + ".blood." + id;
    }

    /** The research id stored per player once this blood has been studied at a Research Station. */
    public ResourceLocation getResearchKey() {
        return Sanguinaire.id("blood/" + id);
    }

    @Nullable
    public static BloodType byId(String id) {
        for (BloodType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    /** What this creature bleeds, or null if it has no blood to draw. */
    @Nullable
    public static BloodType of(LivingEntity entity) {
        if (entity instanceof Player || entity instanceof AbstractVillager || entity instanceof AbstractIllager) {
            return HUMANOID;
        }
        if (entity instanceof Enemy || entity.getMobType() == MobType.ARTHROPOD) {
            return MONSTER;
        }
        if (entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature) {
            return ANIMAL;
        }
        return null;
    }
}
