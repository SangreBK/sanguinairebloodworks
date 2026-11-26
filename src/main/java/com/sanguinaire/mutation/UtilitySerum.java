package com.sanguinaire.mutation;

import com.sanguinaire.registry.ModItems;
import com.sanguinaire.Sanguinaire;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Serums that are not transformations but strengthen the body. They are brewed in the Serum Synthesizer from
 * three body parts of different creatures (no transformation uses these combinations), and never cause Weakened Hearts.
 * <ul>
 *     <li><b>Tainted Blood</b>: used once, permanently; every transformation lasts 20 seconds longer.</li>
 *     <li><b>Physical Boost</b>: Strength II for 2 minutes, during which Weakened Hearts are suppressed.</li>
 *     <li><b>Revitalizing</b>: Instant Health II, which can also refill Weakened Hearts.</li>
 *     <li><b>Delayed Accretion</b>: 18 seconds of invulnerability; all damage taken meanwhile lands at once when it ends.</li>
 *     <li><b>Leeching</b>: for 30 seconds, non-projectile damage dealt heals the attacker by the same amount.</li>
 * </ul>
 * {@link #isTornPage() Torn Page} serums are unlocked per player by reading Torn Pages found in Ancient City chests;
 * the rest are researched like transformations (any ingredient reveals the entry, all three complete the formula).
 * The {@link #getId() id} is stored in serum NBT and must never change.
 */
public enum UtilitySerum {
    TAINTED_BLOOD("tainted_blood", 0x3A0A1E, ModItems.VENOM_FANG, ModItems.BOOMING_DIAPHRAGM, ModItems.SHRIVELED_HEART),
    PHYSICAL_BOOST("physical_boost", 0xE0A030, ModItems.OILY_FLESH, ModItems.TOUGH_FLESH, ModItems.VILLAGER_FLESH),
    REVITALIZING("revitalizing", 0x3FC23A, ModItems.VILLAGER_HEART, ModItems.SPIDER_GLAND, ModItems.MOUNTAIN_TALLOW),
    DELAYED_ACCRETION("delayed_accretion", 0x5B4A8A, ModItems.INSOMNIA_EYE, ModItems.ENDER_MARROW, ModItems.CLOVEN_HOOF),
    LEECHING("leeching", 0x8A1020, ModItems.LEECH_TONGUE, ModItems.SHARP_FANGS, ModItems.FOUL_STOMACH);

    /** Tainted Blood: extra transformation time. */
    public static final int TAINTED_BLOOD_BONUS_TICKS = 30 * 20;
    /** Physical Boost: Strength II and Weakened Heart suppression time. */
    public static final int PHYSICAL_BOOST_TICKS = 2 * 60 * 20;
    /** Delayed Accretion: how long all damage is held back. */
    public static final int DELAYED_ACCRETION_TICKS = 18 * 20;
    /** Leeching: how long melee damage heals the attacker. */
    public static final int LEECHING_TICKS = 30 * 20;

    private static final Map<String, UtilitySerum> BY_ID = new HashMap<>();

    static {
        for (UtilitySerum serum : values()) {
            BY_ID.put(serum.id, serum);
        }
    }

    private final String id;
    private final int liquidColor;
    private final List<Supplier<Item>> ingredients;

    @SafeVarargs
    UtilitySerum(String id, int liquidColor, Supplier<Item>... ingredients) {
        this.id = id;
        this.liquidColor = liquidColor;
        this.ingredients = List.of(ingredients);
    }

    public String getId() {
        return id;
    }

    /** Serums learned only from a Torn Page; the others are learned by researching their ingredients. */
    public boolean isTornPage() {
        return this == TAINTED_BLOOD || this == PHYSICAL_BOOST || this == DELAYED_ACCRETION;
    }

    public int getLiquidColor() {
        return liquidColor;
    }

    public List<Item> getIngredients() {
        List<Item> items = new ArrayList<>(ingredients.size());
        for (Supplier<Item> ingredient : ingredients) {
            items.add(ingredient.get());
        }
        return items;
    }

    public boolean usesIngredient(Item item) {
        return getIngredients().contains(item);
    }

    public Component getDisplayName() {
        return Component.translatable("serum." + Sanguinaire.MOD_ID + "." + id);
    }

    public String getDescriptionKey() {
        return "journal." + Sanguinaire.MOD_ID + ".serum." + id;
    }

    @Nullable
    public static UtilitySerum byId(String id) {
        return BY_ID.get(id);
    }

    /** Whether the item is an ingredient of any utility serum (the Synthesizer accepts these too). */
    public static boolean isIngredient(Item item) {
        for (UtilitySerum serum : values()) {
            if (serum.usesIngredient(item)) {
                return true;
            }
        }
        return false;
    }

    /** The utility serum made from exactly these three items, in any order. */
    @Nullable
    public static UtilitySerum findByIngredients(List<Item> items) {
        if (items.size() != 3) {
            return null;
        }
        for (UtilitySerum serum : values()) {
            List<Item> remaining = new ArrayList<>(items);
            boolean matches = true;
            for (Item ingredient : serum.getIngredients()) {
                if (!remaining.remove(ingredient)) {
                    matches = false;
                    break;
                }
            }
            if (matches && remaining.isEmpty()) {
                return serum;
            }
        }
        return null;
    }
}
