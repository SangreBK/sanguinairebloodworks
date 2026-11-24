package com.sanguinaire.player;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.MutationAbilities;
import com.sanguinaire.mutation.TransformationManager;
import com.sanguinaire.network.ModNetwork;
import com.sanguinaire.research.ResearchManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Attaches the mutation capability and keeps it correct across player replacement:
 * <ul>
 *     <li><b>Death</b>: Weakened Hearts are copied; the transformation already ended on death. Research lives in
 *     world storage ({@link com.sanguinaire.research.ResearchSavedData}) and is unaffected.</li>
 *     <li><b>Returning from the End</b> (also a clone): everything is copied and the transformation re-applied.</li>
 *     <li><b>Login / respawn / dimension change</b>: transient attributes are re-applied and state is re-synced.</li>
 *     <li><b>Start tracking</b>: other players receive this player's state for rendering.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class PlayerMutationEvents {
    private PlayerMutationEvents() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            PlayerMutationProvider provider = new PlayerMutationProvider();
            // No invalidation listener: the data must survive the remove/revive cycle of dimension changes and deaths.
            event.addCapability(PlayerMutationProvider.ID, provider);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        PlayerMutationProvider.get(original).ifPresent(oldData ->
                PlayerMutationProvider.get(event.getEntity()).ifPresent(newData ->
                        newData.copyFrom(oldData, !event.isWasDeath())));
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResearchManager.migrateLegacyResearch(player);
            TransformationManager.reapply(player);
            ModNetwork.syncMutationData(player);
            ModNetwork.syncResearch(player);
            ModNetwork.syncSettings(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MutationAbilities.clear(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TransformationManager.reapply(player);
            ModNetwork.syncMutationData(player);
            ModNetwork.syncResearch(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TransformationManager.reapply(player);
            ModNetwork.syncMutationData(player);
            ModNetwork.syncResearch(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer tracker) {
            ModNetwork.sendMutationDataTo(tracker, target);
        }
    }
}
