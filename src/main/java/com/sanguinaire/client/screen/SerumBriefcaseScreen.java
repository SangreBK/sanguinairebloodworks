package com.sanguinaire.client.screen;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.menu.SerumBriefcaseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * An open Victorian surgeon's briefcase: oxblood leather shell, brass fittings and a crimson velvet tray holding
 * the serums, above a walnut panel with the player's inventory. Purely presentational; all rules live in the menu.
 */
public class SerumBriefcaseScreen extends AbstractContainerScreen<SerumBriefcaseMenu> {
    private static final ResourceLocation TEXTURE = Sanguinaire.id("textures/gui/serum_briefcase.png");

    /** Dark ink for the engraved brass name plate. */
    private static final int TITLE_COLOR = 0x3B2A12;
    /** Pale brass for text on the walnut panel. */
    private static final int INVENTORY_LABEL_COLOR = 0xD8C38C;
    /** Brass plate the title is engraved on (texture coordinates). */
    private static final int PLATE_LEFT = 38;
    private static final int PLATE_RIGHT = 138;
    private static final int PLATE_TEXT_Y = 17;
    private static final int LOCKED_SLOT_SHADE = 0x90200A0A;

    public SerumBriefcaseScreen(SerumBriefcaseMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = SerumBriefcaseMenu.IMAGE_WIDTH;
        this.imageHeight = SerumBriefcaseMenu.IMAGE_HEIGHT;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = SerumBriefcaseMenu.PLAYER_INVENTORY_Y - 11;
        this.titleLabelY = PLATE_TEXT_Y;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Centre the (possibly renamed) title on the brass plate, trimming it if it is too long.
        Component shown = title;
        int maxWidth = PLATE_RIGHT - PLATE_LEFT - 8;
        if (font.width(shown) > maxWidth) {
            shown = Component.literal(font.plainSubstrByWidth(title.getString(), maxWidth - font.width("..")) + "..");
        }
        int titleX = PLATE_LEFT + (PLATE_RIGHT - PLATE_LEFT - font.width(shown)) / 2;
        graphics.drawString(font, shown, titleX, titleLabelY, TITLE_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, INVENTORY_LABEL_COLOR, false);

        // Shade the slot holding the open briefcase so it reads as locked.
        int locked = menu.getLockedMenuSlot();
        if (locked >= 0 && locked < menu.slots.size()) {
            Slot slot = menu.slots.get(locked);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, LOCKED_SLOT_SHADE);
        }
    }
}
