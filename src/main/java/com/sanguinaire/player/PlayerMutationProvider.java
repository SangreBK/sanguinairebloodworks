package com.sanguinaire.player;

import com.sanguinaire.Sanguinaire;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Attaches and serializes {@link PlayerMutationData} on players. */
public class PlayerMutationProvider implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<PlayerMutationData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });
    public static final ResourceLocation ID = Sanguinaire.id("mutation_data");

    private final PlayerMutationData data = new PlayerMutationData();
    private final LazyOptional<PlayerMutationData> optional = LazyOptional.of(() -> data);

    public static Optional<PlayerMutationData> get(Player player) {
        return player.getCapability(CAPABILITY).resolve();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return CAPABILITY.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.serialize();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.deserialize(tag);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
