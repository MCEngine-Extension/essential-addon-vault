package io.github.mcengine.extension.addon.essential.vault.listener;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.vault.command.VaultCommand;
import io.github.mcengine.extension.addon.essential.vault.model.PlayerVault;
import io.github.mcengine.extension.addon.essential.vault.database.VaultDB;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Event listener for the Vault extension.
 */
public class VaultListener implements Listener {

    /**
     * Plugin instance used by this listener for task scheduling and metadata context.
     */
    private final Plugin plugin;

    /**
     * Logger instance for the Vault extension.
     * <p>
     * Used for lightweight diagnostics around open/close persistence.
     */
    private final MCEngineExtensionLogger logger;

    /**
     * Database accessor for vault persistence.
     */
    private final VaultDB vaultDB;

    /**
     * Constructs a new {@link VaultListener}.
     *
     * @param plugin The plugin instance.
     * @param logger The logger instance.
     * @param vaultDB Database accessor to use.
     */
    public VaultListener(Plugin plugin, MCEngineExtensionLogger logger, VaultDB vaultDB) {
        this.plugin = plugin;
        this.logger = logger;
        this.vaultDB = vaultDB;
    }

    /**
     * Persists the player's vault contents when they close a vault inventory.
     *
     * <p>Detection is based on a metadata flag set by {@code /vault} command:
     * the first inventory closed after opening is treated as the vault and saved.</p>
     *
     * @param event inventory close event
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // Check vault-open metadata
        List<MetadataValue> meta = player.getMetadata(VaultCommand.metaKey());
        boolean flagged = meta.stream().anyMatch(m -> m != null && m.asBoolean());
        if (!flagged) return;

        // Clear flag immediately to avoid double-saves on other closes
        player.removeMetadata(VaultCommand.metaKey(), plugin);

        // Persist this inventory as the player's vault
        Inventory inv = event.getInventory();
        int rows = Math.max(1, Math.min(6, inv.getSize() / 9));
        String title = event.getView().getTitle();

        PlayerVault pv = new PlayerVault(player.getUniqueId(), rows, title, 0, java.util.Collections.emptyMap());
        vaultDB.captureInventory(pv, inv);
        boolean ok = vaultDB.savePlayerVault(pv, inv);

        if (ok) {
            player.sendMessage(ChatColor.GREEN + "Vault saved.");
            logger.info("Persisted vault for " + player.getName() + " (" + (rows * 9) + " slots).");
        } else {
            player.sendMessage(ChatColor.RED + "Vault could not be saved. Please contact an admin.");
            logger.warning("Failed to persist vault for " + player.getName() + ".");
        }
    }
}
