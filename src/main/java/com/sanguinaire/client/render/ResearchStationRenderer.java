package com.sanguinaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sanguinaire.block.MachineBlock;
import com.sanguinaire.blockentity.ResearchStationBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the body part being researched impaled on the Research Station's spike. Blood Samples are never shown
 * (the block entity only syncs body parts), and the part disappears once the research is done.
 */
public class ResearchStationRenderer implements BlockEntityRenderer<ResearchStationBlockEntity> {
    /** Size of the impaled part (1 = a full item frame item). */
    private static final float PART_SCALE = 0.45F;

    public ResearchStationRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ResearchStationBlockEntity station, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ItemStack part = station.getDisplayedPart();
        if (part.isEmpty() || station.getLevel() == null) {
            return;
        }
        Direction facing = station.getBlockState().getValue(MachineBlock.FACING);
        poseStack.pushPose();
        // Turn like the blockstate turns the model, then go to the spike in the north-facing model's pixels.
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-ResearchStationBlockEntity.modelYRotation(facing)));
        poseStack.translate(ResearchStationBlockEntity.SPIKE_X / 16.0D - 0.5D, ResearchStationBlockEntity.SPIKE_Y / 16.0D,
                ResearchStationBlockEntity.SPIKE_Z / 16.0D - 0.5D);
        // Face the front of the station (the side the player stands on), like the journal.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(PART_SCALE, PART_SCALE, PART_SCALE);
        // The part sits above the block, so light it with the air above.
        int light = LevelRenderer.getLightColor(station.getLevel(), station.getBlockPos().above());
        Minecraft.getInstance().getItemRenderer().renderStatic(part, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, poseStack, buffers, station.getLevel(), (int) station.getBlockPos().asLong());
        poseStack.popPose();
    }
}
