package com.sanguinaire.client.screen;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.blockentity.SanguineAlembicBlockEntity;
import com.sanguinaire.menu.SanguineAlembicMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SanguineAlembicScreen extends MachineScreen<SanguineAlembicMenu> {
    private static final String PREFIX = "gui." + Sanguinaire.MOD_ID + ".sanguine_alembic.";
    /** The empty panel right of the potion slot, in Nico's GUI art. Text is centred in it vertically. */
    private static final int STATUS_BOX_X = 118;
    private static final int STATUS_BOX_TOP = 17;
    private static final int STATUS_BOX_BOTTOM = 47;
    private static final int STATUS_BOX_WIDTH = 52;

    public SanguineAlembicScreen(SanguineAlembicMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, Sanguinaire.id("textures/gui/sanguine_alembic.png"));
    }

    @Override
    protected int statusX() {
        return STATUS_BOX_X;
    }

    @Override
    protected int statusY(int lineCount) {
        int height = lineCount * font.lineHeight - 1;
        return Math.max(STATUS_BOX_TOP, STATUS_BOX_TOP + (STATUS_BOX_BOTTOM - STATUS_BOX_TOP - height) / 2);
    }

    @Override
    protected int statusWidth() {
        return STATUS_BOX_WIDTH;
    }

    @Override
    protected String statusKey(int status) {
        return PREFIX + switch (status) {
            case SanguineAlembicBlockEntity.STATUS_WORKING -> "working";
            case SanguineAlembicBlockEntity.STATUS_NEEDS_POTION -> "needs_potion";
            case SanguineAlembicBlockEntity.STATUS_NO_EFFECT -> "no_effect";
            case SanguineAlembicBlockEntity.STATUS_ALREADY_ALTERED -> "already_altered";
            case SanguineAlembicBlockEntity.STATUS_RESULT_OCCUPIED -> "result_occupied";
            case SanguineAlembicBlockEntity.STATUS_NEEDS_BLOOD -> "needs_blood";
            case SanguineAlembicBlockEntity.STATUS_DUPLICATE_EFFECT -> "duplicate_effect";
            default -> "idle";
        };
    }
}
