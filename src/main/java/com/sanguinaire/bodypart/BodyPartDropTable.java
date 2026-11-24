package com.sanguinaire.bodypart;

import com.sanguinaire.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Special body-part pools for each supported mob, with the exact drop chances from the design document.
 * <p>
 * A qualifying kill <b>always</b> drops exactly one material. The listed chances are relative weights: a pool of
 * 25 / 10 / 5 drops its materials 62.5% / 25% / 12.5% of the time ({@link #getShare}).
 * <p>
 * "Venomous Spider" in the design document is the vanilla Cave Spider.
 */
public final class BodyPartDropTable {

    public record Entry(Supplier<? extends Item> item, float chancePercent) {
    }

    private static Map<EntityType<?>, List<Entry>> table;

    private BodyPartDropTable() {
    }

    private static Entry e(Supplier<? extends Item> item, float chancePercent) {
        return new Entry(item, chancePercent);
    }

    private static Map<EntityType<?>, List<Entry>> build() {
        Map<EntityType<?>, List<Entry>> map = new LinkedHashMap<>();
        map.put(EntityType.ZOMBIE, List.of(
                e(ModItems.FOUL_STOMACH, 25), e(ModItems.DECAYED_HEART, 10), e(ModItems.ROTTEN_BRAIN, 5)));
        map.put(EntityType.HUSK, List.of(
                e(ModItems.FOUL_STOMACH, 25), e(ModItems.DRIED_HEART, 5), e(ModItems.ROTTEN_BRAIN, 10)));
        map.put(EntityType.DROWNED, List.of(
                e(ModItems.FOUL_STOMACH, 10), e(ModItems.SOAKED_HEART, 5), e(ModItems.ROTTEN_BRAIN, 25)));
        map.put(EntityType.CREEPER, List.of(
                e(ModItems.CREEPER_HEART, 5), e(ModItems.FUSE_SPINE, 10), e(ModItems.VOLATILE_FRONDS, 25)));
        map.put(EntityType.PHANTOM, List.of(
                e(ModItems.INSOMNIA_EYE, 5), e(ModItems.HOLLOW_RIB, 10), e(ModItems.PHANTOM_HEART, 25)));
        map.put(EntityType.SPIDER, List.of(
                e(ModItems.SPIDER_GLAND, 25), e(ModItems.TWISTED_FANG, 5), e(ModItems.CHITIN_PLATE, 10)));
        map.put(EntityType.CAVE_SPIDER, List.of(
                e(ModItems.SPIDER_GLAND, 10), e(ModItems.VENOM_FANG, 5), e(ModItems.CHITIN_PLATE, 25)));
        map.put(EntityType.ENDERMAN, List.of(
                e(ModItems.NULL_RESIDUE, 25), e(ModItems.ENDER_MARROW, 10), e(ModItems.ECHOING_LENS, 5)));
        map.put(EntityType.GOAT, List.of(
                e(ModItems.MOUNTAIN_TALLOW, 10), e(ModItems.GOAT_FUR, 25), e(ModItems.CLOVEN_HOOF, 5)));
        map.put(EntityType.WOLF, List.of(
                e(ModItems.WOLF_FUR, 25), e(ModItems.SHARP_FANGS, 10), e(ModItems.CANINE_TONGUE, 5)));
        map.put(EntityType.GHAST, List.of(
                e(ModItems.DRIED_TENDRIL, 25), e(ModItems.SHRIVELED_HEART, 10), e(ModItems.CHARRED_EYE, 5)));
        map.put(EntityType.VILLAGER, List.of(
                e(ModItems.VILLAGER_HEART, 10), e(ModItems.VILLAGER_NOSE, 5), e(ModItems.VILLAGER_FLESH, 25)));
        map.put(EntityType.GUARDIAN, List.of(
                e(ModItems.SPIKED_HEART, 5), e(ModItems.OILY_FLESH, 25), e(ModItems.GUARDIAN_EYE, 10)));
        map.put(EntityType.ELDER_GUARDIAN, List.of(
                e(ModItems.SPIKED_HEART, 25), e(ModItems.OILY_FLESH, 10), e(ModItems.ELDER_GUARDIAN_EYE, 5)));
        map.put(EntityType.WARDEN, List.of(
                e(ModItems.DENSE_RIB, 25), e(ModItems.RESONATING_ANTLER, 10), e(ModItems.BOOMING_DIAPHRAGM, 5)));
        // Only the Ravager among the raiders yields specimens for now (Pillagers, Vindicators and Evokers removed
        // 2026-11-24): Tough Flesh, Illager Nose and Giant Tooth, the three Ravager serum ingredients.
        map.put(EntityType.RAVAGER, List.of(
                e(ModItems.TOUGH_FLESH, 25), e(ModItems.ILLAGER_NOSE, 25), e(ModItems.GIANT_TOOTH, 10)));
        return Collections.unmodifiableMap(map);
    }

    private static Map<EntityType<?>, List<Entry>> table() {
        if (table == null) {
            table = build();
        }
        return table;
    }

    /** Every supported mob and its pool, in the design document's order (used by the Research Journal). */
    public static Map<EntityType<?>, List<Entry>> getTable() {
        return table();
    }

    public static List<Entry> getEntries(EntityType<?> type) {
        return table().getOrDefault(type, List.of());
    }

    /**
     * Rolls the pool of the given mob once.
     *
     * @return the material to drop, or {@code null} if the mob has no pool.
     */
    @Nullable
    public static Item roll(EntityType<?> type, RandomSource random) {
        List<Entry> entries = getEntries(type);
        if (entries.isEmpty()) {
            return null;
        }
        float roll = random.nextFloat() * totalWeight(entries);
        float cumulative = 0.0F;
        for (Entry entry : entries) {
            cumulative += entry.chancePercent();
            if (roll < cumulative) {
                return entry.item().get();
            }
        }
        return entries.get(entries.size() - 1).item().get(); // float rounding at the very top of the range
    }

    /** The real chance (in percent) that a qualifying kill of a mob with this pool drops {@code entry}. */
    public static float getShare(List<Entry> entries, Entry entry) {
        float total = totalWeight(entries);
        return total <= 0.0F ? 0.0F : entry.chancePercent() * 100.0F / total;
    }

    private static float totalWeight(List<Entry> entries) {
        float total = 0.0F;
        for (Entry entry : entries) {
            total += entry.chancePercent();
        }
        return total;
    }
}
