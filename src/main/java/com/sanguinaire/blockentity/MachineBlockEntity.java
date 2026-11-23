package com.sanguinaire.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Common base for the Research Station and Serum Synthesizer. The block entity owns all machine state (inventory,
 * progress, ingredients being processed); menus only expose it.
 * <p>
 * Automation (hoppers, pipes) sees a restricted view of the inventory: insertion obeys the same slot filters as the
 * GUI plus {@link #canAutomationInsert}, and extraction is only allowed where {@link #canAutomationExtract} permits
 * (finished products). Machines can also push their products into a container directly below
 * ({@link #pushToContainerBelow}).
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider {
    private static final String TAG_INVENTORY = "Inventory";

    protected final ItemStackHandler inventory;
    private final int slotCount;
    private final IItemHandler automationView;
    private LazyOptional<IItemHandler> inventoryCapability = LazyOptional.empty();

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        this.slotCount = slots;
        this.inventory = new ItemStackHandler(slots) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return MachineBlockEntity.this.isItemValid(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return MachineBlockEntity.this.getSlotLimit(slot);
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        this.automationView = new AutomationView();
    }

    /** Extra rules for hoppers/pipes putting {@code stack} into {@code slot}, on top of the slot filter. Default: none. */
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        return true;
    }

    /**
     * Moves as much of {@code slot} as fits into the inventory of the block directly below (a chest, barrel,
     * hopper...). Server only. @return true if anything moved.
     */
    protected boolean pushToContainerBelow(int slot) {
        Level level = this.level;
        ItemStack stack = inventory.getStackInSlot(slot);
        if (level == null || level.isClientSide() || stack.isEmpty()) {
            return false;
        }
        BlockEntity below = level.getBlockEntity(getBlockPos().below());
        if (below == null) {
            return false;
        }
        IItemHandler target = below.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        if (target == null) {
            return false;
        }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
        if (remainder.getCount() == stack.getCount()) {
            return false;
        }
        inventory.setStackInSlot(slot, remainder);
        return true;
    }

    /** Experience orbs popping out of the top of the machine, for whoever is tending it. Server only. */
    protected void awardExperience(int points) {
        if (points > 0 && level instanceof ServerLevel serverLevel) {
            ExperienceOrb.award(serverLevel, Vec3.atCenterOf(getBlockPos()).add(0.0D, 0.7D, 0.0D), points);
        }
    }

    /** Whether hoppers/pipes may take {@code stack} out of {@code slot}. Default: never. */
    protected boolean canAutomationExtract(int slot, ItemStack stack) {
        return false;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    protected abstract boolean isItemValid(int slot, ItemStack stack);

    protected int getSlotLimit(int slot) {
        return 64;
    }

    /** Items consumed into the machine and still being processed; returned to the world if the machine is broken. */
    protected abstract List<ItemStack> getProcessingStacks();

    /** Drops the inventory and any in-process ingredients. */
    public void dropContents() {
        Level level = this.level;
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockPos pos = getBlockPos();
        for (int i = 0; i < inventory.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(i).copy());
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
        for (ItemStack stack : getProcessingStacks()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
        }
        clearProcessing();
    }

    protected abstract void clearProcessing();

    @Override
    public void onLoad() {
        super.onLoad();
        inventoryCapability = LazyOptional.of(() -> automationView);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventoryCapability.invalidate();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    /** Item-handler view exposed to automation. */
    private final class AutomationView implements IItemHandler {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!canAutomationInsert(slot, stack)) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!canAutomationExtract(slot, inventory.getStackInSlot(slot))) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return inventory.isItemValid(slot, stack) && canAutomationInsert(slot, stack);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_INVENTORY, inventory.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_INVENTORY)) {
            inventory.deserializeNBT(tag.getCompound(TAG_INVENTORY));
            if (inventory.getSlots() != slotCount) {
                // Saved by an older version with a different slot count (e.g. before the Synthesizer's blood slot):
                // the handler takes the saved size, so restore the current size and keep what fits.
                List<ItemStack> saved = new ArrayList<>();
                for (int i = 0; i < inventory.getSlots(); i++) {
                    saved.add(inventory.getStackInSlot(i));
                }
                inventory.setSize(slotCount);
                for (int i = 0; i < Math.min(saved.size(), slotCount); i++) {
                    inventory.setStackInSlot(i, saved.get(i));
                }
            }
        }
    }
}
