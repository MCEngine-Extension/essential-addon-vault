package io.github.mcengine.extension.addon.essential.vault.model;

import java.util.Map;
import java.util.UUID;

/**
 * Mutable model representing a player's vault configuration and contents.
 */
public class PlayerVault {

    /**
     * Unique identifier of the player owning this vault.
     */
    private final UUID playerId;

    /**
     * Number of inventory rows (commonly 1..6).
     */
    private final int rows;

    /**
     * Inventory title displayed to the player.
     */
    private final String title;

    /**
     * Page index (reserved for future multi-page vaults).
     */
    private final int page;

    /**
     * Map of slot index to item, representing the current contents.
     */
    private Map<Integer, VaultItem> items;

    /**
     * Constructs a new player vault model.
     *
     * @param playerId player UUID
     * @param rows     number of rows (1..6 typical)
     * @param title    inventory title (nullable)
     * @param page     page index (default 0)
     * @param items    initial item map
     */
    public PlayerVault(UUID playerId, int rows, String title, int page, Map<Integer, VaultItem> items) {
        this.playerId = playerId;
        this.rows = rows;
        this.title = title;
        this.page = page;
        this.items = items;
    }

    /**
     * @return owner player UUID
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * @return number of inventory rows
     */
    public int getRows() {
        return rows;
    }

    /**
     * @return inventory title (may be null)
     */
    public String getTitle() {
        return title;
    }

    /**
     * @return page index
     */
    public int getPage() {
        return page;
    }

    /**
     * @return current items map
     */
    public Map<Integer, VaultItem> getItems() {
        return items;
    }

    /**
     * Replaces the current items map.
     *
     * @param items new item map
     */
    public void setItems(Map<Integer, VaultItem> items) {
        this.items = items;
    }
}
