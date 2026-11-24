package com.sanguinaire.client.model;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Nico's Rotting Abomination animations (Blockbench 5.2.2), only renamed to valid Java names. The creature has no
 * legs and walks on its arms:
 * <ul>
 *     <li>IDLE / MOVE_BODY: left arm and torso (the "body" controller).</li>
 *     <li>IDLE_ARMS / MOVE_ARMS: right and back arms (the "arms" controller).</li>
 *     <li>ATTACK (1.125 s, the right arm's blow lands at 0.17 s): replaces both controllers while it plays.</li>
 * </ul>
 */
public final class RottingAbominationAnimations {
    private RottingAbominationAnimations() {
    }

    public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(2.0F).looping()
        .addAnimation("leftarm", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, -12.5F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("lowerleft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-11.7009F, 19.3547F, -32.0021F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(-10.9119F, 20.6467F, -22.7479F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.25F, KeyframeAnimations.degreeVec(-5.1229F, 21.9386F, -13.4936F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-11.7009F, 19.3547F, -32.0021F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("upperleft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(0.0F, 12.5F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.4583F, KeyframeAnimations.degreeVec(0.373F, 4.2555F, 5.0139F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.SCALE, 
            new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.75F, KeyframeAnimations.scaleVec(1.01F, 1.02F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(2.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .build();

    public static final AnimationDefinition MOVE_BODY = AnimationDefinition.Builder.withLength(1.5F).looping()
        .addAnimation("leftarm", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-50.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.25F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.75F, KeyframeAnimations.degreeVec(1.4459F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.5F, KeyframeAnimations.degreeVec(-50.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("leftarm", new AnimationChannel(AnimationChannel.Targets.POSITION, 
            new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, -5.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.4167F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 3.87F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, -5.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("lowerleft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.9167F, KeyframeAnimations.degreeVec(2.3029F, 1.8178F, -12.369F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.5F, KeyframeAnimations.degreeVec(-20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 2.5375F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.8333F, KeyframeAnimations.degreeVec(0.0F, -2.5758F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.SCALE, 
            new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.25F, KeyframeAnimations.scaleVec(1.0F, 1.05F, 1.03F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.3333F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.1F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .build();

    public static final AnimationDefinition ATTACK = AnimationDefinition.Builder.withLength(1.125F)
        .addAnimation("rightarm", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 25.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.1667F, KeyframeAnimations.degreeVec(11.8868F, -37.8893F, 9.1025F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.75F, KeyframeAnimations.degreeVec(12.5964F, -53.5142F, 17.0914F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.125F, KeyframeAnimations.degreeVec(0.0F, 25.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("rightarm", new AnimationChannel(AnimationChannel.Targets.POSITION, 
            new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.25F, KeyframeAnimations.posVec(-2.0F, 0.0F, -3.75F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.posVec(-0.7798F, -0.8605F, -2.1015F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.125F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("lowerright", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-17.5F, 25.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.25F, KeyframeAnimations.degreeVec(-28.611F, -14.8842F, 12.906F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.375F, KeyframeAnimations.degreeVec(-30.2212F, 15.0661F, 13.5767F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-30.2212F, 15.0661F, 13.5767F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.9167F, KeyframeAnimations.degreeVec(-36.0738F, 55.8619F, 7.336F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.125F, KeyframeAnimations.degreeVec(-17.5F, 25.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("lowerright", new AnimationChannel(AnimationChannel.Targets.POSITION, 
            new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.25F, KeyframeAnimations.posVec(-2.5191F, 1.8794F, -5.4023F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.7083F, KeyframeAnimations.posVec(-2.5191F, 1.8794F, -5.4023F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.125F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .build();

    public static final AnimationDefinition IDLE_ARMS = AnimationDefinition.Builder.withLength(2.0F).looping()
        .addAnimation("rightarm", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(32.3753F, -32.191F, -2.3753F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.5417F, KeyframeAnimations.degreeVec(22.9569F, -6.4296F, 1.5525F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.9167F, KeyframeAnimations.degreeVec(20.2532F, -12.6312F, 2.3293F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(32.3753F, -32.191F, -2.3753F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("backarm", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.8333F, KeyframeAnimations.degreeVec(0.0F, -7.5F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.4167F, KeyframeAnimations.degreeVec(3.588F, -1.0759F, -2.7984F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("lowerback", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.875F, KeyframeAnimations.degreeVec(0.3027F, -6.0979F, -13.7239F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.3333F, KeyframeAnimations.degreeVec(-3.5689F, -0.6384F, -9.4502F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("lowerright", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-17.5F, 25.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(-15.1366F, 24.5157F, 5.8786F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.1667F, KeyframeAnimations.degreeVec(-16.8841F, 20.2231F, 1.6004F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-17.5F, 25.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .build();

    public static final AnimationDefinition MOVE_ARMS = AnimationDefinition.Builder.withLength(1.5F).looping()
        .addAnimation("rightarm", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 25.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.5417F, KeyframeAnimations.degreeVec(-2.3777F, 27.3963F, -5.6282F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.125F, KeyframeAnimations.degreeVec(-5.837F, 25.8435F, -1.9811F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 25.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("backarm", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -5.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.25F, KeyframeAnimations.degreeVec(-0.0493F, 10.0F, 2.5333F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.5417F, KeyframeAnimations.degreeVec(4.8801F, -6.5918F, 2.381F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -5.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("lowerback", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-42.9939F, -22.7265F, 24.9043F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.375F, KeyframeAnimations.degreeVec(-3.7254F, -2.9839F, 1.5909F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.75F, KeyframeAnimations.degreeVec(31.23F, -4.3241F, 4.0058F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.5F, KeyframeAnimations.degreeVec(-42.9939F, -22.7265F, 24.9043F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("lowerright", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-17.5F, 25.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.375F, KeyframeAnimations.degreeVec(-15.1366F, 24.5157F, 5.8786F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.875F, KeyframeAnimations.degreeVec(-16.8841F, 20.2231F, 1.6004F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.5F, KeyframeAnimations.degreeVec(-17.5F, 25.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .build();
}
