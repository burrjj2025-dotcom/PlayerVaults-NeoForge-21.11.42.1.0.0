package me.jjonlinux.playervaults;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Hooks into the game: starts/stops the vault system with the server, registers
// /pv, opens vault 1 when the Player Vaults item is right-clicked, and handles
// the "type a vault number in chat" prompt.
@EventBusSubscriber(modid = PlayerVaults.MODID)
public final class VaultEvents {
    private record Pending(UUID owner, String ownerName) {}

    // Players who clicked the middle button and are now expected to type a number.
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private VaultEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        VaultManager.setServer(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        VaultManager.saveAll();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        VaultManager.clear();
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        VaultManager.tick();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        VaultCommands.register(event.getDispatcher());
    }

    // Right-clicking the Player Vaults item opens your own vault 1.
    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!VaultItems.isVaultItem(event.getItemStack())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        VaultManager.open(player, player.getUUID(), player.getName().getString(), 1);
    }

    // Called (a tick later) when the middle button is pressed.
    public static void startPrompt(ServerPlayer player, UUID owner, String ownerName) {
        player.closeContainer();
        PENDING.put(player.getUUID(), new Pending(owner, ownerName));
        player.sendSystemMessage(Component.literal(
                "Type the vault number you want to open (1-" + Config.MAX_VAULT_NUMBER.get() + "), or type cancel."));
    }

    // While a prompt is pending, the player's next chat message is the answer
    // instead of being sent to everyone.
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        event.setCanceled(true);

        String text = event.getRawText().trim();
        if (text.equalsIgnoreCase("cancel")) {
            VaultManager.defer(() -> player.sendSystemMessage(Component.literal("Cancelled.")));
            return;
        }

        int number;
        try {
            number = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            VaultManager.defer(() -> player.sendSystemMessage(
                    Component.literal("That isn't a number. Use /pv or the Player Vaults item to try again.")));
            return;
        }
        VaultManager.defer(() -> VaultManager.open(player, pending.owner(), pending.ownerName(), number));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        PENDING.remove(player.getUUID());
        if (player.containerMenu instanceof VaultMenu menu) {
            menu.releaseOnce();
        }
    }
}
