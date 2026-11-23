package com.sanguinaire.menu;

import com.sanguinaire.blockentity.ResearchStationBlockEntity;
import com.sanguinaire.registry.ModBlocks;
import com.sanguinaire.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraftforge.items.SlotItemHandler;

/** Research Station: ingredient slot on top, Research Journal slot below, progress flowing down between them. */
public class ResearchStationMenu extends MachineMenu {
    public static final int INGREDIENT_X = 80;
    public static final int INGREDIENT_Y = 17;
    public static final int JOURNAL_X = 80;
    public static final int JOURNAL_Y = 60;

    private final ResearchStationBlockEntity station;

    /** Client constructor: the block position is written by {@code NetworkHooks.openScreen}. */
    public ResearchStationMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory,
                (ResearchStationBlockEntity) playerInventory.player.level().getBlockEntity(buffer.readBlockPos()),
                new SimpleContainerData(3));
    }

    public ResearchStationMenu(int containerId, Inventory playerInventory, ResearchStationBlockEntity station, ContainerData data) {
        super(ModMenuTypes.RESEARCH_STATION.get(), containerId, playerInventory, station, data, ModBlocks.RESEARCH_STATION.get());
        this.station = station;
    }

    @Override
    protected void addMachineSlots() {
        addSlot(new SlotItemHandler(machine.getInventory(), ResearchStationBlockEntity.SLOT_INGREDIENT, INGREDIENT_X, INGREDIENT_Y));
        addSlot(new SlotItemHandler(machine.getInventory(), ResearchStationBlockEntity.SLOT_JOURNAL, JOURNAL_X, JOURNAL_Y));
    }

    public ResearchStationBlockEntity getStation() {
        return station;
    }
}
