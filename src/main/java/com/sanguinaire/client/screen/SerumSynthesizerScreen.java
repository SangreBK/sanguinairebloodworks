package com.sanguinaire.client.screen;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.blockentity.SerumSynthesizerBlockEntity;
import com.sanguinaire.menu.SerumSynthesizerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SerumSynthesizerScreen extends MachineScreen<SerumSynthesizerMenu> {
    private static final String PREFIX = "gui." + Sanguinaire.MOD_ID + ".serum_synthesizer.";

    public SerumSynthesizerScreen(SerumSynthesizerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, Sanguinaire.id("textures/gui/serum_synthesizer.png"));
    }

    @Override
    protected String statusKey(int status) {
        return PREFIX + switch (status) {
            case SerumSynthesizerBlockEntity.STATUS_BREWING -> "brewing";
            case SerumSynthesizerBlockEntity.STATUS_NEEDS_SYRINGE -> "needs_syringe";
            case SerumSynthesizerBlockEntity.STATUS_INVALID -> "invalid";
            case SerumSynthesizerBlockEntity.STATUS_RESULT_OCCUPIED -> "result_occupied";
            default -> "idle";
        };
    }
}
