package com.sanguinaire.network.packet;

import com.sanguinaire.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: the receiving player's own researched ingredients and unlocked utility serums, for their
 * Research Journal.
 * Only ever sent to the player the research belongs to, so research is never revealed to anyone else.
 */
public record SyncResearchPacket(List<ResourceLocation> researched, List<String> unlockedSerums) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeCollection(researched, FriendlyByteBuf::writeResourceLocation);
        buffer.writeCollection(unlockedSerums, FriendlyByteBuf::writeUtf);
    }

    public static SyncResearchPacket decode(FriendlyByteBuf buffer) {
        return new SyncResearchPacket(buffer.readList(FriendlyByteBuf::readResourceLocation), buffer.readList(FriendlyByteBuf::readUtf));
    }

    public static void handle(SyncResearchPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncResearch(packet));
        context.get().setPacketHandled(true);
    }
}
