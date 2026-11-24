package com.sanguinaire.client;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.TransformationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Idle sounds of the custom creatures (Goatman, Rotting Abomination): unlike vanilla ambient sounds, they loop
 * without pause for as long as the transformation lasts, restarting the moment they end. Every client plays one
 * looping instance per transformed player it can see; it follows the player and stops when the form ends or the
 * player leaves.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class IdleLoopSounds {
    private static final Map<Integer, Loop> LOOPS = new HashMap<>();

    private IdleLoopSounds() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        // Forget loops that have stopped (form ended, player gone, or the sound engine dropped them).
        Iterator<Loop> iterator = LOOPS.values().iterator();
        while (iterator.hasNext()) {
            Loop loop = iterator.next();
            if (loop.isStopped() || !minecraft.getSoundManager().isActive(loop)) {
                loop.end();
                iterator.remove();
            }
        }
        for (Player player : minecraft.level.players()) {
            if (!player.isAlive() || player.isSpectator() || LOOPS.containsKey(player.getId())) {
                continue;
            }
            MutationType type = TransformationManager.getActive(player);
            SoundEvent sound = type == null ? null : type.getIdleLoopSound();
            if (sound != null) {
                Loop loop = new Loop(player, type, sound);
                LOOPS.put(player.getId(), loop);
                minecraft.getSoundManager().play(loop);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            LOOPS.values().forEach(Loop::end);
            LOOPS.clear();
        }
    }

    /** A looping sound attached to one player while they stay in one form. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final Player player;
        private final MutationType type;

        Loop(Player player, MutationType type, SoundEvent sound) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.type = type;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            if (player.isRemoved() || !player.isAlive() || TransformationManager.getActive(player) != type) {
                stop();
                return;
            }
            x = player.getX();
            y = player.getY();
            z = player.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        void end() {
            stop();
        }
    }
}
