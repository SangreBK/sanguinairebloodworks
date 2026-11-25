package com.sanguinaire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sanguinaire.conscript.ConscriptHunts;
import net.minecraft.core.BlockPos;
import com.sanguinaire.settings.SanguinaireSettings;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.network.ModNetwork;
import com.sanguinaire.player.PlayerMutationProvider;
import com.sanguinaire.research.ResearchManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Debug / admin commands (operator permission level 2):
 * <ul>
 *     <li>{@code /sanguinaire grantallresearch [player]}: researches every specimen and unlocks every Torn Page entry.</li>
 *     <li>{@code /sanguinaire revokeallresearch [player]}: forgets all research and Torn Page entries.</li>
 *     <li>{@code /sanguinaire curehearts [player]}: clears all Weakened Hearts.</li>
 *     <li>{@code /sanguinaire enablefirstperson <true|false>}: whether transformed players may use first person.</li>
 *     <li>{@code /sanguinaire allowtransformationitems <true|false>}: whether transformed players may hold items and
 *     scroll their hotbar.</li>
 * </ul>
 * The player argument accepts a name or a selector and defaults to the command's user.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class SanguinaireCommand {
    private SanguinaireCommand() {
    }

    @FunctionalInterface
    private interface PlayerAction {
        int run(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Sanguinaire.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(playerCommand("grantallresearch", SanguinaireCommand::grantAllResearch))
                .then(playerCommand("revokeallresearch", SanguinaireCommand::revokeAllResearch))
                .then(playerCommand("curehearts", SanguinaireCommand::cureHearts))
                .then(Commands.literal("enablefirstperson")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> setFirstPerson(context.getSource(), BoolArgumentType.getBool(context, "value")))))
                .then(Commands.literal("conscripts")
                        .then(playerCommand("status", SanguinaireCommand::huntStatus))
                        .then(playerCommand("hunt", SanguinaireCommand::huntNow))
                        .then(Commands.literal("hunted")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(context -> setHunted(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                        BoolArgumentType.getBool(context, "value"))))))
                        .then(Commands.literal("notoriety")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("stars", IntegerArgumentType.integer(0, ConscriptHunts.MAX_NOTORIETY))
                                                .executes(context -> setNotoriety(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "stars"))))))
                        .then(Commands.literal("caravan")
                                .executes(context -> spawnCaravan(context.getSource()))))
                .then(Commands.literal("allowtransformationitems")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> setItems(context.getSource(), BoolArgumentType.getBool(context, "value"))))));
    }

    private static int setFirstPerson(CommandSourceStack source, boolean value) {
        SanguinaireSettings.get(source.getServer()).setFirstPersonAllowed(value);
        ModNetwork.syncSettingsToAll(source.getServer());
        source.sendSuccess(() -> Component.translatable("commands." + Sanguinaire.MOD_ID + ".enablefirstperson." + value), true);
        return 1;
    }

    private static int setItems(CommandSourceStack source, boolean value) {
        SanguinaireSettings.get(source.getServer()).setTransformationItemsAllowed(value);
        ModNetwork.syncSettingsToAll(source.getServer());
        source.sendSuccess(() -> Component.translatable("commands." + Sanguinaire.MOD_ID + ".allowtransformationitems." + value), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> playerCommand(String name, PlayerAction action) {
        return Commands.literal(name)
                .executes(context -> action.run(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> action.run(context.getSource(), EntityArgument.getPlayer(context, "player"))));
    }

    private static int grantAllResearch(CommandSourceStack source, ServerPlayer target) {
        ResearchManager.grantAll(target);
        source.sendSuccess(() -> Component.translatable("commands." + Sanguinaire.MOD_ID + ".grantallresearch", target.getDisplayName()), true);
        return 1;
    }

    private static int revokeAllResearch(CommandSourceStack source, ServerPlayer target) {
        ResearchManager.revokeAll(target);
        source.sendSuccess(() -> Component.translatable("commands." + Sanguinaire.MOD_ID + ".revokeallresearch", target.getDisplayName()), true);
        return 1;
    }

    private static int huntStatus(CommandSourceStack source, ServerPlayer target) {
        boolean hunted = ConscriptHunts.isHunted(target);
        int seconds = ConscriptHunts.getHuntTimer(target) / 20;
        source.sendSuccess(() -> Component.translatable("commands." + Sanguinaire.MOD_ID + ".conscripts.status." + hunted,
                target.getDisplayName(), ConscriptHunts.getNotoriety(target), seconds), false);
        return 1;
    }

    private static int huntNow(CommandSourceStack source, ServerPlayer target) {
        ConscriptHunts.huntNow(target);
        source.sendSuccess(() -> Component.translatable("commands." + Sanguinaire.MOD_ID + ".conscripts.hunt", target.getDisplayName()), true);
        return 1;
    }

    private static int setHunted(CommandSourceStack source, ServerPlayer target, boolean value) {
        ConscriptHunts.setHunted(target, value);
        source.sendSuccess(() -> Component.translatable("commands." + Sanguinaire.MOD_ID + ".conscripts.hunted." + value,
                target.getDisplayName()), true);
        return 1;
    }

    private static int setNotoriety(CommandSourceStack source, ServerPlayer target, int stars) {
        ConscriptHunts.setNotoriety(target, stars);
        source.sendSuccess(() -> Component.translatable("commands." + Sanguinaire.MOD_ID + ".conscripts.notoriety",
                target.getDisplayName(), stars), true);
        return 1;
    }

    private static int spawnCaravan(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = ConscriptHunts.surfaceAt(player.serverLevel(), player.getBlockX() + 12, player.getBlockZ());
        ConscriptHunts.spawnCaravan(player.serverLevel(), pos != null ? pos : player.blockPosition());
        source.sendSuccess(() -> Component.translatable("commands." + Sanguinaire.MOD_ID + ".conscripts.caravan"), true);
        return 1;
    }

    private static int cureHearts(CommandSourceStack source, ServerPlayer target) {
        PlayerMutationProvider.get(target).ifPresent(data -> data.setWeakenedHealth(0.0F));
        ModNetwork.syncMutationData(target);
        source.sendSuccess(() -> Component.translatable("commands." + Sanguinaire.MOD_ID + ".curehearts", target.getDisplayName()), true);
        return 1;
    }
}
