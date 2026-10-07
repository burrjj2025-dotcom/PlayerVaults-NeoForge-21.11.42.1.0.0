package me.jjonlinux.playervaults;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

// Builds the vault items: glass panes used as buttons, and the paper "Player Vaults" item.
//
// These are plain vanilla items with a custom name, so players without the mod see
// ordinary panes and paper. Each one also carries a hidden "custom model data" tag.
// Players WITH the mod have item definitions (in assets/minecraft/items/) that look
// for that tag and show the button icons instead of the plain panes.
public final class VaultItems {
    // Hidden marker stored on the paper so we can recognise it later.
    private static final String TAG_KEY = "player_vaults_item";

    // These must match the names in assets/minecraft/items/*.json and the model files.
    private static final String MODEL_PREVIOUS = PlayerVaults.MODID + ":previous_button";
    private static final String MODEL_NEXT = PlayerVaults.MODID + ":next_button";
    private static final String MODEL_CHAT = PlayerVaults.MODID + ":chat_button";
    private static final String MODEL_VAULT_ITEM = PlayerVaults.MODID + ":vault_item";

    private VaultItems() {}

    private static Component text(String text, ChatFormatting color) {
        // Custom names are italic by default; turn that off.
        return Component.literal(text).withStyle(style -> style.withItalic(false).withColor(color));
    }

    private static ItemStack named(Item item, String title, ChatFormatting titleColor, String lore, String modelKey) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, text(title, titleColor));
        stack.set(DataComponents.LORE, new ItemLore(List.of(text(lore, ChatFormatting.GRAY))));
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(modelKey), List.of()));
        return stack;
    }

    public static ItemStack previousButton(int vaultNumber) {
        String lore = vaultNumber > 1 ? "Opens vault " + (vaultNumber - 1) : "This is the first vault";
        return named(Items.GRAY_STAINED_GLASS_PANE, "Previous vault", ChatFormatting.WHITE, lore, MODEL_PREVIOUS);
    }

    public static ItemStack nextButton(int vaultNumber) {
        return named(Items.GRAY_STAINED_GLASS_PANE, "Next vault", ChatFormatting.WHITE,
                "Opens vault " + (vaultNumber + 1), MODEL_NEXT);
    }

    // Red, so it stands out from the gray slot grid for players without the mod.
    public static ItemStack chatButton() {
        return named(Items.RED_STAINED_GLASS_PANE, "Go to vault...", ChatFormatting.WHITE,
                "Closes this vault so you can type a number in chat", MODEL_CHAT);
    }

    // The item players right-click to open vault 1.
    public static ItemStack createVaultItem() {
        ItemStack stack = named(Items.PAPER, "Player Vaults", ChatFormatting.AQUA,
                "Right-click to open your vault", MODEL_VAULT_ITEM);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_KEY, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isVaultItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(TAG_KEY);
    }
}
