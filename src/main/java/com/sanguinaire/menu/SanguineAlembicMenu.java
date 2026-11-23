package com.sanguinaire.menu;

import com.sanguinaire.blockentity.SanguineAlembicBlockEntity;
import com.sanguinaire.registry.ModBlocks;
import com.sanguinaire.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraftforge.items.SlotItemHandler;

/** Sanguine Alembic: blood and potion side by side on top, progress flowing down to the result slot. */
public class SanguineAlembicMenu extends MachineMenu {
    public static final int TOP_Y = 17;
    public static final int BLOOD_X = 62;
    public static final int POTION_X = 98;
    public static final int RESULT_X = 80;
    public static final int RESULT_Y = 60;

    /** Client constructor: the block position is written by {@code NetworkHooks.openScreen}. */
    public SanguineAlembicMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory,
                (SanguineAlembicBlockEntity) playerInventory.player.level().getBlockEntity(buffer.readBlockPos()),
                new SimpleContainerData(3));
    }

    public SanguineAlembicMenu(int containerId, Inventory playerInventory, SanguineAlembicBlockEntity alembic, ContainerData data) {
        super(ModMenuTypes.SANGUINE_ALEMBIC.get(), containerId, playerInventory, alembic, data, ModBlocks.SANGUINE_ALEMBIC.get());
    }

    @Override
    protected void addMachineSlots() {
        addSlot(new SlotItemHandler(machine.getInventory(), SanguineAlembicBlockEntity.SLOT_BLOOD, BLOOD_X, TOP_Y));
        addSlot(new SlotItemHandler(machine.getInventory(), SanguineAlembicBlockEntity.SLOT_POTION, POTION_X, TOP_Y));
        addSlot(new SlotItemHandler(machine.getInventory(), SanguineAlembicBlockEntity.SLOT_RESULT, RESULT_X, RESULT_Y));
    }
}
