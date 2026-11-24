package com.sanguinaire.network.packet;

import com.sanguinaire.client.ClientPacketHandler;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.player.PlayerMutationData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: a player's Weakened Hearts and transformation state. Sent to the player and to everyone tracking
 * them, so other clients can later render the transformation. Pure state sync; the client never decides anything.
 *
 * @param mutationId empty string when not transformed
 */
public record SyncMutationDataPacket(int entityId, float weakenedHealth, String mutationId, int remainingTicks,
                                     int physicalBoostTicks, boolean taintedBlood, boolean creeperCharged) {

    public static SyncMutationDataPacket of(Player player, PlayerMutationData data) {
        MutationType active = data.getActiveMutation();
        return new SyncMutationDataPacket(player.getId(), data.getWeakenedHealth(),
                active == null ? "" : active.getId(), data.getRemainingTicks(), data.getPhysicalBoostTicks(), data.hasTaintedBlood(),
                data.isCreeperCharged());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeFloat(weakenedHealth);
        buffer.writeUtf(mutationId);
        buffer.writeVarInt(remainingTicks);
        buffer.writeVarInt(physicalBoostTicks);
        buffer.writeBoolean(taintedBlood);
        buffer.writeBoolean(creeperCharged);
    }

    public static SyncMutationDataPacket decode(FriendlyByteBuf buffer) {
        return new SyncMutationDataPacket(buffer.readVarInt(), buffer.readFloat(), buffer.readUtf(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(SyncMutationDataPacket packet, Supplier<NetworkEvent.Context> context) {
        // consumerMainThread already runs this on the client main thread.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncMutationData(packet));
        context.get().setPacketHandled(true);
    }
}
