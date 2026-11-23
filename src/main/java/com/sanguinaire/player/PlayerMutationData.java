package com.sanguinaire.player;

import com.sanguinaire.blood.BloodInfusion;
import com.sanguinaire.mutation.MutationType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persistent per-player mutation state, attached to every player as a Forge capability.
 * <ul>
 *     <li><b>Weakened health</b>: health points (1.0 = half a heart) permanently converted into Weakened Hearts.
 *     Survives ordinary death; cleared only by a Totem of Undying resurrection.</li>
 *     <li><b>Active transformation</b>: the current mutation, the ticks left, the health the player had before
 *     transforming (restored afterwards) and the player's current health while transformed (so a relog does not
 *     clamp it to the untransformed maximum).</li>
 *     <li><b>Research</b>: the body parts this player has researched at a Research Station. Research is strictly
 *     per player and is never shared; it survives death.</li>
 *     <li><b>Tainted Blood</b>: permanent (survives death); transformations last longer.</li>
 *     <li><b>Physical Boost</b>: ticks left during which Weakened Hearts are suppressed (cleared on death).</li>
 *     <li><b>Ability cooldowns</b>: per ability, the (overworld) game time at which it is ready again.</li>
 * </ul>
 * The same class is used on the client as a cache of the values synced by
 * {@link com.sanguinaire.network.packet.SyncMutationDataPacket}; only the server ever changes gameplay state.
 */
public class PlayerMutationData {
    private static final String TAG_WEAKENED = "WeakenedHealth";
    private static final String TAG_MUTATION = "ActiveMutation";
    private static final String TAG_REMAINING = "RemainingTicks";
    private static final String TAG_HEALTH_BEFORE = "HealthBefore";
    private static final String TAG_TRANSFORMED_HEALTH = "TransformedHealth";
    private static final String TAG_RESEARCH = "Research";
    private static final String TAG_COOLDOWNS = "AbilityCooldowns";
    private static final String TAG_TAINTED = "TaintedBlood";
    private static final String TAG_BOOST = "PhysicalBoostTicks";
    private static final String TAG_UNLOCKED_SERUMS = "UnlockedSerums";
    private static final String TAG_BLOOD = "ActiveBlood";

    private float weakenedHealth;
    @Nullable
    private MutationType activeMutation;
    private int remainingTicks;
    private float healthBeforeTransformation;
    private float transformedHealth;
    private final Set<ResourceLocation> researchedIngredients = new LinkedHashSet<>();
    private final Map<String, Long> abilityReadyAt = new HashMap<>();
    private boolean taintedBlood;
    /** Utility serums whose journal entry was unlocked by reading a Torn Page (per player, never shared). */
    private final Set<String> unlockedSerums = new LinkedHashSet<>();
    private int physicalBoostTicks;
    /** Blood mixed into the active transformation's serum, if any. */
    @Nullable
    private BloodInfusion activeBlood;

    public float getWeakenedHealth() {
        return weakenedHealth;
    }

    public void setWeakenedHealth(float weakenedHealth) {
        this.weakenedHealth = Math.max(0.0F, weakenedHealth);
    }

    public boolean hasWeakenedHearts() {
        return weakenedHealth > 0.0F;
    }

    @Nullable
    public MutationType getActiveMutation() {
        return activeMutation;
    }

    public void setActiveMutation(@Nullable MutationType activeMutation) {
        this.activeMutation = activeMutation;
    }

    public boolean isTransformed() {
        return activeMutation != null;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public void setRemainingTicks(int remainingTicks) {
        this.remainingTicks = Math.max(0, remainingTicks);
    }

    public float getHealthBeforeTransformation() {
        return healthBeforeTransformation;
    }

    public void setHealthBeforeTransformation(float health) {
        this.healthBeforeTransformation = health;
    }

    public float getTransformedHealth() {
        return transformedHealth;
    }

    public void setTransformedHealth(float transformedHealth) {
        this.transformedHealth = transformedHealth;
    }

    public Set<String> getUnlockedSerums() {
        return Collections.unmodifiableSet(unlockedSerums);
    }

    public boolean isSerumUnlocked(String serumId) {
        return unlockedSerums.contains(serumId);
    }

    /** @return true if the serum was not unlocked before */
    public boolean unlockSerum(String serumId) {
        return unlockedSerums.add(serumId);
    }

    public void setUnlockedSerums(Collection<String> serumIds) {
        unlockedSerums.clear();
        unlockedSerums.addAll(serumIds);
    }

    public boolean hasTaintedBlood() {
        return taintedBlood;
    }

    public void setTaintedBlood(boolean taintedBlood) {
        this.taintedBlood = taintedBlood;
    }

    public int getPhysicalBoostTicks() {
        return physicalBoostTicks;
    }

    public void setPhysicalBoostTicks(int ticks) {
        this.physicalBoostTicks = Math.max(0, ticks);
    }

    /**
     * Weakened Hearts have no effect and are hidden while transformed or while a Physical Boost serum is active;
     * they return afterwards.
     */
    public boolean areWeakenedHeartsSuppressed() {
        return isTransformed() || physicalBoostTicks > 0;
    }

    /** Item IDs of every researched body part (read-only view). */
    public Set<ResourceLocation> getResearchedIngredients() {
        return Collections.unmodifiableSet(researchedIngredients);
    }

    public boolean isResearched(ResourceLocation itemId) {
        return researchedIngredients.contains(itemId);
    }

    /** @return true if the ingredient was not researched before */
    public boolean addResearch(ResourceLocation itemId) {
        return researchedIngredients.add(itemId);
    }

    public void setResearchedIngredients(Collection<ResourceLocation> itemIds) {
        researchedIngredients.clear();
        researchedIngredients.addAll(itemIds);
    }

    /** Game time at which the given ability is ready again (0 if it never was used). */
    public long getAbilityReadyAt(String ability) {
        return abilityReadyAt.getOrDefault(ability, 0L);
    }

    public void setAbilityReadyAt(String ability, long gameTime) {
        abilityReadyAt.put(ability, gameTime);
    }

    /**
     * Copies state onto a new player entity (respawn or returning from the End).
     *
     * @param includeTransformation false after a death: transformations always end when the player dies.
     */
    @Nullable
    public BloodInfusion getActiveBlood() {
        return activeBlood;
    }

    public void setActiveBlood(@Nullable BloodInfusion blood) {
        this.activeBlood = blood;
    }

    public void copyFrom(PlayerMutationData other, boolean includeTransformation) {
        this.weakenedHealth = other.weakenedHealth;
        this.researchedIngredients.clear();
        this.researchedIngredients.addAll(other.researchedIngredients);
        this.abilityReadyAt.clear();
        this.abilityReadyAt.putAll(other.abilityReadyAt);
        this.taintedBlood = other.taintedBlood;
        this.unlockedSerums.clear();
        this.unlockedSerums.addAll(other.unlockedSerums);
        this.physicalBoostTicks = includeTransformation ? other.physicalBoostTicks : 0;
        if (includeTransformation) {
            this.activeMutation = other.activeMutation;
            this.remainingTicks = other.remainingTicks;
            this.healthBeforeTransformation = other.healthBeforeTransformation;
            this.transformedHealth = other.transformedHealth;
            this.activeBlood = other.activeBlood;
        } else {
            this.activeMutation = null;
            this.remainingTicks = 0;
            this.activeBlood = null;
        }
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat(TAG_WEAKENED, weakenedHealth);
        ListTag research = new ListTag();
        for (ResourceLocation id : researchedIngredients) {
            research.add(StringTag.valueOf(id.toString()));
        }
        tag.put(TAG_RESEARCH, research);
        CompoundTag cooldowns = new CompoundTag();
        abilityReadyAt.forEach(cooldowns::putLong);
        tag.put(TAG_COOLDOWNS, cooldowns);
        tag.putBoolean(TAG_TAINTED, taintedBlood);
        ListTag serums = new ListTag();
        for (String id : unlockedSerums) {
            serums.add(StringTag.valueOf(id));
        }
        tag.put(TAG_UNLOCKED_SERUMS, serums);
        tag.putInt(TAG_BOOST, physicalBoostTicks);
        if (activeMutation != null) {
            tag.putString(TAG_MUTATION, activeMutation.getId());
            tag.putInt(TAG_REMAINING, remainingTicks);
            tag.putFloat(TAG_HEALTH_BEFORE, healthBeforeTransformation);
            tag.putFloat(TAG_TRANSFORMED_HEALTH, transformedHealth);
            if (activeBlood != null) {
                tag.put(TAG_BLOOD, activeBlood.save());
            }
        }
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        weakenedHealth = Math.max(0.0F, tag.getFloat(TAG_WEAKENED));
        researchedIngredients.clear();
        ListTag research = tag.getList(TAG_RESEARCH, Tag.TAG_STRING);
        for (int i = 0; i < research.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(research.getString(i));
            if (id != null) {
                researchedIngredients.add(id);
            }
        }
        abilityReadyAt.clear();
        CompoundTag cooldowns = tag.getCompound(TAG_COOLDOWNS);
        for (String key : cooldowns.getAllKeys()) {
            abilityReadyAt.put(key, cooldowns.getLong(key));
        }
        taintedBlood = tag.getBoolean(TAG_TAINTED);
        unlockedSerums.clear();
        ListTag serums = tag.getList(TAG_UNLOCKED_SERUMS, Tag.TAG_STRING);
        for (int i = 0; i < serums.size(); i++) {
            unlockedSerums.add(serums.getString(i));
        }
        physicalBoostTicks = Math.max(0, tag.getInt(TAG_BOOST));
        activeMutation = tag.contains(TAG_MUTATION, Tag.TAG_STRING) ? MutationType.byId(tag.getString(TAG_MUTATION)) : null;
        remainingTicks = activeMutation == null ? 0 : tag.getInt(TAG_REMAINING);
        healthBeforeTransformation = tag.getFloat(TAG_HEALTH_BEFORE);
        transformedHealth = tag.getFloat(TAG_TRANSFORMED_HEALTH);
        activeBlood = activeMutation != null && tag.contains(TAG_BLOOD, Tag.TAG_COMPOUND)
                ? BloodInfusion.load(tag.getCompound(TAG_BLOOD)) : null;
    }
}
