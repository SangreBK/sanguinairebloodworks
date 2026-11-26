package com.sanguinaire.research;

import com.sanguinaire.advancement.ModAdvancements;
import com.sanguinaire.blood.BloodType;
import com.sanguinaire.item.BloodSampleItem;
import com.sanguinaire.registry.ModItems;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.client.ClientResearchCache;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.UtilitySerum;
import com.sanguinaire.network.ModNetwork;
import com.sanguinaire.player.PlayerMutationProvider;
import com.sanguinaire.registry.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player research (design §2.2). Research is stored per player UUID in {@link ResearchSavedData} (world save),
 * so it is never shared and is never lost to death or respawn. Another player reading the same journal sees only
 * their own discoveries.
 * <ul>
 *     <li>A transformation entry is <b>discovered</b> once any one of its three ingredients has been researched.</li>
 *     <li>Each researched ingredient is revealed in every entry that uses it.</li>
 *     <li>The formula is complete when all three ingredients are researched.</li>
 * </ul>
 * Queries work on both sides; the client reads {@link ClientResearchCache}, filled by
 * {@link com.sanguinaire.network.packet.SyncResearchPacket}.
 */
public final class ResearchManager {
    private ResearchManager() {
    }

    /** Body parts and Blood Samples can be studied at a Research Station. */
    public static boolean isResearchable(ItemStack stack) {
        return isBodyPart(stack) || BloodSampleItem.getType(stack) != null;
    }

    public static boolean isBodyPart(ItemStack stack) {
        return stack.is(ModTags.BODY_PARTS);
    }

    /** What studying this stack teaches: the body part's item id, or the blood type's research key. */
    @Nullable
    public static ResourceLocation getResearchKey(ItemStack stack) {
        BloodType blood = BloodSampleItem.getType(stack);
        if (blood != null) {
            return blood.getResearchKey();
        }
        return isBodyPart(stack) ? ForgeRegistries.ITEMS.getKey(stack.getItem()) : null;
    }

    public static boolean isResearched(Player player, ItemStack stack) {
        ResourceLocation key = getResearchKey(stack);
        return key != null && isResearched(player, key);
    }

    public static boolean isResearched(Player player, ResourceLocation key) {
        if (player instanceof ServerPlayer serverPlayer) {
            return ResearchSavedData.get(serverPlayer.server).isResearched(player.getUUID(), key);
        }
        return player.level().isClientSide() && ClientResearchCache.isResearched(key);
    }

    public static boolean isBloodTypeKnown(Player player, BloodType type) {
        return isResearched(player, type.getResearchKey());
    }

    /** Everything that can be researched: every body part and every blood type (the journal's "Research: X / Y"). */
    public static int getResearchableCount() {
        return ModItems.BODY_PARTS.size() + BloodType.values().length;
    }

    public static boolean isResearched(Player player, Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return ResearchSavedData.get(serverPlayer.server).isResearched(player.getUUID(), id);
        }
        return player.level().isClientSide() && ClientResearchCache.isResearched(id);
    }

    public static int getResearchedCount(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return ResearchSavedData.get(serverPlayer.server).getResearch(player.getUUID()).size();
        }
        return ClientResearchCache.research().size();
    }

    /**
     * Whether this player's journal shows the utility serum's entry: Torn Page serums need their page; the others
     * are discovered once any of their ingredients has been researched (or, for the Revitalizing Serum, if the player
     * read its Torn Page back when it was one).
     */
    public static boolean isSerumUnlocked(Player player, UtilitySerum serum) {
        if (isNoteUnlocked(player, serum.getId())) {
            return true;
        }
        if (serum.isTornPage()) {
            return false;
        }
        for (Item ingredient : serum.getIngredients()) {
            if (isResearched(player, ingredient)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the journal shows this ingredient in the utility serum's formula. */
    public static boolean isSerumIngredientKnown(Player player, UtilitySerum serum, Item ingredient) {
        if (isNoteUnlocked(player, serum.getId())) {
            return true; // a Torn Page (current or legacy) holds the complete formula
        }
        return !serum.isTornPage() && isResearched(player, ingredient);
    }

    /** Whether this player has read the Torn Page notes with this id ({@link ResearchNote#getId()}). */
    public static boolean isNoteUnlocked(Player player, String noteId) {
        if (player instanceof ServerPlayer serverPlayer) {
            return ResearchSavedData.get(serverPlayer.server).isSerumUnlocked(player.getUUID(), noteId);
        }
        return ClientResearchCache.isSerumUnlocked(noteId);
    }

    /** Unlocks Torn Page notes (a journal entry) for this player. @return true if newly unlocked. Server only. */
    public static boolean unlockNote(ServerPlayer player, String noteId) {
        boolean added = ResearchSavedData.get(player.server).unlockSerum(player.getUUID(), noteId);
        if (added) {
            ModNetwork.syncResearch(player);
        }
        return added;
    }

    /** Researches every specimen and unlocks every Torn Page entry for this player (debug command). Server only. */
    public static int grantAll(ServerPlayer player) {
        ResearchSavedData saved = ResearchSavedData.get(player.server);
        int count = 0;
        for (Item item : ForgeRegistries.ITEMS.tags().getTag(ModTags.BODY_PARTS)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id != null && saved.addResearch(player.getUUID(), id)) {
                count++;
            }
        }
        for (ResearchNote note : ResearchNote.values()) {
            if (saved.unlockSerum(player.getUUID(), note.getId())) {
                count++;
            }
        }
        for (BloodType blood : BloodType.values()) {
            if (saved.addResearch(player.getUUID(), blood.getResearchKey())) {
                count++;
            }
        }
        ModNetwork.syncResearch(player);
        return count;
    }

    /** Forgets all of this player's research and Torn Page entries (debug command). Server only. */
    public static void revokeAll(ServerPlayer player) {
        ResearchSavedData.get(player.server).clear(player.getUUID());
        ModNetwork.syncResearch(player);
    }

    /** Whether the journal shows this ingredient in the entry's formula. */
    public static boolean isIngredientKnown(Player player, MutationType type, Item ingredient) {
        if (ResearchNote.isTornPageTransformation(type) && isNoteUnlocked(player, type.getId())) {
            return true; // the Torn Page holds the complete formula
        }
        return isResearched(player, ingredient);
    }

    /**
     * Moves research recorded on the player capability by older versions into the world storage. Server only;
     * called on login.
     */
    public static void migrateLegacyResearch(ServerPlayer player) {
        PlayerMutationProvider.get(player).ifPresent(data -> {
            ResearchSavedData saved = ResearchSavedData.get(player.server);
            for (ResourceLocation id : data.getResearchedIngredients()) {
                saved.addResearch(player.getUUID(), id);
            }
            for (String serum : data.getUnlockedSerums()) {
                saved.unlockSerum(player.getUUID(), serum);
            }
            data.setResearchedIngredients(List.of());
            data.setUnlockedSerums(List.of());
        });
    }

    public static boolean isDiscovered(Player player, MutationType type) {
        if (ResearchNote.isTornPageTransformation(type)) {
            return isNoteUnlocked(player, type.getId()); // only a Torn Page reveals the original transformations
        }
        for (RegistryObject<Item> ingredient : type.getIngredients()) {
            if (isResearched(player, ingredient.get())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFormulaComplete(Player player, MutationType type) {
        for (RegistryObject<Item> ingredient : type.getIngredients()) {
            if (!isIngredientKnown(player, type, ingredient.get())) {
                return false;
            }
        }
        return true;
    }

    public static List<MutationType> getDiscovered(Player player) {
        List<MutationType> discovered = new ArrayList<>();
        for (MutationType type : MutationType.values()) {
            if (isDiscovered(player, type)) {
                discovered.add(type);
            }
        }
        return discovered;
    }

    /**
     * Records a completed research for this player, syncs it to their journal and tells them which entries were
     * discovered or updated. Server only.
     */
    public static void completeResearch(ServerPlayer player, ItemStack stack) {
        BloodType blood = BloodSampleItem.getType(stack);
        if (blood == null) {
            completeResearch(player, stack.getItem());
            return;
        }
        if (!ResearchSavedData.get(player.server).addResearch(player.getUUID(), blood.getResearchKey())) {
            return;
        }
        ModNetwork.syncResearch(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.sendSystemMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".research_complete", blood.getDisplayName())
                .withStyle(ChatFormatting.DARK_RED));
        player.sendSystemMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".research_new_entry", blood.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        boolean allKnown = true;
        for (BloodType type : BloodType.values()) {
            allKnown &= isBloodTypeKnown(player, type);
        }
        if (allKnown) {
            ModAdvancements.grant(player, ModAdvancements.ALL_BLOOD_TYPES);
        }
    }

    public static void completeResearch(ServerPlayer player, Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) {
            return;
        }
        List<Component> newlyDiscovered = new ArrayList<>();
        int updated = 0;
        for (MutationType type : MutationType.values()) {
            if (type.usesIngredient(item) && !ResearchNote.isTornPageTransformation(type)) {
                if (isDiscovered(player, type)) {
                    updated++;
                } else {
                    newlyDiscovered.add(type.getDisplayName());
                }
            }
        }
        for (UtilitySerum serum : UtilitySerum.values()) {
            if (serum.usesIngredient(item) && !serum.isTornPage() && !isNoteUnlocked(player, serum.getId())) {
                if (isSerumUnlocked(player, serum)) {
                    updated++;
                } else {
                    newlyDiscovered.add(serum.getDisplayName());
                }
            }
        }
        if (!ResearchSavedData.get(player.server).addResearch(player.getUUID(), id)) {
            return;
        }
        ModNetwork.syncResearch(player);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        Component itemName = new ItemStack(item).getHoverName();
        if (newlyDiscovered.isEmpty() && updated == 0) {
            player.sendSystemMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".research_nothing", itemName)
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        player.sendSystemMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".research_complete", itemName)
                .withStyle(ChatFormatting.DARK_RED));
        for (Component entry : newlyDiscovered) {
            player.sendSystemMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".research_new_entry", entry)
                    .withStyle(ChatFormatting.GOLD));
        }
        if (updated > 0) {
            player.sendSystemMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".research_updated_entries", updated)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
