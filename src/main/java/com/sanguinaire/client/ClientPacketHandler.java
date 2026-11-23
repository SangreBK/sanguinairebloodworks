package com.sanguinaire.client;

import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.client.render.TransformationRenderer;
import com.sanguinaire.network.packet.AbilityAnimationPacket;
import com.sanguinaire.network.packet.LaserBeamPacket;
import com.sanguinaire.network.packet.SyncMutationDataPacket;
import com.sanguinaire.network.packet.SyncResearchPacket;
import com.sanguinaire.player.PlayerMutationProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Client-only packet handling. Only ever referenced from inside {@code DistExecutor} lambdas. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handleAbilityAnimation(AbilityAnimationPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            TransformationRenderer.playAnimation(packet.playerId(), packet.animation(), minecraft.level.getGameTime(), packet.durationTicks());
            AbilityEffectsClient.onAbility(packet.playerId(), packet.animation(), packet.durationTicks());
        }
    }

    public static void handleLaserBeam(LaserBeamPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            LaserBeamClient.set(packet.playerId(), packet.targetId(), minecraft.level.getGameTime(), packet.chargeTicks());
        }
    }

    public static void handleSyncResearch(SyncResearchPacket packet) {
        ClientResearchCache.set(packet.researched(), packet.unlockedSerums());
    }

    public static void handleSyncMutationData(SyncMutationDataPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(packet.entityId());
        if (!(entity instanceof Player player)) {
            return;
        }
        PlayerMutationProvider.get(player).ifPresent(data -> {
            data.setWeakenedHealth(packet.weakenedHealth());
            MutationType type = packet.mutationId().isEmpty() ? null : MutationType.byId(packet.mutationId());
            boolean started = !data.isTransformed() && type != null;
            if (started && player == minecraft.player) {
                ClientTransformationEffects.onLocalTransformationStarted();
            }
            if (data.isTransformed() && type == null && player == minecraft.player) {
                ClientTransformationEffects.onLocalTransformationEnded();
            }
            boolean changed = data.getActiveMutation() != type;
            data.setActiveMutation(type);
            data.setRemainingTicks(type == null ? 0 : packet.remainingTicks());
            data.setPhysicalBoostTicks(packet.physicalBoostTicks());
            data.setTaintedBlood(packet.taintedBlood());
            if (changed) {
                player.refreshDimensions(); // hitbox follows the transformation on the client too
            }
        });
    }
}
