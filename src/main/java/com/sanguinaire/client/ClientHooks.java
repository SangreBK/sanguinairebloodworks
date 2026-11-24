package com.sanguinaire.client;

import com.sanguinaire.client.screen.SurgicalJournalScreen;
import com.sanguinaire.player.PlayerMutationData;
import com.sanguinaire.player.PlayerMutationProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Client-only entry points called from common code through {@code DistExecutor}. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openSurgicalJournal() {
        Minecraft.getInstance().setScreen(new SurgicalJournalScreen());
    }

    /** The key currently bound to the Mutation Ability, as shown in Controls (e.g. "V"). */
    public static Component abilityKeyName() {
        return ModKeyMappings.MUTATION_ABILITY.getTranslatedKeyMessage();
    }

    /** The local player's Weakened Health (0 if there is no player yet). */
    public static Float localWeakenedHealth() {
        Player player = Minecraft.getInstance().player;
        return player == null ? 0.0F : PlayerMutationProvider.get(player).map(PlayerMutationData::getWeakenedHealth).orElse(0.0F);
    }
}
