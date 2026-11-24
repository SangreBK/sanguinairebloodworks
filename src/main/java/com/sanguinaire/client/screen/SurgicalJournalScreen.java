package com.sanguinaire.client.screen;

import net.minecraft.client.resources.language.I18n;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.blood.BloodType;
import com.sanguinaire.bodypart.BodyPartDropTable;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.UtilitySerum;
import com.sanguinaire.player.PlayerMutationProvider;
import com.sanguinaire.registry.ModBlocks;
import com.sanguinaire.registry.ModItems;
import com.sanguinaire.research.ResearchManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Research Journal: a two-page Victorian research book. The left page is the index, the right page shows the
 * selected entry, with a scrollbar showing how far through a long entry the reader is.
 * <p>
 * The index starts with the general entries (the Surgical Saw with every mob's drop table, Weakened Hearts, and the
 * three workstations: Research Station, Serum Synthesizer and Sanguine Alembic),
 * followed by collapsible categories, each sorted alphabetically:
 * <ul>
 *     <li><b>Enhancing Serums</b>: utility serums, with full formulas once the player has read their Torn Page.</li>
 *     <li><b>Transformation Serums</b>: transformations, once one of their ingredients has been researched by the
 *     <em>local player</em> (unresearched ingredients stay hidden, "?"), or, for the original creatures, once their
 *     Torn Page has been read.</li>
 *     <li><b>Blood Types</b>: Monster, Animal and Humanoid blood, once a sample has been studied at a Research
 *     Station.</li>
 * </ul>
 * Clicking a category header opens or closes it; which categories are open is remembered while the game runs.
 * New categories are added by extending {@link Category} and filling it in {@link #buildEntries()}.
 * <p>
 * The journal closes on Escape or on the inventory key.
 */
public class SurgicalJournalScreen extends Screen {
    private static final ResourceLocation TEXTURE = Sanguinaire.id("textures/gui/surgical_journal.png");
    private static final String PREFIX = "journal." + Sanguinaire.MOD_ID + ".";

    private static final int BOOK_WIDTH = 320;
    private static final int BOOK_HEIGHT = 180;
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int LEFT_PAGE_X = 20;
    private static final int LEFT_PAGE_WIDTH = 130;
    private static final int RIGHT_PAGE_X = 170;
    private static final int RIGHT_PAGE_WIDTH = 118;
    private static final int PAGE_TOP = 16;
    private static final int PAGE_BOTTOM = 164;
    private static final int INDEX_TOP = 44;
    private static final int INDEX_ROW_HEIGHT = 11;
    private static final int ICON_ROW_HEIGHT = 22;
    private static final int SCROLLBAR_X = 294;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_THUMB_HEIGHT = 12;

    private static final int INK = 0x3A2414;
    private static final int FADED_INK = 0x7A6450;
    private static final int RUBRIC = 0x7A1414;
    private static final int SELECTED = 0x9A1C1C;
    private static final int TRACK_COLOR = 0xFFC2AE84;
    private static final int TRACK_EDGE = 0xFFA8946A;
    private static final int THUMB_COLOR = 0xFF7A1414;
    private static final int THUMB_LIGHT = 0xFFA83A2A;
    private static final int THUMB_DARK = 0xFF4A0A0A;

    /** One rendered line of an entry: text, or a row of ingredient icons (null stacks are unknown ingredients). */
    private record Line(@Nullable FormattedCharSequence text, int color, @Nullable List<ItemStack> icons) {
        int height(int fontHeight) {
            return icons != null ? ICON_ROW_HEIGHT : fontHeight + 1;
        }
    }

    /** An index entry: a stable key (so the selection survives categories opening and closing), its label and how to build its page. */
    private record Entry(String key, Component label, Consumer<List<Line>> builder) {
    }

    /** A collapsible group of entries on the index page. */
    private enum Category {
        ENHANCING("category.enhancing"),
        TRANSFORMATIONS("category.transformations"),
        BLOOD_TYPES("category.blood_types");

        private final String labelKey;
        /** Remembered while the game runs, so the index looks the same each time the journal is opened. */
        private boolean open;

        Category(String labelKey) {
            this.labelKey = labelKey;
        }
    }

    /** One line of the index: a general entry, a category header, an entry inside a category, or an empty-category note. */
    private record Row(@Nullable Entry entry, @Nullable Category header, boolean indented) {
    }

    private static final int CATEGORY_INDENT = 8;
    private static final String SAW_KEY = "saw";

    private int bookLeft;
    private int bookTop;
    private final List<Entry> general = new ArrayList<>();
    private final Map<Category, List<Entry>> categories = new EnumMap<>(Category.class);
    private List<Row> rows = List.of();
    private int researchedCount;
    private String selectedKey = SAW_KEY;
    private List<Line> content = List.of();
    private int contentScroll;
    private int indexScroll;
    @Nullable
    private ItemStack hoveredStack;

    public SurgicalJournalScreen() {
        super(Component.translatable(PREFIX + "title"));
    }

    @Override
    protected void init() {
        super.init();
        bookLeft = (width - BOOK_WIDTH) / 2;
        bookTop = (height - BOOK_HEIGHT) / 2;
        buildEntries();
        if (findEntry(selectedKey) == null) {
            selectedKey = SAW_KEY;
        }
        rebuildRows();
        rebuildContent();
    }

    private void buildEntries() {
        general.clear();
        categories.clear();
        general.add(new Entry(SAW_KEY, Component.translatable(PREFIX + "saw.title"), this::buildSawEntry));
        general.add(new Entry("weakened_hearts", Component.translatable(PREFIX + "weakened.title"), this::buildWeakenedHeartsEntry));
        addBlockEntry(ModBlocks.RESEARCH_STATION.get(), "research_station");
        addBlockEntry(ModBlocks.SERUM_SYNTHESIZER.get(), "serum_synthesizer");
        addBlockEntry(ModBlocks.SANGUINE_ALEMBIC.get(), "sanguine_alembic");
        LocalPlayer player = Minecraft.getInstance().player;

        List<Entry> enhancing = new ArrayList<>();
        for (UtilitySerum serum : UtilitySerum.values()) {
            // Utility serum entries are unlocked by reading Torn Pages found in Ancient Cities.
            if (player != null && ResearchManager.isSerumUnlocked(player, serum)) {
                enhancing.add(new Entry("serum:" + serum.getId(), serum.getDisplayName(), lines -> buildUtilityEntry(lines, serum)));
            }
        }
        List<Entry> transformations = new ArrayList<>();
        if (player != null) {
            for (MutationType type : ResearchManager.getDiscovered(player)) {
                transformations.add(new Entry("mutation:" + type.getId(), type.getDisplayName(),
                        lines -> buildMutationEntry(lines, player, type)));
            }
        }
        List<Entry> bloodTypes = new ArrayList<>();
        if (player != null) {
            for (BloodType blood : BloodType.values()) {
                if (ResearchManager.isBloodTypeKnown(player, blood)) {
                    bloodTypes.add(new Entry("blood:" + blood.getId(), blood.getDisplayName(), lines -> buildBloodEntry(lines, blood)));
                }
            }
        }
        // Alphabetical by the name the reader actually sees (works in any language).
        Comparator<Entry> alphabetical = Comparator.comparing(entry -> entry.label().getString(), String.CASE_INSENSITIVE_ORDER);
        enhancing.sort(alphabetical);
        transformations.sort(alphabetical);
        bloodTypes.sort(alphabetical);
        categories.put(Category.ENHANCING, enhancing);
        categories.put(Category.TRANSFORMATIONS, transformations);
        categories.put(Category.BLOOD_TYPES, bloodTypes);
        researchedCount = player == null ? 0 : ResearchManager.getResearchedCount(player);
    }

    /** An always-present entry for one of the mod's workstations: what it does, and how to automate it. */
    private void addBlockEntry(Block block, String id) {
        Component name = block.getName();
        general.add(new Entry("block:" + id, name, lines -> {
            addTitle(lines, name);
            lines.add(new Line(null, INK, List.of(new ItemStack(block))));
            addWrapped(lines, Component.translatable(PREFIX + "block." + id + ".text"), INK);
            addSpacer(lines);
            addHeading(lines, "block.automation_heading");
            addWrapped(lines, Component.translatable(PREFIX + "block." + id + ".automation"), INK);
        }));
    }

    /** Lays out the index from the entries and which categories are open. */
    private void rebuildRows() {
        List<Row> list = new ArrayList<>();
        for (Entry entry : general) {
            list.add(new Row(entry, null, false));
        }
        for (Category category : Category.values()) {
            list.add(new Row(null, category, false));
            if (!category.open) {
                continue;
            }
            List<Entry> inside = categories.getOrDefault(category, List.of());
            if (inside.isEmpty()) {
                list.add(new Row(null, null, true)); // "Nothing recorded yet"
            }
            for (Entry entry : inside) {
                list.add(new Row(entry, null, true));
            }
        }
        rows = list;
        indexScroll = Mth.clamp(indexScroll, 0, maxIndexScroll());
    }

    @Nullable
    private Entry findEntry(String key) {
        for (Entry entry : general) {
            if (entry.key().equals(key)) {
                return entry;
            }
        }
        for (List<Entry> inside : categories.values()) {
            for (Entry entry : inside) {
                if (entry.key().equals(key)) {
                    return entry;
                }
            }
        }
        return null;
    }

    // ---- Content -----------------------------------------------------------------------------------------------

    private void rebuildContent() {
        contentScroll = 0;
        List<Line> lines = new ArrayList<>();
        Entry entry = findEntry(selectedKey);
        if (entry != null) {
            entry.builder().accept(lines);
        }
        content = lines;
    }

    private void addWrapped(List<Line> lines, Component text, int color) {
        for (FormattedCharSequence sequence : font.split(text, RIGHT_PAGE_WIDTH)) {
            lines.add(new Line(sequence, color, null));
        }
    }

    private void addSpacer(List<Line> lines) {
        lines.add(new Line(FormattedCharSequence.EMPTY, INK, null));
    }

    private void addTitle(List<Line> lines, Component title) {
        addWrapped(lines, title.copy().withStyle(ChatFormatting.BOLD), RUBRIC);
    }

    private void addHeading(List<Line> lines, String key) {
        addWrapped(lines, Component.translatable(PREFIX + key).withStyle(ChatFormatting.UNDERLINE), RUBRIC);
    }

    private void buildSawEntry(List<Line> lines) {
        addTitle(lines, Component.translatable(PREFIX + "saw.title"));
        addSpacer(lines);
        addWrapped(lines, Component.translatable(PREFIX + "saw.text"), INK);
        addSpacer(lines);
        addHeading(lines, "saw.drops");
        for (Map.Entry<EntityType<?>, List<BodyPartDropTable.Entry>> mob : BodyPartDropTable.getTable().entrySet()) {
            addSpacer(lines);
            addWrapped(lines, mobName(mob.getKey()).copy().withStyle(ChatFormatting.BOLD), INK);
            for (BodyPartDropTable.Entry drop : mob.getValue()) {
                Component name = new ItemStack(drop.item().get()).getHoverName();
                addWrapped(lines, Component.translatable(PREFIX + "saw.drop_line", name, formatChance(BodyPartDropTable.getShare(mob.getValue(), drop))), INK);
            }
        }
    }

    private void buildWeakenedHeartsEntry(List<Line> lines) {
        addTitle(lines, Component.translatable(PREFIX + "weakened.title"));
        addSpacer(lines);
        addWrapped(lines, Component.translatable(PREFIX + "weakened.intro"), INK);
        addSpacer(lines);
        addHeading(lines, "weakened.gained_heading");
        addWrapped(lines, Component.translatable(PREFIX + "weakened.gained"), INK);
        addSpacer(lines);
        addHeading(lines, "weakened.effects_heading");
        addWrapped(lines, Component.translatable(PREFIX + "weakened.effects"), INK);
        addSpacer(lines);
        addHeading(lines, "weakened.decay_heading");
        addWrapped(lines, Component.translatable(PREFIX + "weakened.decay"), INK);
        addSpacer(lines);
        addHeading(lines, "weakened.cure_heading");
        addWrapped(lines, Component.translatable(PREFIX + "weakened.cure"), INK);
    }

    private void buildUtilityEntry(List<Line> lines, UtilitySerum serum) {
        addTitle(lines, serum.getDisplayName());
        addSpacer(lines);
        addHeading(lines, "formula");
        List<ItemStack> icons = new ArrayList<>(3);
        for (Item ingredient : serum.getIngredients()) {
            icons.add(new ItemStack(ingredient));
        }
        lines.add(new Line(null, INK, icons));
        for (ItemStack icon : icons) {
            addWrapped(lines, Component.literal("- ").append(icon.getHoverName()), INK);
        }
        addWrapped(lines, Component.translatable(PREFIX + "formula_complete").withStyle(ChatFormatting.ITALIC), RUBRIC);
        addSpacer(lines);
        addHeading(lines, "effects");
        addWrapped(lines, Component.translatable(serum.getDescriptionKey()), INK);
        LocalPlayer player = Minecraft.getInstance().player;
        if (serum == UtilitySerum.TAINTED_BLOOD && player != null
                && PlayerMutationProvider.get(player).map(data -> data.hasTaintedBlood()).orElse(false)) {
            addSpacer(lines);
            addWrapped(lines, Component.translatable(PREFIX + "serum.tainted_blood.yours").withStyle(ChatFormatting.ITALIC), RUBRIC);
        }
    }

    private void buildBloodEntry(List<Line> lines, BloodType blood) {
        addTitle(lines, blood.getDisplayName());
        addSpacer(lines);
        addWrapped(lines, Component.translatable(blood.getDescriptionKey()), INK);
        addSpacer(lines);
        addHeading(lines, "blood.in_serum_heading");
        addWrapped(lines, Component.translatable(blood.getDescriptionKey() + ".serum"), INK);
        addSpacer(lines);
        addHeading(lines, "blood.drawing_heading");
        addWrapped(lines, Component.translatable(PREFIX + "blood.drawing"), INK);
        addSpacer(lines);
        addHeading(lines, "blood.altering_heading");
        addWrapped(lines, Component.translatable(PREFIX + "blood.altering"), INK);
    }

    private static Component mobName(EntityType<?> type) {
        return type.getDescription();
    }

    private static String formatChance(float chance) {
        float rounded = Math.round(chance * 10.0F) / 10.0F;
        return rounded == Math.floor(rounded) ? String.valueOf((int) rounded) : String.valueOf(rounded);
    }

    private void buildMutationEntry(List<Line> lines, LocalPlayer player, MutationType type) {
        addTitle(lines, type.getDisplayName());
        boolean tainted = PlayerMutationProvider.get(player).map(data -> data.hasTaintedBlood()).orElse(false);
        int seconds = type.getDurationSeconds() + (tainted ? UtilitySerum.TAINTED_BLOOD_BONUS_TICKS / 20 : 0);
        addWrapped(lines, Component.translatable(PREFIX + "duration", seconds), FADED_INK);
        addSpacer(lines);

        addHeading(lines, "formula");
        List<ItemStack> icons = new ArrayList<>(3);
        int known = 0;
        for (RegistryObject<Item> ingredient : type.getIngredients()) {
            if (ResearchManager.isIngredientKnown(player, type, ingredient.get())) {
                icons.add(new ItemStack(ingredient.get()));
                known++;
            } else {
                icons.add(null);
            }
        }
        lines.add(new Line(null, INK, icons));
        for (ItemStack icon : icons) {
            MutableComponent name = icon == null
                    ? Component.translatable(PREFIX + "unknown_ingredient")
                    : icon.getHoverName().copy();
            addWrapped(lines, Component.literal("- ").append(name), icon == null ? FADED_INK : INK);
        }
        addWrapped(lines, known == 3
                ? Component.translatable(PREFIX + "formula_complete").withStyle(ChatFormatting.ITALIC)
                : Component.translatable(PREFIX + "formula_partial", known), known == 3 ? RUBRIC : FADED_INK);
        addSpacer(lines);

        // The active ability gets its own section above the passive effects (when the form has one).
        String abilityKey = type.getDescriptionKey() + ".ability";
        if (I18n.exists(abilityKey)) {
            addHeading(lines, "ability");
            addWrapped(lines, Component.translatable(abilityKey), INK);
            addSpacer(lines);
        }
        addHeading(lines, "effects");
        addWrapped(lines, Component.translatable(type.getDescriptionKey()), INK);
    }

    // ---- Rendering ---------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.blit(TEXTURE, bookLeft, bookTop, 0.0F, 0.0F, BOOK_WIDTH, BOOK_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        hoveredStack = null;
        renderIndex(graphics, mouseX, mouseY);
        renderContent(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredStack != null) {
            graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
        }
    }

    private void renderIndex(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = bookLeft + LEFT_PAGE_X;
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, title, x + (LEFT_PAGE_WIDTH - font.width(title)) / 2, bookTop + PAGE_TOP, RUBRIC, false);
        Component count = Component.translatable(PREFIX + "entries", researchedCount, ResearchManager.getResearchableCount());
        graphics.drawString(font, count, x + (LEFT_PAGE_WIDTH - font.width(count)) / 2, bookTop + PAGE_TOP + 12, FADED_INK, false);

        int top = bookTop + INDEX_TOP;
        int bottom = bookTop + PAGE_BOTTOM;
        graphics.enableScissor(x, top, x + LEFT_PAGE_WIDTH, bottom);
        for (int index = 0; index < rows.size(); index++) {
            int y = top + index * INDEX_ROW_HEIGHT - indexScroll;
            if (y + INDEX_ROW_HEIGHT < top || y > bottom) {
                continue;
            }
            Row row = rows.get(index);
            boolean hovered = mouseX >= x && mouseX < x + LEFT_PAGE_WIDTH && mouseY >= Math.max(y, top)
                    && mouseY < Math.min(y + INDEX_ROW_HEIGHT, bottom);
            int rowX = x + (row.indented() ? CATEGORY_INDENT : 0);
            if (row.header() != null) {
                int color = hovered ? SELECTED : RUBRIC;
                drawDisclosureArrow(graphics, x + 1, y + 1, row.header().open, color);
                Component label = Component.translatable(PREFIX + row.header().labelKey);
                graphics.drawString(font, label, x + 9, y, color, false);
            } else if (row.entry() != null) {
                boolean selected = row.entry().key().equals(selectedKey);
                int color = selected ? SELECTED : hovered ? RUBRIC : INK;
                String marker = selected ? "> " : "  ";
                graphics.drawString(font, Component.literal(marker).append(row.entry().label()), rowX, y, color, false);
            } else {
                Component empty = Component.translatable(PREFIX + "category.empty").withStyle(ChatFormatting.ITALIC);
                graphics.drawString(font, Component.literal("  ").append(empty), rowX, y, FADED_INK, false);
            }
        }
        graphics.disableScissor();
    }

    /** A small inked triangle: pointing right when the category is closed, down when it is open. */
    private static void drawDisclosureArrow(GuiGraphics graphics, int x, int y, boolean open, int rgb) {
        int color = 0xFF000000 | rgb;
        if (open) {
            for (int i = 0; i < 4; i++) {
                graphics.fill(x + i, y + 2 + i, x + 7 - i, y + 3 + i, color);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                graphics.fill(x + 1 + i, y + i, x + 2 + i, y + 7 - i, color);
            }
        }
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = bookLeft + RIGHT_PAGE_X;
        int top = bookTop + PAGE_TOP;
        int bottom = bookTop + PAGE_BOTTOM;
        graphics.enableScissor(x, top, x + RIGHT_PAGE_WIDTH, bottom);
        int y = top - contentScroll;
        for (Line line : content) {
            int height = line.height(font.lineHeight);
            if (y + height >= top && y <= bottom) {
                if (line.icons() != null) {
                    renderIconRow(graphics, line.icons(), x, y, mouseX, mouseY, top, bottom);
                } else if (line.text() != null) {
                    graphics.drawString(font, line.text(), x, y, line.color(), false);
                }
            }
            y += height;
        }
        graphics.disableScissor();
    }

    private void renderIconRow(GuiGraphics graphics, List<ItemStack> icons, int x, int y, int mouseX, int mouseY, int top, int bottom) {
        int slotX = x + (RIGHT_PAGE_WIDTH - icons.size() * 22) / 2;
        for (ItemStack icon : icons) {
            graphics.blit(TEXTURE, slotX, y + 1, 0.0F, 184.0F, 20, 20, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            if (icon == null) {
                graphics.drawString(font, "?", slotX + 7, y + 7, FADED_INK, false);
            } else {
                graphics.renderItem(icon, slotX + 2, y + 3);
                if (mouseX >= slotX && mouseX < slotX + 20 && mouseY >= y + 1 && mouseY < y + 21
                        && mouseY >= top && mouseY < bottom) {
                    hoveredStack = icon;
                }
            }
            slotX += 22;
        }
    }

    /** A brass-and-ink scrollbar beside the right page; shown only when the entry is longer than the page. */
    private void renderScrollbar(GuiGraphics graphics) {
        int view = PAGE_BOTTOM - PAGE_TOP;
        int total = contentHeight();
        if (total <= view) {
            return;
        }
        int x0 = bookLeft + SCROLLBAR_X;
        int x1 = x0 + SCROLLBAR_WIDTH;
        int top = bookTop + PAGE_TOP;
        int bottom = bookTop + PAGE_BOTTOM;
        graphics.fill(x0, top, x1, bottom, TRACK_EDGE);
        graphics.fill(x0 + 1, top + 1, x1 - 1, bottom - 1, TRACK_COLOR);

        int trackHeight = view - 2;
        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, trackHeight * view / total);
        int maxScroll = total - view;
        int thumbTop = top + 1 + (trackHeight - thumbHeight) * contentScroll / maxScroll;
        graphics.fill(x0 + 1, thumbTop, x1 - 1, thumbTop + thumbHeight, THUMB_COLOR);
        graphics.fill(x0 + 1, thumbTop, x0 + 2, thumbTop + thumbHeight, THUMB_LIGHT);
        graphics.fill(x1 - 2, thumbTop, x1 - 1, thumbTop + thumbHeight, THUMB_DARK);
    }

    // ---- Input -----------------------------------------------------------------------------------------------

    private int maxIndexScroll() {
        return Math.max(0, rows.size() * INDEX_ROW_HEIGHT - (PAGE_BOTTOM - INDEX_TOP));
    }

    private int contentHeight() {
        int total = 0;
        for (Line line : content) {
            total += line.height(font.lineHeight);
        }
        return total;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = bookLeft + LEFT_PAGE_X;
        int top = bookTop + INDEX_TOP;
        int bottom = bookTop + PAGE_BOTTOM;
        if (button == 0 && mouseX >= x && mouseX < x + LEFT_PAGE_WIDTH && mouseY >= top && mouseY < bottom) {
            int index = (int) ((mouseY - top + indexScroll) / INDEX_ROW_HEIGHT);
            if (index >= 0 && index < rows.size()) {
                Row row = rows.get(index);
                if (row.header() != null) {
                    row.header().open = !row.header().open;
                    rebuildRows();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.4F));
                } else if (row.entry() != null && !row.entry().key().equals(selectedKey)) {
                    selectedKey = row.entry().key();
                    rebuildContent();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int step = (int) (-delta * font.lineHeight * 2);
        if (mouseX < bookLeft + BOOK_WIDTH / 2.0D) {
            indexScroll = Mth.clamp(indexScroll + step, 0, maxIndexScroll());
        } else {
            int maxScroll = Math.max(0, contentHeight() - (PAGE_BOTTOM - PAGE_TOP));
            contentScroll = Mth.clamp(contentScroll + step, 0, maxScroll);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
