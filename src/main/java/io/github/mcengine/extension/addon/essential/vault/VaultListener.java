package io.github.mcengine.extension.addon.essential.vault;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Example event listener for the Vault extension.
 */
public class VaultListener implements Listener {

    /**
     * Plugin instance used by this listener.
     */
    private final Plugin plugin;

    /**
     * Constructs a new {@link VaultListener}.
     *
     * @param plugin The plugin instance.
     */
    public VaultListener(Plugin plugin) {
        this.plugin = plugin;
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
     * Handles player quit event and logs the departure.
     *
     * @param event The player quit event.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Bukkit.getLogger().info("[Vault][essential-addon-vault] " + player.getName() + " has left the server.");
    }
}
