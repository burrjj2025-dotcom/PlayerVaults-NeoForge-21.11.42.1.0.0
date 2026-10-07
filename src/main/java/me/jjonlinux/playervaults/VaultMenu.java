package me.jjonlinux.playervaults;

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// A normal 9x6 chest menu (so players without the mod see a regular double chest),
// except that the three button slots in the top row act as buttons.
public class VaultMenu extends AbstractContainerMenu {
    public static final int SIZE = 54;
    public static final int PREVIOUS_SLOT = 0;
    public static final int CHAT_SLOT = 4;
    public static final int NEXT_SLOT = 8;

    private final VaultManager.OpenVault vault;
    private boolean released;

    public VaultMenu(int containerId, Inventory playerInventory, VaultManager.OpenVault vault) {
        super(MenuType.GENERIC_9x6, containerId);
        this.vault = vault;
        this.vault.viewers++;
        checkContainerSize(vault.container, SIZE);

        // Vault slots (same layout as a vanilla double chest)
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                if (isButtonSlot(index)) {
                    this.addSlot(new ButtonSlot(vault.container, index, x, y));
                } else {
                    this.addSlot(new Slot(vault.container, index, x, y));
                }
            }
        }
        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 139 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 197));
        }
    }

    public static boolean isButtonSlot(int index) {
        return index == PREVIOUS_SLOT || index == CHAT_SLOT || index == NEXT_SLOT;
    }

    // A slot that can never have anything put in it or taken out of it.
    private static class ButtonSlot extends Slot {
        ButtonSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < SIZE && isButtonSlot(slotId)) {
            // Undo whatever the client guessed would happen, then run the button.
            this.sendAllDataToRemote();
            boolean plainClick = clickType == ClickType.PICKUP && (button == 0 || button == 1);
            if (plainClick && player instanceof ServerPlayer serverPlayer) {
                pressButton(slotId, serverPlayer);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void pressButton(int slot, ServerPlayer player) {
        UUID owner = this.vault.owner;
        String ownerName = this.vault.data.name;
        int number = this.vault.number;

        if (slot == PREVIOUS_SLOT) {
            if (number > 1) {
                VaultManager.defer(() -> VaultManager.open(player, owner, ownerName, number - 1));
            }
        } else if (slot == NEXT_SLOT) {
            VaultManager.defer(() -> VaultManager.open(player, owner, ownerName, number + 1));
        } else if (slot == CHAT_SLOT) {
            VaultManager.defer(() -> VaultEvents.startPrompt(player, owner, ownerName));
        }
    }

    // Shift-click: vault <-> inventory, never touching the buttons.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isButtonSlot(index)) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SIZE) {
            if (!this.moveItemStackTo(stack, SIZE, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, SIZE, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        releaseOnce();
    }

    // Safe to call more than once (closing and logging out can both reach here).
    public void releaseOnce() {
        if (!this.released) {
            this.released = true;
            VaultManager.release(this.vault);
        }
    }
}
