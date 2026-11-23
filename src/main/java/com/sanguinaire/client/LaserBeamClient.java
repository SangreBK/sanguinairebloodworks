package com.sanguinaire.client;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Client-side cache of which players are charging a Guardian laser at which entity (visual state only). */
public final class LaserBeamClient {

    public record Beam(int targetId, long startGameTime, int chargeTicks) {
        /** Charge progress in [0, 1]. */
        public float progress(long gameTime, float partialTick) {
            return chargeTicks <= 0 ? 1.0F : Math.min(1.0F, (gameTime - startGameTime + partialTick) / chargeTicks);
        }
    }

    private static final Map<Integer, Beam> BEAMS = new HashMap<>();

    private LaserBeamClient() {
    }

    public static void set(int playerId, int targetId, long gameTime, int chargeTicks) {
        if (targetId < 0) {
            BEAMS.remove(playerId);
        } else {
            BEAMS.put(playerId, new Beam(targetId, gameTime, chargeTicks));
        }
    }

    @Nullable
    public static Beam get(int playerId) {
        return BEAMS.get(playerId);
    }

    public static void clear() {
        BEAMS.clear();
    }
}
