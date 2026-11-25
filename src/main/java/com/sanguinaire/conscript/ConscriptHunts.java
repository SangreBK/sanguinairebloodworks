package com.sanguinaire.conscript;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.advancement.ModAdvancements;
import com.sanguinaire.config.SanguinaireConfig;
import com.sanguinaire.registry.ModEntities;
import com.sanguinaire.research.ResearchManager;
import com.sanguinaire.research.ResearchNote;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Conscripts' hunt.
 * <ul>
 *     <li><b>Hunted</b>: a player who reads a Torn Page or transforms with a serum for the first time is marked, for
 *     good ("Crimes Against Nature"). Every Conscript is hostile to them and can spot them from 40 blocks.</li>
 *     <li><b>Notoriety</b> (0 to 5, internal only, never shown to the player): +1 for every transformation and for every 5 Conscripts killed; -1 after
 *     20 minutes without either. It sets the size of hunting parties (2 to 6) and how often they come.</li>
 *     <li><b>Hunting parties</b>: about every 10 minutes (sooner at high notoriety) a warning sounds, and 10 seconds
 *     later a party arrives 30-48 blocks away, following the player's trail. They give up after 3 minutes without
 *     seeing their quarry, a new party only comes once the last one is gone, and dying resets the timer.</li>
 *     <li><b>Raids</b>: every raid wave near a hunted player is joined by Conscripts.</li>
 *     <li><b>Caravans</b>: now and then 5 Conscripts and 2 chest-carrying donkeys roam near a player. Riding off with
 *     (or hurting) a caravan donkey turns the guards on you.</li>
 * </ul>
 * Hunt state lives in the player's persisted NBT (survives death), under {@value #TAG}.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class ConscriptHunts {
    public static final String CRIMES_AGAINST_NATURE = "crimes_against_nature";
    /** Journal note id (stored with the Torn Page notes) that unlocks "The Hunt" category. */
    public static final String HUNT_JOURNAL_NOTE = "conscript_hunt";
    public static final int MAX_NOTORIETY = 5;
    private static final String TAG = Sanguinaire.MOD_ID + "_hunt";
    private static final String HUNTED = "Hunted";
    private static final String NOTORIETY = "Notoriety";
    private static final String HUNT_TIMER = "HuntTimer";
    private static final String CALM = "Calm";
    private static final String PENDING = "Pending";
    private static final String KILLS = "Kills";
    public static final String CARAVAN_TAG = Sanguinaire.MOD_ID + ":caravan";

    private static final int CALM_DECAY_TICKS = 20 * 60 * 20;
    private static final int WARNING_TICKS = 10 * 20;
    private static final int RETRY_TICKS = 60 * 20;
    private static final int KILLS_PER_STAR = 5;
    private static final double RAID_RANGE = 96.0D;

    private static final Set<Integer> ANNOUNCED_SQUADS = new HashSet<>();
    private static final Set<String> REINFORCED_WAVES = new HashSet<>();
    private static final List<PendingReinforcement> RAID_QUEUE = new ArrayList<>();
    private static int nextSquadId = 1;
    private static int caravanTimer = -1;

    private record PendingReinforcement(ServerLevel level, Raid raid, int wave, BlockPos pos) {
    }

    private ConscriptHunts() {
    }

    // ---- State ---------------------------------------------------------------------------------------------------

    private static CompoundTag data(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag hunt = persisted.getCompound(TAG);
        persisted.put(TAG, hunt);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return hunt;
    }

    private static CompoundTag peek(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(TAG);
    }

    /** Whether Conscripts hunt this player. Server only (always false on the client). */
    public static boolean isHunted(Player player) {
        return !player.level().isClientSide() && peek(player).getBoolean(HUNTED);
    }

    public static int getNotoriety(Player player) {
        return peek(player).getInt(NOTORIETY);
    }

    /** Ticks until the next hunting party is sent (or until its warning, if none is pending). */
    public static int getHuntTimer(Player player) {
        return peek(player).getInt(HUNT_TIMER);
    }

    public static void setNotoriety(ServerPlayer player, int notoriety) {
        data(player).putInt(NOTORIETY, Mth.clamp(notoriety, 0, MAX_NOTORIETY));
    }

    private static void changeNotoriety(ServerPlayer player, int delta) {
        int before = getNotoriety(player);
        int after = Mth.clamp(before + delta, 0, MAX_NOTORIETY);
        CompoundTag tag = data(player);
        tag.putInt(CALM, 0);
        if (after == before) {
            return;
        }
        tag.putInt(NOTORIETY, after); // internal only: the player is never told
    }

    /** Marks a player as hunted (permanent), with the advancement and a horn only they hear. */
    public static void markHunted(ServerPlayer player) {
        if (isHunted(player)) {
            return;
        }
        CompoundTag tag = data(player);
        tag.putBoolean(HUNTED, true);
        tag.putInt(NOTORIETY, 0);
        tag.putInt(CALM, 0);
        tag.putInt(HUNT_TIMER, huntInterval(player));
        ModAdvancements.grant(player, CRIMES_AGAINST_NATURE);
        if (ResearchManager.unlockNote(player, HUNT_JOURNAL_NOTE)) {
            player.sendSystemMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".journal_hunt_entry")
                    .withStyle(ChatFormatting.GOLD));
        }
        player.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.HOSTILE,
                player.getX(), player.getY(), player.getZ(), 64.0F, 1.0F, player.getRandom().nextLong()));
    }

    /** Admin: clears (or sets) the hunted mark. */
    public static void setHunted(ServerPlayer player, boolean hunted) {
        if (hunted) {
            markHunted(player);
            return;
        }
        data(player).putBoolean(HUNTED, false);
        data(player).putInt(PENDING, 0);
        callOffSquads(player);
    }

    /** Admin: the next hunting party's warning sounds right away. */
    public static void huntNow(ServerPlayer player) {
        markHunted(player);
        data(player).putInt(HUNT_TIMER, 1);
        data(player).putInt(PENDING, 0);
    }

    private static int huntInterval(Player player) {
        double base = SanguinaireConfig.HUNT_INTERVAL_MINUTES.get() * 60.0D * 20.0D;
        double notorietyFactor = 1.15D - 0.1D * getNotoriety(player);
        double jitter = 0.9D + player.getRandom().nextDouble() * 0.2D;
        return Math.max(RETRY_TICKS, (int) (base * notorietyFactor * jitter));
    }

    private static int squadSize(Player player) {
        return 2 + getNotoriety(player) * 4 / 5;
    }

    static void onSquadGaveUp(ServerPlayer player, int squadId) {
        if (ANNOUNCED_SQUADS.add(squadId)) {
            player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".hunt_lost_trail")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }
    }

    // ---- Becoming hunted -----------------------------------------------------------------------------------------

    private static boolean isDone(ServerPlayer player, String path) {
        var advancement = player.server.getAdvancements().getAdvancement(Sanguinaire.id(path));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static boolean hasCommittedCrimes(ServerPlayer player) {
        if (isDone(player, ModAdvancements.FIRST_TRANSFORMATION)) {
            return true;
        }
        for (ResearchNote note : ResearchNote.values()) {
            if (isDone(player, ModAdvancements.tornPage(note.getId()))) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var id = event.getAdvancement().getId();
        if (id.getNamespace().equals(Sanguinaire.MOD_ID)
                && (id.getPath().equals(ModAdvancements.FIRST_TRANSFORMATION) || id.getPath().startsWith("torn_page/"))) {
            markHunted(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isHunted(player) && hasCommittedCrimes(player)) {
            markHunted(player); // players from before Conscripts existed
        } else if (isHunted(player)) {
            ResearchManager.unlockNote(player, HUNT_JOURNAL_NOTE); // hunted before the journal entry existed
        }
    }

    /** Every transformation raises notoriety. Called right after the transformation starts. */
    public static void onTransform(ServerPlayer player) {
        if (isHunted(player)) {
            changeNotoriety(player, 1);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ConscriptEntity && event.getSource().getEntity() instanceof ServerPlayer killer
                && isHunted(killer)) {
            CompoundTag tag = data(killer);
            int kills = tag.getInt(KILLS) + 1;
            tag.putInt(CALM, 0);
            if (kills >= KILLS_PER_STAR) {
                kills = 0;
                changeNotoriety(killer, 1);
            }
            tag.putInt(KILLS, kills);
        }
        if (event.getEntity() instanceof ServerPlayer player && isHunted(player)) {
            // No spawn camping: the party disperses and the next one takes a full interval.
            CompoundTag tag = data(player);
            tag.putInt(PENDING, 0);
            tag.putInt(HUNT_TIMER, huntInterval(player));
            callOffSquads(player);
        }
    }

    private static void callOffSquads(ServerPlayer player) {
        for (ConscriptEntity conscript : player.serverLevel().getEntitiesOfClass(ConscriptEntity.class,
                player.getBoundingBox().inflate(160.0D), c -> c.isHunting(player))) {
            conscript.stopHunting();
        }
    }

    private static boolean squadStillHunting(ServerPlayer player) {
        return !player.serverLevel().getEntitiesOfClass(ConscriptEntity.class, player.getBoundingBox().inflate(160.0D),
                c -> c.isAlive() && c.isHunting(player)).isEmpty();
    }

    private static boolean canBeHuntedNow(ServerPlayer player) {
        return player.level().dimension() == Level.OVERWORLD && player.isAlive() && !player.isSpectator() && !player.isCreative()
                && player.level().getDifficulty() != Difficulty.PEACEFUL;
    }

    // ---- Hunting parties -----------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0
                || !isHunted(player)) {
            return;
        }
        CompoundTag tag = data(player);
        int calm = tag.getInt(CALM) + 20;
        if (calm >= CALM_DECAY_TICKS) {
            calm = 0;
            if (getNotoriety(player) > 0) {
                changeNotoriety(player, -1);
            }
        }
        tag.putInt(CALM, calm);
        if (!SanguinaireConfig.HUNTS_ENABLED.get()) {
            return;
        }
        int pending = tag.getInt(PENDING);
        if (pending > 0) {
            pending -= 20;
            tag.putInt(PENDING, pending);
            if (pending <= 0 && canBeHuntedNow(player)) {
                BlockPos at = new BlockPos(tag.getInt("PendingX"), tag.getInt("PendingY"), tag.getInt("PendingZ"));
                spawnHuntingParty(player, at);
            }
            return;
        }
        int timer = tag.getInt(HUNT_TIMER) - 20;
        if (timer > 0) {
            tag.putInt(HUNT_TIMER, timer);
            return;
        }
        if (!canBeHuntedNow(player) || squadStillHunting(player)) {
            tag.putInt(HUNT_TIMER, RETRY_TICKS);
            return;
        }
        BlockPos pos = findSpawnPos(player.serverLevel(), player.blockPosition(), 30, 48, player.getRandom());
        if (pos == null) {
            tag.putInt(HUNT_TIMER, RETRY_TICKS); // over water or in a sealed place: they'll try again soon
            return;
        }
        tag.putInt(HUNT_TIMER, huntInterval(player));
        tag.putInt(PENDING, WARNING_TICKS);
        tag.putInt("PendingX", pos.getX());
        tag.putInt("PendingY", pos.getY());
        tag.putInt("PendingZ", pos.getZ());
        player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".hunt_warning")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
        player.level().playSound(null, pos, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5).value(), SoundSource.HOSTILE, 12.0F, 0.9F);
    }

    /** Sends a hunting party after {@code player}, arriving around {@code near}. */
    public static void spawnHuntingParty(ServerPlayer player, BlockPos near) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        BlockPos center = surfaceAt(level, near.getX(), near.getZ());
        if (center == null) {
            center = findSpawnPos(level, player.blockPosition(), 30, 48, random);
        }
        if (center == null) {
            return;
        }
        int squad = nextSquadId++;
        int spawned = 0;
        for (int i = 0; i < squadSize(player); i++) {
            BlockPos pos = surfaceAt(level, center.getX() + random.nextInt(7) - 3, center.getZ() + random.nextInt(7) - 3);
            ConscriptEntity conscript = createConscript(level, pos != null ? pos : center, MobSpawnType.EVENT);
            if (conscript != null) {
                conscript.setHunting(player, squad);
                level.addFreshEntityWithPassengers(conscript);
                spawned++;
            }
        }
        if (spawned > 0) {
            level.playSound(null, center, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(), SoundSource.HOSTILE, 16.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".hunt_arrived")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    @Nullable
    private static ConscriptEntity createConscript(ServerLevel level, BlockPos pos, MobSpawnType spawnType) {
        ConscriptEntity conscript = ModEntities.CONSCRIPT.get().create(level);
        if (conscript == null) {
            return null;
        }
        conscript.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        conscript.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), spawnType, null, null);
        return conscript;
    }

    /** A dry, open surface spot in a loaded chunk, or null. */
    @Nullable
    public static BlockPos surfaceAt(ServerLevel level, int x, int z) {
        BlockPos probe = new BlockPos(x, level.getMinBuildHeight(), z);
        if (!level.isLoaded(probe) || !level.getWorldBorder().isWithinBounds(probe)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        BlockState below = level.getBlockState(pos.below());
        if (below.is(BlockTags.LEAVES) || !below.isFaceSturdy(level, pos.below(), Direction.UP)
                || !level.getFluidState(pos.below()).isEmpty() || !level.getFluidState(pos).isEmpty()
                || !level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) {
            return null;
        }
        return pos;
    }

    @Nullable
    private static BlockPos findSpawnPos(ServerLevel level, BlockPos around, int minDistance, int maxDistance, RandomSource random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            BlockPos pos = surfaceAt(level, around.getX() + (int) (Math.cos(angle) * distance),
                    around.getZ() + (int) (Math.sin(angle) * distance));
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    // ---- Caravans ------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        processRaidQueue();
        ServerLevel overworld = event.getServer().overworld();
        if (caravanTimer < 0) {
            caravanTimer = caravanInterval(overworld.random);
        }
        if (--caravanTimer > 0) {
            return;
        }
        caravanTimer = caravanInterval(overworld.random);
        if (!SanguinaireConfig.CARAVANS_ENABLED.get() || overworld.getDifficulty() == Difficulty.PEACEFUL
                || overworld.random.nextDouble() >= SanguinaireConfig.CARAVAN_CHANCE.get()) {
            return;
        }
        List<ServerPlayer> players = overworld.getPlayers(p -> !p.isSpectator() && p.isAlive());
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer player = players.get(overworld.random.nextInt(players.size()));
        if (!overworld.getEntitiesOfClass(ConscriptEntity.class, player.getBoundingBox().inflate(160.0D)).isEmpty()) {
            return; // one group at a time around a player
        }
        BlockPos pos = findSpawnPos(overworld, player.blockPosition(), 40, 64, overworld.random);
        if (pos != null && !overworld.isCloseToVillage(pos, 3)) {
            spawnCaravan(overworld, pos);
        }
    }

    private static int caravanInterval(RandomSource random) {
        double base = SanguinaireConfig.CARAVAN_INTERVAL_MINUTES.get() * 60.0D * 20.0D;
        return Math.max(1200, (int) (base * (0.75D + random.nextDouble() * 0.5D)));
    }

    /** 5 Conscripts (a patrol leader and 4) and 2 donkeys whose chests hold the caravan's supplies. */
    public static void spawnCaravan(ServerLevel level, BlockPos center) {
        RandomSource random = level.random;
        for (int i = 0; i < 5; i++) {
            BlockPos pos = surfaceAt(level, center.getX() + random.nextInt(9) - 4, center.getZ() + random.nextInt(9) - 4);
            ConscriptEntity conscript = ModEntities.CONSCRIPT.get().create(level);
            if (conscript == null) {
                continue;
            }
            BlockPos at = pos != null ? pos : center;
            if (i == 0) {
                conscript.setPatrolLeader(true);
                conscript.findPatrolTarget();
            }
            conscript.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            conscript.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.PATROL, null, null);
            level.addFreshEntityWithPassengers(conscript);
        }
        for (int i = 0; i < 2; i++) {
            BlockPos pos = surfaceAt(level, center.getX() + random.nextInt(7) - 3, center.getZ() + random.nextInt(7) - 3);
            spawnCaravanDonkey(level, pos != null ? pos : center);
        }
    }

    private static void spawnCaravanDonkey(ServerLevel level, BlockPos pos) {
        Donkey donkey = EntityType.DONKEY.create(level);
        if (donkey == null) {
            return;
        }
        donkey.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        donkey.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        // Give it a chest full of supplies (done through its save data so the chest inventory is built properly).
        CompoundTag tag = donkey.saveWithoutId(new CompoundTag());
        tag.putBoolean("ChestedHorse", true);
        ListTag items = new ListTag();
        LootTable table = level.getServer().getLootData().getLootTable(Sanguinaire.id("chests/conscript_caravan"));
        ObjectArrayList<ItemStack> loot = table.getRandomItems(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST));
        List<Integer> slots = new ArrayList<>();
        for (int slot = 2; slot < 17; slot++) {
            slots.add(slot);
        }
        java.util.Collections.shuffle(slots, new java.util.Random(level.random.nextLong()));
        for (int i = 0; i < loot.size() && i < slots.size(); i++) {
            CompoundTag item = loot.get(i).save(new CompoundTag());
            item.putByte("Slot", slots.get(i).byteValue());
            items.add(item);
        }
        tag.put("Items", items);
        donkey.load(tag);
        donkey.getPersistentData().putBoolean(CARAVAN_TAG, true);
        level.addFreshEntity(donkey);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Donkey donkey && donkey.getPersistentData().getBoolean(CARAVAN_TAG) && !donkey.isTamed()) {
            donkey.goalSelector.addGoal(1, new CaravanDonkeyGoal(donkey));
        }
        // Wandering traders keep well away from Conscripts (villagers are scared off by the Conscripts themselves).
        if (entity instanceof WanderingTrader trader) {
            trader.goalSelector.addGoal(1, new AvoidEntityGoal<>(trader, ConscriptEntity.class, 10.0F, 0.5D, 0.5D));
        }
        if (!event.loadedFromDisk() && entity instanceof Raider raider && !(entity instanceof ConscriptEntity)
                && raider.getCurrentRaid() != null && event.getLevel() instanceof ServerLevel serverLevel) {
            Raid raid = raider.getCurrentRaid();
            if (REINFORCED_WAVES.add(raid.getId() + ":" + raider.getWave())) {
                RAID_QUEUE.add(new PendingReinforcement(serverLevel, raid, raider.getWave(), raider.blockPosition()));
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // Conscript crossbow bolts always hit for a flat amount.
        if (event.getSource().getDirectEntity() != null
                && event.getSource().getDirectEntity().getPersistentData().getBoolean(ConscriptEntity.BOLT_TAG)) {
            event.setAmount(ConscriptEntity.CROSSBOW_DAMAGE);
        }
        if (event.getEntity() instanceof Donkey donkey && donkey.getPersistentData().getBoolean(CARAVAN_TAG) && !donkey.isTamed()
                && event.getSource().getEntity() instanceof Player player) {
            angerCaravan(donkey, player);
        }
    }

    private static void angerCaravan(Donkey donkey, Player player) {
        List<ConscriptEntity> guards = donkey.level().getEntitiesOfClass(ConscriptEntity.class, donkey.getBoundingBox().inflate(24.0D));
        if (guards.isEmpty() || player.isCreative() || player.isSpectator() || guards.get(0).isAngryAt(player)) {
            return;
        }
        guards.get(0).angerGroupAt(player);
        player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".caravan_theft")
                .withStyle(ChatFormatting.RED), true);
    }

    /** Caravan donkeys keep close to the Conscripts and vanish if left alone far from everyone. */
    private static class CaravanDonkeyGoal extends Goal {
        private final Donkey donkey;
        private int lonely;
        private int repath;

        CaravanDonkeyGoal(Donkey donkey) {
            this.donkey = donkey;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Nullable
        private ConscriptEntity nearestGuard(double range) {
            return donkey.level().getNearestEntity(ConscriptEntity.class,
                    net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().range(range),
                    donkey, donkey.getX(), donkey.getY(), donkey.getZ(), donkey.getBoundingBox().inflate(range));
        }

        @Override
        public boolean canUse() {
            if (donkey.isTamed()) {
                return false;
            }
            if (donkey.getControllingPassenger() instanceof Player rider) {
                angerCaravan(donkey, rider);
                return false;
            }
            ConscriptEntity guard = nearestGuard(48.0D);
            if (guard == null && donkey.level().getNearestPlayer(donkey, 64.0D) == null) {
                if ((lonely += 2) > 600) {
                    donkey.discard(); // the caravan moved on
                }
                return false;
            }
            lonely = 0;
            return guard != null && donkey.distanceToSqr(guard) > 25.0D;
        }

        @Override
        public boolean canContinueToUse() {
            ConscriptEntity guard = nearestGuard(48.0D);
            return !donkey.isTamed() && !donkey.isVehicle() && guard != null && donkey.distanceToSqr(guard) > 9.0D;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (--repath > 0) {
                return;
            }
            repath = 20;
            ConscriptEntity guard = nearestGuard(48.0D);
            if (guard != null) {
                donkey.getNavigation().moveTo(guard, 1.3D);
            }
        }

        @Override
        public void stop() {
            donkey.getNavigation().stop();
        }
    }

    // ---- Raids ---------------------------------------------------------------------------------------------------

    private static void processRaidQueue() {
        if (RAID_QUEUE.isEmpty()) {
            return;
        }
        List<PendingReinforcement> queue = new ArrayList<>(RAID_QUEUE);
        RAID_QUEUE.clear();
        for (PendingReinforcement pending : queue) {
            Raid raid = pending.raid();
            if (raid.isStopped() || raid.isOver()) {
                continue;
            }
            BlockPos center = raid.getCenter();
            List<ServerPlayer> hunted = pending.level().getPlayers(p -> isHunted(p)
                    && p.distanceToSqr(Vec3.atCenterOf(center)) < RAID_RANGE * RAID_RANGE);
            if (hunted.isEmpty()) {
                continue;
            }
            int count = 1 + Math.min(3, pending.wave() / 2);
            for (int i = 0; i < count; i++) {
                ConscriptEntity conscript = ModEntities.CONSCRIPT.get().create(pending.level());
                if (conscript != null) {
                    raid.joinRaid(pending.wave(), conscript, pending.pos(), false);
                }
            }
            for (ServerPlayer player : hunted) {
                player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".raid_conscripts")
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
        }
        if (REINFORCED_WAVES.size() > 512) {
            REINFORCED_WAVES.clear();
        }
    }
}
