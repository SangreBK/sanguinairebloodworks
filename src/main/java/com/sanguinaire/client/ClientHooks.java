package com.sanguinaire.client;

import com.sanguinaire.client.screen.SurgicalJournalScreen;
import net.minecraft.client.Minecraft;

/** Client-only entry points called from common code through {@code DistExecutor}. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openSurgicalJournal() {
        Minecraft.getInstance().setScreen(new SurgicalJournalScreen());
    }
}
