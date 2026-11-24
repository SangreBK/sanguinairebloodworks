package com.sanguinaire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
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

    private static int cureHearts(CommandSourceStack source, ServerPlayer target) {
        PlayerMutationProvider.get(target).ifPresent(data -> data.setWeakenedHealth(0.0F));
        ModNetwork.syncMutationData(target);
        source.sendSuccess(() -> Component.translatable("commands." + Sanguinaire.MOD_ID + ".curehearts", target.getDisplayName()), true);
        return 1;
    }
}
