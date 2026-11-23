package com.sanguinaire.menu;

import com.sanguinaire.blockentity.MachineBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Shared menu logic for the machines: player inventory slots, synced progress/status values, distance check and
 * filter-respecting shift-click. All slot rules come from the block entity's inventory, so they are enforced
 * identically on the server and client and for automation.
 */
public abstract class MachineMenu extends AbstractContainerMenu {
    public static final int PLAYER_INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    protected final MachineBlockEntity machine;
    protected final ContainerData data;
    private final ContainerLevelAccess access;
    private final Block block;
    private final int machineSlots;

    protected MachineMenu(MenuType<?> type, int containerId, Inventory playerInventory, MachineBlockEntity machine,
                          ContainerData data, Block block) {
        super(type, containerId);
        this.machine = machine;
        this.data = data;
        this.block = block;
        this.access = ContainerLevelAccess.create(machine.getLevel(), machine.getBlockPos());
        this.machineSlots = machine.getInventory().getSlots();
        checkContainerDataCount(data, 3);
        addMachineSlots();
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    /** Adds exactly {@code machine.getInventory().getSlots()} slots, in slot-index order. */
    protected abstract void addMachineSlots();

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        return data.get(1);
    }

    public int getStatus() {
        return data.get(2);
    }

    /** Progress scaled to {@code pixels} for the progress bar. */
    public int getScaledProgress(int pixels) {
        int max = getMaxProgress();
        return max <= 0 ? 0 : Math.min(pixels, getProgress() * pixels / max);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = machineSlots;
        int playerEnd = slots.size();

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            // moveItemStackTo only places into slots whose mayPlace accepts the item.
            return ItemStack.EMPTY;
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
        return stillValid(access, player, block);
    }
}
