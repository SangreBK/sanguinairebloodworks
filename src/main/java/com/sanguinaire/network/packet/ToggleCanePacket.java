package com.sanguinaire.network.packet;

import com.sanguinaire.item.VileCaneItem;
import com.sanguinaire.mutation.VileCaneEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the Mutation Ability key was pressed by a human holding a Vile Cane in the main hand. */
public record ToggleCanePacket() {

    public void encode(FriendlyByteBuf buffer) {
        // No payload.
    }

    public static ToggleCanePacket decode(FriendlyByteBuf buffer) {
        return new ToggleCanePacket();
    }

    public static void handle(ToggleCanePacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            ItemStack cane = VileCaneEvents.heldCane(sender); // main hand only, human only
            if (!cane.isEmpty() && !sender.getCooldowns().isOnCooldown(cane.getItem())) {
                VileCaneItem.switchMode(sender, cane);
            }
        }
        context.get().setPacketHandled(true);
    }
}
