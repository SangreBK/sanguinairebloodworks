package com.sanguinaire.blockentity;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.block.MachineBlock;
import com.sanguinaire.block.ResearchStationBlock;
import com.sanguinaire.menu.ResearchStationMenu;
import com.sanguinaire.registry.ModBlockEntities;
import com.sanguinaire.registry.ModItems;
import com.sanguinaire.registry.ModTags;
import com.sanguinaire.research.ResearchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Research Station (design §2.2). Top slot: a body part or Blood Sample to research. Bottom slot: a Research Journal.
 * Each completed study gives the researcher {@value #EXPERIENCE} experience points.
 * <p>
 * When a body part and a journal are present, one body part is consumed and research progresses towards the
 * journal. On completion the research is credited to the researching player: per-player and never shared.
 * <p>
 * <b>Who researches:</b> the player who last opened this station. Research is refused (nothing consumed) if that
 * player already knows the body part. If the researcher is offline when it finishes, the result waits in the
 * station and is credited as soon as they are online again; nothing is lost.
 */
public class ResearchStationBlockEntity extends MachineBlockEntity {
    public static final int SLOT_INGREDIENT = 0;
    public static final int SLOT_JOURNAL = 1;
    public static final int SLOT_COUNT = 2;
    /** Research time: 10 seconds. */
    public static final int RESEARCH_TICKS = 10 * 20;
    /** Experience points given to the researcher for each completed study. */
    public static final int EXPERIENCE = 4;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_RESEARCHING = 1;
    public static final int STATUS_NEEDS_JOURNAL = 2;
    public static final int STATUS_ALREADY_KNOWN = 3;
    public static final int STATUS_NO_RESEARCHER = 4;
    public static final int STATUS_AWAITING_RESEARCHER = 5;

    private static final String TAG_RESEARCHING = "Researching";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_RESEARCHER = "Researcher";
    private static final String TAG_LAST_USER = "LastUser";
    /** Client sync only: the body part impaled on the spike, and whether it is being consumed right now. */
    private static final String TAG_DISPLAY = "Display";
    private static final String TAG_ACTIVE = "Active";

    /**
     * Where the impaled part sits, in model pixels of the north-facing model: on the spike (x 3.5, z 11.475), a
     * little below its tip (the spike runs from y 16 to 27).
     */
    public static final double SPIKE_X = 3.5D;
    public static final double SPIKE_Y = 21.5D;
    public static final double SPIKE_Z = 11.475D;

    /** Client copy of what the spike shows (empty for nothing, and never a Blood Sample). */
    private ItemStack displayed = ItemStack.EMPTY;
    private boolean displayActive;

    private ItemStack researching = ItemStack.EMPTY;
    private int progress;
    @Nullable
    private UUID researcher;
    @Nullable
    private UUID lastUser;
    private int status = STATUS_IDLE;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> RESEARCH_TICKS;
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

    public ResearchStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESEARCH_STATION.get(), pos, state, SLOT_COUNT);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_INGREDIENT -> ResearchManager.isResearchable(stack);
            case SLOT_JOURNAL -> stack.is(ModItems.SURGICAL_JOURNAL.get());
            default -> false;
        };
    }

    @Override
    protected int getSlotLimit(int slot) {
        return slot == SLOT_JOURNAL ? 1 : 64;
    }

    @Override
    protected List<ItemStack> getProcessingStacks() {
        return researching.isEmpty() ? List.of() : List.of(researching);
    }

    @Override
    protected void clearProcessing() {
        researching = ItemStack.EMPTY;
        progress = 0;
        researcher = null;
        setChanged();
        syncToClients();
    }

    private boolean hasJournal() {
        return inventory.getStackInSlot(SLOT_JOURNAL).is(ModItems.SURGICAL_JOURNAL.get());
    }

    @Nullable
    private static ServerPlayer onlinePlayer(Level level, @Nullable UUID id) {
        if (id == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(id);
    }

    private void setStatus(int newStatus) {
        if (status != newStatus) {
            status = newStatus;
            setChanged();
            syncToClients(); // the spike's particles follow whether research is running
        }
    }

    /** Only body parts are impaled on the spike; Blood Samples are studied without showing anything. */
    private ItemStack spikeStack() {
        return researching.is(ModTags.BODY_PARTS) ? researching : ItemStack.EMPTY;
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Shows the journal on the model only while one is in the journal slot. */
    private void updateJournalState(Level level) {
        BlockState state = getBlockState();
        if (state.hasProperty(ResearchStationBlock.HAS_JOURNAL) && state.getValue(ResearchStationBlock.HAS_JOURNAL) != hasJournal()) {
            level.setBlock(worldPosition, state.setValue(ResearchStationBlock.HAS_JOURNAL, hasJournal()), 3);
        }
    }

    /** The point on the spike where the part sits, in world coordinates, for this station's facing. */
    public static Vec3 spikePoint(BlockPos pos, Direction facing) {
        double x = SPIKE_X / 16.0D - 0.5D;
        double z = SPIKE_Z / 16.0D - 0.5D;
        // Same turn the blockstate gives the model (y = 0/90/180/270 for north/east/south/west).
        double angle = Math.toRadians(-modelYRotation(facing));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rx = x * cos + z * sin;
        double rz = -x * sin + z * cos;
        return new Vec3(pos.getX() + 0.5D + rx, pos.getY() + SPIKE_Y / 16.0D, pos.getZ() + 0.5D + rz);
    }

    /** The blockstate file's y rotation for each facing. */
    public static float modelYRotation(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    public ItemStack getDisplayedPart() {
        return displayed;
    }

    /** Client: crumbs of the impaled part flake off while it is being consumed. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, ResearchStationBlockEntity station) {
        if (station.displayed.isEmpty() || !station.displayActive || level.getGameTime() % 3 != 0) {
            return;
        }
        Vec3 point = spikePoint(pos, state.getValue(MachineBlock.FACING));
        ItemParticleOption crumbs = new ItemParticleOption(ParticleTypes.ITEM, station.displayed);
        for (int i = 0; i < 2; i++) {
            double dx = (level.random.nextDouble() - 0.5D) * 0.15D;
            double dy = (level.random.nextDouble() - 0.5D) * 0.15D;
            double dz = (level.random.nextDouble() - 0.5D) * 0.15D;
            level.addParticle(crumbs, point.x + dx, point.y + dy, point.z + dz,
                    dx * 0.4D, 0.02D + level.random.nextDouble() * 0.04D, dz * 0.4D);
        }
        if (level.random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.SMOKE, point.x, point.y + 0.1D, point.z, 0.0D, 0.02D, 0.0D);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ResearchStationBlockEntity station) {
        station.tick(level);
    }

    private void tick(Level level) {
        updateJournalState(level);
        if (researching.isEmpty()) {
            tryStartResearch(level);
            return;
        }
        if (!hasJournal()) {
            setStatus(STATUS_NEEDS_JOURNAL); // paused, progress kept
            return;
        }
        if (progress < RESEARCH_TICKS) {
            progress++;
            setStatus(STATUS_RESEARCHING);
            setChanged();
            return;
        }
        ServerPlayer owner = onlinePlayer(level, researcher);
        if (owner == null) {
            setStatus(STATUS_AWAITING_RESEARCHER);
            return;
        }
        ResearchManager.completeResearch(owner, researching);
        owner.giveExperiencePoints(EXPERIENCE);
        researching = ItemStack.EMPTY;
        progress = 0;
        researcher = null;
        setStatus(STATUS_IDLE);
        setChanged();
        syncToClients(); // the part is used up: take it off the spike
        BlockPos pos = getBlockPos();
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 0.8F);
    }

    private void tryStartResearch(Level level) {
        ItemStack ingredient = inventory.getStackInSlot(SLOT_INGREDIENT);
        if (ingredient.isEmpty()) {
            setStatus(STATUS_IDLE);
            return;
        }
        if (!hasJournal()) {
            setStatus(STATUS_NEEDS_JOURNAL);
            return;
        }
        ServerPlayer user = onlinePlayer(level, lastUser);
        if (user == null) {
            setStatus(STATUS_NO_RESEARCHER);
            return;
        }
        if (ResearchManager.isResearched(user, ingredient)) {
            setStatus(STATUS_ALREADY_KNOWN);
            return;
        }
        researching = inventory.extractItem(SLOT_INGREDIENT, 1, false);
        researcher = user.getUUID();
        progress = 0;
        setStatus(STATUS_RESEARCHING);
        setChanged();
        syncToClients(); // put the part on the spike
        BlockPos pos = getBlockPos();
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.7F);
    }

    // ---- MenuProvider ----------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block." + Sanguinaire.MOD_ID + ".research_station");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        lastUser = player.getUUID();
        setChanged();
        return new ResearchStationMenu(containerId, playerInventory, this, data);
    }

    // ---- Persistence -----------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!researching.isEmpty()) {
            tag.put(TAG_RESEARCHING, researching.save(new CompoundTag()));
        }
        tag.putInt(TAG_PROGRESS, progress);
        if (researcher != null) {
            tag.putUUID(TAG_RESEARCHER, researcher);
        }
        if (lastUser != null) {
            tag.putUUID(TAG_LAST_USER, lastUser);
        }
    }

    // ---- Client sync (the spike's part) ------------------------------------------------------------------------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        ItemStack shown = spikeStack();
        if (!shown.isEmpty()) {
            tag.put(TAG_DISPLAY, shown.save(new CompoundTag()));
        }
        tag.putBoolean(TAG_ACTIVE, status == STATUS_RESEARCHING);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readDisplay(tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        readDisplay(tag == null ? new CompoundTag() : tag);
    }

    private void readDisplay(CompoundTag tag) {
        displayed = tag.contains(TAG_DISPLAY) ? ItemStack.of(tag.getCompound(TAG_DISPLAY)) : ItemStack.EMPTY;
        displayActive = tag.getBoolean(TAG_ACTIVE);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        researching = tag.contains(TAG_RESEARCHING) ? ItemStack.of(tag.getCompound(TAG_RESEARCHING)) : ItemStack.EMPTY;
        progress = tag.getInt(TAG_PROGRESS);
        researcher = tag.hasUUID(TAG_RESEARCHER) ? tag.getUUID(TAG_RESEARCHER) : null;
        lastUser = tag.hasUUID(TAG_LAST_USER) ? tag.getUUID(TAG_LAST_USER) : null;
    }
}
