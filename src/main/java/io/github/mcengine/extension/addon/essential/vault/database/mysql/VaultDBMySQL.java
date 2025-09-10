package io.github.mcengine.extension.addon.essential.vault.database.mysql;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.vault.database.VaultDB;
import io.github.mcengine.extension.addon.essential.vault.model.PlayerVault;
import io.github.mcengine.extension.addon.essential.vault.model.VaultItem;
import io.github.mcengine.extension.addon.essential.vault.util.ItemIO;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MySQL implementation of {@link VaultDB}.
 */
public class VaultDBMySQL implements VaultDB {

    /** Active JDBC connection supplied by the Essential module. */
    private final Connection conn;

    /** Logger for reporting status and problems. */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs the DB helper.
     *
     * @param conn   JDBC connection
     * @param logger logger wrapper
     */
    public VaultDBMySQL(Connection conn, MCEngineExtensionLogger logger) {
        this.conn = conn;
        this.logger = logger;
    }

    @Override
    public void ensureSchema() {
        final String createMeta = """
            CREATE TABLE IF NOT EXISTS essential_vault_meta (
                player_uuid VARCHAR(36) PRIMARY KEY,
                rows INT NOT NULL,
                title TEXT,
                updated_at TIMESTAMP NULL
            ) ENGINE=InnoDB;
            """;
        final String createItem = """
            CREATE TABLE IF NOT EXISTS essential_vault_item (
                player_uuid VARCHAR(36) NOT NULL,
                page INT NOT NULL DEFAULT 0,
                slot INT NOT NULL,
                item_bytes BLOB NOT NULL,
                PRIMARY KEY (player_uuid, page, slot)
            ) ENGINE=InnoDB;
            """;
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(createMeta);
            st.executeUpdate(createItem);
            if (logger != null) logger.info("[VaultDB] MySQL schema ensured.");
        } catch (SQLException e) {
            if (logger != null) logger.warning("[VaultDB] MySQL schema ensure failed: " + e.getMessage());
        }
    }

    @Override
    public PlayerVault loadPlayerVault(UUID playerId, int defaultRows, String defaultTitle) {
        int rows = defaultRows;
        String title = defaultTitle;
        Map<Integer, VaultItem> items = new HashMap<>();

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
        } catch (SQLException e) {
            if (logger != null) logger.warning("[VaultDB] MySQL load meta failed: " + e.getMessage());
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT slot, item_bytes FROM essential_vault_item WHERE player_uuid = ? AND page = 0")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt(1);
                    byte[] data = rs.getBytes(2);
                    ItemStack stack = ItemIO.fromBytes(data);
                    if (stack != null) items.put(slot, new VaultItem(slot, stack));
                }
            }
        } catch (SQLException e) {
            if (logger != null) logger.warning("[VaultDB] MySQL load items failed: " + e.getMessage());
        }

        return new PlayerVault(playerId, rows, title, 0, items);
    }

    @Override
    public boolean savePlayerVault(PlayerVault vault, Inventory inventory) {
        boolean originalAutoCommit;
        try { originalAutoCommit = conn.getAutoCommit(); } catch (SQLException e) { originalAutoCommit = true; }
        try {
            conn.setAutoCommit(false);

            boolean hasMeta = false;
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT 1 FROM essential_vault_meta WHERE player_uuid=?")) {
                chk.setString(1, vault.getPlayerId().toString());
                try (ResultSet rs = chk.executeQuery()) { hasMeta = rs.next(); }
            }

            if (hasMeta) {
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE essential_vault_meta SET updated_at=? WHERE player_uuid=?")) {
                    upd.setTimestamp(1, Timestamp.from(Instant.now()));
                    upd.setString(2, vault.getPlayerId().toString());
                    upd.executeUpdate();
                }
            } else {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO essential_vault_meta (player_uuid, rows, title, updated_at) VALUES (?,?,?,?)")) {
                    ins.setString(1, vault.getPlayerId().toString());
                    ins.setInt(2, vault.getRows());
                    ins.setString(3, vault.getTitle());
                    ins.setTimestamp(4, Timestamp.from(Instant.now()));
                    ins.executeUpdate();
                }
            }

            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM essential_vault_item WHERE player_uuid=? AND page=?")) {
                del.setString(1, vault.getPlayerId().toString());
                del.setInt(2, vault.getPage());
                del.executeUpdate();
            }

            String sql = "INSERT INTO essential_vault_item (player_uuid, page, slot, item_bytes) VALUES (?,?,?,?)";
            try (PreparedStatement ins = conn.prepareStatement(sql)) {
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack == null || stack.getType().isAir()) continue;
                    byte[] bytes = ItemIO.toBytes(stack);
                    if (bytes == null || bytes.length == 0) continue;

                    ins.setString(1, vault.getPlayerId().toString());
                    ins.setInt(2, vault.getPage());
                    ins.setInt(3, slot);
                    ins.setBytes(4, bytes);
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            conn.commit();
            if (logger != null) logger.info("[VaultDB] MySQL saved vault for " + vault.getPlayerId());
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignore) {}
            if (logger != null) logger.warning("[VaultDB] MySQL save failed: " + e.getMessage());
            return false;
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException ignore) {}
        }
    }

    @Override
    public boolean clearPlayerVault(UUID playerId) {
        boolean originalAutoCommit;
        try { originalAutoCommit = conn.getAutoCommit(); } catch (SQLException e) { originalAutoCommit = true; }
        try {
            conn.setAutoCommit(false);
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
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignore) {}
            if (logger != null) logger.warning("[VaultDB] MySQL clear failed: " + e.getMessage());
            return false;
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException ignore) {}
        }
    }
}
