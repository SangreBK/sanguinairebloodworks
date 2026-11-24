package com.sanguinaire.network.packet;

import com.sanguinaire.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the world's {@code /sanguinaire} rules (see {@code SanguinaireSettings}). */
public record SettingsPacket(boolean firstPersonAllowed, boolean transformationItemsAllowed) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(firstPersonAllowed);
        buffer.writeBoolean(transformationItemsAllowed);
    }

    public static SettingsPacket decode(FriendlyByteBuf buffer) {
        return new SettingsPacket(buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(SettingsPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSettings(packet));
        context.get().setPacketHandled(true);
    }
}
