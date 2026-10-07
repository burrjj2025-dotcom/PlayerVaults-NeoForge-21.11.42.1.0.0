package me.jjonlinux.playervaults;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

// Keeps track of every vault that is currently open, loads them from the player
// files, and writes them back. Everything here runs on the server thread.
public final class VaultManager {
    private static final int AUTOSAVE_TICKS = 20 * 60; // once a minute, only vaults that changed

    private static MinecraftServer server;

    // A player's file is loaded once and shared by all of their open vaults.
    private static final Map<UUID, PlayerVaultData> DATA = new HashMap<>();
    private static final Map<UUID, Integer> DATA_USERS = new HashMap<>();
    private static final Map<String, OpenVault> OPEN = new HashMap<>();

    // Actions to run at the start of the next tick (see defer).
    private static final Queue<Runnable> DEFERRED = new ConcurrentLinkedQueue<>();
    private static int ticks;

    private VaultManager() {}

    // One vault that is open for at least one viewer. If an op and the owner open
    // the same vault, they share this object, so they see each other's changes.
    public static final class OpenVault {
        public final UUID owner;
        public final int number;
        public final PlayerVaultData data;
        public final VaultContainer container = new VaultContainer(VaultMenu.SIZE);
        int viewers;
        // True when the file should be written even if the player changes nothing:
        // a brand new vault section, or a repair of something wrong in the file.
        boolean needsSave;

        OpenVault(UUID owner, int number, PlayerVaultData data) {
            this.owner = owner;
            this.number = number;
            this.data = data;
        }
    }

    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }

    // Runs the action at the start of the next server tick. Used when a click
    // inside a menu needs to open a different menu: doing that while the click is
    // still being processed can confuse the game.
    public static void defer(Runnable action) {
        DEFERRED.add(action);
    }

    public static void tick() {
        Runnable action;
        while ((action = DEFERRED.poll()) != null) {
            try {
                action.run();
            } catch (RuntimeException ex) {
                PlayerVaults.LOGGER.error("Error in a deferred vault action", ex);
            }
        }

        if (++ticks >= AUTOSAVE_TICKS) {
            ticks = 0;
            for (OpenVault vault : new ArrayList<>(OPEN.values())) {
                if (vault.container.isDirty()) {
                    save(vault);
                }
            }
        }
    }

    public static void saveAll() {
        for (OpenVault vault : new ArrayList<>(OPEN.values())) {
            save(vault);
        }
    }

    public static void clear() {
        OPEN.clear();
        DATA.clear();
        DATA_USERS.clear();
        DEFERRED.clear();
        server = null;
    }

    private static String key(UUID owner, int number) {
        return owner + "#" + number;
    }

    // Opens vault <number> belonging to <owner> for <viewer>.
    public static void open(ServerPlayer viewer, UUID owner, String ownerName, int number) {
        int max = Config.MAX_VAULT_NUMBER.get();
        if (number < 1 || number > max) {
            viewer.sendSystemMessage(Component.literal("Vault numbers go from 1 to " + max + "."));
            return;
        }

        OpenVault vault = OPEN.get(key(owner, number));
        if (vault == null) {
            try {
                vault = load(owner, ownerName, number);
            } catch (RuntimeException ex) {
                PlayerVaults.LOGGER.error("Could not load vault {} of {}", number, owner, ex);
                viewer.sendSystemMessage(Component.literal("Couldn't load that vault. Check the server log."));
                return;
            }
        }

        boolean ownVault = viewer.getUUID().equals(owner);
        Component title = Component.literal(ownVault ? "Vault " + number : "Vault " + number + " - " + vault.data.name);

        final OpenVault toOpen = vault;
        OptionalInt result = viewer.openMenu(
                new SimpleMenuProvider((id, inventory, player) -> new VaultMenu(id, inventory, toOpen), title));

        // If the menu never opened, nobody is viewing it; drop it again.
        if (result.isEmpty() && toOpen.viewers == 0) {
            unload(toOpen);
        }
    }

    // Called by VaultMenu when a viewer closes it.
    static void release(OpenVault vault) {
        vault.viewers--;
        if (vault.viewers > 0) {
            return;
        }
        vault.viewers = 0;
        if (vault.container.isDirty() || vault.needsSave) {
            save(vault);
        }
        unload(vault);
    }

    private static void unload(OpenVault vault) {
        OPEN.remove(key(vault.owner, vault.number), vault);
        DATA_USERS.computeIfPresent(vault.owner, (id, users) -> users <= 1 ? null : users - 1);
        if (!DATA_USERS.containsKey(vault.owner)) {
            DATA.remove(vault.owner);
        }
    }

    private static OpenVault load(UUID owner, String ownerName, int number) {
        PlayerVaultData data = DATA.get(owner);
        if (data == null) {
            data = PlayerVaultData.load(owner, ownerName);
            DATA.put(owner, data);
        }
        DATA_USERS.merge(owner, 1, Integer::sum);
        if (ownerName != null && !ownerName.isBlank()) {
            data.name = ownerName;
        }

        OpenVault vault = new OpenVault(owner, number, data);
        JsonArray stored = data.vaults.get(number);
        if (stored == null) {
            // First time this vault is opened: it gets its own section in the file.
            data.vaults.put(number, new JsonArray());
            vault.needsSave = true;
        } else {
            readItems(vault, stored);
        }

        vault.container.setItem(VaultMenu.PREVIOUS_SLOT, VaultItems.previousButton(number));
        vault.container.setItem(VaultMenu.CHAT_SLOT, VaultItems.chatButton());
        vault.container.setItem(VaultMenu.NEXT_SLOT, VaultItems.nextButton(number));
        vault.container.clearDirty();

        OPEN.put(key(owner, number), vault);
        return vault;
    }

    private static RegistryOps<JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
    }

    // Anything in the stored vault that can't be turned into an item (a typo, or an
    // item from a mod that was removed) is dropped, leaving an empty slot. The vault
    // is then marked so the file is rewritten without the bad entry.
    private static void readItems(OpenVault vault, JsonArray stored) {
        RegistryOps<JsonElement> ops = ops();
        for (JsonElement element : stored) {
            if (!readItem(vault, element, ops)) {
                vault.needsSave = true;
            }
        }
    }

    private static boolean readItem(OpenVault vault, JsonElement element, RegistryOps<JsonElement> ops) {
        try {
            if (!element.isJsonObject()) {
                PlayerVaults.LOGGER.warn("Vault {} of {}: dropped an entry that isn't an object.",
                        vault.number, vault.data.name);
                return false;
            }
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("slot") || !entry.has("item")) {
                PlayerVaults.LOGGER.warn("Vault {} of {}: dropped an entry without \"slot\" and \"item\".",
                        vault.number, vault.data.name);
                return false;
            }

            DataResult<ItemStack> parsed = ItemStack.CODEC.parse(ops, entry.get("item"));
            if (parsed.result().isEmpty()) {
                PlayerVaults.LOGGER.warn("Vault {} of {}: dropped an item that could not be read "
                        + "(wrong format, or from a mod that is no longer installed).",
                        vault.number, vault.data.name);
                return false;
            }

            int slot = entry.get("slot").getAsInt();
            boolean slotUsable = slot >= 0 && slot < VaultMenu.SIZE
                    && !VaultMenu.isButtonSlot(slot)
                    && vault.container.getItem(slot).isEmpty();
            if (!slotUsable) {
                // Wrong or taken slot: move the item to the first free one.
                slot = firstFreeSlot(vault.container);
                vault.needsSave = true;
            }
            if (slot < 0) {
                PlayerVaults.LOGGER.warn("Vault {} of {}: dropped an item because the vault is full.",
                        vault.number, vault.data.name);
                return false;
            }
            vault.container.setItem(slot, parsed.result().get());
            return true;
        } catch (RuntimeException ex) {
            PlayerVaults.LOGGER.warn("Vault {} of {}: dropped a bad entry ({})",
                    vault.number, vault.data.name, ex.toString());
            return false;
        }
    }

    private static int firstFreeSlot(VaultContainer container) {
        for (int slot = 0; slot < VaultMenu.SIZE; slot++) {
            if (!VaultMenu.isButtonSlot(slot) && container.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    // Copies the vault's items into the player's file data and writes the file.
    private static void save(OpenVault vault) {
        try {
            RegistryOps<JsonElement> ops = ops();
            JsonArray array = new JsonArray();

            for (int slot = 0; slot < VaultMenu.SIZE; slot++) {
                if (VaultMenu.isButtonSlot(slot)) {
                    continue; // the buttons are never stored
                }
                ItemStack stack = vault.container.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                DataResult<JsonElement> encoded = ItemStack.CODEC.encodeStart(ops, stack);
                if (encoded.result().isEmpty()) {
                    PlayerVaults.LOGGER.error("Vault {} of {}: could not save the item in slot {}: {}",
                            vault.number, vault.data.name, slot, stack);
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("slot", slot);
                entry.add("item", encoded.result().get());
                array.add(entry);
            }

            vault.data.vaults.put(vault.number, array);
            vault.data.save();
            vault.container.clearDirty();
            vault.needsSave = false;
        } catch (IOException | UncheckedIOException ex) {
            PlayerVaults.LOGGER.error("Could not save vault {} of {}", vault.number, vault.data.name, ex);
        }
    }
}
