package io.github.mcengine.extension.addon.essential.vault.database.postgresql;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.common.essential.MCEngineEssentialCommon;
import io.github.mcengine.extension.addon.essential.vault.database.VaultDB;
import io.github.mcengine.extension.addon.essential.vault.model.PlayerVault;
import io.github.mcengine.extension.addon.essential.vault.model.VaultItem;
import io.github.mcengine.extension.addon.essential.vault.util.ItemIO;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link VaultDB}.
 */
public class VaultDBPostgreSQL implements VaultDB {

    /** Logger for reporting status and problems. */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs the DB helper.
     *
     * @param logger logger wrapper
     */
    public VaultDBPostgreSQL(MCEngineExtensionLogger logger) {
        this.logger = logger;
    }

    /** DB facade shortcut. */
    private static MCEngineEssentialCommon db() {
        return MCEngineEssentialCommon.getApi();
    }

    /** Escape single quotes for SQL string literal. */
    private static String q(String s) {
        if (s == null) return "NULL";
        return "'" + s.replace("'", "''") + "'";
    }

    /** Convert bytes to upper-hex. */
    private static String toHex(byte[] b) {
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            out[i * 2] = hexArray[v >>> 4];
            out[i * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(out);
    }

    /** Parse hex to bytes. */
    private static byte[] fromHex(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    @Override
    public void ensureSchema() {
        final String createMeta = """
            CREATE TABLE IF NOT EXISTS essential_vault_meta (
                player_uuid VARCHAR(36) PRIMARY KEY,
                rows INT NOT NULL,
                title TEXT,
                updated_at TIMESTAMP
            );
            """;
        final String createItem = """
            CREATE TABLE IF NOT EXISTS essential_vault_item (
                player_uuid VARCHAR(36) NOT NULL,
                page INT NOT NULL DEFAULT 0,
                slot INT NOT NULL,
                item_bytes BYTEA NOT NULL,
                PRIMARY KEY (player_uuid, page, slot)
            );
            """;
        try {
            db().executeQuery(createMeta);
            db().executeQuery(createItem);
            if (logger != null) logger.info("[VaultDB] PostgreSQL schema ensured.");
        } catch (Exception e) {
            if (logger != null) logger.warning("[VaultDB] PostgreSQL schema ensure failed: " + e.getMessage());
        }
    }

    @Override
    public PlayerVault loadPlayerVault(UUID playerId, int defaultRows, String defaultTitle) {
        int rows = defaultRows;
        String title = defaultTitle;
        Map<Integer, VaultItem> items = new HashMap<>();

        try {
            String packed = db().getValue(
                "SELECT (rows::text || '|' || COALESCE(title,'')) FROM essential_vault_meta WHERE player_uuid = " + q(playerId.toString()),
                String.class
            );
            if (packed != null) {
                int sep = packed.indexOf('|');
                if (sep >= 0) {
                    rows = Integer.parseInt(packed.substring(0, sep));
                    String t = packed.substring(sep + 1);
                    if (t != null && !t.isBlank()) title = t;
                }
            }
        } catch (Exception e) {
            if (logger != null) logger.warning("[VaultDB] PostgreSQL load meta failed: " + e.getMessage());
        }

        try {
            String packed = db().getValue(
                "SELECT string_agg(slot::text || ':' || encode(item_bytes,'hex'), ';') " +
                "FROM essential_vault_item WHERE player_uuid = " + q(playerId.toString()) + " AND page = 0",
                String.class
            );
            if (packed != null && !packed.isBlank()) {
                for (String pair : packed.split(";")) {
                    int idx = pair.indexOf(':');
                    if (idx > 0) {
                        int slot = Integer.parseInt(pair.substring(0, idx));
                        byte[] data = fromHex(pair.substring(idx + 1));
                        ItemStack stack = ItemIO.fromBytes(data);
                        if (stack != null) items.put(slot, new VaultItem(slot, stack));
                    }
                }
            }
        } catch (Exception e) {
            if (logger != null) logger.warning("[VaultDB] PostgreSQL load items failed: " + e.getMessage());
        }

        return new PlayerVault(playerId, rows, title, 0, items);
    }

    @Override
    public boolean savePlayerVault(PlayerVault vault, Inventory inventory) {
        try {
            // Upsert meta
            String upsert = "INSERT INTO essential_vault_meta (player_uuid, rows, title, updated_at) VALUES (" +
                    q(vault.getPlayerId().toString()) + ", " + vault.getRows() + ", " + q(vault.getTitle()) + ", CURRENT_TIMESTAMP) " +
                    "ON CONFLICT (player_uuid) DO UPDATE SET rows=EXCLUDED.rows, title=EXCLUDED.title, updated_at=EXCLUDED.updated_at";
            db().executeQuery(upsert);

            // Clear page items
            db().executeQuery("DELETE FROM essential_vault_item WHERE player_uuid = " +
                    q(vault.getPlayerId().toString()) + " AND page = " + vault.getPage());

            // Insert items; use decode(hex,'hex')
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack == null || stack.getType().isAir()) continue;
                byte[] bytes = ItemIO.toBytes(stack);
                if (bytes == null || bytes.length == 0) continue;
                String sql = "INSERT INTO essential_vault_item (player_uuid, page, slot, item_bytes) VALUES (" +
                        q(vault.getPlayerId().toString()) + ", " + vault.getPage() + ", " + slot +
                        ", decode('" + toHex(bytes) + "','hex'))";
                db().executeQuery(sql);
            }

            if (logger != null) logger.info("[VaultDB] PostgreSQL saved vault for " + vault.getPlayerId());
            return true;
        } catch (Exception e) {
            if (logger != null) logger.warning("[VaultDB] PostgreSQL save failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean clearPlayerVault(UUID playerId) {
        try {
            db().executeQuery("DELETE FROM essential_vault_item WHERE player_uuid = " + q(playerId.toString()));
            db().executeQuery("DELETE FROM essential_vault_meta WHERE player_uuid = " + q(playerId.toString()));
            return true;
        } catch (Exception e) {
            if (logger != null) logger.warning("[VaultDB] PostgreSQL clear failed: " + e.getMessage());
            return false;
        }
    }
}
