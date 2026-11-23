package com.sanguinaire.advancement;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.TransformationManager;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod's advancements (data/sanguinaire/advancements). The ones vanilla can detect use vanilla triggers in
 * their JSON (obtaining the journal, crafting the saw, the first specimen); the rest use an "impossible" criterion
 * and are granted from code through {@link #grant}. Server only.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class ModAdvancements {
    public static final String FIRST_TRANSFORMATION = "first_transformation";
    public static final String INSIDE_TRADER = "inside_trader";
    public static final String PATIENT_ZERO = "patient_zero";
    public static final String AW_MAN = "aw_man";
    public static final String ALL_BLOOD_TYPES = "all_blood_types";
    public static final String FRIENDLY_FIRE = "friendly_fire";
    public static final String SHRIEK_AND_DESTROY = "shriek_and_destroy";
    public static final String BORROWED_TIME = "borrowed_time";
    public static final String CLEAN_SLATE = "clean_slate";
    public static final String SILENCE_OF_THE_LAMBS = "silence_of_the_lambs";
    public static final String ACID_REFLUX = "acid_reflux";
    public static final String DOG_EAT_DOG = "dog_eat_dog";

    private ModAdvancements() {
    }

    /** The advancement for reading the Torn Page with these notes ({@code torn_page/<note id>}). */
    public static String tornPage(String noteId) {
        return "torn_page/" + noteId;
    }

    /** Completes {@code sanguinaire:<path>} for this player, if it exists and isn't done yet. */
    public static void grant(ServerPlayer player, String path) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(Sanguinaire.id(path));
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        for (String criterion : remaining) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    /** "Inside Trader": trading with a villager while transformed into one. */
    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TransformationManager.getActive(player) == MutationType.VILLAGER) {
            grant(player, INSIDE_TRADER);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MutationType active = TransformationManager.getActive(player);
        if (active == null) {
            return;
        }
        // "Patient Zero": a villager slain by one of the zombie forms.
        if (event.getEntity() instanceof AbstractVillager
                && (active == MutationType.ZOMBIE || active == MutationType.HUSK || active == MutationType.DROWNED)) {
            grant(player, PATIENT_ZERO);
        }
        // "Friendly Fire": a Ghast brought down by a Ghast-form player's own fireball.
        if (event.getEntity() instanceof Ghast && active == MutationType.GHAST && source.getDirectEntity() instanceof LargeFireball) {
            grant(player, FRIENDLY_FIRE);
        }
        // "Shriek and Destroy": a Warden silenced by a Warden-form player's sonic boom.
        if (event.getEntity() instanceof Warden && active == MutationType.WARDEN && source.is(DamageTypes.SONIC_BOOM)) {
            grant(player, SHRIEK_AND_DESTROY);
        }
        // "Acid Reflux": a Slime dissolved by a Rotting Abomination's bile (the bile's direct hit, not the poison).
        if (event.getEntity().getType() == EntityType.SLIME && active == MutationType.ROTTING_ABOMINATION
                && source.is(DamageTypes.INDIRECT_MAGIC) && source.getDirectEntity() == player) {
            grant(player, ACID_REFLUX);
        }
        // "Dog Eat Dog": a Ravager brought down by a Fade Hound.
        if (event.getEntity() instanceof Ravager && active == MutationType.FADEHOUND) {
            grant(player, DOG_EAT_DOG);
        }
    }
}
