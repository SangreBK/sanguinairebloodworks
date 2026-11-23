package com.sanguinaire.research;

import com.sanguinaire.Sanguinaire;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-level storage of every player's research, keyed by player UUID (saved in {@code data/sanguinaire_research.dat}
 * of the overworld). Because it does not live on the player entity, research can never be lost to death, respawn,
 * dimension changes or relogs, and it stays strictly per player.
 */
public class ResearchSavedData extends SavedData {
    private static final String FILE_ID = Sanguinaire.MOD_ID + "_research";
    private static final String TAG_PLAYERS = "Players";
    private static final String TAG_UUID = "Id";
    private static final String TAG_RESEARCH = "Research";
    private static final String TAG_SERUMS = "UnlockedSerums";

    private static final class Entry {
        final Set<ResourceLocation> research = new LinkedHashSet<>();
        final Set<String> serums = new LinkedHashSet<>();
    }

    private final Map<UUID, Entry> players = new HashMap<>();

    public static ResearchSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ResearchSavedData::load, ResearchSavedData::new, FILE_ID);
    }

    private Entry entry(UUID player) {
        return players.computeIfAbsent(player, id -> new Entry());
    }

    public Set<ResourceLocation> getResearch(UUID player) {
        Entry entry = players.get(player);
        return entry == null ? Set.of() : Collections.unmodifiableSet(entry.research);
    }

    public boolean isResearched(UUID player, ResourceLocation item) {
        Entry entry = players.get(player);
        return entry != null && entry.research.contains(item);
    }

    /** @return true if newly researched */
    public boolean addResearch(UUID player, ResourceLocation item) {
        boolean added = entry(player).research.add(item);
        if (added) {
            setDirty();
        }
        return added;
    }

    public Set<String> getUnlockedSerums(UUID player) {
        Entry entry = players.get(player);
        return entry == null ? Set.of() : Collections.unmodifiableSet(entry.serums);
    }

    public boolean isSerumUnlocked(UUID player, String serumId) {
        Entry entry = players.get(player);
        return entry != null && entry.serums.contains(serumId);
    }

    /** @return true if newly unlocked */
    public boolean unlockSerum(UUID player, String serumId) {
        boolean added = entry(player).serums.add(serumId);
        if (added) {
            setDirty();
        }
        return added;
    }

    /** Forgets everything this player has researched or unlocked. */
    public void clear(UUID player) {
        if (players.remove(player) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        players.forEach((id, entry) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(TAG_UUID, id);
            ListTag research = new ListTag();
            entry.research.forEach(item -> research.add(StringTag.valueOf(item.toString())));
            playerTag.put(TAG_RESEARCH, research);
            ListTag serums = new ListTag();
            entry.serums.forEach(serum -> serums.add(StringTag.valueOf(serum)));
            playerTag.put(TAG_SERUMS, serums);
            list.add(playerTag);
        });
        tag.put(TAG_PLAYERS, list);
        return tag;
    }

    public static ResearchSavedData load(CompoundTag tag) {
        ResearchSavedData data = new ResearchSavedData();
        ListTag list = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag playerTag = list.getCompound(i);
            if (!playerTag.hasUUID(TAG_UUID)) {
                continue;
            }
            Entry entry = data.entry(playerTag.getUUID(TAG_UUID));
            ListTag research = playerTag.getList(TAG_RESEARCH, Tag.TAG_STRING);
            for (int j = 0; j < research.size(); j++) {
                ResourceLocation id = ResourceLocation.tryParse(research.getString(j));
                if (id != null) {
                    entry.research.add(id);
                }
            }
            ListTag serums = playerTag.getList(TAG_SERUMS, Tag.TAG_STRING);
            for (int j = 0; j < serums.size(); j++) {
                entry.serums.add(serums.getString(j));
            }
        }
        return data;
    }
}
