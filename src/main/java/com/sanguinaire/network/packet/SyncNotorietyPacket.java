package com.sanguinaire.network.packet;

import com.sanguinaire.client.ClientResearchCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: the receiving player's own Conscript notoriety (0 to 5), shown only as the stars on the Journal's
 * Notoriety entry. Sent to that player alone, whenever it changes and on login.
 */
public record SyncNotorietyPacket(int notoriety) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(notoriety);
    }

    public static SyncNotorietyPacket decode(FriendlyByteBuf buffer) {
        return new SyncNotorietyPacket(buffer.readVarInt());
    }

    public static void handle(SyncNotorietyPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientResearchCache.setNotoriety(packet.notoriety()));
        context.get().setPacketHandled(true);
    }
}
