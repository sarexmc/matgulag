package com.mathgulag.game;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class GulagCommand {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("mathgulag")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("stop").executes(c -> {
                    GulagManager.end(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Gulag detenido"), true);
                    return 1;
                }))
                .then(Commands.argument("jugadores", EntityArgument.players()).executes(c -> {
                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "jugadores");
                    int n = GulagManager.start(c.getSource().getServer(), targets);
                    c.getSource().sendSuccess(() -> Component.literal("Gulag iniciado con " + n + " jugador(es)"), true);
                    return n;
                })));
    }
}
