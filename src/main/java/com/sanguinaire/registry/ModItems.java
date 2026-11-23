package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.item.SerumBriefcaseItem;
import com.sanguinaire.item.BloodSampleItem;
import com.sanguinaire.item.EmptySyringeItem;
import com.sanguinaire.item.SerumItem;
import com.sanguinaire.item.SurgicalJournalItem;
import com.sanguinaire.item.SurgicalSawItem;
import com.sanguinaire.item.TornPageItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Sanguinaire.MOD_ID);

    /** Every special body-part material, in registration order. Must be declared before the body parts. */
    private static final List<RegistryObject<Item>> BODY_PART_LIST = new ArrayList<>();
    public static final List<RegistryObject<Item>> BODY_PARTS = Collections.unmodifiableList(BODY_PART_LIST);

    // ---- Tools & equipment -------------------------------------------------------------------------------------

    /** Durability 100 per the design document. */
    public static final RegistryObject<Item> SURGICAL_SAW = ITEMS.register("surgical_saw",
            () -> new SurgicalSawItem(new Item.Properties().durability(100)));

    /** Reusable and stackable. Sneak-use on a creature (or the air, for yourself) to draw blood. */
    public static final RegistryObject<Item> EMPTY_SYRINGE = ITEMS.register("empty_syringe",
            () -> new EmptySyringeItem(new Item.Properties().stacksTo(64)));

    /** One item for every blood sample; type, donor and bound effect are stored in NBT. */
    public static final RegistryObject<BloodSampleItem> BLOOD_SAMPLE = ITEMS.register("blood_sample",
            () -> new BloodSampleItem(new Item.Properties().stacksTo(1)));

    /** One item for every serum; the mutation is stored in NBT. Serums never stack. */
    public static final RegistryObject<SerumItem> SERUM = ITEMS.register("serum",
            () -> new SerumItem(new Item.Properties().stacksTo(1)));

    /** Research guide; reads the owner's own per-player research. */
    public static final RegistryObject<SurgicalJournalItem> SURGICAL_JOURNAL = ITEMS.register("surgical_journal",
            () -> new SurgicalJournalItem(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(1)));

    /** Uncommon research notes from Ancient Cities; unlock a utility serum's journal entry. */
    public static final RegistryObject<TornPageItem> TORN_PAGE = ITEMS.register("torn_page",
            () -> new TornPageItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<SerumBriefcaseItem> SERUM_BRIEFCASE = ITEMS.register("serum_briefcase",
            () -> new SerumBriefcaseItem(new Item.Properties().stacksTo(1)));

    // ---- Body parts ----------------------------------------------------------------------------------------------
    // Zombie family
    public static final RegistryObject<Item> FOUL_STOMACH = bodyPart("foul_stomach");
    public static final RegistryObject<Item> DECAYED_HEART = bodyPart("decayed_heart");
    public static final RegistryObject<Item> ROTTEN_BRAIN = bodyPart("rotten_brain");
    public static final RegistryObject<Item> DRIED_HEART = bodyPart("dried_heart");
    public static final RegistryObject<Item> SOAKED_HEART = bodyPart("soaked_heart");
    // Creeper
    public static final RegistryObject<Item> CREEPER_HEART = bodyPart("creeper_heart");
    public static final RegistryObject<Item> FUSE_SPINE = bodyPart("fuse_spine");
    public static final RegistryObject<Item> VOLATILE_FRONDS = bodyPart("volatile_fronds");
    // Phantom
    public static final RegistryObject<Item> INSOMNIA_EYE = bodyPart("insomnia_eye");
    public static final RegistryObject<Item> HOLLOW_RIB = bodyPart("hollow_rib");
    public static final RegistryObject<Item> PHANTOM_HEART = bodyPart("phantom_heart");
    // Spiders
    public static final RegistryObject<Item> SPIDER_GLAND = bodyPart("spider_gland");
    public static final RegistryObject<Item> TWISTED_FANG = bodyPart("twisted_fang");
    public static final RegistryObject<Item> CHITIN_PLATE = bodyPart("chitin_plate");
    public static final RegistryObject<Item> VENOM_FANG = bodyPart("venom_fang");
    // Enderman
    public static final RegistryObject<Item> NULL_RESIDUE = bodyPart("null_residue");
    public static final RegistryObject<Item> ENDER_MARROW = bodyPart("ender_marrow");
    public static final RegistryObject<Item> ECHOING_LENS = bodyPart("echoing_lens");
    // Goat
    public static final RegistryObject<Item> MOUNTAIN_TALLOW = bodyPart("mountain_tallow");
    public static final RegistryObject<Item> GOAT_FUR = bodyPart("goat_fur");
    public static final RegistryObject<Item> CLOVEN_HOOF = bodyPart("cloven_hoof");
    // Wolf
    public static final RegistryObject<Item> WOLF_FUR = bodyPart("wolf_fur");
    public static final RegistryObject<Item> SHARP_FANGS = bodyPart("sharp_fangs");
    public static final RegistryObject<Item> CANINE_TONGUE = bodyPart("canine_tongue");
    // Ghast
    public static final RegistryObject<Item> DRIED_TENDRIL = bodyPart("dried_tendril");
    public static final RegistryObject<Item> SHRIVELED_HEART = bodyPart("shriveled_heart");
    public static final RegistryObject<Item> CHARRED_EYE = bodyPart("charred_eye");
    // Villager
    public static final RegistryObject<Item> VILLAGER_HEART = bodyPart("villager_heart");
    public static final RegistryObject<Item> VILLAGER_NOSE = bodyPart("villager_nose");
    public static final RegistryObject<Item> VILLAGER_FLESH = bodyPart("villager_flesh");
    // Guardians
    public static final RegistryObject<Item> SPIKED_HEART = bodyPart("spiked_heart");
    public static final RegistryObject<Item> OILY_FLESH = bodyPart("oily_flesh");
    public static final RegistryObject<Item> GUARDIAN_EYE = bodyPart("guardian_eye");
    public static final RegistryObject<Item> ELDER_GUARDIAN_EYE = bodyPart("elder_guardian_eye");
    // Warden
    public static final RegistryObject<Item> DENSE_RIB = bodyPart("dense_rib");
    public static final RegistryObject<Item> RESONATING_ANTLER = bodyPart("resonating_antler");
    public static final RegistryObject<Item> BOOMING_DIAPHRAGM = bodyPart("booming_diaphragm");

    private ModItems() {
    }

    /** Body parts stack to 16. */
    private static RegistryObject<Item> bodyPart(String name) {
        RegistryObject<Item> item = ITEMS.register(name, () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16)));
        BODY_PART_LIST.add(item);
        return item;
    }
}
