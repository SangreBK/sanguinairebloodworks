package com.sanguinaire.settings;

import com.sanguinaire.Sanguinaire;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-wide rules set with {@code /sanguinaire} (saved with the overworld, synced to every client):
 * <ul>
 *     <li>{@code enablefirstperson}: transformed players may use first person (default off: third person only).</li>
 *     <li>{@code allowtransformationitems}: transformed players may hold items and scroll their hotbar (default off:
 *     empty hands, hotbar hidden and locked).</li>
 * </ul>
 */
public class SanguinaireSettings extends SavedData {
    private static final String NAME = Sanguinaire.MOD_ID + "_settings";
    private static final String TAG_FIRST_PERSON = "FirstPerson";
    private static final String TAG_ITEMS = "TransformationItems";

    private boolean firstPersonAllowed;
    private boolean transformationItemsAllowed;

    public static SanguinaireSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SanguinaireSettings::load, SanguinaireSettings::new, NAME);
    }

    private static SanguinaireSettings load(CompoundTag tag) {
        SanguinaireSettings settings = new SanguinaireSettings();
        settings.firstPersonAllowed = tag.getBoolean(TAG_FIRST_PERSON);
        settings.transformationItemsAllowed = tag.getBoolean(TAG_ITEMS);
        return settings;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(TAG_FIRST_PERSON, firstPersonAllowed);
        tag.putBoolean(TAG_ITEMS, transformationItemsAllowed);
        return tag;
    }

    public boolean isFirstPersonAllowed() {
        return firstPersonAllowed;
    }

    public void setFirstPersonAllowed(boolean allowed) {
        firstPersonAllowed = allowed;
        setDirty();
    }

    public boolean areTransformationItemsAllowed() {
        return transformationItemsAllowed;
    }

    public void setTransformationItemsAllowed(boolean allowed) {
        transformationItemsAllowed = allowed;
        setDirty();
    }
}
