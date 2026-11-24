package com.sanguinaire.client.render;

import net.minecraft.world.entity.monster.Phantom;

import java.lang.reflect.Modifier;

import java.lang.reflect.Field;

import com.sanguinaire.player.PlayerMutationProvider;

import com.sanguinaire.player.PlayerMutationData;


import net.minecraft.network.syncher.EntityDataSerializers;

import net.minecraft.network.syncher.EntityDataAccessor;

import net.minecraft.world.entity.monster.Creeper;

import net.minecraft.world.entity.animal.Wolf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.client.LaserBeamClient;
import com.sanguinaire.client.model.FadeHoundModel;
import com.sanguinaire.client.model.GoatmanModel;
import com.sanguinaire.client.model.RottingAbominationModel;
import com.sanguinaire.mutation.MutationAbilities;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.TransformationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Draws transformed players as their creature (design §3.1 step 4), for every client that can see them.
 * <ul>
 *     <li><b>Vanilla-mob forms</b> are drawn with the real vanilla renderer and model: each transformed player gets
 *     a client-only "puppet" entity of that mob type that mirrors the player's position, rotation, walk cycle,
 *     swing and hurt flash every frame.</li>
 *     <li><b>Custom creatures</b> (Goatman, Rotting Abomination, FadeHound) use the mod's own models.</li>
 * </ul>
 * The player's normal model, held items and armor layers are not drawn while transformed. The hitbox matches the
 * creature (see {@link MutationType#getDimensions()}). Guardian lasers being charged are drawn here too, from the player's eyes to the target.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class TransformationRenderer {
    private static final ResourceLocation GOATMAN_TEXTURE = Sanguinaire.id("textures/entity/goatman.png");
    private static final ResourceLocation ABOMINATION_TEXTURE = Sanguinaire.id("textures/entity/rotting_abomination.png");
    /** Nico's Abomination art: the normal skin, the skin while it vomits, and the glowing eyes drawn over either. */
    private static final ResourceLocation ABOMINATION_VOMIT_TEXTURE = Sanguinaire.id("textures/entity/rotting_abomination_vomit.png");
    private static final ResourceLocation ABOMINATION_GLOWING = Sanguinaire.id("textures/entity/rotting_abomination_glowing.png");
    /** How long the vomiting skin stays on after the bile ability: 2 seconds. */
    private static final int ABOMINATION_VOMIT_SKIN_TICKS = 40;
    /** Player id → client game time until which the Abomination wears its vomiting skin. */
    private static final Map<Integer, Long> VOMIT_SKIN_UNTIL = new HashMap<>();
    /** Nico's Fade Hound texture is split in three layers drawn over the same model: */
    private static final ResourceLocation FADEHOUND_TEXTURE = Sanguinaire.id("textures/entity/fadehound_solid.png");
    private static final ResourceLocation FADEHOUND_SEMI = Sanguinaire.id("textures/entity/fadehound_semi.png");
    private static final float FADEHOUND_SEMI_OPACITY = 0.55F;
    private static final ResourceLocation FADEHOUND_GLOWING = Sanguinaire.id("textures/entity/fadehound_glowing.png");
    private static final ResourceLocation GUARDIAN_BEAM = new ResourceLocation("textures/entity/guardian_beam.png");
    private static final RenderType BEAM_RENDER_TYPE = RenderType.entityCutoutNoCull(GUARDIAN_BEAM);

    private record Puppet(EntityType<?> type, LivingEntity entity) {
    }

    /** Puppet entities, by the id of the player they stand in for. */
    private static final Map<Integer, Puppet> PUPPETS = new HashMap<>();
    /** An ability wind-up animation playing on a player's creature model. */
    private static final class Animation {
        final int kind;
        final long startGameTime;
        final int durationTicks;
        boolean started;

        Animation(int kind, long startGameTime, int durationTicks) {
            this.kind = kind;
            this.startGameTime = startGameTime;
            this.durationTicks = durationTicks;
        }

        boolean isActive(long gameTime) {
            return gameTime - startGameTime < durationTicks;
        }
    }

    private static final Map<Integer, Animation> ANIMATIONS = new HashMap<>();
    /** Player id → client game time until which a Wolf player looks angry. */
    private static final Map<Integer, Long> WOLF_RAGE_UNTIL = new HashMap<>();
    /** Creeper's private "powered" data accessor, for the charged look (found once by reflection). */
    @Nullable
    private static EntityDataAccessor<Boolean> creeperPowered;
    private static boolean creeperPoweredLookedUp;
    /** Player id → client game time a Fade Hound split started. */
    private static final Map<Integer, Long> FADE_SPLITS = new HashMap<>();
    private static final int FADE_SPLIT_COPIES = 4;
    private static final float FADE_SPLIT_RADIUS = 1.5F;

    /** Every live puppet, so other client hooks (Warden vision) can recognise them. */
    private static final Set<Entity> PUPPET_SET = Collections.newSetFromMap(new IdentityHashMap<>());

    @Nullable
    private static GoatmanModel goatmanModel;
    @Nullable
    private static RottingAbominationModel abominationModel;
    @Nullable
    private static FadeHoundModel fadeHoundModel;

    private TransformationRenderer() {
    }

    /** Called when the server announces an ability wind-up (Ghast shot, Warden sonic boom). */
    public static void playAnimation(int playerId, int kind, long gameTime, int durationTicks) {
        if (kind == MutationAbilities.ANIMATION_RAVAGER_ROAR) {
            Puppet puppet = PUPPETS.get(playerId);
            if (puppet != null && puppet.entity() instanceof PuppetRavager ravager) {
                ravager.roarTicks = durationTicks;
            }
            return;
        }
        if (kind == MutationAbilities.ANIMATION_FADEHOUND_SPLIT) {
            FADE_SPLITS.put(playerId, gameTime);
            return;
        }
        if (kind == MutationAbilities.ANIMATION_WOLF_RAGE) {
            WOLF_RAGE_UNTIL.put(playerId, gameTime + durationTicks);
            return;
        }
        if (kind == MutationAbilities.ANIMATION_ABOMINATION_VOMIT) {
            VOMIT_SKIN_UNTIL.put(playerId, gameTime + ABOMINATION_VOMIT_SKIN_TICKS); // and the usual particles below
        }
        ANIMATIONS.put(playerId, new Animation(kind, gameTime, durationTicks));
    }

    public static boolean isPuppet(Entity entity) {
        return PUPPET_SET.contains(entity);
    }

    // ---- Player rendering ------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        MutationType type = TransformationManager.getActive(player);
        if (type == null) {
            return;
        }
        event.setCanceled(true);

        Minecraft minecraft = Minecraft.getInstance();
        boolean hidden = minecraft.player != null && player.isInvisibleTo(minecraft.player);
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        float partialTick = event.getPartialTick();
        int light = event.getPackedLight();

        if (!hidden) {
            EntityType<? extends LivingEntity> appearance = type.getAppearance();
            if (appearance != null) {
                renderPuppet(player, appearance, poseStack, buffers, partialTick, light);
            } else {
                renderCustomCreature(player, type, poseStack, buffers, partialTick, light, 1.0F);
            }
        }
        if (type == MutationType.FADEHOUND) {
            renderFadeSplit(player, poseStack, buffers, partialTick, light);
        }
        renderLaser(player, poseStack, buffers, partialTick);
    }

    private static void renderPuppet(Player player, EntityType<? extends LivingEntity> appearance, PoseStack poseStack,
                                     MultiBufferSource buffers, float partialTick, int light) {
        LivingEntity puppet = getPuppet(player, appearance);
        if (puppet == null) {
            return;
        }
        syncPuppet(player, puppet);
        EntityRenderer<? super LivingEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(puppet);
        float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());
        renderer.render(puppet, yaw, partialTick, poseStack, buffers, light);
    }

    @Nullable
    private static LivingEntity getPuppet(Player player, EntityType<? extends LivingEntity> appearance) {
        Puppet puppet = PUPPETS.get(player.getId());
        if (puppet != null && puppet.type() == appearance && puppet.entity().level() == player.level()) {
            return puppet.entity();
        }
        if (puppet != null) {
            PUPPET_SET.remove(puppet.entity());
        }
        LivingEntity entity = createPuppetEntity(appearance, player.level());
        if (entity == null) {
            return null;
        }
        entity.setSilent(true);
        entity.noPhysics = true;
        PUPPETS.put(player.getId(), new Puppet(appearance, entity));
        PUPPET_SET.add(entity);
        return entity;
    }

    /**
     * Guardians animate their tail and spikes from fields that only advance while the mob itself ticks, and puppets
     * never tick. They get small subclasses that drive the same animation from the player's movement instead.
     */
    @Nullable
    private static LivingEntity createPuppetEntity(EntityType<? extends LivingEntity> appearance, Level level) {
        if (appearance == EntityType.GUARDIAN) {
            return new PuppetGuardian(level);
        }
        if (appearance == EntityType.ELDER_GUARDIAN) {
            return new PuppetElderGuardian(level);
        }
        if (appearance == EntityType.RAVAGER) {
            return new PuppetRavager(level);
        }
        return appearance.create(level);
    }

    /** Copies everything the vanilla renderer reads from the player onto the puppet. */
    private static void syncPuppet(Player player, LivingEntity puppet) {
        puppet.setPos(player.getX(), player.getY(), player.getZ());
        puppet.xo = player.xo;
        puppet.yo = player.yo;
        puppet.zo = player.zo;
        puppet.xOld = player.xOld;
        puppet.yOld = player.yOld;
        puppet.zOld = player.zOld;
        puppet.setYRot(player.getYRot());
        puppet.yRotO = player.yRotO;
        if (puppet instanceof Phantom) {
            // The Phantom renderer tilts the body by its pitch with the opposite sign to a player's view pitch
            // (positive = nose up), so the model looked down when the player looked up.
            puppet.setXRot(-player.getXRot());
            puppet.xRotO = -player.xRotO;
        } else {
            puppet.setXRot(player.getXRot());
            puppet.xRotO = player.xRotO;
        }
        puppet.yBodyRot = player.yBodyRot;
        puppet.yBodyRotO = player.yBodyRotO;
        puppet.yHeadRot = player.yHeadRot;
        puppet.yHeadRotO = player.yHeadRotO;
        puppet.tickCount = player.tickCount;
        puppet.setOnGround(player.onGround());
        puppet.attackAnim = player.attackAnim;
        puppet.oAttackAnim = player.oAttackAnim;
        puppet.swinging = player.swinging;
        puppet.swingTime = player.swingTime;
        puppet.hurtTime = player.hurtTime;
        puppet.hurtDuration = player.hurtDuration;
        puppet.setInvisible(player.isInvisible());
        if (puppet instanceof Mob mob) {
            // Zombie-type models raise their arms while aggressive; show it while the player is attacking.
            mob.setAggressive(player.swinging);
        }
        applyAnimation(player, puppet);
    }

    /** Mirrors the mobs' own attack tells: the Ghast's angry shooting face and the Warden's sonic-boom charge. */
    private static void applyAnimation(Player player, LivingEntity puppet) {
        Animation animation = ANIMATIONS.get(player.getId());
        long gameTime = player.level().getGameTime();
        boolean active = animation != null && animation.isActive(gameTime);
        if (puppet instanceof Ghast ghast) {
            boolean charging = active && animation.kind == MutationAbilities.ANIMATION_GHAST_CHARGE;
            if (ghast.isCharging() != charging) {
                ghast.setCharging(charging);
            }
        } else if (puppet instanceof Warden warden && animation != null
                && animation.kind == MutationAbilities.ANIMATION_WARDEN_SONIC_BOOM) {
            if (active && !animation.started) {
                warden.sonicBoomAnimationState.start(puppet.tickCount);
                animation.started = true;
            } else if (!active) {
                warden.sonicBoomAnimationState.stop();
            }
        }
        if (animation != null && !active) {
            ANIMATIONS.remove(player.getId());
        }
        if (puppet instanceof Wolf wolf) {
            Long until = WOLF_RAGE_UNTIL.get(player.getId());
            boolean raging = until != null && gameTime < until;
            if (!raging) {
                WOLF_RAGE_UNTIL.remove(player.getId());
            }
            wolf.setRemainingPersistentAngerTime(raging ? 100 : 0); // angry texture and snarl
        } else if (puppet instanceof Creeper creeper) {
            EntityDataAccessor<Boolean> powered = creeperPoweredAccessor();
            boolean charged = PlayerMutationProvider.get(player).map(PlayerMutationData::isCreeperCharged).orElse(false);
            if (powered != null && creeper.isPowered() != charged) {
                creeper.getEntityData().set(powered, charged); // the vanilla charged-creeper aura layer
            }
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Boolean> creeperPoweredAccessor() {
        if (creeperPoweredLookedUp) {
            return creeperPowered;
        }
        creeperPoweredLookedUp = true;
        {
            // DATA_IS_POWERED is the first Boolean accessor Creeper declares (DATA_SWELL_DIR, DATA_IS_POWERED,
            // DATA_IS_IGNITED), which holds under any mappings.
            for (Field field : Creeper.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == EntityDataAccessor.class) {
                    try {
                        field.setAccessible(true);
                        EntityDataAccessor<?> accessor = (EntityDataAccessor<?>) field.get(null);
                        if (accessor.getSerializer() == EntityDataSerializers.BOOLEAN) {
                            creeperPowered = (EntityDataAccessor<Boolean>) accessor;
                            break;
                        }
                    } catch (IllegalAccessException ignored) {
                        // keep looking
                    }
                }
            }
        }
        return creeperPowered;
    }

    private static void renderCustomCreature(Player player, MutationType type, PoseStack poseStack, MultiBufferSource buffers,
                                             float partialTick, int light, float alpha) {
        EntityModel<Player> model;
        ResourceLocation texture;
        switch (type) {
            case GOATMAN -> {
                if (goatmanModel == null) {
                    goatmanModel = new GoatmanModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModModelLayers.GOATMAN));
                }
                model = goatmanModel;
                texture = GOATMAN_TEXTURE;
            }
            case ROTTING_ABOMINATION -> {
                if (abominationModel == null) {
                    abominationModel = new RottingAbominationModel(
                            Minecraft.getInstance().getEntityModels().bakeLayer(ModModelLayers.ROTTING_ABOMINATION));
                }
                model = abominationModel;
                Long vomitUntil = VOMIT_SKIN_UNTIL.get(player.getId());
                boolean vomiting = vomitUntil != null && player.level().getGameTime() < vomitUntil;
                if (vomitUntil != null && !vomiting) {
                    VOMIT_SKIN_UNTIL.remove(player.getId());
                }
                texture = vomiting ? ABOMINATION_VOMIT_TEXTURE : ABOMINATION_TEXTURE;
            }
            case FADEHOUND -> {
                if (fadeHoundModel == null) {
                    fadeHoundModel = new FadeHoundModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModModelLayers.FADEHOUND));
                }
                model = fadeHoundModel;
                texture = FADEHOUND_TEXTURE;
            }
            default -> {
                return;
            }
        }

        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float ageInTicks = player.tickCount + partialTick;
        float limbSwingAmount = Math.min(player.walkAnimation.speed(partialTick), 1.0F);
        float limbSwing = player.walkAnimation.position(partialTick);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        model.attackTime = player.getAttackAnim(partialTick);
        model.riding = false;
        model.young = false;
        model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, headYaw - bodyYaw, headPitch);
        boolean ghost = alpha < 1.0F;
        int overlay = ghost ? OverlayTexture.NO_OVERLAY : LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        RenderType bodyType = ghost ? RenderType.entityTranslucent(texture) : model.renderType(texture);
        // Ghosts are tinted towards a cold violet so they read as shadows rather than real hounds.
        float tintR = ghost ? 0.62F : 1.0F;
        float tintG = ghost ? 0.5F : 1.0F;
        model.renderToBuffer(poseStack, buffers.getBuffer(bodyType), light, overlay, tintR, tintG, 1.0F, alpha);
        if (type == MutationType.ROTTING_ABOMINATION) {
            // Glowing eyes, full bright like an Enderman's.
            model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(ABOMINATION_GLOWING)), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, alpha, alpha, alpha, 1.0F);
        }
        if (type == MutationType.FADEHOUND) {
            // Semi-transparent layer (the shadowy parts), blended over the solid body at a fixed opacity.
            model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityTranslucent(FADEHOUND_SEMI)), light, overlay,
                    tintR, tintG, 1.0F, alpha * FADEHOUND_SEMI_OPACITY);
            // Glowing layer, like an Enderman's eyes: full brightness in the dark, additive (so fading = darkening).
            model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(FADEHOUND_GLOWING)), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, alpha, alpha, alpha, 1.0F);
        }
        poseStack.popPose();
    }

    // ---- Fade Hound split -------------------------------------------------------------------------------------

    /**
     * Where shadow {@code copy} sits, relative to the hound, {@code progress} (0..1) through the split: the four
     * shadows burst outward, slowing as they go, while the whole ring turns.
     */
    public static Vec3 fadeSplitOffset(float progress, float bodyYaw, int copy) {
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        float radius = FADE_SPLIT_RADIUS * eased;
        float angle = (bodyYaw + 45.0F + copy * (360.0F / FADE_SPLIT_COPIES) + progress * 140.0F) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(angle) * radius, 0.0D, Mth.cos(angle) * radius);
    }

    /** True while the given player is still shattered into shadows. */
    public static boolean isFadeSplitting(int playerId, long gameTime) {
        Long start = FADE_SPLITS.get(playerId);
        return start != null && gameTime - start < MutationAbilities.FADE_SPLIT_TICKS;
    }

    /** Progress (0..1) of a split, or -1 if none is running. */
    public static float fadeSplitProgress(int playerId, long gameTime, float partialTick) {
        Long start = FADE_SPLITS.get(playerId);
        if (start == null) {
            return -1.0F;
        }
        float progress = (gameTime - start + partialTick) / MutationAbilities.FADE_SPLIT_TICKS;
        return progress >= 1.0F ? -1.0F : Math.max(0.0F, progress);
    }

    /**
     * Like the Illusioner's mirror trick, but fleeting: four translucent violet copies of the hound scatter in a
     * spinning ring, jittering and flickering as they fade to nothing.
     */
    private static void renderFadeSplit(Player player, PoseStack poseStack, MultiBufferSource buffers, float partialTick, int light) {
        long gameTime = player.level().getGameTime();
        float progress = fadeSplitProgress(player.getId(), gameTime, partialTick);
        if (progress < 0.0F) {
            if (FADE_SPLITS.containsKey(player.getId()) && !isFadeSplitting(player.getId(), gameTime)) {
                FADE_SPLITS.remove(player.getId());
            }
            return;
        }
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float fade = (1.0F - progress);
        long tick = gameTime;
        for (int copy = 0; copy < FADE_SPLIT_COPIES; copy++) {
            // Each shadow flickers and twitches on its own rhythm, changing every tick.
            RandomSource noise = RandomSource.create(player.getId() * 31L + copy * 7919L + tick * 104729L);
            float flicker = 0.55F + 0.45F * noise.nextFloat();
            float alpha = Mth.clamp(0.8F * fade * fade * flicker, 0.0F, 0.95F);
            if (alpha < 0.02F) {
                continue;
            }
            Vec3 offset = fadeSplitOffset(progress, bodyYaw, copy);
            double jitter = 0.14D * fade;
            poseStack.pushPose();
            poseStack.translate(offset.x + (noise.nextDouble() - 0.5D) * jitter,
                    offset.y + (noise.nextDouble() - 0.5D) * jitter * 0.5D,
                    offset.z + (noise.nextDouble() - 0.5D) * jitter);
            renderCustomCreature(player, MutationType.FADEHOUND, poseStack, buffers, partialTick, light, alpha);
            poseStack.popPose();
        }
    }

    // ---- Guardian laser --------------------------------------------------------------------------------------

    /** Draws a Guardian-style beam that brightens from purple to yellow as the charge completes. */
    private static void renderLaser(Player player, PoseStack poseStack, MultiBufferSource buffers, float partialTick) {
        LaserBeamClient.Beam beam = LaserBeamClient.get(player.getId());
        Level level = player.level();
        if (beam == null) {
            return;
        }
        Entity target = level.getEntity(beam.targetId());
        if (target == null) {
            return;
        }
        float charge = beam.progress(level.getGameTime(), partialTick);
        Vec3 origin = new Vec3(0.0D, player.getEyeHeight(), 0.0D);
        Vec3 playerPos = player.getPosition(partialTick);
        Vec3 targetEyes = target.getPosition(partialTick).add(0.0D, target.getEyeHeight(), 0.0D).subtract(playerPos);
        Vec3 delta = targetEyes.subtract(origin);
        float length = (float) delta.length();
        if (length < 1.0E-3F) {
            return;
        }
        Vec3 direction = delta.normalize();
        float pitch = (float) Math.acos(direction.y);
        float yaw = (float) Math.atan2(direction.z, direction.x);

        poseStack.pushPose();
        poseStack.translate(origin.x, origin.y, origin.z);
        poseStack.mulPose(Axis.YP.rotation((Mth.PI / 2.0F) - yaw));
        poseStack.mulPose(Axis.XP.rotation(pitch));

        float time = level.getGameTime() + partialTick;
        float spin = time * 0.05F * -1.5F;
        float brightness = charge * charge;
        int red = 64 + (int) (brightness * 191.0F);
        int green = 32 + (int) (brightness * 191.0F);
        int blue = 128 - (int) (brightness * 64.0F);
        float width = 0.2F;
        float v0 = -1.0F + time * 0.05F % 1.0F;
        float v1 = length * 2.5F + v0;

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        VertexConsumer consumer = buffers.getBuffer(BEAM_RENDER_TYPE);
        for (int plane = 0; plane < 2; plane++) {
            float angle = spin + plane * (Mth.PI / 2.0F);
            float dx = Mth.cos(angle) * width;
            float dz = Mth.sin(angle) * width;
            beamVertex(consumer, matrix, normal, -dx, length, -dz, red, green, blue, 0.4999F, v1);
            beamVertex(consumer, matrix, normal, -dx, 0.0F, -dz, red, green, blue, 0.4999F, v0);
            beamVertex(consumer, matrix, normal, dx, 0.0F, dz, red, green, blue, 0.0F, v0);
            beamVertex(consumer, matrix, normal, dx, length, dz, red, green, blue, 0.0F, v1);
        }
        poseStack.popPose();
    }

    private static void beamVertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float y, float z,
                                   int red, int green, int blue, float u, float v) {
        consumer.vertex(matrix, x, y, z).color(red, green, blue, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    // ---- Puppet upkeep ---------------------------------------------------------------------------------------

    /** Advances each puppet's walk cycle exactly like a player's, and forgets puppets that are no longer needed. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clearAll();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        Iterator<Map.Entry<Integer, Puppet>> iterator = PUPPETS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Puppet> entry = iterator.next();
            Entity owner = minecraft.level.getEntity(entry.getKey());
            if (!(owner instanceof Player player) || !TransformationManager.isTransformed(player)) {
                PUPPET_SET.remove(entry.getValue().entity());
                iterator.remove();
                continue;
            }
            float moved = (float) Mth.length(player.getX() - player.xo, 0.0D, player.getZ() - player.zo);
            LivingEntity puppet = entry.getValue().entity();
            puppet.walkAnimation.update(Math.min(moved * 4.0F, 1.0F), 0.4F);
            if (puppet instanceof Warden warden && player.swinging && player.swingTime == 0) {
                // The Warden's model animates its swing from a keyframe animation, not from the arm swing:
                // start it the tick the player's attack swing begins (vanilla: entity event 4).
                warden.attackAnimationState.start(player.tickCount);
            }
            if (puppet instanceof AnimatedGuardian guardian) {
                tickGuardian(player, guardian.animation());
            }
            if (puppet instanceof PuppetRavager ravager) {
                ravager.tickAnimations(player);
            }
            if (puppet instanceof Phantom phantom && !player.isInvisible()) {
                spawnPhantomWingParticles(player, phantom);
            }
        }
    }

    /**
     * The mycelium specks a Phantom constantly sheds from its wingtips (vanilla Phantom#tick, client side; puppets
     * never tick, so it is repeated here), following the flap of the wings.
     */
    private static void spawnPhantomWingParticles(Player player, Phantom phantom) {
        float flap = Mth.cos((phantom.getUniqueFlapTickOffset() + player.tickCount) * 7.448451F * Mth.DEG_TO_RAD + Mth.PI);
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        float sideX = Mth.cos(yaw) * 1.3F;
        float sideZ = Mth.sin(yaw) * 1.3F;
        float height = 0.3F + flap * 0.45F;
        Level level = player.level();
        level.addParticle(ParticleTypes.MYCELIUM, player.getX() + sideX, player.getY() + height, player.getZ() + sideZ, 0.0D, 0.0D, 0.0D);
        level.addParticle(ParticleTypes.MYCELIUM, player.getX() - sideX, player.getY() + height, player.getZ() - sideZ, 0.0D, 0.0D, 0.0D);
    }

    // ---- Ravager puppet ---------------------------------------------------------------------------------------

    /**
     * The Ravager model animates its bite and roar from tick counters ({@code getAttackTick}, {@code getRoarTick})
     * that only advance while the mob itself ticks. The puppet overrides them with its own counters: a bite starts
     * when the player's attack swing begins (vanilla: entity event 4, 10 ticks), a roar when the roar ability is
     * used (20 ticks). It is never stunned.
     */
    private static final class PuppetRavager extends Ravager {
        private static final int BITE_TICKS = 10;
        private int biteTicks;
        private int roarTicks;

        PuppetRavager(Level level) {
            super(EntityType.RAVAGER, level);
        }

        void tickAnimations(Player player) {
            if (biteTicks > 0) {
                biteTicks--;
            }
            if (roarTicks > 0) {
                roarTicks--;
            }
            if (player.swinging && player.swingTime == 0 && biteTicks == 0) {
                biteTicks = BITE_TICKS;
            }
        }

        @Override
        public int getAttackTick() {
            return biteTicks;
        }

        @Override
        public int getRoarTick() {
            return roarTicks;
        }

        @Override
        public int getStunnedTick() {
            return 0;
        }
    }

    // ---- Guardian puppets ------------------------------------------------------------------------------------

    /** Tail swish and spike state, advanced with the same rules as {@code Guardian#aiStep} on the client. */
    private static final class GuardianAnimation {
        float tail;
        float tailO;
        float tailSpeed;
        float spikes;
        float spikesO;

        void tick(boolean inWater, boolean inWaterOrBubble, boolean moving, RandomSource random) {
            tailO = tail;
            if (!inWater) {
                tailSpeed = 2.0F; // flopping about on land
            } else if (moving) {
                if (tailSpeed < 0.5F) {
                    tailSpeed = 4.0F;
                } else {
                    tailSpeed += (0.5F - tailSpeed) * 0.1F;
                }
            } else {
                tailSpeed += (0.125F - tailSpeed) * 0.2F;
            }
            tail += tailSpeed;

            spikesO = spikes;
            if (!inWaterOrBubble) {
                spikes = random.nextFloat();
            } else if (moving) {
                spikes += (0.0F - spikes) * 0.25F; // spikes fold in while swimming
            } else {
                spikes += (1.0F - spikes) * 0.06F;
            }
        }
    }

    private interface AnimatedGuardian {
        GuardianAnimation animation();
    }

    private static final class PuppetGuardian extends Guardian implements AnimatedGuardian {
        private final GuardianAnimation animation = new GuardianAnimation();

        PuppetGuardian(Level level) {
            super(EntityType.GUARDIAN, level);
        }

        @Override
        public GuardianAnimation animation() {
            return animation;
        }

        @Override
        public float getTailAnimation(float partialTick) {
            return Mth.lerp(partialTick, animation.tailO, animation.tail);
        }

        @Override
        public float getSpikesAnimation(float partialTick) {
            return Mth.lerp(partialTick, animation.spikesO, animation.spikes);
        }
    }

    private static final class PuppetElderGuardian extends ElderGuardian implements AnimatedGuardian {
        private final GuardianAnimation animation = new GuardianAnimation();

        PuppetElderGuardian(Level level) {
            super(EntityType.ELDER_GUARDIAN, level);
        }

        @Override
        public GuardianAnimation animation() {
            return animation;
        }

        @Override
        public float getTailAnimation(float partialTick) {
            return Mth.lerp(partialTick, animation.tailO, animation.tail);
        }

        @Override
        public float getSpikesAnimation(float partialTick) {
            return Mth.lerp(partialTick, animation.spikesO, animation.spikes);
        }
    }

    private static void tickGuardian(Player player, GuardianAnimation animation) {
        double dx = player.getX() - player.xo;
        double dy = player.getY() - player.yo;
        double dz = player.getZ() - player.zo;
        boolean moving = dx * dx + dy * dy + dz * dz > 1.0E-4D;
        boolean inWater = player.isInWater();
        animation.tick(inWater, player.isInWaterOrBubble(), moving, player.getRandom());
        if (moving && inWater && !player.isInvisible()) {
            // The bubble trail vanilla guardians leave while swimming.
            Vec3 look = player.getViewVector(0.0F);
            for (int i = 0; i < 2; i++) {
                player.level().addParticle(ParticleTypes.BUBBLE,
                        player.getRandomX(0.5D) - look.x * 1.5D,
                        player.getRandomY() - look.y * 1.5D,
                        player.getRandomZ(0.5D) - look.z * 1.5D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clearAll();
        }
    }

    private static void clearAll() {
        PUPPETS.clear();
        PUPPET_SET.clear();
        ANIMATIONS.clear();
        FADE_SPLITS.clear();
        LaserBeamClient.clear();
    }
}
