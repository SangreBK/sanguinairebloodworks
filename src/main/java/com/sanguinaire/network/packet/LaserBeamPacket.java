package com.sanguinaire.network.packet;

import com.sanguinaire.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → clients: a transformed Guardian/Elder Guardian player started ({@code targetId >= 0}) or stopped
 * ({@code targetId == -1}) charging its laser. Purely visual; damage is dealt by the server.
 */
public record LaserBeamPacket(int playerId, int targetId, int chargeTicks) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(playerId);
        buffer.writeVarInt(targetId);
        buffer.writeVarInt(chargeTicks);
    }

    public static LaserBeamPacket decode(FriendlyByteBuf buffer) {
        return new LaserBeamPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(LaserBeamPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleLaserBeam(packet));
        context.get().setPacketHandled(true);
    }
}
