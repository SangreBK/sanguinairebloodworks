package com.sanguinaire.research;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.UtilitySerum;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Knowledge that can only come from a Torn Page (Ancient City loot): the three utility serums and the three
 * original transformations. Each page holds exactly one note; reading it unlocks that journal entry for the reader
 * only. The id is the serum's or transformation's own id (they never collide), and is what the per-player unlock
 * list in {@link ResearchSavedData} stores.
 */
public enum ResearchNote {
    TAINTED_BLOOD(UtilitySerum.TAINTED_BLOOD, null),
    PHYSICAL_BOOST(UtilitySerum.PHYSICAL_BOOST, null),
    REVITALIZING(UtilitySerum.REVITALIZING, null),
    GOATMAN(null, MutationType.GOATMAN),
    ROTTING_ABOMINATION(null, MutationType.ROTTING_ABOMINATION),
    FADEHOUND(null, MutationType.FADEHOUND);

    @Nullable
    private final UtilitySerum serum;
    @Nullable
    private final MutationType mutation;

    ResearchNote(@Nullable UtilitySerum serum, @Nullable MutationType mutation) {
        this.serum = serum;
        this.mutation = mutation;
    }

    public String getId() {
        return serum != null ? serum.getId() : mutation.getId();
    }

    /** What the notes are about, e.g. "Tainted Blood Serum" or "Goatman Transformation". */
    public Component getSubject() {
        if (serum != null) {
            return serum.getDisplayName();
        }
        return Component.translatable("tooltip." + Sanguinaire.MOD_ID + ".torn_page.transformation", mutation.getDisplayName());
    }

    @Nullable
    public static ResearchNote byId(String id) {
        for (ResearchNote note : values()) {
            if (note.getId().equals(id)) {
                return note;
            }
        }
        return null;
    }

    /** True for the transformations whose journal entry comes from a Torn Page instead of research. */
    public static boolean isTornPageTransformation(MutationType type) {
        for (ResearchNote note : values()) {
            if (note.mutation == type) {
                return true;
            }
        }
        return false;
    }
}
