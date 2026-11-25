package com.sanguinaire.blockentity;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.item.BloodSampleItem;
import com.sanguinaire.menu.SanguineAlembicMenu;
import com.sanguinaire.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Sanguine Alembic: binds potion effects to a Blood Sample, one per pass, up to
 * {@value BloodSampleItem#MAX_EFFECTS} different effects. Top left: a Blood Sample (fresh or already altered). Top
 * right: a drinkable potion. After 30 seconds the sample comes out in the result slot as an <b>Altered Blood
 * Sample</b> carrying the potion's effect too (its first, for potions with several), and the potion leaves an empty
 * Glass Bottle behind. A sample that is full, or already carries that effect, is refused. Nothing is consumed until the process finishes; taking an input out resets the progress.
 * <p>
 * A serum brewed with altered blood applies that effect when its transformation ends. Hoppers feed blood and
 * potions in, and may take results and empty bottles out. Each altered sample releases {@value #EXPERIENCE}
 * experience points.
 */
public class SanguineAlembicBlockEntity extends MachineBlockEntity {
    public static final int SLOT_BLOOD = 0;
    public static final int SLOT_POTION = 1;
    public static final int SLOT_RESULT = 2;
    public static final int SLOT_COUNT = 3;
    /** Binding time: 30 seconds. */
    public static final int PROCESS_TICKS = 30 * 20;
    /** Experience points released per altered sample. */
    public static final int EXPERIENCE = 8;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_WORKING = 1;
    public static final int STATUS_NEEDS_POTION = 2;
    public static final int STATUS_NO_EFFECT = 3;
    public static final int STATUS_ALREADY_ALTERED = 4;
    public static final int STATUS_RESULT_OCCUPIED = 5;
    public static final int STATUS_NEEDS_BLOOD = 6;
    public static final int STATUS_DUPLICATE_EFFECT = 7;

    private static final String TAG_PROGRESS = "Progress";

    private int progress;
    private int status = STATUS_IDLE;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> PROCESS_TICKS;
                case 2 -> status;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative: values only flow server -> client.
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public SanguineAlembicBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SANGUINE_ALEMBIC.get(), pos, state, SLOT_COUNT);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_BLOOD -> BloodSampleItem.getType(stack) != null;
            case SLOT_POTION -> stack.is(Items.POTION);
            default -> false; // the result slot is output only
        };
    }

    @Override
    protected int getSlotLimit(int slot) {
        return 1;
    }

    /** Hoppers may take altered samples and the empty bottles left behind. */
    @Override
    protected boolean canAutomationExtract(int slot, ItemStack stack) {
        return slot == SLOT_RESULT || (slot == SLOT_POTION && stack.is(Items.GLASS_BOTTLE));
    }

    @Override
    protected List<ItemStack> getProcessingStacks() {
        return List.of(); // inputs stay in their slots until the work is done
    }

    @Override
    protected void clearProcessing() {
        progress = 0;
        setChanged();
    }

    private void setStatus(int newStatus) {
        if (status != newStatus) {
            status = newStatus;
            setChanged();
        }
    }

    /** The effect a drinkable potion would bind, or null. */
    @Nullable
    private static MobEffectInstance potionEffect(ItemStack potion) {
        if (!potion.is(Items.POTION)) {
            return null;
        }
        List<MobEffectInstance> effects = PotionUtils.getMobEffects(potion);
        return effects.isEmpty() ? null : effects.get(0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SanguineAlembicBlockEntity alembic) {
        alembic.tick(level);
        // The syringe on the model only appears once the altered sample is waiting in the result slot.
        com.sanguinaire.block.MachineBlock.syncResultState(level, pos, level.getBlockState(pos),
                !alembic.inventory.getStackInSlot(SLOT_RESULT).isEmpty());
    }

    private void tick(Level level) {
        int blocked = blockedStatus();
        if (blocked != STATUS_WORKING) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            setStatus(blocked);
            return;
        }
        progress++;
        setStatus(STATUS_WORKING);
        setChanged();
        if (progress < PROCESS_TICKS) {
            return;
        }
        ItemStack blood = inventory.getStackInSlot(SLOT_BLOOD);
        MobEffectInstance effect = potionEffect(inventory.getStackInSlot(SLOT_POTION));
        if (effect == null) {
            return;
        }
        inventory.setStackInSlot(SLOT_RESULT, BloodSampleItem.withEffect(blood, effect));
        inventory.setStackInSlot(SLOT_BLOOD, ItemStack.EMPTY);
        inventory.setStackInSlot(SLOT_POTION, new ItemStack(Items.GLASS_BOTTLE));
        progress = 0;
        setStatus(STATUS_IDLE);
        BlockPos pos = getBlockPos();
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 0.5F);
        level.playSound(null, pos, SoundEvents.HONEY_DRINK, SoundSource.BLOCKS, 0.6F, 0.6F);
        awardExperience(EXPERIENCE);
    }

    /** {@link #STATUS_WORKING} if everything is in place, otherwise what is missing. */
    private int blockedStatus() {
        ItemStack blood = inventory.getStackInSlot(SLOT_BLOOD);
        ItemStack potion = inventory.getStackInSlot(SLOT_POTION);
        if (blood.isEmpty()) {
            return potion.isEmpty() ? STATUS_IDLE : STATUS_NEEDS_BLOOD;
        }
        if (BloodSampleItem.getEffects(blood).size() >= BloodSampleItem.MAX_EFFECTS) {
            return STATUS_ALREADY_ALTERED; // full
        }
        if (!potion.is(Items.POTION)) {
            return STATUS_NEEDS_POTION;
        }
        MobEffectInstance effect = potionEffect(potion);
        if (effect == null) {
            return STATUS_NO_EFFECT;
        }
        if (BloodSampleItem.hasEffect(blood, effect)) {
            return STATUS_DUPLICATE_EFFECT;
        }
        if (!inventory.getStackInSlot(SLOT_RESULT).isEmpty()) {
            return STATUS_RESULT_OCCUPIED;
        }
        return STATUS_WORKING;
    }

    // ---- MenuProvider ----------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block." + Sanguinaire.MOD_ID + ".sanguine_alembic");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SanguineAlembicMenu(containerId, playerInventory, this, data);
    }

    // ---- Persistence -----------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_PROGRESS, progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        progress = tag.getInt(TAG_PROGRESS);
    }
}
