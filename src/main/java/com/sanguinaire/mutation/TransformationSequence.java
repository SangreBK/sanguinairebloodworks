package com.sanguinaire.mutation;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.blood.BloodInfusion;
import com.sanguinaire.network.ModNetwork;
import com.sanguinaire.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The build-up between injecting a transformation serum and the change itself (2 seconds). Server only.
 * <ol>
 *     <li>Tick 0: the needle sound; the player is slowed and their client snaps to first person and starts its
 *     screen effect ({@link MutationAbilities#ANIMATION_SERUM_INJECT}).</li>
 *     <li>The heart races ({@link #HEARTBEAT_TICKS}, each beat faster and higher), blood seeps from the body in
 *     growing amounts, and bones crack near the end ({@link #CRACK_TICKS}).</li>
 *     <li>Tick {@link #DURATION_TICKS}: a burst of blood and the transformation starts
 *     ({@link TransformationManager#tryStart}); the client switches to third person.</li>
 * </ol>
 * The serum is already used up when the sequence begins. Dying during it wastes the serum; logging out refunds it.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class TransformationSequence {
    public static final int DURATION_TICKS = 40;
    /** Ticks (since injection) on which the heart beats; shared with the client so its screen pulses in time. */
    public static final int[] HEARTBEAT_TICKS = {4, 15, 23, 29, 33, 36, 38};
    private static final int[] CRACK_TICKS = {26, 32, 37};
    private static final int BLEED_START_TICKS = 8;

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private static final class Pending {
        final MutationType type;
        @Nullable
        final BloodInfusion blood;
        final ItemStack serum;
        int ticks;

        Pending(MutationType type, @Nullable BloodInfusion blood, ItemStack serum) {
            this.type = type;
            this.blood = blood;
            this.serum = serum;
        }
    }

    private TransformationSequence() {
    }

    /** True while the player is between injection and transformation. */
    public static boolean isPending(Player player) {
        return PENDING.containsKey(player.getUUID());
    }

    /**
     * Begins the build-up. {@code serum} is the single serum already taken from the player; it is only used to
     * refund them if they log out before the change.
     */
    public static void begin(ServerPlayer player, MutationType type, @Nullable BloodInfusion blood, ItemStack serum) {
        PENDING.put(player.getUUID(), new Pending(type, blood, serum.copy()));
        play(player, ModSounds.SERUM_INJECT.get(), 1.0F, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DURATION_TICKS, 2, false, false, false));
        ModNetwork.playAbilityAnimation(player, MutationAbilities.ANIMATION_SERUM_INJECT, DURATION_TICKS);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Pending>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Pending> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Pending pending = entry.getValue();
            if (player == null || !player.isAlive() || TransformationManager.isTransformed(player)) {
                iterator.remove(); // gone or dead: the serum is spent
                continue;
            }
            pending.ticks++;
            tick(player, pending);
            if (pending.ticks >= DURATION_TICKS) {
                iterator.remove();
                finish(player, pending);
            }
        }
    }

    private static void tick(ServerPlayer player, Pending pending) {
        int t = pending.ticks;
        for (int i = 0; i < HEARTBEAT_TICKS.length; i++) {
            if (HEARTBEAT_TICKS[i] == t) {
                float urgency = i / (float) (HEARTBEAT_TICKS.length - 1);
                play(player, ModSounds.SERUM_HEARTBEAT.get(), 1.2F + urgency * 0.8F, 0.8F + urgency * 0.5F);
            }
        }
        for (int crack : CRACK_TICKS) {
            if (crack == t) {
                play(player, ModSounds.TRANSFORMATION_CRACK.get(), 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
            }
        }
        if (t >= BLEED_START_TICKS && t % 2 == 0 && player.level() instanceof ServerLevel level) {
            float progress = (t - BLEED_START_TICKS) / (float) (DURATION_TICKS - BLEED_START_TICKS);
            int count = 2 + (int) (progress * 10);
            double width = player.getBbWidth() * 0.4D;
            level.sendParticles(new DustParticleOptions(new Vector3f(0.45F, 0.02F, 0.02F), 1.2F),
                    player.getX(), player.getY(0.55D), player.getZ(), count, width, player.getBbHeight() * 0.3D, width, 0.0D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                    player.getX(), player.getY(0.55D), player.getZ(), count / 2, width, player.getBbHeight() * 0.3D, width, 0.05D);
        }
    }

    private static void finish(ServerPlayer player, Pending pending) {
        if (!TransformationManager.tryStart(player, pending.type, pending.blood)) {
            return;
        }
        play(player, ModSounds.TRANSFORMATION_BURST.get(), 1.2F, 0.85F); // a deeper, louder final break
        if (player.level() instanceof ServerLevel level) {
            double x = player.getX();
            double y = player.getY(0.5D);
            double z = player.getZ();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                    x, y, z, 70, 0.5D, 0.7D, 0.5D, 0.2D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState()),
                    x, y, z, 35, 0.45D, 0.6D, 0.45D, 0.15D);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.5F, 0.02F, 0.02F), 1.8F),
                    x, y, z, 45, 0.7D, 0.8D, 0.7D, 0.0D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 12, 0.4D, 0.5D, 0.4D, 0.02D);
        }
    }

    private static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Logging out mid-sequence gives the serum back instead of wasting it. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Pending pending = PENDING.remove(event.getEntity().getUUID());
        if (pending != null && !event.getEntity().getInventory().add(pending.serum)) {
            event.getEntity().drop(pending.serum, false);
        }
    }
}
