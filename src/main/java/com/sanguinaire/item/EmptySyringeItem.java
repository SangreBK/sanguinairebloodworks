package com.sanguinaire.item;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.blood.BloodType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The Empty Syringe. Besides holding brewed serums, it draws blood:
 * <ul>
 *     <li>Sneak + use on a creature: 1 damage to it, and the syringe fills with its blood ({@link BloodType}).</li>
 *     <li>Sneak + use on the air: you draw your own blood (1 damage to yourself), labelled with your name.</li>
 * </ul>
 * One syringe from the stack is filled; the sample goes to the hand if it was the last one, otherwise into the
 * inventory (or is dropped if full). In creative the syringe is kept.
 * <p>
 * Careful: after {@code interactLivingEntity}, vanilla clears the hand if the stack it passed in has become empty,
 * which would delete a sample placed there. So the last syringe is never shrunk; the hand is simply given the
 * sample instead.
 */
public class EmptySyringeItem extends Item {
    private static final float DRAW_DAMAGE = 1.0F;

    public EmptySyringeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!player.isSecondaryUseActive() || !target.isAlive()) {
            return InteractionResult.PASS;
        }
        BloodType type = BloodType.of(target);
        if (type == null) {
            if (!player.level().isClientSide()) {
                player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".no_blood")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide());
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        target.hurt(player.damageSources().playerAttack(player), DRAW_DAMAGE);
        String donor = target instanceof Player donorPlayer ? donorPlayer.getGameProfile().getName() : null;
        fill(player, hand, stack, BloodSampleItem.create(type, donor), target);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        player.hurt(player.damageSources().generic(), DRAW_DAMAGE);
        fill(player, hand, stack, BloodSampleItem.create(BloodType.HUMANOID, player.getGameProfile().getName()), player);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    /** Self-draw is only for the open air: sneak-using the syringe on a block does nothing. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return context.getPlayer() != null && context.getPlayer().isSecondaryUseActive() ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    private static void fill(Player player, InteractionHand hand, ItemStack stack, ItemStack sample, LivingEntity donor) {
        if (player.getAbilities().instabuild) {
            if (!player.getInventory().contains(sample)) {
                player.getInventory().add(sample);
            }
        } else if (stack.getCount() <= 1) {
            player.setItemInHand(hand, sample); // the used stack object stays non-empty, so vanilla leaves the hand alone
        } else {
            stack.shrink(1);
            if (!player.getInventory().add(sample)) {
                player.drop(sample, false);
            }
        }
        Level level = player.level();
        level.playSound(null, donor.getX(), donor.getY(), donor.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8F, 0.6F);
        level.playSound(null, donor.getX(), donor.getY(), donor.getZ(), SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 0.6F, 0.7F);
        if (level instanceof ServerLevel serverLevel) {
            spawnPunctureBlood(serverLevel, player, donor);
        }
    }

    /**
     * A spurt of blood where the needle went in. For a creature that is the side of its body facing the player; for
     * a self-draw it is the forearm held out in front of the camera, so it can be seen in first person.
     */
    private static void spawnPunctureBlood(ServerLevel level, Player player, LivingEntity donor) {
        Vec3 point;
        double spread;
        if (donor == player) {
            Vec3 look = player.getViewVector(1.0F);
            Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
            point = player.getEyePosition().add(look.scale(0.7D)).add(side.scale(0.25D)).subtract(0.0D, 0.45D, 0.0D);
            spread = 0.08D;
        } else {
            Vec3 center = new Vec3(donor.getX(), donor.getY(0.6D), donor.getZ());
            Vec3 toPlayer = player.getEyePosition().subtract(center);
            Vec3 flat = new Vec3(toPlayer.x, 0.0D, toPlayer.z);
            point = flat.lengthSqr() < 1.0E-4D ? center : center.add(flat.normalize().scale(donor.getBbWidth() * 0.5D));
            spread = Math.min(0.25D, donor.getBbWidth() * 0.2D);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                point.x, point.y, point.z, 18, spread, spread, spread, 0.08D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState()),
                point.x, point.y, point.z, 8, spread, spread, spread, 0.06D);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.5F, 0.02F, 0.02F), 1.0F),
                point.x, point.y, point.z, 10, spread * 1.5D, spread * 1.5D, spread * 1.5D, 0.0D);
    }
}
