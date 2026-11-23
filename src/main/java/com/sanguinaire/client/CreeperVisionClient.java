package com.sanguinaire.client;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.TransformationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Seeing through a Creeper's eyes: while the local player is transformed into a Creeper and looking in first person,
 * the world gets the same green, pixelated post-processing filter vanilla uses when spectating a Creeper.
 * <p>
 * The filter is loaded through vanilla's own {@link GameRenderer#checkEntityPostEffect}, handed a client-only Creeper
 * that is never added to the world. When the transformation ends (or the view leaves first person), the filter is
 * handed back to vanilla by re-checking the real camera entity, so spectating other mobs keeps working.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class CreeperVisionClient {
    private static final String CREEPER_SHADER = "minecraft:shaders/post/creeper.json";

    @Nullable
    private static Creeper lens;
    private static boolean applied;

    private CreeperVisionClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GameRenderer renderer = minecraft.gameRenderer;
        if (minecraft.player == null || minecraft.level == null) {
            applied = false;
            return;
        }
        boolean wanted = TransformationManager.getActive(minecraft.player) == MutationType.CREEPER
                && minecraft.getCameraEntity() == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson();
        if (wanted) {
            PostChain current = renderer.currentEffect();
            if (current == null || !CREEPER_SHADER.equals(current.getName())) {
                Creeper creeper = getLens(minecraft.level);
                if (creeper != null) {
                    renderer.checkEntityPostEffect(creeper);
                    applied = true;
                }
            }
        } else if (applied) {
            applied = false;
            renderer.checkEntityPostEffect(minecraft.getCameraEntity());
        }
    }

    @Nullable
    private static Creeper getLens(Level level) {
        if (lens == null || lens.level() != level) {
            lens = EntityType.CREEPER.create(level);
        }
        return lens;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            lens = null;
        }
    }
}
