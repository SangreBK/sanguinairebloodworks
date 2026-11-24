package com.sanguinaire.network.packet;

import com.sanguinaire.mutation.MutationAbilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: the player swung at nothing (vanilla never tells the server about a miss beyond the arm swing).
 * Used for creatures whose attack has a sound of its own, such as the Ravager's bite. The server checks everything.
 */
public record EmptySwingPacket() {

    public void encode(FriendlyByteBuf buffer) {
        // No payload.
    }

    public static EmptySwingPacket decode(FriendlyByteBuf buffer) {
        return new EmptySwingPacket();
    }

    public static void handle(EmptySwingPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            MutationAbilities.onEmptySwing(sender);
        }
        context.get().setPacketHandled(true);
    }
}
