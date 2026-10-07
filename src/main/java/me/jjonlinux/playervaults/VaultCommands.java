package me.jjonlinux.playervaults;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// /pv                         open your vault 1
// /pv <vault>                 open one of your vaults
// /pv <player> [vault]        (ops) open another player's vault, online or offline
// /pv give [player]           (ops) give the "Player Vaults" item
public final class VaultCommands {
    private VaultCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pv")
                .executes(ctx -> openOwn(ctx, 1))
                .then(Commands.argument("vault", IntegerArgumentType.integer(1))
                        .executes(ctx -> openOwn(ctx, IntegerArgumentType.getInteger(ctx, "vault"))))
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(VaultCommands::giveToSelf)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(VaultCommands::giveToTarget)))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> openOther(ctx, 1))
                        .then(Commands.argument("vault", IntegerArgumentType.integer(1))
                                .executes(ctx -> openOther(ctx, IntegerArgumentType.getInteger(ctx, "vault"))))));
    }

    private static int openOwn(CommandContext<CommandSourceStack> ctx, int number) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        VaultManager.open(player, player.getUUID(), player.getName().getString(), number);
        return 1;
    }

    private static int openOther(CommandContext<CommandSourceStack> ctx, int number) throws CommandSyntaxException {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        var profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) {
            ctx.getSource().sendFailure(Component.literal("Pick exactly one player."));
            return 0;
        }
        var profile = profiles.iterator().next();
        VaultManager.open(viewer, profile.id(), profile.name(), number);
        return 1;
    }

    private static int giveToSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        give(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int giveToTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        give(target);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Gave the Player Vaults item to " + target.getName().getString()), true);
        return 1;
    }

    private static void give(ServerPlayer target) {
        var stack = VaultItems.createVaultItem();
        if (!target.getInventory().add(stack)) {
            target.drop(stack, false);
        }
    }
}
