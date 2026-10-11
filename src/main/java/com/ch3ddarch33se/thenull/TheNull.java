package com.ch3ddarch33se.thenull;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sift extension prototype for The Null. No custom mobs, items or blocks.
 */
public final class TheNull implements ModInitializer {
    public static final String MOD_ID = "thenull";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("The Null 0.0.1 initialization (Sift Backport compatibility prototype)");
        var container = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
        if (!ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(MOD_ID, "null_extension"),
                container, Component.literal("The Null — Sift extension"), PackActivationType.ALWAYS_ENABLED)) {
            throw new IllegalStateException("The Null's required Sift extension data pack is missing");
        }
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
