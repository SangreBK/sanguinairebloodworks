package com.sanguinaire.client.screen;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.blockentity.ResearchStationBlockEntity;
import com.sanguinaire.menu.ResearchStationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ResearchStationScreen extends MachineScreen<ResearchStationMenu> {
    private static final String PREFIX = "gui." + Sanguinaire.MOD_ID + ".research_station.";
    private static final int STATUS_Y_HIGH = 24;

    public ResearchStationScreen(ResearchStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, Sanguinaire.id("textures/gui/research_station.png"));
    }

    @Override
    protected String statusKey(int status) {
        return PREFIX + switch (status) {
            case ResearchStationBlockEntity.STATUS_RESEARCHING -> "researching";
            case ResearchStationBlockEntity.STATUS_NEEDS_JOURNAL -> "needs_journal";
            case ResearchStationBlockEntity.STATUS_ALREADY_KNOWN -> "already_known";
            case ResearchStationBlockEntity.STATUS_NO_RESEARCHER -> "no_researcher";
            case ResearchStationBlockEntity.STATUS_AWAITING_RESEARCHER -> "awaiting_researcher";
            default -> "idle";
        };
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Component specimen = Component.translatable(PREFIX + "specimen");
        Component journal = Component.translatable(PREFIX + "journal");
        graphics.drawString(font, specimen, ResearchStationMenu.INGREDIENT_X - 6 - font.width(specimen),
                ResearchStationMenu.INGREDIENT_Y + 4, STATUS_COLOR, false);
        // Right of the journal slot: on the left it would run into the "Inventory" label.
        graphics.drawString(font, journal, ResearchStationMenu.JOURNAL_X + 16 + 6,
                ResearchStationMenu.JOURNAL_Y + 4, STATUS_COLOR, false);
    }

    /** Status text starts higher than on the other machines, so long messages end above the "Journal" label. */
    @Override
    protected int statusY(int lineCount) {
        return STATUS_Y_HIGH;
    }
}
