package com.sanguinaire.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Client key mappings. Registered in {@link ClientModEvents}; rebindable in Controls like any vanilla key. */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.sanguinaire";

    /** Mutation Active Ability Key, V by default. */
    public static final KeyMapping MUTATION_ABILITY = new KeyMapping("key.sanguinaire.mutation_ability",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    private ModKeyMappings() {
    }
}
