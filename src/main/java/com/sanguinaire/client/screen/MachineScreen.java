package com.sanguinaire.client.screen;

import com.sanguinaire.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Shared screen for the machines: walnut-and-brass panel, a glass gauge that fills downward with the machine's
 * progress, and a status line explaining what the machine is waiting for. Pure presentation; all state comes from
 * the menu's synced data.
 */
public abstract class MachineScreen<T extends MachineMenu> extends AbstractContainerScreen<T> {
    protected static final int LABEL_COLOR = 0xD8C38C;
    protected static final int STATUS_COLOR = 0xC9A9A0;
    /** Gauge position on screen and the fill sprite in the texture. */
    protected static final int GAUGE_X = 84;
    protected static final int GAUGE_Y = 37;
    protected static final int GAUGE_WIDTH = 8;
    protected static final int GAUGE_HEIGHT = 21;
    protected static final int GAUGE_FILL_U = 176;
    protected static final int GAUGE_FILL_V = 0;
    protected static final int STATUS_X = 100;
    protected static final int STATUS_Y = 40;
    protected static final int STATUS_WIDTH = 70;
    /** "Inventory" sits inside the panel, just above the gold line (y = 78) Nico drew over the player inventory. */
    protected static final int INVENTORY_LABEL_Y = 69;

    private final ResourceLocation texture;

    protected MachineScreen(T menu, Inventory playerInventory, Component title, ResourceLocation texture) {
        super(menu, playerInventory, title);
        this.texture = texture;
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    /** Translation key describing the machine's current status. */
    protected abstract String statusKey(int status);

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int filled = menu.getScaledProgress(GAUGE_HEIGHT);
        if (filled > 0) {
            graphics.blit(texture, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_FILL_U, GAUGE_FILL_V, GAUGE_WIDTH, filled);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 5, LABEL_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
        List<FormattedCharSequence> lines = font.split(Component.translatable(statusKey(menu.getStatus())), statusWidth());
        int y = statusY(lines.size());
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, statusX(), y, STATUS_COLOR, false);
            y += font.lineHeight;
        }
    }

    /** Left edge of the status text; screens whose art leaves room elsewhere override these three. */
    protected int statusX() {
        return STATUS_X;
    }

    /** Top of the status text, given how many lines it wraps to. */
    protected int statusY(int lineCount) {
        return STATUS_Y;
    }

    protected int statusWidth() {
        return STATUS_WIDTH;
    }
}
