package com.sanguinaire.blockentity;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.blood.BloodInfusion;
import com.sanguinaire.item.BloodSampleItem;
import com.sanguinaire.item.SerumItem;
import com.sanguinaire.menu.SerumSynthesizerMenu;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.UtilitySerum;
import com.sanguinaire.registry.ModBlockEntities;
import com.sanguinaire.registry.ModItems;
import com.sanguinaire.research.ResearchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Serum Synthesizer (design §2.3): three ingredient slots, a progress bar, and a result slot that must hold an
 * Empty Syringe.
 * <p>
 * Ingredients are body parts (transformation serums) or the vanilla items of utility serums.
 * When the three ingredients form a valid serum (in any order) and an Empty Syringe is waiting, one of each
 * ingredient is consumed and brewing starts. When it completes, the syringe becomes the serum. Invalid combinations
 * never consume anything. The result slot holds a single syringe, so a finished serum never overwrites or deletes
 * another item. If the syringe is removed mid-brew, brewing pauses until one is put back; if the machine is broken,
 * the consumed ingredients are dropped back.
 * <p>
 * <b>Blood slot</b> (optional, middle left): a Blood Sample there is mixed into the next <em>transformation</em>
 * serum ({@link BloodInfusion}): it is consumed when brewing starts and changes what the serum does. Utility serums
 * never use it.
 * <p>
 * <b>Automation:</b> hoppers (from above or the sides) feed ingredients, Empty Syringes and Blood Samples into their
 * slots; each distinct ingredient gets its own slot so three different parts end up side by side. Finished serums
 * are pushed into a chest (or any container) directly below, and hoppers below may pull them out.
 * <p>
 * Each finished serum releases {@value #EXPERIENCE} experience points.
 */
public class SerumSynthesizerBlockEntity extends MachineBlockEntity {
    public static final int SLOT_INGREDIENT_1 = 0;
    public static final int SLOT_INGREDIENT_2 = 1;
    public static final int SLOT_INGREDIENT_3 = 2;
    public static final int SLOT_RESULT = 3;
    public static final int SLOT_BLOOD = 4;
    public static final int SLOT_COUNT = 5;
    /** Brewing time: 25 seconds. */
    public static final int BREW_TICKS = 25 * 20;
    /** Experience points released per finished serum. */
    public static final int EXPERIENCE = 15;
    private static final int PUSH_INTERVAL = 10;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_BREWING = 1;
    public static final int STATUS_NEEDS_SYRINGE = 2;
    public static final int STATUS_INVALID = 3;
    public static final int STATUS_RESULT_OCCUPIED = 4;

    private static final String TAG_BREWING = "Brewing";
    private static final String TAG_CONSUMED = "Consumed";
    private static final String TAG_PROGRESS = "Progress";

    /** The serum being brewed (empty when idle). */
    private ItemStack brewing = ItemStack.EMPTY;
    private final List<ItemStack> consumed = new ArrayList<>();
    private int progress;
    private int status = STATUS_IDLE;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> BREW_TICKS;
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

    public SerumSynthesizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERUM_SYNTHESIZER.get(), pos, state, SLOT_COUNT);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        if (slot == SLOT_RESULT) {
            return stack.is(ModItems.EMPTY_SYRINGE.get());
        }
        if (slot == SLOT_BLOOD) {
            return BloodSampleItem.getType(stack) != null;
        }
        return ResearchManager.isBodyPart(stack) || UtilitySerum.isIngredient(stack.getItem());
    }

    @Override
    protected int getSlotLimit(int slot) {
        return slot == SLOT_RESULT || slot == SLOT_BLOOD ? 1 : 64;
    }

    /** Hoppers give every distinct ingredient its own slot instead of piling one ingredient into all three. */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        if (slot < SLOT_INGREDIENT_1 || slot > SLOT_INGREDIENT_3) {
            return true;
        }
        ItemStack current = inventory.getStackInSlot(slot);
        if (!current.isEmpty()) {
            return ItemStack.isSameItemSameTags(current, stack);
        }
        for (int other = SLOT_INGREDIENT_1; other <= SLOT_INGREDIENT_3; other++) {
            if (other != slot && ItemStack.isSameItemSameTags(inventory.getStackInSlot(other), stack)) {
                return false;
            }
        }
        return true;
    }

    /** Hoppers may only pull finished serums out of the result slot. */
    @Override
    protected boolean canAutomationExtract(int slot, ItemStack stack) {
        return slot == SLOT_RESULT && stack.is(ModItems.SERUM.get());
    }

    @Override
    protected List<ItemStack> getProcessingStacks() {
        return consumed;
    }

    @Override
    protected void clearProcessing() {
        consumed.clear();
        brewing = ItemStack.EMPTY;
        progress = 0;
        setChanged();
    }

    private void setStatus(int newStatus) {
        if (status != newStatus) {
            status = newStatus;
            setChanged();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SerumSynthesizerBlockEntity synthesizer) {
        synthesizer.tick(level);
    }

    private void tick(Level level) {
        if (level.getGameTime() % PUSH_INTERVAL == 0 && inventory.getStackInSlot(SLOT_RESULT).is(ModItems.SERUM.get())) {
            pushToContainerBelow(SLOT_RESULT);
        }
        ItemStack result = inventory.getStackInSlot(SLOT_RESULT);
        boolean hasSyringe = result.is(ModItems.EMPTY_SYRINGE.get());

        if (brewing.isEmpty()) {
            tryStartBrewing(level, result, hasSyringe);
            return;
        }
        if (!hasSyringe) {
            setStatus(STATUS_NEEDS_SYRINGE); // paused, progress kept
            return;
        }
        progress++;
        setStatus(STATUS_BREWING);
        setChanged();
        if (progress < BREW_TICKS) {
            return;
        }
        inventory.setStackInSlot(SLOT_RESULT, brewing.copy());
        brewing = ItemStack.EMPTY;
        consumed.clear();
        progress = 0;
        setStatus(STATUS_IDLE);
        BlockPos pos = getBlockPos();
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 0.8F);
        awardExperience(EXPERIENCE);
        pushToContainerBelow(SLOT_RESULT);
    }

    private void tryStartBrewing(Level level, ItemStack result, boolean hasSyringe) {
        List<Item> ingredients = new ArrayList<>(3);
        for (int slot = SLOT_INGREDIENT_1; slot <= SLOT_INGREDIENT_3; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                setStatus(STATUS_IDLE);
                return;
            }
            ingredients.add(stack.getItem());
        }
        ItemStack match = findSerum(ingredients);
        if (match.isEmpty()) {
            setStatus(STATUS_INVALID);
            return;
        }
        if (!result.isEmpty() && !hasSyringe) {
            setStatus(STATUS_RESULT_OCCUPIED);
            return;
        }
        if (!hasSyringe) {
            setStatus(STATUS_NEEDS_SYRINGE);
            return;
        }
        for (int slot = SLOT_INGREDIENT_1; slot <= SLOT_INGREDIENT_3; slot++) {
            consumed.add(inventory.extractItem(slot, 1, false));
        }
        // Blood only goes into transformation serums.
        BloodInfusion blood = BloodSampleItem.toInfusion(inventory.getStackInSlot(SLOT_BLOOD));
        if (blood != null && SerumItem.getMutation(match) != null) {
            consumed.add(inventory.extractItem(SLOT_BLOOD, 1, false));
            SerumItem.setBlood(match, blood);
        }
        brewing = match;
        progress = 0;
        setStatus(STATUS_BREWING);
        setChanged();
        BlockPos pos = getBlockPos();
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6F, 1.2F);
    }

    /** The serum these three ingredients make: a transformation serum or a utility serum. */
    private static ItemStack findSerum(List<Item> ingredients) {
        MutationType mutation = MutationType.findByIngredients(ingredients);
        if (mutation != null) {
            return SerumItem.create(mutation);
        }
        UtilitySerum utility = UtilitySerum.findByIngredients(ingredients);
        return utility != null ? SerumItem.create(utility) : ItemStack.EMPTY;
    }

    // ---- MenuProvider ----------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block." + Sanguinaire.MOD_ID + ".serum_synthesizer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SerumSynthesizerMenu(containerId, playerInventory, this, data);
    }

    // ---- Persistence -----------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!brewing.isEmpty()) {
            tag.put(TAG_BREWING, brewing.save(new CompoundTag()));
        }
        ListTag list = new ListTag();
        for (ItemStack stack : consumed) {
            list.add(stack.save(new CompoundTag()));
        }
        tag.put(TAG_CONSUMED, list);
        tag.putInt(TAG_PROGRESS, progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        brewing = tag.contains(TAG_BREWING, Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound(TAG_BREWING)) : ItemStack.EMPTY;
        consumed.clear();
        ListTag list = tag.getList(TAG_CONSUMED, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                consumed.add(stack);
            }
        }
        progress = tag.getInt(TAG_PROGRESS);
    }
}
