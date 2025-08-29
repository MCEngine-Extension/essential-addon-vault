package io.github.mcengine.extension.addon.essential.vault.util;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.common.essential.MCEngineEssentialCommon;
import io.github.mcengine.extension.addon.essential.vault.model.PlayerVault;
import io.github.mcengine.extension.addon.essential.vault.model.VaultItem;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Database utility for the Essential Vault AddOn.
 *
 * <p>This class manages schema creation and CRUD operations for per-player vault
 * data using the shared Essential database connection provided by
 * {@link MCEngineEssentialCommon}. Items (including full metadata/NBT) are stored
 * as a binary payload via {@link ItemIO} serialization.</p>
 *
 * <h3>Tables</h3>
 * <ul>
 *   <li><b>essential_vault_meta</b>:
 *     <ul>
 *       <li>player_uuid (PK)</li>
 *       <li>rows (int) – inventory rows (multiple of 1..6)</li>
 *       <li>title (text) – UI title (nullable)</li>
 *       <li>updated_at (timestamp)</li>
 *     </ul>
 *   </li>
 *   <li><b>essential_vault_item</b>:
 *     <ul>
 *       <li>player_uuid</li>
 *       <li>page (int) – reserved for future multi-page vaults (default 0)</li>
 *       <li>slot (int)</li>
 *       <li>item_bytes (blob/bytea)</li>
 *       <li>PRIMARY KEY (player_uuid, page, slot)</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class VaultDB {

    /**
     * Cached plugin reference used for configuration and scheduler access.
     */
    private static Plugin plugin;

    /**
     * Logger used for schema/setup and operational diagnostics.
     */
    private static MCEngineExtensionLogger logger;

    /**
     * Database-specific binary column type, resolved on first schema ensure
     * (e.g., "BLOB" for SQLite/MySQL, "BYTEA" for PostgreSQL).
     */
    private static String binType;

    private VaultDB() {
        // Utility class
    }

    /**
     * Initializes and ensures the vault schema exists.
     *
     * <p>Call this once during your AddOn startup (e.g., in {@code onLoad}). It is
     * safe to call multiple times; DDL uses IF NOT EXISTS / equivalent.</p>
     *
     * @param ownerPlugin the owning Bukkit plugin
     * @param extLogger   the add-on logger
     */
    public static synchronized void ensureSchema(Plugin ownerPlugin, MCEngineExtensionLogger extLogger) {
        plugin = ownerPlugin;
        logger = extLogger;

        try (Connection conn = MCEngineEssentialCommon.getApi().getDBConnection()) {
            if (conn == null) {
                if (logger != null) logger.warning("[VaultDB] No DB connection available; schema ensure skipped.");
                return;
            }

            // Resolve binary type per backend
            binType = resolveBinaryType(conn);

            // Create meta table
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS essential_vault_meta (
                            player_uuid VARCHAR(36) PRIMARY KEY,
                            rows INT NOT NULL,
                            title TEXT,
                            updated_at TIMESTAMP
                        )
                        """);
            }

            // Create item table with composite primary key
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS essential_vault_item (
                            player_uuid VARCHAR(36) NOT NULL,
                            page INT NOT NULL DEFAULT 0,
                            slot INT NOT NULL,
                            item_bytes %s NOT NULL,
                            PRIMARY KEY (player_uuid, page, slot)
                        )
                        """.formatted(binType));
            }

            if (logger != null) logger.info("[VaultDB] Schema ensured (binType=" + binType + ").");
        } catch (SQLException e) {
            if (logger != null) logger.warning("[VaultDB] Schema ensure failed: " + e.getMessage());
        }
    }

    /**
     * Loads a player's vault from the database; if no metadata exists, a new
     * {@link PlayerVault} is synthesized using the provided defaults.
     *
     * @param playerId the player UUID
     * @param defaultRows default number of rows (e.g., 6 for a 54-slot vault)
     * @param defaultTitle default inventory title
     * @return a {@link PlayerVault} containing meta and items
     */
    public static PlayerVault loadPlayerVault(UUID playerId, int defaultRows, String defaultTitle) {
        Objects.requireNonNull(playerId, "playerId");
        ensureDefaults();

        try (Connection conn = MCEngineEssentialCommon.getApi().getDBConnection()) {
            if (conn == null) {
                if (logger != null) logger.warning("[VaultDB] No DB connection; returning empty vault.");
                return new PlayerVault(playerId, defaultRows, defaultTitle, 0, new HashMap<>());
            }

            // Load meta or default
            int rows = defaultRows;
            String title = defaultTitle;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT rows, title FROM essential_vault_meta WHERE player_uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        rows = rs.getInt(1);
                        title = rs.getString(2);
                        if (title == null) title = defaultTitle;
                    }
                }
            }

            // Load items for page 0
            Map<Integer, VaultItem> items = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT slot, item_bytes FROM essential_vault_item WHERE player_uuid = ? AND page = 0")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int slot = rs.getInt(1);
                        byte[] data = rs.getBytes(2);
                        ItemStack stack = ItemIO.fromBytes(data);
                        if (stack != null) {
                            items.put(slot, new VaultItem(slot, stack));
                        }
                    }
                }
            }

            return new PlayerVault(playerId, rows, title, 0, items);
        } catch (SQLException e) {
            if (logger != null) logger.warning("[VaultDB] loadPlayerVault failed: " + e.getMessage());
            return new PlayerVault(playerId, defaultRows, defaultTitle, 0, new HashMap<>());
        }
    }

    /**
     * Persists the provided {@link PlayerVault} metadata and all non-null items in
     * the given inventory to the database in a single transaction (per page).
     *
     * <p>Persistence behavior:</p>
     * <ul>
     *   <li>Upserts {@code essential_vault_meta} (rows/title/updated_at).</li>
     *   <li>Deletes existing {@code essential_vault_item} rows for the same player/page.</li>
     *   <li>Inserts an entry for each non-null inventory slot.</li>
     * </ul>
     *
     * @param vault     the vault metadata
     * @param inventory the inventory to persist (its size should match {@code rows * 9})
     */
    public static void savePlayerVault(PlayerVault vault, Inventory inventory) {
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(inventory, "inventory");
        ensureDefaults();

        final int page = vault.getPage();
        final int expectedSize = vault.getRows() * 9;
        if (inventory.getSize() != expectedSize && logger != null) {
            logger.warning("[VaultDB] Inventory size (" + inventory.getSize() + ") does not match rows*9 (" + expectedSize + "). Proceeding anyway.");
        }

        try (Connection conn = MCEngineEssentialCommon.getApi().getDBConnection()) {
            if (conn == null) {
                if (logger != null) logger.warning("[VaultDB] No DB connection; save skipped.");
                return;
            }

            conn.setAutoCommit(false);
            try {
                // Upsert meta
                // Try update first; if 0 rows, insert.
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE essential_vault_meta SET rows=?, title=?, updated_at=? WHERE player_uuid=?")) {
                    upd.setInt(1, vault.getRows());
                    upd.setString(2, vault.getTitle());
                    upd.setTimestamp(3, Timestamp.from(Instant.now()));
                    upd.setString(4, vault.getPlayerId().toString());
                    int updated = upd.executeUpdate();
                    if (updated == 0) {
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO essential_vault_meta (player_uuid, rows, title, updated_at) VALUES (?,?,?,?)")) {
                            ins.setString(1, vault.getPlayerId().toString());
                            ins.setInt(2, vault.getRows());
                            ins.setString(3, vault.getTitle());
                            ins.setTimestamp(4, Timestamp.from(Instant.now()));
                            ins.executeUpdate();
                        }
                    }
                }

                // Clear existing items for this page
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM essential_vault_item WHERE player_uuid=? AND page=?")) {
                    del.setString(1, vault.getPlayerId().toString());
                    del.setInt(2, page);
                    del.executeUpdate();
                }

                // Insert items
                String sql = "INSERT INTO essential_vault_item (player_uuid, page, slot, item_bytes) VALUES (?,?,?,?)";
                try (PreparedStatement ins = conn.prepareStatement(sql)) {
                    for (int slot = 0; slot < inventory.getSize(); slot++) {
                        ItemStack stack = inventory.getItem(slot);
                        if (stack == null || stack.getType().isAir()) continue;
                        byte[] bytes = ItemIO.toBytes(stack);
                        if (bytes == null || bytes.length == 0) continue;

                        ins.setString(1, vault.getPlayerId().toString());
                        ins.setInt(2, page);
                        ins.setInt(3, slot);
                        ins.setBytes(4, bytes);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }

                conn.commit();
                if (logger != null) logger.info("[VaultDB] Saved vault for " + vault.getPlayerId() + " (page=" + page + ").");
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            if (logger != null) logger.warning("[VaultDB] savePlayerVault failed: " + e.getMessage());
        }
    }

    /**
     * Deletes all stored vault items for a player (all pages) and removes metadata.
     *
     * @param playerId player UUID
     */
    public static void clearPlayerVault(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        ensureDefaults();

        try (Connection conn = MCEngineEssentialCommon.getApi().getDBConnection()) {
            if (conn == null) {
                if (logger != null) logger.warning("[VaultDB] No DB connection; clear skipped.");
                return;
            }
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement delItems = conn.prepareStatement(
                        "DELETE FROM essential_vault_item WHERE player_uuid=?")) {
                    delItems.setString(1, playerId.toString());
                    delItems.executeUpdate();
                }
                try (PreparedStatement delMeta = conn.prepareStatement(
                        "DELETE FROM essential_vault_meta WHERE player_uuid=?")) {
                    delMeta.setString(1, playerId.toString());
                    delMeta.executeUpdate();
                }
                conn.commit();
                if (logger != null) logger.info("[VaultDB] Cleared vault for " + playerId + ".");
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            if (logger != null) logger.warning("[VaultDB] clearPlayerVault failed: " + e.getMessage());
        }
    }

    /**
     * Constructs a Bukkit {@link Inventory} instance using the vault's rows/title and
     * populates it with the loaded items.
     *
     * @param vault the player vault model
     * @return a populated {@link Inventory}
     */
    public static Inventory createInventoryFor(PlayerVault vault) {
        Objects.requireNonNull(vault, "vault");
        Inventory inv = Bukkit.createInventory(
                /* owner = */ null,
                Math.max(9, Math.min(54, vault.getRows() * 9)),
                vault.getTitle() == null ? "Vault" : vault.getTitle()
        );
        vault.getItems().forEach((slot, vItem) -> inv.setItem(slot, vItem.getItem()));
        return inv;
    }

    /**
     * Captures an {@link Inventory} into a {@link PlayerVault}'s item map (in-memory only).
     * Use {@link #savePlayerVault(PlayerVault, Inventory)} to persist.
     *
     * @param vault the vault model to fill
     * @param inv   the inventory to read
     */
    public static void captureInventory(PlayerVault vault, Inventory inv) {
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(inv, "inv");
        Map<Integer, VaultItem> map = new HashMap<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            map.put(i, new VaultItem(i, stack.clone()));
        }
        vault.setItems(map);
    }

    // --------------------
    // Internal helpers
    // --------------------

    private static void ensureDefaults() {
        if (plugin == null) plugin = MCEngineEssentialCommon.getApi() != null ? MCEngineEssentialCommon.getApi().getPlugin() : null;
        if (logger == null && plugin != null) {
            logger = new MCEngineExtensionLogger(plugin, "AddOn", "EssentialVault");
        }
        if (binType == null) {
            try (Connection c = MCEngineEssentialCommon.getApi().getDBConnection()) {
                if (c != null) binType = resolveBinaryType(c);
            } catch (SQLException ignored) {}
            if (binType == null) binType = "BLOB";
        }
    }

    private static String resolveBinaryType(Connection conn) throws SQLException {
        String prod = conn.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (prod.contains("postgres")) return "BYTEA";
        // SQLite/MySQL, MariaDB, etc.
        return "BLOB";
    }
}
