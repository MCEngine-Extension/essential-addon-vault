package io.github.mcengine.extension.addon.essential.vault.command;

import io.github.mcengine.common.essential.MCEngineEssentialCommon;
import io.github.mcengine.extension.addon.essential.vault.model.PlayerVault;
import io.github.mcengine.extension.addon.essential.vault.util.db.VaultDB;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

/**
 * Handles {@code /vault} command logic for the Vault extension.
 *
 * <p>Supported usage:</p>
 * <ul>
 *   <li>{@code /vault} – open the player's vault (requires {@code mcengine.essential.vault.use})</li>
 *   <li>{@code /vault open} – same as above</li>
 * </ul>
 */
public class VaultCommand implements CommandExecutor {

    /**
     * Metadata key stored on a player to indicate the next closed inventory
     * should be treated as a Vault inventory to be persisted.
     */
    private static final String META_VAULT_OPEN = "mcengine_vault_open";

    /**
     * Permission node required to open a vault.
     */
    private static final String PERM_USE = "mcengine.essential.vault.use";

    /**
     * Database accessor for vault operations.
     */
    private final VaultDB vaultDB;

    /**
     * Constructs a {@link VaultCommand} with a DB accessor.
     *
     * @param vaultDB database accessor
     */
    public VaultCommand(VaultDB vaultDB) {
        this.vaultDB = vaultDB;
    }

    /**
     * Executes the {@code /vault} command.
     *
     * @param sender  The source of the command.
     * @param command The command which was executed.
     * @param label   The alias used.
     * @param args    The command arguments.
     * @return true if command executed successfully.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /vault.");
            return true;
        }

        Plugin plugin = MCEngineEssentialCommon.getApi() != null ? MCEngineEssentialCommon.getApi().getPlugin() : null;
        if (plugin == null) {
            sender.sendMessage(ChatColor.RED + "Vault is not initialized yet.");
            return true;
        }

        if (!player.hasPermission(PERM_USE)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use the vault.");
            return true;
        }

        // Defaults from config if present
        int defaultRows = plugin.getConfig().getInt("vault.rows", 6);
        if (defaultRows < 1) defaultRows = 1;
        if (defaultRows > 6) defaultRows = 6;
        String defaultTitle = plugin.getConfig().getString("vault.title", "Vault");

        String sub = (args.length == 0) ? "open" : args[0].toLowerCase();

        switch (sub) {
            case "open" -> {
                openVault(player, defaultRows, defaultTitle, plugin);
                return true;
            }
            default -> {
                // Minimal help now that setrows/settitle are removed
                player.sendMessage(ChatColor.AQUA + "Vault commands:");
                player.sendMessage(ChatColor.GRAY + " • /vault" + ChatColor.DARK_GRAY + " – open your vault");
                player.sendMessage(ChatColor.GRAY + " • /vault open");
                return true;
            }
        }
    }

    /**
     * Opens the player's vault inventory and tags the player with a metadata flag
     * so the listener can persist it on close.
     *
     * @param player       the player
     * @param rows         number of rows to open with (1..6)
     * @param title        inventory title
     * @param owningPlugin plugin instance for metadata association
     */
    private void openVault(Player player, int rows, String title, Plugin owningPlugin) {
        PlayerVault pv = vaultDB.loadPlayerVault(player.getUniqueId(), rows, title);
        Inventory inv = vaultDB.createInventoryFor(pv);

        // Flag this player so InventoryCloseEvent knows to persist.
        player.setMetadata(META_VAULT_OPEN, new FixedMetadataValue(owningPlugin, true));

        player.openInventory(inv);
        player.sendMessage(ChatColor.GREEN + "Vault opened (" + (rows * 9) + " slots).");
        Bukkit.getScheduler().runTaskLater(owningPlugin, () -> { /* no-op hook */ }, 1L);
    }

    /**
     * Exposes the metadata key used by the listener to detect a "vault session".
     *
     * @return metadata key for a vault-open session
     */
    public static String metaKey() {
        return META_VAULT_OPEN;
    }
}
