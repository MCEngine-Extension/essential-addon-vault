package io.github.mcengine.extension.addon.essential.vault.util.db;

import io.github.mcengine.extension.addon.essential.vault.model.PlayerVault;
import io.github.mcengine.extension.addon.essential.vault.model.VaultItem;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abstraction for Vault database operations to support multiple SQL dialects.
 *
 * <p>Implementations must create/ensure the following logical schema:</p>
 * <ul>
 *   <li><strong>essential_vault_meta</strong>(player_uuid PK, rows, title, updated_at)</li>
 *   <li><strong>essential_vault_item</strong>(player_uuid, page, slot, item_bytes, PK(player_uuid,page,slot))</li>
 * </ul>
 */
public interface VaultDB {

    /** Creates required tables if they don't already exist. */
    void ensureSchema();

    /** Loads a player's vault or synthesizes one with defaults. */
    PlayerVault loadPlayerVault(UUID playerId, int defaultRows, String defaultTitle);

    /** Persists meta and items for a given vault page. */
    boolean savePlayerVault(PlayerVault vault, Inventory inventory);

    /** Deletes a player's vault meta and all items. */
    boolean clearPlayerVault(UUID playerId);

    /**
     * Constructs a Bukkit {@link Inventory} using vault rows/title and fills items.
     *
     * @param vault the player vault model
     * @return populated inventory
     */
    default Inventory createInventoryFor(PlayerVault vault) {
        Inventory inv = Bukkit.createInventory(
                null,
                Math.max(9, Math.min(54, vault.getRows() * 9)),
                vault.getTitle() == null ? "Vault" : vault.getTitle()
        );
        vault.getItems().forEach((slot, vItem) -> inv.setItem(slot, vItem.getItem()));
        return inv;
    }

    /**
     * Captures an {@link Inventory} into a {@link PlayerVault}'s item map (in-memory only).
     * Call {@link #savePlayerVault(PlayerVault, Inventory)} to persist.
     *
     * @param vault player vault model
     * @param inv   inventory to read from
     */
    default void captureInventory(PlayerVault vault, Inventory inv) {
        Map<Integer, VaultItem> map = new HashMap<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            map.put(i, new VaultItem(i, stack.clone()));
        }
        vault.setItems(map);
    }
}
