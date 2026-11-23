package com.sanguinaire.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Re-routes everything an entity renderer draws into a single translucent, full-bright, solid-colour layer
 * (used to highlight moving creatures in Warden vision). Only entity-format geometry is redirected; anything else
 * (e.g. name-tag text) is discarded, so vertex formats can never be mixed.
 */
public final class TintedBufferSource implements MultiBufferSource {
    private final VertexConsumer tinted;

    public TintedBufferSource(MultiBufferSource base, ResourceLocation whiteTexture, int red, int green, int blue, int alpha) {
        this.tinted = new TintedConsumer(base.getBuffer(RenderType.entityTranslucentEmissive(whiteTexture)), red, green, blue, alpha);
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return type.format() == DefaultVertexFormat.NEW_ENTITY ? tinted : DiscardingConsumer.INSTANCE;
    }

    private static final class TintedConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int red;
        private final int green;
        private final int blue;
        private final int alpha;

        TintedConsumer(VertexConsumer delegate, int red, int green, int blue, int alpha) {
            this.delegate = delegate;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v,
                           int overlay, int light, float normalX, float normalY, float normalZ) {
            delegate.vertex(x, y, z, red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F, u, v,
                    overlay, LightTexture.FULL_BRIGHT, normalX, normalY, normalZ);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16 & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            // Colour is always forced.
        }

        @Override
        public void unsetDefaultColor() {
            // Colour is always forced.
        }
    }

    /** Swallows geometry that must not go into the tinted layer. */
    private static final class DiscardingConsumer implements VertexConsumer {
        static final DiscardingConsumer INSTANCE = new DiscardingConsumer();

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
