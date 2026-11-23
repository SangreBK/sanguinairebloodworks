package com.sanguinaire.network.packet;

import com.sanguinaire.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → clients: a transformed player started an ability wind-up whose animation should be shown on their
 * creature model (Ghast shooting face, Warden sonic-boom charge). Visual only.
 */
public record AbilityAnimationPacket(int playerId, int animation, int durationTicks) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(playerId);
        buffer.writeVarInt(animation);
        buffer.writeVarInt(durationTicks);
    }

    public static AbilityAnimationPacket decode(FriendlyByteBuf buffer) {
        return new AbilityAnimationPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(AbilityAnimationPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleAbilityAnimation(packet));
        context.get().setPacketHandled(true);
    }
}
