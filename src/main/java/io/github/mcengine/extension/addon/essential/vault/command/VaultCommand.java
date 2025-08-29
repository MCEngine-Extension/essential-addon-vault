package io.github.mcengine.extension.addon.essential.vault.command;

import io.github.mcengine.common.essential.MCEngineEssentialCommon;
import io.github.mcengine.extension.addon.essential.vault.model.PlayerVault;
import io.github.mcengine.extension.addon.essential.vault.util.VaultDB;
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
 */
public class VaultCommand implements CommandExecutor {

    /**
     * Metadata key stored on a player to indicate the next closed inventory
     * should be treated as a Vault inventory to be persisted.
     */
    private static final String META_VAULT_OPEN = "mcengine_vault_open";

    /**
     * Permission node required to open a vault (including via subcommands that open it).
     */
    private static final String PERM_USE = "mcengine.essential.vault.use";

    /**
     * Permission node required to set the vault rows (configuration action).
     */
    private static final String PERM_SET = "mcengine.essential.vault.set";

    /**
     * Executes the {@code /vault} command.
     *
     * <p>Supported subcommands:</p>
     * <ul>
     *   <li>{@code /vault} or {@code /vault open} – open the player's vault (requires {@code mcengine.essential.vault.use})</li>
     *   <li>{@code /vault setrows <1..6>} – set rows & open (requires {@code mcengine.essential.vault.set} and {@code mcengine.essential.vault.use})</li>
     *   <li>{@code /vault settitle <title...>} – open with custom title (requires {@code mcengine.essential.vault.use})</li>
     * </ul>
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

        // Defaults from config if present
        int defaultRows = plugin.getConfig().getInt("vault.rows", 6);
        if (defaultRows < 1) defaultRows = 1;
        if (defaultRows > 6) defaultRows = 6;
        String defaultTitle = plugin.getConfig().getString("vault.title", "Vault");

        String sub = (args.length == 0) ? "open" : args[0].toLowerCase();

        switch (sub) {
            case "open" -> {
                if (!player.hasPermission(PERM_USE)) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use the vault.");
                    return true;
                }
                openVault(player, defaultRows, defaultTitle, plugin);
                return true;
            }
            case "setrows" -> {
                if (!player.hasPermission(PERM_SET)) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to set vault rows.");
                    return true;
                }
                if (!player.hasPermission(PERM_USE)) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use the vault.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /vault setrows <1..6>");
                    return true;
                }
                int rows;
                try {
                    rows = Integer.parseInt(args[1]);
                } catch (NumberFormatException nfe) {
                    player.sendMessage(ChatColor.RED + "Rows must be a number between 1 and 6.");
                    return true;
                }
                rows = Math.max(1, Math.min(6, rows));
                openVault(player, rows, defaultTitle, plugin);
                return true;
            }
            case "settitle" -> {
                if (!player.hasPermission(PERM_USE)) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use the vault.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /vault settitle <title...>");
                    return true;
                }
                String title = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                if (title.isBlank()) title = defaultTitle;
                openVault(player, defaultRows, title, plugin);
                return true;
            }
            default -> {
                // Keep your existing command shape but show helpful usage.
                player.sendMessage(ChatColor.AQUA + "Vault commands:");
                player.sendMessage(ChatColor.GRAY + " • /vault" + ChatColor.DARK_GRAY + " – open your vault");
                player.sendMessage(ChatColor.GRAY + " • /vault setrows <1..6>");
                player.sendMessage(ChatColor.GRAY + " • /vault settitle <title>");
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
        PlayerVault pv = VaultDB.loadPlayerVault(player.getUniqueId(), rows, title);
        Inventory inv = VaultDB.createInventoryFor(pv);

        // Flag this player so InventoryCloseEvent knows to persist.
        player.setMetadata(META_VAULT_OPEN, new FixedMetadataValue(owningPlugin, true));

        player.openInventory(inv);
        player.sendMessage(ChatColor.GREEN + "Vault opened (" + (rows * 9) + " slots).");
        // Optional: async task could refresh, but we keep it simple & synchronous here.
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
