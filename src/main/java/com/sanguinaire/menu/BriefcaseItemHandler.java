package com.sanguinaire.menu;

import com.sanguinaire.item.SerumBriefcaseItem;
import com.sanguinaire.registry.ModTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The Serum Briefcase's inventory: a fixed-size, filtered item handler whose contents are persisted inside the
 * briefcase {@link ItemStack}'s NBT under {@value #TAG_INVENTORY}.
 * <p>
 * Because the contents live on the item stack itself, they automatically survive the briefcase being closed,
 * dropped, moved, stored in a chest, the player logging out, or changing dimensions.
 */
public class BriefcaseItemHandler extends ItemStackHandler {
    public static final int ROWS = 2;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;
    public static final String TAG_INVENTORY = "BriefcaseInventory";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";

    public BriefcaseItemHandler() {
        super(SIZE);
    }

    /**
     * The single source of truth for what the briefcase accepts: items in {@link ModTags#BRIEFCASE_STORABLE}
     * (serums and Empty Syringes), never another briefcase and never any item that refuses to be placed inside
     * container items (e.g. shulker boxes). This prevents recursive storage even if a datapack edits the tag.
     */
    public static boolean isStorable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof SerumBriefcaseItem) {
            return false;
        }
        if (!stack.getItem().canFitInsideContainerItems()) {
            return false;
        }
        return stack.is(ModTags.BRIEFCASE_STORABLE);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return isStorable(stack);
    }

    /**
     * Replaces this handler's contents with those stored on the briefcase, without firing change callbacks.
     *
     * @return stacks found in the NBT that are not allowed in the briefcase (e.g. inserted by commands or a changed
     * tag). They are removed from the handler and must be returned to the player by the caller, never deleted.
     */
    public List<ItemStack> loadFrom(ItemStack briefcase) {
        List<ItemStack> rejected = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i++) {
            stacks.set(i, ItemStack.EMPTY);
        }
        CompoundTag root = briefcase.getTag();
        if (root == null || !root.contains(TAG_INVENTORY, Tag.TAG_COMPOUND)) {
            return rejected;
        }
        ListTag list = root.getCompound(TAG_INVENTORY).getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getInt(TAG_SLOT);
            ItemStack stack = ItemStack.of(entry);
            if (stack.isEmpty()) {
                continue;
            }
            if (slot < 0 || slot >= stacks.size() || !stacks.get(slot).isEmpty() || !isStorable(stack)) {
                rejected.add(stack);
            } else {
                stacks.set(slot, stack);
            }
        }
        return rejected;
    }

    /** Writes the current contents onto the briefcase stack. */
    public void saveTo(ItemStack briefcase) {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(TAG_SLOT, i);
                stack.save(entry);
                list.add(entry);
            }
        }
        CompoundTag inventory = new CompoundTag();
        inventory.put(TAG_ITEMS, list);
        briefcase.getOrCreateTag().put(TAG_INVENTORY, inventory);
    }

    /** Read-only view of the stacks stored on a briefcase, for tooltips. */
    public static List<ItemStack> readContents(ItemStack briefcase) {
        List<ItemStack> contents = new ArrayList<>();
        CompoundTag root = briefcase.getTag();
        if (root == null || !root.contains(TAG_INVENTORY, Tag.TAG_COMPOUND)) {
            return contents;
        }
        ListTag list = root.getCompound(TAG_INVENTORY).getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                contents.add(stack);
            }
        }
        return contents;
    }
}
