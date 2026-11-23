package com.sanguinaire.menu;

import com.sanguinaire.blockentity.SerumSynthesizerBlockEntity;
import com.sanguinaire.registry.ModBlocks;
import com.sanguinaire.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraftforge.items.SlotItemHandler;

/** Serum Synthesizer: three ingredient slots in a row on top, progress flowing down to the syringe/result slot, and the optional blood slot on the middle left. */
public class SerumSynthesizerMenu extends MachineMenu {
    public static final int INGREDIENT_Y = 17;
    public static final int[] INGREDIENT_X = {44, 80, 116};
    public static final int RESULT_X = 80;
    public static final int RESULT_Y = 60;
    public static final int BLOOD_X = 26;
    public static final int BLOOD_Y = 40;

    /** Client constructor: the block position is written by {@code NetworkHooks.openScreen}. */
    public SerumSynthesizerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory,
                (SerumSynthesizerBlockEntity) playerInventory.player.level().getBlockEntity(buffer.readBlockPos()),
                new SimpleContainerData(3));
    }

    public SerumSynthesizerMenu(int containerId, Inventory playerInventory, SerumSynthesizerBlockEntity synthesizer, ContainerData data) {
        super(ModMenuTypes.SERUM_SYNTHESIZER.get(), containerId, playerInventory, synthesizer, data, ModBlocks.SERUM_SYNTHESIZER.get());
    }

    @Override
    protected void addMachineSlots() {
        for (int i = 0; i < 3; i++) {
            addSlot(new SlotItemHandler(machine.getInventory(), SerumSynthesizerBlockEntity.SLOT_INGREDIENT_1 + i,
                    INGREDIENT_X[i], INGREDIENT_Y));
        }
        addSlot(new SlotItemHandler(machine.getInventory(), SerumSynthesizerBlockEntity.SLOT_RESULT, RESULT_X, RESULT_Y));
        addSlot(new SlotItemHandler(machine.getInventory(), SerumSynthesizerBlockEntity.SLOT_BLOOD, BLOOD_X, BLOOD_Y));
    }
}
