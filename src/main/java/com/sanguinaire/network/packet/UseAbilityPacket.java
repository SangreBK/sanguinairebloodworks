package com.sanguinaire.network.packet;

import com.sanguinaire.mutation.MutationAbilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: "the Mutation Active Ability Key was pressed". Carries no data: the server decides everything
 * (which ability, target, whether it is allowed) from its own state.
 */
public record UseAbilityPacket() {

    public void encode(FriendlyByteBuf buffer) {
        // No payload.
    }

    public static UseAbilityPacket decode(FriendlyByteBuf buffer) {
        return new UseAbilityPacket();
    }

    public static void handle(UseAbilityPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            MutationAbilities.requestAbility(sender);
        }
        context.get().setPacketHandled(true);
    }
}
