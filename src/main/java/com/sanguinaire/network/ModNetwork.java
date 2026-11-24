package com.sanguinaire.network;

import com.sanguinaire.network.packet.ToggleCanePacket;

import net.minecraft.server.MinecraftServer;

import com.sanguinaire.settings.SanguinaireSettings;

import com.sanguinaire.network.packet.EmptySwingPacket;

import com.sanguinaire.network.packet.SettingsPacket;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.network.packet.AbilityAnimationPacket;
import com.sanguinaire.network.packet.LaserBeamPacket;
import com.sanguinaire.network.packet.SyncMutationDataPacket;
import com.sanguinaire.network.packet.SyncResearchPacket;
import com.sanguinaire.network.packet.UseAbilityPacket;
import com.sanguinaire.player.PlayerMutationData;
import com.sanguinaire.player.PlayerMutationProvider;
import com.sanguinaire.research.ResearchSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;

/** The mod's SimpleChannel and helpers for sending its packets. */
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Sanguinaire.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static boolean registered;

    private ModNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(SyncMutationDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncMutationDataPacket::encode)
                .decoder(SyncMutationDataPacket::decode)
                .consumerMainThread(SyncMutationDataPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncResearchPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncResearchPacket::encode)
                .decoder(SyncResearchPacket::decode)
                .consumerMainThread(SyncResearchPacket::handle)
                .add();
        CHANNEL.messageBuilder(LaserBeamPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LaserBeamPacket::encode)
                .decoder(LaserBeamPacket::decode)
                .consumerMainThread(LaserBeamPacket::handle)
                .add();
        CHANNEL.messageBuilder(AbilityAnimationPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AbilityAnimationPacket::encode)
                .decoder(AbilityAnimationPacket::decode)
                .consumerMainThread(AbilityAnimationPacket::handle)
                .add();
        CHANNEL.messageBuilder(UseAbilityPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UseAbilityPacket::encode)
                .decoder(UseAbilityPacket::decode)
                .consumerMainThread(UseAbilityPacket::handle)
                .add();
        CHANNEL.messageBuilder(SettingsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SettingsPacket::encode)
                .decoder(SettingsPacket::decode)
                .consumerMainThread(SettingsPacket::handle)
                .add();
        CHANNEL.messageBuilder(ToggleCanePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleCanePacket::encode)
                .decoder(ToggleCanePacket::decode)
                .consumerMainThread(ToggleCanePacket::handle)
                .add();
        CHANNEL.messageBuilder(EmptySwingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EmptySwingPacket::encode)
                .decoder(EmptySwingPacket::decode)
                .consumerMainThread(EmptySwingPacket::handle)
                .add();
    }

    /** Sends the world's /sanguinaire rules to one player (on login). */
    public static void syncSettings(ServerPlayer player) {
        SanguinaireSettings settings = SanguinaireSettings.get(player.server);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SettingsPacket(settings.isFirstPersonAllowed(), settings.areTransformationItemsAllowed()));
    }

    /** Sends the world's /sanguinaire rules to everyone (after a change). */
    public static void syncSettingsToAll(MinecraftServer server) {
        SanguinaireSettings settings = SanguinaireSettings.get(server);
        CHANNEL.send(PacketDistributor.ALL.noArg(),
                new SettingsPacket(settings.isFirstPersonAllowed(), settings.areTransformationItemsAllowed()));
    }

    /** Sends a player's own research to that player only (research is private). */
    public static void syncResearch(ServerPlayer player) {
        ResearchSavedData research = ResearchSavedData.get(player.server);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncResearchPacket(new ArrayList<>(research.getResearch(player.getUUID())),
                        new ArrayList<>(research.getUnlockedSerums(player.getUUID()))));
    }

    /** Tells the player and everyone tracking them that a laser charge started ({@code targetId >= 0}) or stopped. */
    public static void syncLaser(ServerPlayer player, int targetId, int chargeTicks) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new LaserBeamPacket(player.getId(), targetId, chargeTicks));
    }

    /** Tells the player and everyone tracking them to show an ability wind-up animation on the creature model. */
    public static void playAbilityAnimation(ServerPlayer player, int animation, int durationTicks) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new AbilityAnimationPacket(player.getId(), animation, durationTicks));
    }

    /** Client → server. */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    /** Sends a player's mutation state to that player and everyone tracking them. */
    public static void syncMutationData(ServerPlayer player) {
        PlayerMutationProvider.get(player).ifPresent(data ->
                CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), SyncMutationDataPacket.of(player, data)));
    }

    /** Sends {@code target}'s mutation state to a single receiving player (e.g. when they start tracking it). */
    public static void sendMutationDataTo(ServerPlayer receiver, Player target) {
        PlayerMutationProvider.get(target).ifPresent((PlayerMutationData data) ->
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver), SyncMutationDataPacket.of(target, data)));
    }
}
