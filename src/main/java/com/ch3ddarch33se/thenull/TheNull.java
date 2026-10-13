package com.ch3ddarch33se.thenull;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ch3ddarch33se.thenull.worldgen.LayeredChunkGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Standalone temporary world provider. No survival portals or custom assets.
 */
public final class TheNull implements ModInitializer {
    public static final String MOD_ID = "thenull";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.parse("thenull:layered"), LayeredChunkGenerator.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var level : server.getAllLevels()) {
                if (level.getChunkSource().getGenerator() instanceof LayeredChunkGenerator layered) {
                    layered.garbageCollectLowerBuffers();
                }
            }
        });
        LOGGER.info("The Null native generation development build");
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
                .executes(context -> NullTeleport.leave(context.getSource())))
            .then(Commands.literal("surface")
                .executes(context -> NullTeleport.surface(context.getSource()))));
    }
}
