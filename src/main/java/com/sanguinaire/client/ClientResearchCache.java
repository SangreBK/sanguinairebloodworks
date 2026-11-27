package com.sanguinaire.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The local player's own research, as last sent by the server. Kept outside the player entity so it survives the
 * client recreating the player on respawn or dimension change. Cleared when leaving a world.
 */
public final class ClientResearchCache {
    private static final Set<ResourceLocation> RESEARCH = new LinkedHashSet<>();
    private static final Set<String> SERUMS = new LinkedHashSet<>();
    /** The local player's Conscript notoriety (0 to 5), for the Journal's Notoriety stars. */
    private static int notoriety;

    private ClientResearchCache() {
    }

    public static void set(Collection<ResourceLocation> research, Collection<String> serums) {
        RESEARCH.clear();
        RESEARCH.addAll(research);
        SERUMS.clear();
        SERUMS.addAll(serums);
    }

    public static void clear() {
        RESEARCH.clear();
        SERUMS.clear();
        notoriety = 0;
    }

    public static void setNotoriety(int value) {
        notoriety = value;
    }

    public static int getNotoriety() {
        return notoriety;
    }

    public static Set<ResourceLocation> research() {
        return Collections.unmodifiableSet(RESEARCH);
    }

    public static boolean isResearched(ResourceLocation item) {
        return RESEARCH.contains(item);
    }

    public static boolean isSerumUnlocked(String serumId) {
        return SERUMS.contains(serumId);
    }
}
