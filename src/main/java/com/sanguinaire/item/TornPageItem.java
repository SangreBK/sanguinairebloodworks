package com.sanguinaire.item;

import com.sanguinaire.advancement.ModAdvancements;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.client.ClientResearchCache;
import com.sanguinaire.research.ResearchNote;
import com.sanguinaire.research.ResearchManager;
import com.sanguinaire.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Torn Page: uncommon research notes found in Ancient City chests. Each page holds the notes on one
 * {@link ResearchNote} (a utility serum or one of the three original transformations), stored in NBT
 * {@value #TAG_NOTE}. Pages from older versions store a serum id under {@value #TAG_LEGACY_SERUM}; pages with
 * neither (e.g. from /give) are given random notes the first time they sit in a player's inventory.
 * <p>
 * The tooltip reads "Strange Research Notes" until the viewing player has learned that page's notes; from then on
 * that player (and only that player) sees "Research Notes on X".
 * <p>
 * Right-clicking with a Research Journal anywhere in the inventory copies the notes into the reader's journal,
 * unlocking that entry for this player only, and consumes the page (in survival). Without a journal, nothing
 * happens and the page is kept. A page whose notes the player already knows is also kept.
 */
public class TornPageItem extends Item {
    public static final String TAG_NOTE = "Notes";
    public static final String TAG_LEGACY_SERUM = "Serum";

    public TornPageItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(ResearchNote note) {
        ItemStack stack = new ItemStack(ModItems.TORN_PAGE.get());
        stack.getOrCreateTag().putString(TAG_NOTE, note.getId());
        return stack;
    }

    /** A page with random notes (used by the Ancient City loot modifier). */
    public static ItemStack createRandom(RandomSource random) {
        return create(randomNote(random));
    }

    private static ResearchNote randomNote(RandomSource random) {
        ResearchNote[] notes = ResearchNote.values();
        return notes[random.nextInt(notes.length)];
    }

    @Nullable
    public static ResearchNote getNote(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return null;
        }
        if (tag.contains(TAG_NOTE, Tag.TAG_STRING)) {
            return ResearchNote.byId(tag.getString(TAG_NOTE));
        }
        if (tag.contains(TAG_LEGACY_SERUM, Tag.TAG_STRING)) {
            return ResearchNote.byId(tag.getString(TAG_LEGACY_SERUM));
        }
        return null;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide() && getNote(stack) == null) {
            stack.getOrCreateTag().putString(TAG_NOTE, randomNote(level.getRandom()).getId());
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.consume(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!serverPlayer.getInventory().hasAnyOf(Set.of(ModItems.SURGICAL_JOURNAL.get()))) {
            serverPlayer.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".torn_page_no_journal")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        ResearchNote note = getNote(stack);
        if (note == null) {
            note = randomNote(level.getRandom());
        }
        if (!ResearchManager.unlockNote(serverPlayer, note.getId())) {
            serverPlayer.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".torn_page_known")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        ModAdvancements.grant(serverPlayer, ModAdvancements.tornPage(note.getId()));
        // Creative players keep the page (vanilla restores the stack in creative).
        stack.shrink(1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        serverPlayer.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".torn_page_read")
                .withStyle(ChatFormatting.GOLD), true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ResearchNote note = getNote(stack);
        // Tooltips are built on the client: only the viewer's own knowledge (ClientResearchCache) decides.
        if (note != null && level != null && level.isClientSide() && ClientResearchCache.isSerumUnlocked(note.getId())) {
            tooltip.add(Component.translatable("tooltip." + Sanguinaire.MOD_ID + ".torn_page.known", note.getSubject())
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        tooltip.add(Component.translatable("tooltip." + Sanguinaire.MOD_ID + ".torn_page").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
