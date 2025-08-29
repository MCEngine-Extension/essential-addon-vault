package io.github.mcengine.extension.addon.essential.vault.listener;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Event listener for the Vault extension.
 */
public class VaultListener implements Listener {

    /** Plugin instance used by this listener. */
    private final Plugin plugin;

    /** Logger instance for the Vault extension. */
    private final MCEngineExtensionLogger logger;

    /**
     * Constructs a new {@link VaultListener}.
     *
     * @param plugin The plugin instance.
     * @param logger The logger instance.
     */
    public VaultListener(Plugin plugin, MCEngineExtensionLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    /**
     * Handles player join event and sends a welcome message.
     *
     * @param event The player join event.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(ChatColor.AQUA + "[Vault][essential-addon-vault] Hello " + player.getName() + ", enjoy your time!");
    }

    /**
     * Handles player quit event and logs the departure using the extension logger.
     *
     * @param event The player quit event.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        logger.info(player.getName() + " has left the server.");
    }
}
