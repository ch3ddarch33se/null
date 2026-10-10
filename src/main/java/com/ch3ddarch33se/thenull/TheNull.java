package com.ch3ddarch33se.thenull;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal starter for The Null. No custom registries, mobs, items or blocks.
 */
public final class TheNull implements ModInitializer {
    public static final String MOD_ID = "thenull";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("The Null 0.0.1 initialization (Sift Backport compatibility prototype)");
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("null")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.literal("enter")
                .executes(context -> NullTeleport.enter(context.getSource())))
            .then(Commands.literal("leave")
                .executes(context -> NullTeleport.leave(context.getSource()))));
    }
}
