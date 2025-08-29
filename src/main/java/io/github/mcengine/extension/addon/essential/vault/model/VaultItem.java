package io.github.mcengine.extension.addon.essential.vault.model;

import org.bukkit.inventory.ItemStack;

/**
 * Immutable value object representing an item stored in the player's vault.
 */
public final class VaultItem {

    /**
     * Zero-based inventory slot index.
     */
    private final int slot;

    /**
     * The stored Bukkit item stack (includes metadata/NBT).
     */
    private final ItemStack item;

    /**
     * Creates a new VaultItem.
     *
     * @param slot zero-based slot index
     * @param item Bukkit item with full metadata
     */
    public VaultItem(int slot, ItemStack item) {
        this.slot = slot;
        this.item = item;
    }

    /**
     * @return the zero-based slot index
     */
    public int getSlot() {
        return slot;
    }

    /**
     * @return the stored {@link ItemStack}
     */
    public ItemStack getItem() {
        return item;
    }
}
