package com.sanguinaire.menu;

import com.sanguinaire.item.SerumBriefcaseItem;
import com.sanguinaire.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

import java.util.List;

/**
 * Menu for an open Serum Briefcase.
 *
 * <h2>Safety model</h2>
 * <ul>
 *     <li>All slot rules are enforced on the server (and mirrored on the client for prediction): briefcase slots
 *     accept only {@link BriefcaseItemHandler#isStorable} items, including through shift-click and number keys.</li>
 *     <li>The inventory slot holding the open briefcase is locked: it cannot be picked up, swapped (number keys / F),
 *     thrown, or have anything placed into it. The briefcase therefore cannot be moved, dropped or nested while
 *     its contents are being edited.</li>
 *     <li>Every change is written straight back to the briefcase stack on the server, so nothing is held only in
 *     the menu; closing the menu never needs to give items back, which rules out close-time duplication.</li>
 *     <li>{@link #stillValid} closes the menu if the exact briefcase stack is no longer in its slot (or has been
 *     emptied, e.g. by a drop packet). The server also rejects clicks on a menu that is no longer valid.</li>
 * </ul>
 */
public class SerumBriefcaseMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 196;
    public static final int CASE_SLOTS_X = 8;
    public static final int CASE_ROW_0_Y = 36;
    public static final int CASE_ROW_1_Y = 62;
    public static final int PLAYER_INVENTORY_Y = 114;
    public static final int HOTBAR_Y = 172;

    private static final int CASE_SLOT_START = 0;
    private static final int CASE_SLOT_END = BriefcaseItemHandler.SIZE; // exclusive
    private static final int PLAYER_SLOT_START = CASE_SLOT_END;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 36; // exclusive

    private final Player player;
    private final ItemStack briefcase;
    /** Index in {@link Inventory} of the slot holding the open briefcase (0-8 hotbar, or 40 for the offhand). */
    private final int lockedInventoryIndex;
    /** Menu slot index of the locked slot, or -1 when it is the offhand (which this menu does not display). */
    private int lockedMenuSlot = -1;
    private final BriefcaseItemHandler handler;

    /** Client-side constructor, called by the menu type factory with the data written by the server. */
    public SerumBriefcaseMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory, data.readEnum(InteractionHand.class));
    }

    public SerumBriefcaseMenu(int containerId, Inventory playerInventory, InteractionHand hand) {
        super(ModMenuTypes.SERUM_BRIEFCASE.get(), containerId);
        this.player = playerInventory.player;
        this.lockedInventoryIndex = hand == InteractionHand.MAIN_HAND ? playerInventory.selected : Inventory.SLOT_OFFHAND;
        this.briefcase = playerInventory.getItem(lockedInventoryIndex);
        this.handler = new BriefcaseItemHandler() {
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                saveToBriefcase();
            }
        };

        if (!player.level().isClientSide()) {
            List<ItemStack> rejected = handler.loadFrom(briefcase);
            if (!rejected.isEmpty()) {
                // Never delete items: anything that is not allowed in the briefcase goes back to the player.
                for (ItemStack stack : rejected) {
                    playerInventory.placeItemBackInInventory(stack);
                }
                saveToBriefcase();
            }
        }

        for (int row = 0; row < BriefcaseItemHandler.ROWS; row++) {
            int y = row == 0 ? CASE_ROW_0_Y : CASE_ROW_1_Y;
            for (int column = 0; column < BriefcaseItemHandler.COLUMNS; column++) {
                addSlot(new BriefcaseSlot(handler, column + row * BriefcaseItemHandler.COLUMNS, CASE_SLOTS_X + column * 18, y));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                addPlayerSlot(playerInventory, index, 8 + column * 18, PLAYER_INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            addPlayerSlot(playerInventory, column, 8 + column * 18, HOTBAR_Y);
        }
    }

    private void addPlayerSlot(Inventory inventory, int index, int x, int y) {
        if (index == lockedInventoryIndex) {
            Slot slot = addSlot(new LockedSlot(inventory, index, x, y));
            lockedMenuSlot = slot.index;
        } else {
            addSlot(new Slot(inventory, index, x, y));
        }
    }

    /** Persists the handler onto the briefcase stack. Server only; the client view is synced through the menu. */
    private void saveToBriefcase() {
        if (!player.level().isClientSide() && briefcase.getItem() instanceof SerumBriefcaseItem && !briefcase.isEmpty()) {
            handler.saveTo(briefcase);
        }
    }

    /** Menu slot index of the locked briefcase slot, or -1 if it is not displayed (offhand). */
    public int getLockedMenuSlot() {
        return lockedMenuSlot;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId == lockedMenuSlot) {
            return;
        }
        // Number keys (0-8) and the offhand key (40) swap with an inventory index: never allow the briefcase's own.
        if (clickType == ClickType.SWAP && button == lockedInventoryIndex) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < CASE_SLOT_END) {
            // Briefcase -> player inventory (hotbar first, like vanilla chests).
            if (!moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory -> briefcase; anything not storable is refused outright.
            if (!BriefcaseItemHandler.isStorable(stack)) {
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(stack, CASE_SLOT_START, CASE_SLOT_END, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive()
                && !briefcase.isEmpty()
                && briefcase.getItem() instanceof SerumBriefcaseItem
                && player.getInventory().getItem(lockedInventoryIndex) == briefcase;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        saveToBriefcase();
    }

    /** Briefcase storage slot: filtered, and persists direct stack mutations (e.g. merges) to the briefcase. */
    private class BriefcaseSlot extends SlotItemHandler {
        BriefcaseSlot(BriefcaseItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return BriefcaseItemHandler.isStorable(stack) && super.mayPlace(stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            saveToBriefcase();
        }
    }

    /** The player inventory slot that holds the open briefcase: completely immovable while the menu is open. */
    private static class LockedSlot extends Slot {
        LockedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
